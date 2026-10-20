package fr.codetics.ev_server.controllers.cars

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.services.cars.AutoChargeInput
import fr.codetics.ev_server.services.cars.AutoChargeService
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
@RequestMapping("/api/v1")
class AutoChargeController(private val autoCharge: AutoChargeService) {
    @GetMapping("/account/autocharge/candidates")
    fun candidates(@AuthenticationPrincipal user: AppPrincipal) = autoCharge.candidates(user)

    @GetMapping("/account/cars/{id}/autocharge")
    fun ownVehicleIds(@PathVariable id: UUID, @AuthenticationPrincipal user: AppPrincipal) =
        autoCharge.list(user, id, own = true)

    @PostMapping("/account/cars/{id}/autocharge")
    fun enrollOwn(
        @PathVariable id: UUID,
        @RequestBody input: AutoChargeInput,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = autoCharge.enroll(user, id, input, own = true)

    @DeleteMapping("/account/cars/{id}/autocharge/{uid}")
    fun revokeOwn(
        @PathVariable id: UUID,
        @PathVariable uid: String,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = autoCharge.revoke(user, id, uid, own = true)

    @GetMapping("/cars/{id}/autocharge")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    fun vehicleIds(@PathVariable id: UUID, @AuthenticationPrincipal user: AppPrincipal) =
        autoCharge.list(user, id, own = false)

    @PostMapping("/cars/{id}/autocharge")
    @PreAuthorize("hasRole('ADMIN')")
    fun enroll(
        @PathVariable id: UUID,
        @RequestBody input: AutoChargeInput,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = autoCharge.enroll(user, id, input, own = false)

    @DeleteMapping("/cars/{id}/autocharge/{uid}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    fun revoke(
        @PathVariable id: UUID,
        @PathVariable uid: String,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = autoCharge.revoke(user, id, uid, own = false)
}
