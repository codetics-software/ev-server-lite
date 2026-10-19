package fr.codetics.ev_server.services.billing

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.lock
import fr.codetics.ev_server.models.billing.PaymentState
import fr.codetics.ev_server.models.billing.tariff
import fr.codetics.ev_server.models.commands.CommandState
import fr.codetics.ev_server.models.entities.ChargingPaymentEntity
import fr.codetics.ev_server.repositories.ChargingPaymentRepository
import fr.codetics.ev_server.repositories.CommandRepository
import fr.codetics.ev_server.repositories.TransactionRepository
import fr.codetics.ev_server.stripe.PaymentGateway
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ChargingPaymentWorker(
    private val payments: ChargingPaymentRepository,
    private val commands: CommandRepository,
    private val transactions: TransactionRepository,
    private val lifecycle: ChargingPaymentLifecycle,
    private val checkout: ChargingCheckoutService,
    private val gateway: PaymentGateway,
    private val db: Database,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        fixedDelayString = "\${app.payments.poll-milliseconds:10000}",
        initialDelayString = "\${app.payments.initial-delay-milliseconds:10000}",
    )
    fun run() {
        if (!gateway.configured) return
        payments
            .findAllByStateIn(
                PaymentState.WORKABLE,
                org.springframework.data.domain.Pageable.unpaged(),
            )
            .forEach { order ->
                try {
                    advance(order.id)
                } catch (error: Exception) {
                    // Les données financières restent en base et seront reprises au prochain
                    // passage.
                    log.warn(
                        "Reprise du paiement {} différée ({})",
                        order.id,
                        error.javaClass.simpleName,
                    )
                    log.debug("Détail de l’échec du paiement {}", order.id, error)
                }
            }
    }

    fun advance(id: UUID) {
        val snapshot = payments.findById(id).orElseThrow()
        if (
            snapshot.state == PaymentState.PENDING_PAYMENT &&
                snapshot.stripeCheckoutId == null &&
                snapshot.expiresAt.isAfter(Instant.now(clock))
        ) {
            checkout.initialize(id)
        }
        db.atomic {
            val order = requireNotNull(db.lock<ChargingPaymentEntity>(id))
            val now = Instant.now(clock)
            when (order.state) {
                PaymentState.PENDING_PAYMENT ->
                    if (order.expiresAt.isBefore(now)) {
                        order.state = PaymentState.CANCELLED
                        lifecycle.disableToken(order)
                    }
                PaymentState.START_REQUESTED -> {
                    val command = order.startCommandId?.let { commands.findById(it).orElse(null) }
                    val rejected =
                        command?.state == CommandState.SUCCEEDED &&
                            db.decode(command.result ?: "{}").path("status").asText() == "Rejected"
                    val startDeadline = order.updatedAt.plus(ChargingTimeouts.START_WINDOW)
                    if (rejected || startDeadline.isBefore(now)) {
                        lifecycle.settleWithoutCharging(
                            order,
                            "La borne n’a pas démarré la recharge. Le crédit est remboursé.",
                        )
                        // Une commande non transmise ne doit plus démarrer après le remboursement.
                        if (command?.state == CommandState.QUEUED) {
                            command.state = CommandState.CANCELLED
                            commands.save(command)
                        }
                    }
                }
                PaymentState.CHARGING -> {
                    val transaction =
                        order.transactionId?.let { transactions.findById(it).orElse(null) }
                    if (transaction != null) {
                        val estimate =
                            order
                                .tariff()
                                .amountMinor(order.observedEnergyWh, transaction.startedAt, now)
                        if (
                            estimate >= order.prepaidMinor ||
                                order.createdAt.plus(ChargingTimeouts.MAX_SESSION).isBefore(now)
                        )
                            lifecycle.requestStop(order)
                    }
                }
                else -> Unit
            }
            payments.save(order)
        }
        settle(id)
    }

    private fun settle(id: UUID) {
        val order = payments.findById(id).orElseThrow()
        if (order.state !in setOf(PaymentState.SETTLING, PaymentState.REFUND_PENDING)) return
        val unused = order.prepaidMinor - requireNotNull(order.billedMinor)
        val refund =
            if (unused <= 0) null
            else if (order.refundId != null)
                gateway.retrieveRefund(order.stripeAccountId, requireNotNull(order.refundId))
            else
                gateway.refund(
                    order.stripeAccountId,
                    requireNotNull(order.stripePaymentIntentId),
                    unused,
                    order.id,
                )
        db.atomic {
            val saved = requireNotNull(db.lock<ChargingPaymentEntity>(id))
            if (saved.state !in setOf(PaymentState.SETTLING, PaymentState.REFUND_PENDING))
                return@atomic
            saved.refundId = refund?.id
            saved.updatedAt = Instant.now(clock)
            when (refund?.status) {
                null,
                "succeeded" -> {
                    saved.refundedMinor = unused.coerceAtLeast(0)
                    saved.state = PaymentState.COMPLETED
                }
                "failed",
                "canceled" -> {
                    saved.state = PaymentState.FAILED
                    saved.lastError = "Le remboursement nécessite l’intervention de l’exploitant."
                }
                else -> saved.state = PaymentState.REFUND_PENDING
            }
            payments.save(saved)
        }
    }
}
