package fr.codetics.ev_server.controllers.stations

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.services.stations.RegistrationTokenInput
import fr.codetics.ev_server.services.stations.RegistrationTokenService
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

@RestController
@RequestMapping("/api/v1/registration-tokens")
@PreAuthorize("hasRole('ADMIN')")
class RegistrationTokenController(private val tokens: RegistrationTokenService) {
    @GetMapping fun list(@AuthenticationPrincipal user: AppPrincipal) = tokens.list(user)

    @PostMapping
    fun create(
        @RequestBody input: RegistrationTokenInput,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = tokens.create(user, input)

    @PostMapping("/{id}/revoke")
    fun revoke(@PathVariable id: UUID, @AuthenticationPrincipal user: AppPrincipal) =
        tokens.revoke(user, id)

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID, @AuthenticationPrincipal user: AppPrincipal) =
        tokens.delete(user, id)
}
