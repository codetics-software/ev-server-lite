package fr.codetics.ev_server.ocpi

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.randomToken
import fr.codetics.ev_server.core.sha256
import fr.codetics.ev_server.models.entities.PartnerEntity
import fr.codetics.ev_server.repositories.PartnerRepository
import fr.codetics.ev_server.security.SecretVault
import java.util.UUID
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class PartnerInput(
    val name: String,
    val countryCode: String,
    val partyId: String,
    val localRole: String,
    val remoteUrl: String,
)

@RestController
@RequestMapping("/api/v1/ocpi/partners")
@PreAuthorize("hasRole('ADMIN')")
class OcpiAdminController(
    private val db: Database,
    private val vault: SecretVault,
    private val transport: OcpiTransport,
    private val partners: PartnerRepository,
    private val service: OcpiPartnerService,
) {
    @GetMapping
    fun list(@AuthenticationPrincipal user: AppPrincipal) = service.list(user)

    @PostMapping
    fun create(
        @RequestBody body: PartnerInput,
        @AuthenticationPrincipal user: AppPrincipal,
    ): Map<String, Any> {
        require(body.name.isNotBlank() && body.name.length <= 200)
        require(body.countryCode.matches(Regex("[A-Z]{2}")))
        require(body.partyId.matches(Regex("[A-Z0-9]{3}")))
        require(body.localRole in setOf("cpo", "emsp"))
        transport.validateRemote(body.remoteUrl, body.remoteUrl)

        return db.atomic {
            val token = randomToken()
            val partner =
                partners.save(
                    PartnerEntity(
                        tenantId = user.tenantId,
                        name = body.name,
                        countryCode = body.countryCode,
                        partyId = body.partyId,
                        localRole = body.localRole,
                        tokenHash = sha256(token),
                        localTokenEncrypted = vault.encrypt(token),
                        remoteUrl = body.remoteUrl,
                    )
                )
            db.audit(
                user.tenantId,
                user.id.toString(),
                "OCPI_PARTNER_CREATE",
                partner.id.toString(),
            )

            mapOf(
                "id" to partner.id,
                "token" to token,
                "message" to "Transmettez ce jeton initial au partenaire par un canal sécurisé.",
            )
        }
    }

    @DeleteMapping("/{id}")
    fun disable(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: AppPrincipal,
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

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @RequestBody body: PartnerUpdate,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = service.update(user, id, body)

    @PostMapping("/{id}/register")
    fun register(
        @PathVariable id: UUID,
        @RequestBody body: PartnerRegistration,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = service.register(user, id, body)

    @PostMapping("/{id}/unregister")
    fun unregister(@PathVariable id: UUID, @AuthenticationPrincipal user: AppPrincipal) =
        service.unregister(user, id)

    @PostMapping("/{id}/ping")
    fun ping(@PathVariable id: UUID, @AuthenticationPrincipal user: AppPrincipal) =
        service.ping(user, id)

    @PostMapping("/{id}/push/{module}")
    fun push(
        @PathVariable id: UUID,
        @PathVariable module: String,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = service.push(user, id, module)

    @PostMapping("/{id}/pull/{module}")
    fun pull(
        @PathVariable id: UUID,
        @PathVariable module: String,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = service.pull(user, id, module)

    @GetMapping("/{id}/jobs")
    fun jobs(@PathVariable id: UUID, @AuthenticationPrincipal user: AppPrincipal) =
        service.jobs(user, id)

    @GetMapping("/{id}/objects")
    fun objects(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: AppPrincipal,
        @RequestParam module: String,
        @RequestParam(defaultValue = "SENDER") direction: String,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "50") limit: Int,
    ) = service.objects(user, id, module, direction, offset, limit)
}
