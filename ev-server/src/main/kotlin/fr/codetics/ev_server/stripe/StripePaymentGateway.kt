package fr.codetics.ev_server.stripe

import com.stripe.exception.SignatureVerificationException
import com.stripe.net.Webhook
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.models.billing.CheckoutDetails
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

@Component
class StripePaymentGateway(
    private val stripe: StripeClientProvider,
    private val accounts: StripeConnectedAccounts,
    private val checkout: StripeChargingCheckout,
    private val db: Database,
) : PaymentGateway {
    override val configured
        get() = stripe.configured

    override fun createAccount(tenantId: UUID, name: String, email: String, country: String) =
        accounts.create(tenantId, name, email, country)

    override fun onboardingUrl(accountId: String, returnUrl: String) =
        accounts.onboardingUrl(accountId, returnUrl)

    override fun accountReady(accountId: String) = accounts.ready(accountId)

    override fun createCheckout(details: CheckoutDetails) = checkout.create(details)

    override fun refund(
        accountId: String,
        paymentIntentId: String,
        amountMinor: Long,
        orderId: UUID,
    ) = checkout.refund(accountId, paymentIntentId, amountMinor, orderId)

    override fun retrieveRefund(accountId: String, refundId: String) =
        checkout.retrieveRefund(accountId, refundId)

    override fun verifyEvent(payload: String, signature: String): SignedPaymentEvent {
        if (stripe.webhookSecret.isBlank())
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Le paiement n’est pas configuré.",
            )
        val event =
            try {
                Webhook.constructEvent(payload, signature, stripe.webhookSecret)
            } catch (_: SignatureVerificationException) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Signature Stripe invalide.")
            } catch (_: RuntimeException) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Notification Stripe invalide.",
                )
            }
        return SignedPaymentEvent(
            event.id,
            event.account.orEmpty(),
            event.type,
            db.json.readTree(payload).path("data").path("object"),
        )
    }
}
