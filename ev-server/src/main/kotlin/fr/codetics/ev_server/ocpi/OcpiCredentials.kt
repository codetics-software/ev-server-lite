package fr.codetics.ev_server.ocpi

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.randomToken
import fr.codetics.ev_server.core.sha256
import fr.codetics.ev_server.models.entities.PartnerEntity
import fr.codetics.ev_server.repositories.PartnerRepository
import fr.codetics.ev_server.security.SecretVault
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode

@Service
class OcpiCredentials(
    private val db: Database,
    private val partners: PartnerRepository,
    private val transport: OcpiTransport,
    private val vault: SecretVault,
    @param:Value("\${app.ocpi.public-url}") private val publicUrl: String,
    @param:Value("\${app.ocpi.country-code}") private val country: String,
    @param:Value("\${app.ocpi.party-id}") private val party: String,
) {
    fun exchange(partner: PartnerEntity, method: String, body: JsonNode?): Any {
        when (method) {
            "GET" -> {
                requireRegistered(partner)
                val token = vault.decrypt(requireNotNull(partner.localTokenEncrypted))
                return ocpi(ourCredentials(partner.localRole, token))
            }
            "DELETE" -> {
                return db.atomic {
                    val locked =
                        partners.findForUpdate(partner.id)
                            ?: throw OcpiFault(
                                2001,
                                "Partenaire indisponible.",
                                HttpStatus.FORBIDDEN,
                            )
                    requireRegistered(locked)
                    locked.registered = false
                    locked.enabled = false
                    locked.remoteTokenEncrypted = null
                    locked.localTokenEncrypted = null
                    locked.remoteEndpoints = "[]"
                    partners.save(locked)
                    ocpi()
                }
            }
        }

        if ((method == "POST" && partner.registered) || (method == "PUT" && !partner.registered)) {
            throw OcpiFault(2001, "État d’enregistrement invalide.", HttpStatus.METHOD_NOT_ALLOWED)
        }

        require(body != null && body.isObject)
        val token = body.path("token").asText()
        require(token.length in 1..64)
        require(body.path("country_code").asText() == partner.countryCode)
        require(body.path("party_id").asText() == partner.partyId)
        require(body.path("business_details").path("name").isString)

        val versions = transport.request(partner, body.path("url").asText(), token = token)
        val version =
            versions.firstOrNull { entry -> entry.path("version").asText() == "2.1.1" }
                ?: throw OcpiFault(3001, "Le partenaire distant ne publie pas OCPI 2.1.1.")
        val details = transport.request(partner, version.path("url").asText(), token = token)
        val endpoints = details.path("endpoints")
        require(endpoints.isArray && !endpoints.isEmpty)

        endpoints.forEach { endpoint ->
            transport.validateRemote(
                endpoint.path("url").asText(),
                requireNotNull(partner.remoteUrl),
            )
        }

        return db.atomic {
            val locked =
                partners.findForUpdate(partner.id)
                    ?: throw OcpiFault(2001, "Partenaire indisponible.", HttpStatus.FORBIDDEN)

            if (
                locked.tokenHash != partner.tokenHash ||
                    locked.registered != partner.registered ||
                    !locked.enabled
            ) {
                throw OcpiFault(
                    2001,
                    "Les identifiants ont changé. Réessayez avec les identifiants actuels.",
                    HttpStatus.CONFLICT,
                )
            }

            val newToken = randomToken()
            locked.tokenHash = sha256(newToken)
            locked.localTokenEncrypted = vault.encrypt(newToken)
            locked.remoteTokenEncrypted = vault.encrypt(token)
            locked.remoteEndpoints = db.encode(endpoints)
            locked.registered = true
            partners.save(locked)
            db.audit(locked.tenantId, "partner:${locked.id}", "OCPI_CREDENTIALS", locked.localRole)

            ocpi(ourCredentials(locked.localRole, newToken))
        }
    }

    private fun requireRegistered(partner: PartnerEntity) {
        if (!partner.registered) {
            throw OcpiFault(2001, "Non enregistré.", HttpStatus.METHOD_NOT_ALLOWED)
        }
    }

    private fun ourCredentials(role: String, token: String): Map<String, Any> {
        return mapOf(
            "token" to token,
            "url" to "${publicUrl.trimEnd('/')}/ocpi/$role/versions",
            "country_code" to country,
            "party_id" to party,
            "business_details" to mapOf("name" to "Codetics", "website" to "https://codetics.fr"),
        )
    }
}
