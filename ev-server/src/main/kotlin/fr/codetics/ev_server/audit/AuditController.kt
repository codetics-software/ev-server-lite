package fr.codetics.ev_server.audit

import fr.codetics.ev_server.core.AppPrincipal
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasRole('ADMIN')")
class AuditController(private val audit: AuditService) {
    @GetMapping
    fun list(@AuthenticationPrincipal user: AppPrincipal) = audit.list(user)
}
