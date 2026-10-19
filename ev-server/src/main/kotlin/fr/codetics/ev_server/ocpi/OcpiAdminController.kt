package fr.codetics.ev_server.ocpi

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.randomToken
import fr.codetics.ev_server.core.sha256
import fr.codetics.ev_server.persistence.PartnerEntity
import fr.codetics.ev_server.persistence.PartnerRepository
import java.util.UUID
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class PartnerInput(
    val name: String,
    val countryCode: String,
    val partyId: String,
    val localRole: String,
    val remoteUrl: String
)

@RestController
@RequestMapping("/api/v1/ocpi/partners")
@PreAuthorize("hasRole('ADMIN')")
class OcpiAdminController(
    private val db: Database,
    private val vault: SecretVault,
    private val transport: OcpiTransport,
    private val partners: PartnerRepository
) {
    @GetMapping
    fun list(@AuthenticationPrincipal user: AppPrincipal): List<Map<String, Any?>> {
        return partners.findAllByTenantIdOrderByName(user.tenantId).map { partner ->
            mapOf(
                "id" to partner.id,
                "name" to partner.name,
                "country_code" to partner.countryCode,
                "party_id" to partner.partyId,
                "local_role" to partner.localRole,
                "remote_url" to partner.remoteUrl,
                "registered" to partner.registered,
                "enabled" to partner.enabled
            )
        }
    }

    @PostMapping
    fun create(
        @RequestBody body: PartnerInput,
        @AuthenticationPrincipal user: AppPrincipal
    ): Map<String, Any> {
        require(body.name.isNotBlank() && body.name.length <= 200)
        require(body.countryCode.matches(Regex("[A-Z]{2}")))
        require(body.partyId.matches(Regex("[A-Z0-9]{3}")))
        require(body.localRole in setOf("cpo", "emsp"))
        transport.validateRemote(body.remoteUrl, body.remoteUrl)

        return db.atomic {
            val token = randomToken()
            val partner = partners.save(
                PartnerEntity(
                    tenantId = user.tenantId,
                    name = body.name,
                    countryCode = body.countryCode,
                    partyId = body.partyId,
                    localRole = body.localRole,
                    tokenHash = sha256(token),
                    localTokenEncrypted = vault.encrypt(token),
                    remoteUrl = body.remoteUrl
                )
            )
            db.audit(user.tenantId, user.id.toString(), "OCPI_PARTNER_CREATE", partner.id.toString())

            mapOf(
                "id" to partner.id,
                "token" to token,
                "message" to "Share this bootstrap token with the partner over a secure channel"
            )
        }
    }

    @DeleteMapping("/{id}")
    fun disable(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: AppPrincipal
    ): Map<String, Boolean> {
        return db.atomic {
            val partner = partners.findByTenantIdAndId(user.tenantId, id)

            if (partner != null) {
                partner.enabled = false
                partner.registered = false
                partner.remoteTokenEncrypted = null
                partner.localTokenEncrypted = null
                partners.save(partner)
                db.audit(user.tenantId, user.id.toString(), "OCPI_PARTNER_DISABLE", id.toString())
            }

            mapOf("success" to true)
        }
    }
}
