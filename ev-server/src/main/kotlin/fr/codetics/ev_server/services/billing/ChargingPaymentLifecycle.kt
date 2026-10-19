package fr.codetics.ev_server.services.billing

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.models.billing.ChargingTariff
import fr.codetics.ev_server.models.billing.PaymentState
import fr.codetics.ev_server.models.entities.ChargingPaymentEntity
import fr.codetics.ev_server.models.entities.ChargingTokenKey
import fr.codetics.ev_server.models.entities.TransactionEntity
import fr.codetics.ev_server.ocpp.OcppCommandQueue
import fr.codetics.ev_server.repositories.ChargingPaymentRepository
import fr.codetics.ev_server.repositories.ChargingTokenRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service

/** Ces opérations participent à la transaction de base de données du message OCPP. */
@Service
class ChargingPaymentLifecycle(
    private val payments: ChargingPaymentRepository,
    private val tokens: ChargingTokenRepository,
    private val queue: OcppCommandQueue,
    private val db: Database,
) {
    fun authorization(tenant: UUID, station: String, connector: Int?, uid: String): String? {
        val order = payments.findByTenantIdAndTokenUid(tenant, uid) ?: return null
        return if (
            order.stationId == station &&
                (connector == null || order.connectorId == connector) &&
                order.state == PaymentState.START_REQUESTED &&
                order.updatedAt.plusSeconds(300).isAfter(Instant.now())
        )
            "Accepted"
        else "Blocked"
    }

    fun started(transaction: TransactionEntity) {
        val candidate =
            payments.findByTenantIdAndTokenUid(transaction.tenantId, transaction.tokenUid) ?: return
        val order = requireNotNull(payments.findForUpdate(candidate.id))
        if (
            transaction.authorizationStatus != "Accepted" ||
                order.state != PaymentState.START_REQUESTED ||
                order.stationId != transaction.stationId ||
                order.connectorId != transaction.connectorId
        ) {
            // Une borne peut annoncer une transaction malgré un refus : demander son arrêt réel.
            queue.command(
                transaction.tenantId,
                transaction.stationId,
                "RemoteStopTransaction",
                db.json.valueToTree(mapOf("transactionId" to transaction.id)),
            )
            return
        }
        order.transactionId = transaction.id
        order.state = PaymentState.CHARGING
        order.updatedAt = Instant.now()
        transaction.tariffSnapshot = db.encode(tariff(order))
        disableToken(order)
        payments.save(order)
    }

    fun observed(transaction: TransactionEntity, registerWh: BigDecimal, sampledAt: Instant) {
        val candidate =
            payments.findByTenantIdAndTransactionId(
                transaction.tenantId,
                requireNotNull(transaction.id),
            ) ?: return
        val order = requireNotNull(payments.findForUpdate(candidate.id))
        if (
            order.state !in setOf(PaymentState.CHARGING, PaymentState.STOP_REQUESTED) ||
                sampledAt.isBefore(transaction.startedAt) ||
                sampledAt.isAfter(Instant.now().plusSeconds(300))
        )
            return
        order.observedEnergyWh =
            order.observedEnergyWh.max((registerWh - transaction.meterStartWh).max(BigDecimal.ZERO))
        if (
            tariff(order).amountMinor(order.observedEnergyWh, transaction.startedAt, sampledAt) >=
                order.prepaidMinor
        ) {
            requestStop(order)
        }
        payments.save(order)
    }

    fun stopped(transaction: TransactionEntity) {
        val candidate =
            payments.findByTenantIdAndTransactionId(
                transaction.tenantId,
                requireNotNull(transaction.id),
            ) ?: return
        val order = requireNotNull(payments.findForUpdate(candidate.id))
        if (order.state !in setOf(PaymentState.CHARGING, PaymentState.STOP_REQUESTED)) return
        val energy =
            (requireNotNull(transaction.meterStopWh) - transaction.meterStartWh).max(
                BigDecimal.ZERO
            )
        order.billedMinor =
            tariff(order)
                .amountMinor(energy, transaction.startedAt, requireNotNull(transaction.stoppedAt))
                .coerceAtMost(order.prepaidMinor)
        order.state = PaymentState.SETTLING
        order.updatedAt = Instant.now()
        disableToken(order)
        payments.save(order)
    }

    fun requestStop(order: ChargingPaymentEntity) {
        if (order.state != PaymentState.CHARGING) return
        order.stopCommandId =
            queue.command(
                order.tenantId,
                order.stationId,
                "RemoteStopTransaction",
                db.json.valueToTree(mapOf("transactionId" to requireNotNull(order.transactionId))),
            )
        order.state = PaymentState.STOP_REQUESTED
        order.updatedAt = Instant.now()
    }

    fun settleWithoutCharging(order: ChargingPaymentEntity, message: String) {
        if (order.transactionId != null) return
        order.billedMinor = 0
        order.state = PaymentState.SETTLING
        order.lastError = message
        order.updatedAt = Instant.now()
        disableToken(order)
    }

    fun disableToken(order: ChargingPaymentEntity) {
        tokens.findById(ChargingTokenKey(order.tenantId, order.tokenUid)).orElse(null)?.let {
            it.enabled = false
            tokens.save(it)
        }
    }

    private fun tariff(order: ChargingPaymentEntity) =
        ChargingTariff(order.currency, order.energyPrice, order.timePrice, order.flatPrice)
}
