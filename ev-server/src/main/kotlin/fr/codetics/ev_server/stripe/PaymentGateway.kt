package fr.codetics.ev_server.stripe

import fr.codetics.ev_server.models.billing.CheckoutDetails
import fr.codetics.ev_server.models.billing.CreatedCheckout
import fr.codetics.ev_server.models.billing.RefundResult
import java.util.UUID
import tools.jackson.databind.JsonNode

interface PaymentGateway {
    val configured: Boolean

    fun createAccount(tenantId: UUID, name: String, email: String, country: String): String

    fun onboardingUrl(accountId: String, returnUrl: String): String

    fun accountReady(accountId: String): Boolean

    fun createCheckout(details: CheckoutDetails): CreatedCheckout

    fun refund(
        accountId: String,
        paymentIntentId: String,
        amountMinor: Long,
        orderId: UUID,
    ): RefundResult

    fun retrieveRefund(accountId: String, refundId: String): RefundResult

    fun verifyEvent(payload: String, signature: String): SignedPaymentEvent
}

data class SignedPaymentEvent(
    val id: String,
    val accountId: String,
    val type: String,
    val data: JsonNode,
)
