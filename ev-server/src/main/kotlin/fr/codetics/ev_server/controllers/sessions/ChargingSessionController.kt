package fr.codetics.ev_server.controllers.sessions

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.models.sessions.SessionStartInput
import fr.codetics.ev_server.services.sessions.ChargingSessionService
import fr.codetics.ev_server.services.sessions.SessionFilter
import java.time.Instant
import java.util.UUID
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/transactions")
@PreAuthorize("isAuthenticated()")
class ChargingSessionController(private val sessions: ChargingSessionService) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal user: AppPrincipal,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) stationId: String?,
        @RequestParam(required = false) siteId: UUID?,
        @RequestParam(required = false) siteAreaId: UUID?,
        @RequestParam(required = false) userId: UUID?,
        @RequestParam(required = false) tokenUid: String?,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
        @RequestParam(defaultValue = "") search: String,
    ) =
        sessions.list(
            user,
            offset,
            limit,
            SessionFilter(status, stationId, siteId, siteAreaId, userId, tokenUid, from, to, search),
        )

    @GetMapping("/export")
    fun export(
        @AuthenticationPrincipal user: AppPrincipal,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) stationId: String?,
        @RequestParam(required = false) siteId: UUID?,
        @RequestParam(required = false) siteAreaId: UUID?,
        @RequestParam(required = false) userId: UUID?,
        @RequestParam(required = false) tokenUid: String?,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
        @RequestParam(defaultValue = "") search: String,
    ) =
        sessions.export(
            user,
            SessionFilter(status, stationId, siteId, siteAreaId, userId, tokenUid, from, to, search),
        )

    @GetMapping("/in-error")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    fun inError(
        @AuthenticationPrincipal user: AppPrincipal,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "50") limit: Int,
    ) = sessions.inError(user, offset, limit)

    @GetMapping("/{id}")
    fun find(@PathVariable id: Int, @AuthenticationPrincipal user: AppPrincipal) =
        sessions.find(user, id)

    @GetMapping("/{id}/consumptions")
    fun consumptions(@PathVariable id: Int, @AuthenticationPrincipal user: AppPrincipal) =
        sessions.consumptions(user, id)

    @PostMapping("/start")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','DRIVER')")
    fun start(@RequestBody input: SessionStartInput, @AuthenticationPrincipal user: AppPrincipal) =
        sessions.start(user, input)

    @PostMapping("/{id}/stop")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','DRIVER')")
    fun stop(@PathVariable id: Int, @AuthenticationPrincipal user: AppPrincipal) =
        sessions.stop(user, id)

    @PostMapping("/{id}/soft-stop")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    fun softStop(@PathVariable id: Int, @AuthenticationPrincipal user: AppPrincipal) =
        sessions.softStop(user, id)

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun delete(@PathVariable id: Int, @AuthenticationPrincipal user: AppPrincipal) =
        sessions.delete(user, id)
}
