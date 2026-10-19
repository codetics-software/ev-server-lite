package fr.codetics.ev_server.billing

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.repositories.AuditRepository
import fr.codetics.ev_server.stripe.StripeChargingCheckout
import fr.codetics.ev_server.stripe.StripeClientProvider
import fr.codetics.ev_server.stripe.StripeConnectedAccounts
import fr.codetics.ev_server.stripe.StripePaymentGateway
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper

class StripeSignatureTests {
    private val secret = "whsec_cle_reservee_aux_tests"
    private val provider = StripeClientProvider("sk_test_inutilisee", secret)
    private val db =
        Database(
            JsonMapper.builder().build(),
            mock(TransactionTemplate::class.java),
            mock(AuditRepository::class.java),
        )
    private val gateway =
        StripePaymentGateway(
            provider,
            StripeConnectedAccounts(provider),
            StripeChargingCheckout(provider),
            db,
        )

    private fun signature(payload: String, timestamp: Long): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val digest =
            mac.doFinal("$timestamp.$payload".toByteArray()).joinToString("") { "%02x".format(it) }
        return "t=$timestamp,v1=$digest"
    }

    @Test
    fun `le SDK Stripe refuse les signatures alterees et les notifications anciennes`() {
        val now = Instant.now().epochSecond
        val payload =
            """{"id":"evt_test","object":"event","account":"acct_test","type":"checkout.session.completed","data":{"object":{"id":"cs_test"}}}"""
        assertEquals("acct_test", gateway.verifyEvent(payload, signature(payload, now)).accountId)
        assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            gateway.verifyEvent(payload.replace("acct_test", "acct_autre"), signature(payload, now))
        }
        assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            gateway.verifyEvent(payload, signature(payload, now - 600))
        }
    }
}
