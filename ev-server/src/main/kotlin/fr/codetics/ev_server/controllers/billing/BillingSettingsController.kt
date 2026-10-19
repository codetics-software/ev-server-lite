package fr.codetics.ev_server.controllers.billing

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.models.workspaces.RedirectResponse
import fr.codetics.ev_server.models.workspaces.WorkspacePaymentInput
import fr.codetics.ev_server.services.billing.StripeConnectService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/billing")
@PreAuthorize("hasRole('ADMIN')")
class BillingSettingsController(private val connect: StripeConnectService) {
    @GetMapping("/settings")
    fun settings(@AuthenticationPrincipal user: AppPrincipal) = connect.status(user)

    @PutMapping("/settings")
    fun update(
        @RequestBody input: WorkspacePaymentInput,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = connect.update(user, input)

    @PostMapping("/onboarding")
    fun onboarding(@AuthenticationPrincipal user: AppPrincipal) =
        RedirectResponse(connect.onboarding(user))
}
