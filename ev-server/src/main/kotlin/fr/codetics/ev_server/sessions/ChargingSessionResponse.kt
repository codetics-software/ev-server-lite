package fr.codetics.ev_server.sessions

import fr.codetics.ev_server.persistence.TransactionEntity
import java.math.BigDecimal
import java.time.Instant
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
    val authorizationStatus: String,
    val stopReason: String?
) {
    companion object {
        fun from(transaction: TransactionEntity) = ChargingSessionResponse(
            id = transaction.id,
            stationId = transaction.stationId,
            connectorId = transaction.connectorId,
            startedAt = transaction.startedAt,
            stoppedAt = transaction.stoppedAt,
            energyKwh = transaction.meterStopWh?.subtract(transaction.meterStartWh)
                ?.max(BigDecimal.ZERO)?.divide(BigDecimal(1000)),
            authorizationStatus = transaction.authorizationStatus,
            stopReason = transaction.stopReason
        )
    }
}
