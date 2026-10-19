package fr.codetics.ev_server.stripe

import com.stripe.param.RefundCreateParams
import com.stripe.param.checkout.SessionCreateParams
import fr.codetics.ev_server.models.billing.CheckoutDetails
import fr.codetics.ev_server.models.billing.CreatedCheckout
import fr.codetics.ev_server.models.billing.RefundResult
import java.util.UUID
import org.springframework.stereotype.Component

@Component
class StripeChargingCheckout(private val stripe: StripeClientProvider) {
    fun create(details: CheckoutDetails): CreatedCheckout {
        val price =
            SessionCreateParams.LineItem.PriceData.builder()
                .setCurrency(details.currency.lowercase())
                .setUnitAmount(details.amountMinor)
                .setProductData(
                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                        .setName(details.description)
                        .build()
                )
                .build()
        val parameters =
            SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setLocale(SessionCreateParams.Locale.FR)
                .setClientReferenceId(details.orderId.toString())
                .setSuccessUrl(details.successUrl)
                .setCancelUrl(details.cancelUrl)
                .setExpiresAt(details.expiresAt.epochSecond)
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setPriceData(price)
                        .setQuantity(1L)
                        .build()
                )
                .putMetadata("charging_order_id", details.orderId.toString())
                .setPaymentIntentData(
                    SessionCreateParams.PaymentIntentData.builder()
                        .putMetadata("charging_order_id", details.orderId.toString())
                        .build()
                )
                .putExtraParam(
                    "integration_identifier",
                    "codetics_ev_" +
                        details.orderId.toString().filter(Char::isLetter).padEnd(8, 'a').take(8),
                )
                .build()
        val session =
            stripe.client
                .v1()
                .checkout()
                .sessions()
                .create(
                    parameters,
                    stripe.options(details.accountId, "charging:${details.orderId}:checkout"),
                )
        return CreatedCheckout(session.id, requireNotNull(session.url))
    }

    fun refund(
        accountId: String,
        paymentIntentId: String,
        amountMinor: Long,
        orderId: UUID,
    ): RefundResult {
        val parameters =
            RefundCreateParams.builder()
                .setPaymentIntent(paymentIntentId)
                .setAmount(amountMinor)
                .putMetadata("charging_order_id", orderId.toString())
                .build()
        val refund =
            stripe.client
                .v1()
                .refunds()
                .create(parameters, stripe.options(accountId, "charging:$orderId:unused-credit"))
        return RefundResult(refund.id, refund.status)
    }

    fun retrieveRefund(accountId: String, refundId: String): RefundResult {
        val refund = stripe.client.v1().refunds().retrieve(refundId, stripe.options(accountId))
        return RefundResult(refund.id, refund.status)
    }
}
