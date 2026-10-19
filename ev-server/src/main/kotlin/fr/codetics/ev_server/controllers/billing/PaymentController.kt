package fr.codetics.ev_server.controllers.billing

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.services.billing.PaymentQueryService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/payments")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
class PaymentController(private val payments: PaymentQueryService) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal user: AppPrincipal,
        @RequestParam(defaultValue = "0") page: Int,
    ) = payments.list(user.tenantId, page)
}
