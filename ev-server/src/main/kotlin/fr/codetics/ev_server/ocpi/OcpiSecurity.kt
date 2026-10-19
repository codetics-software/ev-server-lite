package fr.codetics.ev_server.ocpi

import fr.codetics.ev_server.core.Database
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
        require(uri.scheme == "https" && uri.host != null) {
            "L’adresse OCPI distante doit utiliser HTTPS."
        }
        require(uri.userInfo == null && uri.fragment == null && uri.port in setOf(-1, 443)) {
            "L’adresse OCPI distante doit utiliser le port 443, sans identifiants ni fragment."
        }
        require(
            uri.host.equals(expected.host, true) &&
                uri.scheme == expected.scheme &&
                uri.port == expected.port
        ) {
            "L’adresse distante doit correspondre à l’origine configurée du partenaire."
        }

        return uri
    }

    fun request(
        partner: PartnerEntity,
        url: String,
        method: String = "GET",
        body: Any? = null,
        token: String? = null,
    ): JsonNode {
        val pinned =
            partner.remoteUrl
                ?: throw OcpiFault(3001, "Configurez d’abord l’adresse de découverte distante.")
        val uri = validateRemote(url, pinned)
        val credential =
            token
                ?: partner.remoteTokenEncrypted?.let(vault::decrypt)
                ?: throw OcpiFault(3001, "Les identifiants distants sont manquants.")
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
                throw OcpiFault(3001, "Le service distant a refusé la demande.")
            }

            val json = db.json.readTree(bytes)

            if (json.path("status_code").intValue() != 1000) {
                throw OcpiFault(3001, "L’opération OCPI distante a échoué.")
            }

            return json.path("data")
        } catch (fault: OcpiFault) {
            throw fault
        } catch (_: Exception) {
            throw OcpiFault(3001, "Le service distant n’a pas pu être contacté.")
        }
    }
}
