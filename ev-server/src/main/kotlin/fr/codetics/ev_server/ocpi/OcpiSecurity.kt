package fr.codetics.ev_server.ocpi

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.core.msg
import fr.codetics.ev_server.core.sha256
import fr.codetics.ev_server.models.entities.PartnerEntity
import fr.codetics.ev_server.repositories.PartnerRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.security.SecretVault
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

class OcpiFault(
    val status: Int,
    override val message: String,
    val http: HttpStatus = HttpStatus.OK,
) : RuntimeException(message)

@Component
class OcpiTransport(
    private val db: Database,
    private val vault: SecretVault,
    private val partners: PartnerRepository,
    private val tenants: TenantRepository,
) {
    private val client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

    fun authenticate(authorization: String?, role: String): PartnerEntity {
        if (
            authorization == null ||
                !authorization.startsWith("Token ") ||
                authorization.length > 100
        ) {
            throw OcpiFault(2001, "Jeton invalide.", HttpStatus.UNAUTHORIZED)
        }

        val partner =
            partners.findByTokenHashAndLocalRoleAndEnabledTrue(
                sha256(authorization.removePrefix("Token ")),
                role,
            ) ?: throw OcpiFault(2001, "Jeton invalide.", HttpStatus.UNAUTHORIZED)
        val tenant = tenants.findById(partner.tenantId).orElse(null)

        if (tenant == null || !tenant.active) {
            throw OcpiFault(2001, "L’espace client est indisponible.", HttpStatus.FORBIDDEN)
        }

        return partner
    }

    fun validateRemote(url: String, pinned: String): URI {
        val uri = URI(url)
        val expected = URI(pinned)
        ensure(uri.scheme == "https" && uri.host != null) {
            msg("ocpi.remote.https")
        }
        ensure(uri.userInfo == null && uri.fragment == null && uri.port in setOf(-1, 443)) {
            msg("ocpi.remote.port")
        }
        ensure(
            uri.host.equals(expected.host, true) &&
                uri.scheme == expected.scheme &&
                uri.port == expected.port
        ) {
            msg("ocpi.remote.origin")
        }

        return uri
    }

    fun request(
        partner: PartnerEntity,
        url: String,
        method: String = "GET",
        body: Any? = null,
        token: String? = null,
    ): JsonNode = exchange(partner, url, method, body, token).data

    fun page(partner: PartnerEntity, url: String): OcpiPage = exchange(partner, url, "GET", null, null)

    fun endpoint(partner: PartnerEntity, module: String): String? =
        runCatching { db.decode(partner.remoteEndpoints) }.getOrNull()
            ?.firstOrNull { it.path("identifier").asText() == module }
            ?.path("url")
            ?.asText()
            ?.trimEnd('/')

    private fun exchange(
        partner: PartnerEntity,
        url: String,
        method: String,
        body: Any?,
        token: String?,
    ): OcpiPage {
        val pinned =
            partner.remoteUrl
                ?: throw OcpiFault(3001, msg("ocpi.remote.configure_url"))
        val uri = validateRemote(url, pinned)
        val credential =
            token
                ?: partner.remoteTokenEncrypted?.let(vault::decrypt)
                ?: throw OcpiFault(3001, msg("ocpi.remote.missing_credentials"))
        val publisher =
            if (body == null) {
                HttpRequest.BodyPublishers.noBody()
            } else {
                HttpRequest.BodyPublishers.ofString(db.encode(body))
            }
        val request =
            HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Token $credential")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .method(method, publisher)
                .build()

        try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            val bytes = response.body().use { stream -> stream.readNBytes(2_097_153) }

            if (response.statusCode() !in 200..299 || bytes.size > 2_097_152) {
                throw OcpiFault(3001, msg("ocpi.remote.refused", response.statusCode()))
            }

            val json = if (bytes.isEmpty()) db.json.createObjectNode().put("status_code", 1000) else db.json.readTree(bytes)

            if (json.path("status_code").intValue() != 1000) {
                throw OcpiFault(3001, msg("ocpi.remote.failed", json.path("status_code").intValue()))
            }

            val next =
                response.headers().firstValue("Link").orElse(null)?.let { link ->
                    Regex("<([^>]+)>\\s*;\\s*rel=\"?next\"?").find(link)?.groupValues?.get(1)
                }
            return OcpiPage(json.path("data"), next)
        } catch (fault: OcpiFault) {
            throw fault
        } catch (_: Exception) {
            throw OcpiFault(3001, msg("ocpi.remote.unreachable"))
        }
    }
}

data class OcpiPage(val data: JsonNode, val next: String?)
