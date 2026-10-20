package fr.codetics.ev_server.controllers.notifications

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.models.notifications.ErrorReportInput
import fr.codetics.ev_server.services.notifications.NotificationService
import java.util.UUID
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("isAuthenticated()")
class NotificationController(private val notifications: NotificationService) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal user: AppPrincipal,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) userId: UUID?,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "50") limit: Int,
    ) = notifications.list(user, type, userId, offset, limit)

    @GetMapping("/types") fun types() = notifications.types()

    @PostMapping("/report-error")
    fun report(@RequestBody input: ErrorReportInput, @AuthenticationPrincipal user: AppPrincipal) =
        notifications.report(user, input)
}
