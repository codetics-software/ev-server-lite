package fr.codetics.ev_server.controllers.billing

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.models.workspaces.RedirectResponse
import fr.codetics.ev_server.models.workspaces.UserBillingInput
import fr.codetics.ev_server.models.workspaces.WorkspacePaymentInput
import fr.codetics.ev_server.services.billing.StripeConnectService
import fr.codetics.ev_server.services.billing.UserBillingService
import java.util.UUID
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/billing")
@PreAuthorize("hasRole('ADMIN')")
class BillingSettingsController(
    private val connect: StripeConnectService,
    private val billing: UserBillingService,
) {
    @GetMapping("/settings")
    fun settings(@AuthenticationPrincipal user: AppPrincipal) = connect.status(user)

    @PutMapping("/settings")
    fun update(
        @RequestBody input: WorkspacePaymentInput,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = connect.update(user, input)

    @PutMapping("/settings/users")
    fun updateUserBilling(
        @RequestBody input: UserBillingInput,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = billing.updateSettings(user, input).let { connect.status(user) }

    @PostMapping("/onboarding")
    fun onboarding(@AuthenticationPrincipal user: AppPrincipal) =
        RedirectResponse(connect.onboarding(user))

    @GetMapping("/taxes") fun taxes(@AuthenticationPrincipal user: AppPrincipal) = billing.taxes(user)

    @PostMapping("/check") fun check(@AuthenticationPrincipal user: AppPrincipal) = billing.check(user)

    @PostMapping("/users/{id}/synchronize")
    fun synchronize(@PathVariable id: UUID, @AuthenticationPrincipal user: AppPrincipal) =
        billing.synchronize(user, id)
}
