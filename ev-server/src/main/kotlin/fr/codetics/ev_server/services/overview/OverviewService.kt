package fr.codetics.ev_server.services.overview

import fr.codetics.ev_server.models.entities.ConnectorEntity
import fr.codetics.ev_server.models.overview.ConnectorTotal
import fr.codetics.ev_server.models.overview.OverviewResponse
import fr.codetics.ev_server.models.overview.SessionTotals
import fr.codetics.ev_server.models.overview.StationTotals
import fr.codetics.ev_server.persistence.SessionStatistics
import fr.codetics.ev_server.repositories.ConnectorRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TransactionRepository
import java.math.BigDecimal
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class OverviewService(
    private val stations: StationRepository,
    private val connectors: ConnectorRepository,
    private val sessions: TransactionRepository,
    private val statistics: SessionStatistics,
) {
    fun summarize(tenantId: UUID): OverviewResponse {
        val physicalConnectors =
            connectors
                .findAllByKeyTenantId(tenantId)
                .filter { it.key.connectorId > 0 }
                .groupingBy(ConnectorEntity::status)
                .eachCount()

        return OverviewResponse(
            stations =
                StationTotals(
                    stations.countByKeyTenantIdAndDeletedFalse(tenantId),
                    stations.countByKeyTenantIdAndConnectedTrueAndDeletedFalse(tenantId),
                ),
            transactions =
                SessionTotals(
                    sessions.countByTenantId(tenantId),
                    sessions.countByTenantIdAndStoppedAtIsNull(tenantId),
                    statistics.deliveredEnergyWh(tenantId).divide(BigDecimal(1000)),
                ),
            connectors = physicalConnectors.map { (status, count) -> ConnectorTotal(status, count) },
        )
    }
}
