package fr.codetics.ev_server.controllers.billing

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.models.workspaces.RedirectResponse
import fr.codetics.ev_server.services.billing.UserBillingService
import java.util.UUID
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("isAuthenticated()")
class InvoiceController(private val billing: UserBillingService) {
    @GetMapping("/invoices")
    fun invoices(
        @AuthenticationPrincipal user: AppPrincipal,
        @RequestParam(required = false) userId: UUID?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "50") limit: Int,
    ) = billing.invoices(user, userId, status, offset, limit)

    @GetMapping("/invoices/{id}/download")
    fun download(@PathVariable id: UUID, @AuthenticationPrincipal user: AppPrincipal) =
        RedirectResponse(billing.download(user, id))

    @PostMapping("/invoices/{id}/pay")
    @PreAuthorize("hasRole('ADMIN')")
    fun pay(@PathVariable id: UUID, @AuthenticationPrincipal user: AppPrincipal) = billing.pay(user, id)

    @GetMapping("/account/payment-methods")
    fun paymentMethods(@AuthenticationPrincipal user: AppPrincipal) = billing.paymentMethods(user)

    @PostMapping("/account/payment-methods/setup")
    fun setup(@AuthenticationPrincipal user: AppPrincipal) = billing.setup(user)

    @PostMapping("/account/payment-methods/{id}/default")
    fun setDefault(@PathVariable id: String, @AuthenticationPrincipal user: AppPrincipal) =
        billing.setDefault(user, id)

    @DeleteMapping("/account/payment-methods/{id}")
    fun detach(@PathVariable id: String, @AuthenticationPrincipal user: AppPrincipal) =
        billing.detach(user, id)

    @GetMapping("/users/{id}/payment-methods")
    @PreAuthorize("hasRole('ADMIN')")
    fun userPaymentMethods(@PathVariable id: UUID, @AuthenticationPrincipal user: AppPrincipal) =
        billing.paymentMethods(user, id)
}
