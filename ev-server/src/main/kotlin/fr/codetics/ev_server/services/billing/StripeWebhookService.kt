package fr.codetics.ev_server.services.billing

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.core.lock
import fr.codetics.ev_server.core.msg
import fr.codetics.ev_server.models.billing.PaymentState
import fr.codetics.ev_server.models.entities.ChargingPaymentEntity
import fr.codetics.ev_server.models.entities.ChargingTokenKey
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.entities.StripeEventEntity
import fr.codetics.ev_server.ocpp.OcppCommandQueue
import fr.codetics.ev_server.repositories.ChargingPaymentRepository
import fr.codetics.ev_server.repositories.ChargingTokenRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.StripeEventRepository
import fr.codetics.ev_server.services.tokens.CardOrderService
import fr.codetics.ev_server.stripe.PaymentGateway
import java.time.Clock
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
    private val clock: Clock,
    private val userBilling: UserBillingService,
    private val cardOrders: CardOrderService,
) {
    fun receive(payload: String, signature: String) {
        val event = gateway.verifyEvent(payload, signature)
        if (events.existsById(event.id)) return
        if (cardOrders.handle(event)) return
        if (userBilling.handle(event)) {
            db.atomic { if (!events.existsById(event.id)) events.save(StripeEventEntity(event.id, event.accountId, event.type)) }
            return
        }
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
            val order = db.lock<ChargingPaymentEntity>(id) ?: return@atomic
            if (order.stripeAccountId != event.accountId)
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    msg("stripe.webhook.account_mismatch_payment"),
                )
            if (order.stripeCheckoutId == null)
                throw ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    msg("stripe.webhook.payment_pending"),
                )
            ensure(order.stripeCheckoutId == event.data.path("id").asText()) {
                msg("stripe.webhook.session_mismatch_payment")
            }
            if (events.existsById(event.id)) return@atomic
            if (
                event.type in
                    setOf("checkout.session.expired", "checkout.session.async_payment_failed")
            ) {
                if (order.state == PaymentState.PENDING_PAYMENT) {
                    order.state = PaymentState.CANCELLED
                    order.updatedAt = Instant.now(clock)
                    lifecycle.disableToken(order)
                }
            } else if (event.data.path("payment_status").asText() == "paid") {
                ensure(
                    event.data.path("amount_total").longValue() == order.prepaidMinor &&
                        event.data.path("currency").asText().equals(order.currency, true)
                ) {
                    msg("stripe.webhook.amount_mismatch_payment")
                }
                val intent = event.data.path("payment_intent").asText()
                ensure(intent.startsWith("pi_")) { msg("stripe.webhook.incomplete") }
                if (order.stripePaymentIntentId == null) {
                    order.stripePaymentIntentId = intent
                    val station =
                        stations.findById(StationKey(order.tenantId, order.stationId)).orElse(null)
                    if (
                        order.state != PaymentState.PENDING_PAYMENT ||
                            order.expiresAt.isBefore(Instant.now(clock)) ||
                            station?.enabled != true ||
                            !station.connected
                    ) {
                        lifecycle.settleWithoutCharging(
                            order,
                            msg("checkout.start_failed_refunded"),
                        )
                    } else {
                        val token =
                            tokens
                                .findById(ChargingTokenKey(order.tenantId, order.tokenUid))
                                .orElseThrow()
                        token.enabled = true
                        token.expiresAt = Instant.now(clock).plus(ChargingTimeouts.START_WINDOW)
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
                        order.updatedAt = Instant.now(clock)
                    }
                } else
                    ensure(order.stripePaymentIntentId == intent) {
                        msg("stripe.webhook.payment_changed")
                    }
            }
            payments.save(order)
            events.save(StripeEventEntity(event.id, event.accountId, event.type))
        }
    }
}
