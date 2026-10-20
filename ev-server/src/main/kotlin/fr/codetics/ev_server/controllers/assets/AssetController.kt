package fr.codetics.ev_server.controllers.assets

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.services.assets.AssetConsumptionInput
import fr.codetics.ev_server.services.assets.AssetSecretsInput
import fr.codetics.ev_server.services.assets.AssetService
import java.time.Instant
import java.util.UUID
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class AssetController(private val assets: AssetService) {
    @GetMapping("/assets/in-error")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    fun inError(@AuthenticationPrincipal user: AppPrincipal) = assets.inError(user)

    @GetMapping("/assets/{id}/consumptions")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    fun consumptions(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: AppPrincipal,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
    ) = assets.list(user, id, from, to)

    @PostMapping("/assets/{id}/consumptions")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    fun push(
        @PathVariable id: UUID,
        @RequestBody input: List<AssetConsumptionInput>,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = assets.push(user, id, input)

    @PostMapping("/assets/{id}/consumptions/refresh")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    fun refresh(@PathVariable id: UUID, @AuthenticationPrincipal user: AppPrincipal) =
        assets.refresh(user, id)

    @GetMapping("/assets/{id}/connection")
    @PreAuthorize("hasRole('ADMIN')")
    fun connection(@PathVariable id: UUID, @AuthenticationPrincipal user: AppPrincipal) =
        assets.status(user, id)

    @PutMapping("/assets/{id}/connection")
    @PreAuthorize("hasRole('ADMIN')")
    fun secrets(
        @PathVariable id: UUID,
        @RequestBody input: AssetSecretsInput,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = assets.updateSecrets(user, id, input)

    @PostMapping("/assets/{id}/connection/check")
    @PreAuthorize("hasRole('ADMIN')")
    fun check(@PathVariable id: UUID, @AuthenticationPrincipal user: AppPrincipal) =
        assets.check(user, id)

    @PostMapping("/assets/{id}/push-token")
    @PreAuthorize("hasRole('ADMIN')")
    fun pushToken(@PathVariable id: UUID, @AuthenticationPrincipal user: AppPrincipal) =
        assets.pushToken(user, id)

    @PostMapping("/public/assets/{id}/consumptions")
    fun devicePush(
        @PathVariable id: UUID,
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody input: List<AssetConsumptionInput>,
    ) = assets.pushWithToken(authorization, id, input)
}
