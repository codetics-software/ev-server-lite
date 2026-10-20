package fr.codetics.ev_server.controllers.smartcharging

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.models.stations.PowerLimitInput
import fr.codetics.ev_server.services.smartcharging.ChargingProfileInput
import fr.codetics.ev_server.services.smartcharging.ChargingProfileService
import fr.codetics.ev_server.services.smartcharging.SmartChargingService
import java.time.Instant
import java.util.UUID
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
class SmartChargingController(
    private val profiles: ChargingProfileService,
    private val smartCharging: SmartChargingService,
) {
    @GetMapping("/charging-profiles")
    fun profiles(
        @AuthenticationPrincipal user: AppPrincipal,
        @RequestParam(required = false) stationId: String?,
    ) = profiles.list(user, stationId)

    @PostMapping("/charging-profiles")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    fun create(@RequestBody input: ChargingProfileInput, @AuthenticationPrincipal user: AppPrincipal) =
        profiles.create(user, input)

    @DeleteMapping("/charging-profiles/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    fun delete(@PathVariable id: UUID, @AuthenticationPrincipal user: AppPrincipal) =
        profiles.delete(user, id)

    @PutMapping("/stations/{id}/power-limit")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    fun powerLimit(
        @PathVariable id: String,
        @RequestBody input: PowerLimitInput,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = profiles.powerLimit(user, id, input)

    @GetMapping("/site-areas/{id}/smart-charging")
    fun plan(@PathVariable id: UUID, @AuthenticationPrincipal user: AppPrincipal) =
        smartCharging.plan(user, id)

    @PostMapping("/site-areas/{id}/smart-charging/trigger")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    fun trigger(@PathVariable id: UUID, @AuthenticationPrincipal user: AppPrincipal) =
        smartCharging.trigger(user, id)

    @GetMapping("/site-areas/{id}/consumptions")
    fun consumptions(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: AppPrincipal,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
    ) = smartCharging.consumptions(user, id, from, to)
}
