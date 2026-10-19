package fr.codetics.ev_server.sessions

import fr.codetics.ev_server.core.AppPrincipal
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
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
        @RequestParam(defaultValue = "50") limit: Int
    ) = sessions.list(user, offset, limit)
}
