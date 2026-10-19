package fr.codetics.ev_server.overview

import java.math.BigDecimal
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

data class OverviewResponse(
    val stations: StationTotals,
    val transactions: SessionTotals,
    val connectors: List<ConnectorTotal>
)

data class StationTotals(val total: Long, val online: Long)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SessionTotals(val total: Long, val active: Long, val energyKwh: BigDecimal)

data class ConnectorTotal(val status: String, val count: Int)
