package fr.codetics.ev_server.ocpi

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.sha256
import fr.codetics.ev_server.persistence.PartnerEntity
import fr.codetics.ev_server.persistence.PartnerRepository
import fr.codetics.ev_server.persistence.TenantRepository
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

class OcpiFault(
    val status: Int,
    override val message: String,
    val http: HttpStatus = HttpStatus.OK
) : RuntimeException(message)

@Component
class SecretVault(
    @param:Value("\${app.encryption-key}") private val key: String
) {
    private fun secret(): SecretKeySpec {
        val bytes = try {
            Base64.getDecoder().decode(key)
        } catch (_: IllegalArgumentException) {
            byteArrayOf()
        }
        check(bytes.size == 32) {
            "Set APP_ENCRYPTION_KEY to a Base64 encoded random 32-byte key before configuring OCPI"
        }

        return SecretKeySpec(bytes, "AES")
    }

    fun encrypt(value: String): String {
        val nonce = ByteArray(12)
        SecureRandom().nextBytes(nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret(), GCMParameterSpec(128, nonce))

        return Base64.getEncoder().encodeToString(nonce + cipher.doFinal(value.toByteArray()))
    }

    fun decrypt(value: String): String {
        val bytes = Base64.getDecoder().decode(value)
        require(bytes.size >= 28)
        val nonce = bytes.copyOfRange(0, 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, nonce))

        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)))
    }
}

@Component
class OcpiTransport(
    private val db: Database,
    private val vault: SecretVault,
    private val partners: PartnerRepository,
    private val tenants: TenantRepository
) {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    fun authenticate(authorization: String?, role: String): PartnerEntity {
        if (authorization == null || !authorization.startsWith("Token ") || authorization.length > 100) {
            throw OcpiFault(2001, "Invalid token", HttpStatus.UNAUTHORIZED)
        }

        val partner = partners.findByTokenHashAndLocalRoleAndEnabledTrue(
            sha256(authorization.removePrefix("Token ")),
            role
        ) ?: throw OcpiFault(2001, "Invalid token", HttpStatus.UNAUTHORIZED)
        val tenant = tenants.findById(partner.tenantId).orElse(null)

        if (tenant == null || !tenant.active) {
            throw OcpiFault(2001, "Tenant is unavailable", HttpStatus.FORBIDDEN)
        }

        return partner
    }

    fun validateRemote(url: String, pinned: String): URI {
        val uri = URI(url)
        val expected = URI(pinned)
        require(uri.scheme == "https" && uri.host != null) {
            "OCPI remote URL must use HTTPS"
        }
        require(uri.userInfo == null && uri.fragment == null && uri.port in setOf(-1, 443)) {
            "OCPI remote URL must use port 443 without credentials or fragments"
        }
        require(uri.host.equals(expected.host, true) && uri.scheme == expected.scheme && uri.port == expected.port) {
            "Remote URL must match the partner's configured origin"
        }

        return uri
    }

    fun request(
        partner: PartnerEntity,
        url: String,
        method: String = "GET",
        body: Any? = null,
        token: String? = null
    ): JsonNode {
        val pinned = partner.remoteUrl
            ?: throw OcpiFault(3001, "Configure a remote discovery URL first")
        val uri = validateRemote(url, pinned)
        val credential = token ?: partner.remoteTokenEncrypted?.let(vault::decrypt)
            ?: throw OcpiFault(3001, "Missing remote credentials")
        val publisher = if (body == null) {
            HttpRequest.BodyPublishers.noBody()
        } else {
            HttpRequest.BodyPublishers.ofString(db.encode(body))
        }
        val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(10))
            .header("Authorization", "Token $credential")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .method(method, publisher)
            .build()

        try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            val bytes = response.body().use { stream ->
                stream.readNBytes(2_097_153)
            }

            if (response.statusCode() !in 200..299 || bytes.size > 2_097_152) {
                throw OcpiFault(3001, "Remote endpoint rejected the request")
            }

            val json = db.json.readTree(bytes)

            if (json.path("status_code").intValue() != 1000) {
                throw OcpiFault(3001, "Remote OCPI operation failed")
            }

            return json.path("data")
        } catch (fault: OcpiFault) {
            throw fault
        } catch (_: Exception) {
            throw OcpiFault(3001, "Remote endpoint could not be contacted")
        }
    }
}
