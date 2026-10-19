package fr.codetics.ev_server.controllers.overview

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.services.overview.OverviewService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/overview")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
class OverviewController(private val overview: OverviewService) {
    @GetMapping
    fun summarize(@AuthenticationPrincipal user: AppPrincipal) = overview.summarize(user.tenantId)
}
