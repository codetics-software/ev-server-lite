package fr.codetics.ev_server.services.billing

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.models.billing.PaymentState
import fr.codetics.ev_server.models.entities.ChargingTokenKey
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.entities.StripeEventEntity
import fr.codetics.ev_server.ocpp.OcppCommandQueue
import fr.codetics.ev_server.repositories.ChargingPaymentRepository
import fr.codetics.ev_server.repositories.ChargingTokenRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.StripeEventRepository
import fr.codetics.ev_server.stripe.PaymentGateway
import java.time.Instant
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class StripeWebhookService(
    private val gateway: PaymentGateway,
    private val payments: ChargingPaymentRepository,
    private val events: StripeEventRepository,
    private val tokens: ChargingTokenRepository,
    private val stations: StationRepository,
    private val lifecycle: ChargingPaymentLifecycle,
    private val queue: OcppCommandQueue,
    private val db: Database,
) {
    fun receive(payload: String, signature: String) {
        val event = gateway.verifyEvent(payload, signature)
        if (
            event.type !in
                setOf(
                    "checkout.session.completed",
                    "checkout.session.async_payment_succeeded",
                    "checkout.session.async_payment_failed",
                    "checkout.session.expired",
                )
        )
            return
        val id =
            runCatching {
                    UUID.fromString(event.data.path("metadata").path("charging_order_id").asText())
                }
                .getOrNull() ?: return
        db.atomic {
            val order = payments.findForUpdate(id) ?: return@atomic true
            if (order.stripeAccountId != event.accountId)
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Le compte Stripe ne correspond pas au paiement.",
                )
            if (order.stripeCheckoutId == null)
                throw ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Le paiement est en cours d’enregistrement. Réessayez.",
                )
            require(order.stripeCheckoutId == event.data.path("id").asText()) {
                "La session Stripe ne correspond pas au paiement."
            }
            if (events.existsById(event.id)) return@atomic true
            if (
                event.type in
                    setOf("checkout.session.expired", "checkout.session.async_payment_failed")
            ) {
                if (order.state == PaymentState.PENDING_PAYMENT) {
                    order.state = PaymentState.CANCELLED
                    order.updatedAt = Instant.now()
                    lifecycle.disableToken(order)
                }
            } else if (event.data.path("payment_status").asText() == "paid") {
                require(
                    event.data.path("amount_total").longValue() == order.prepaidMinor &&
                        event.data.path("currency").asText().equals(order.currency, true)
                ) {
                    "Le montant Stripe ne correspond pas au crédit demandé."
                }
                val intent = event.data.path("payment_intent").asText()
                require(intent.startsWith("pi_")) { "Le paiement Stripe est incomplet." }
                if (order.stripePaymentIntentId == null) {
                    order.stripePaymentIntentId = intent
                    val station =
                        stations.findById(StationKey(order.tenantId, order.stationId)).orElse(null)
                    if (
                        order.state != PaymentState.PENDING_PAYMENT ||
                            order.expiresAt.isBefore(Instant.now()) ||
                            station?.enabled != true ||
                            !station.connected
                    ) {
                        lifecycle.settleWithoutCharging(
                            order,
                            "La recharge n’a pas pu démarrer. Le crédit est remboursé.",
                        )
                    } else {
                        val token =
                            tokens
                                .findById(ChargingTokenKey(order.tenantId, order.tokenUid))
                                .orElseThrow()
                        token.enabled = true
                        token.expiresAt = Instant.now().plusSeconds(300)
                        tokens.save(token)
                        order.startCommandId =
                            queue.command(
                                order.tenantId,
                                order.stationId,
                                "RemoteStartTransaction",
                                db.json.valueToTree(
                                    mapOf(
                                        "connectorId" to order.connectorId,
                                        "idTag" to order.tokenUid,
                                    )
                                ),
                            )
                        order.state = PaymentState.START_REQUESTED
                        order.updatedAt = Instant.now()
                    }
                } else
                    require(order.stripePaymentIntentId == intent) {
                        "Le paiement Stripe a changé."
                    }
            }
            payments.save(order)
            events.save(StripeEventEntity(event.id, event.accountId, event.type))
            true
        }
    }
}
