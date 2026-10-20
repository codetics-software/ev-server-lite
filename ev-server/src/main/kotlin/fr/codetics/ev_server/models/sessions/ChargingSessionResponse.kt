package fr.codetics.ev_server.models.sessions

import fr.codetics.ev_server.models.entities.TransactionEntity
import fr.codetics.ev_server.ocpp.AuthorizationStatus
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.util.UUID
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class ChargingSessionResponse(
    val id: Int?,
    val stationId: String,
    val connectorId: Int,
    val startedAt: Instant,
    val stoppedAt: Instant?,
    val energyKwh: BigDecimal?,
    val authorizationStatus: AuthorizationStatus,
    val stopReason: String?,
    val tokenUid: String? = null,
    val userId: UUID? = null,
    val userName: String? = null,
    val siteAreaId: UUID? = null,
    val siteId: UUID? = null,
    val durationSeconds: Long = 0,
    val inactivitySeconds: Long = 0,
    val currentPowerKw: BigDecimal? = null,
    val stateOfChargeStart: Int? = null,
    val stateOfCharge: Int? = null,
    val price: BigDecimal? = null,
    val currency: String? = null,
    val pricingDefinitionId: UUID? = null,
    val billingStatus: String = "NONE",
    val invoiceId: UUID? = null,
    val roamingPartner: String? = null,
    val publicPayment: Boolean = false,
    val softStopped: Boolean = false,
    val vehicleId: UUID? = null,
    val errors: List<String>? = null,
) {
    companion object {
        fun energyKwh(transaction: TransactionEntity): BigDecimal? =
            (transaction.meterStopWh ?: transaction.lastEnergyWh)
                ?.subtract(transaction.meterStartWh)
                ?.max(BigDecimal.ZERO)
                ?.divide(BigDecimal(1000), 3, RoundingMode.HALF_UP)

        fun from(
            transaction: TransactionEntity,
            userName: String? = null,
            partner: String? = null,
            publicPayment: Boolean = false,
            estimate: BigDecimal? = null,
            estimateCurrency: String? = null,
            now: Instant = Instant.now(),
        ) =
            ChargingSessionResponse(
                id = transaction.id,
                stationId = transaction.stationId,
                connectorId = transaction.connectorId,
                startedAt = transaction.startedAt,
                stoppedAt = transaction.stoppedAt,
                energyKwh = energyKwh(transaction),
                authorizationStatus = transaction.authorizationStatus,
                stopReason = transaction.stopReason,
                tokenUid = transaction.tokenUid,
                userId = transaction.userId,
                userName = userName,
                siteAreaId = transaction.siteAreaId,
                siteId = transaction.siteId,
                durationSeconds =
                    Duration.between(transaction.startedAt, transaction.stoppedAt ?: now)
                        .seconds
                        .coerceAtLeast(0),
                inactivitySeconds = transaction.inactivitySeconds,
                currentPowerKw =
                    if (transaction.stoppedAt == null) {
                        transaction.lastPowerW?.divide(BigDecimal(1000), 3, RoundingMode.HALF_UP)
                    } else {
                        null
                    },
                stateOfChargeStart = transaction.socStart,
                stateOfCharge = transaction.socLast,
                price = transaction.price ?: estimate,
                currency = transaction.currency ?: estimateCurrency,
                pricingDefinitionId = transaction.pricingDefinitionId,
                billingStatus = transaction.billingStatus,
                invoiceId = transaction.invoiceId,
                roamingPartner = partner,
                publicPayment = publicPayment,
                softStopped = transaction.softStopped,
                vehicleId = transaction.vehicleId,
            )
    }
}

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class ConsumptionPoint(
    val at: Instant,
    val energyKwh: BigDecimal?,
    val powerKw: BigDecimal?,
    val soc: Int?,
    val amperageA: BigDecimal?,
    val voltageV: BigDecimal?,
    val cumulatedPrice: BigDecimal?,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SessionInError(val session: ChargingSessionResponse, val errors: List<String>)

data class SessionStartInput(val stationId: String, val connectorId: Int, val tokenUid: String)
