package fr.codetics.ev_server.billing

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.models.billing.CheckoutDetails
import fr.codetics.ev_server.models.billing.CreatedCheckout
import fr.codetics.ev_server.models.billing.RefundResult
import fr.codetics.ev_server.stripe.PaymentGateway
import fr.codetics.ev_server.stripe.SignedPaymentEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/** Adaptateur exclusivement réservé aux tests ; aucun appel à Stripe. */
class TestPaymentGateway(private val db: Database) : PaymentGateway {
    override val configured = true
    val checkouts = ConcurrentHashMap<UUID, CheckoutDetails>()
    val refunds = ConcurrentHashMap<UUID, Long>()

    override fun createAccount(tenantId: UUID, name: String, email: String, country: String) =
        "acct_$tenantId"

    override fun onboardingUrl(accountId: String, returnUrl: String) =
        "https://connect.stripe.com/test"

    override fun accountReady(accountId: String) = true

    override fun createCheckout(details: CheckoutDetails): CreatedCheckout {
        checkouts.putIfAbsent(details.orderId, details)
        return CreatedCheckout(
            "cs_${details.orderId}",
            "https://checkout.stripe.com/test/${details.orderId}",
        )
    }

    override fun refund(
        accountId: String,
        paymentIntentId: String,
        amountMinor: Long,
        orderId: UUID,
    ): RefundResult {
        val existing = refunds.putIfAbsent(orderId, amountMinor)
        check(existing == null || existing == amountMinor)
        return RefundResult("re_$orderId", "succeeded")
    }

    override fun retrieveRefund(accountId: String, refundId: String) =
        RefundResult(refundId, "succeeded")

    override fun verifyEvent(payload: String, signature: String): SignedPaymentEvent {
        require(signature == "signature-de-test") { "Signature invalide." }
        val json = db.decode(payload)
        return SignedPaymentEvent(
            json.path("id").asText(),
            json.path("account").asText(),
            json.path("type").asText(),
            json.path("data"),
        )
    }
}

@TestConfiguration(proxyBeanMethods = false)
class PaymentTestConfiguration {
    @Bean @Primary fun testPaymentGateway(db: Database) = TestPaymentGateway(db)
}
