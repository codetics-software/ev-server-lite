package fr.codetics.ev_server.controllers.audit

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.services.logs.LogFilter
import fr.codetics.ev_server.services.logs.LogService
import java.time.Instant
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/logs")
@PreAuthorize("hasRole('ADMIN')")
class LogController(private val logs: LogService) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal user: AppPrincipal,
        @RequestParam(required = false) action: String?,
        @RequestParam(required = false) actor: String?,
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
        @RequestParam(defaultValue = "") search: String,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "50") limit: Int,
    ) = logs.list(user, LogFilter(action, actor, target, from, to, search), offset, limit)

    @GetMapping("/export")
    fun export(
        @AuthenticationPrincipal user: AppPrincipal,
        @RequestParam(required = false) action: String?,
        @RequestParam(required = false) actor: String?,
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
        @RequestParam(defaultValue = "") search: String,
    ) = logs.export(user, LogFilter(action, actor, target, from, to, search))
}
