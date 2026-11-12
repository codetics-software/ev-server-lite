package fr.codetics.ev_server.controllers.reservations

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.services.reservations.ReservationService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/reservations")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
class ReservationController(private val reservations: ReservationService) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal user: AppPrincipal,
        @RequestParam(name = "station_id", required = false) stationId: String?,
    ) = reservations.list(user.tenantId, stationId)
}
