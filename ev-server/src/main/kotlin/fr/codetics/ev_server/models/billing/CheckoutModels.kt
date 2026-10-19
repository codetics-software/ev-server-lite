package fr.codetics.ev_server.models.billing

import java.time.Instant
import java.util.UUID

data class CheckoutRequest(val requestId: UUID, val accessToken: String, val creditMinor: Long)

data class CheckoutResponse(val orderId: UUID, val checkoutUrl: String)

data class CheckoutDetails(
    val orderId: UUID,
    val accountId: String,
    val amountMinor: Long,
    val currency: String,
    val description: String,
    val successUrl: String,
    val cancelUrl: String,
    val expiresAt: Instant,
)

data class CreatedCheckout(val id: String, val url: String)

data class RefundResult(val id: String, val status: String)

data class PortalPaymentResponse(
    val id: UUID,
    val state: PaymentState,
    val prepaidMinor: Long,
    val billedMinor: Long?,
    val refundedMinor: Long,
    val currency: String,
    val transactionId: Int?,
    val message: String?,
)
