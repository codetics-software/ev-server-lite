package fr.codetics.ev_server.controllers.evses

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.models.evses.EvseInput
import fr.codetics.ev_server.services.evses.EvseService
import fr.codetics.ev_server.services.evses.QrCodeService
import java.util.UUID
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/evses")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
class EvseController(private val evses: EvseService, private val qr: QrCodeService) {
    @GetMapping fun list(@AuthenticationPrincipal user: AppPrincipal) = evses.list(user.tenantId)

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun create(@RequestBody input: EvseInput, @AuthenticationPrincipal user: AppPrincipal) =
        evses.save(user, input)

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun update(
        @PathVariable id: UUID,
        @RequestBody input: EvseInput,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = evses.save(user, input, id)

    @GetMapping("/{id}/qr", produces = ["image/svg+xml"])
    fun qr(@PathVariable id: UUID, @AuthenticationPrincipal user: AppPrincipal): String {
        evses.find(user.tenantId, id)
        return qr.svg(evses.portalUrl(user.tenantId, id))
    }
}
