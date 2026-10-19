package fr.codetics.ev_server.overview

import fr.codetics.ev_server.persistence.ConnectorEntity
import fr.codetics.ev_server.persistence.ConnectorRepository
import fr.codetics.ev_server.persistence.StationRepository
import fr.codetics.ev_server.persistence.TransactionRepository
import java.math.BigDecimal
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class OverviewService(
    private val stations: StationRepository,
    private val connectors: ConnectorRepository,
    private val sessions: TransactionRepository
) {
    fun summarize(tenantId: UUID): OverviewResponse {
        val physicalConnectors = connectors.findAllByKeyTenantId(tenantId)
            .filter { it.key.connectorId > 0 }
            .groupingBy(ConnectorEntity::status)
            .eachCount()

        return OverviewResponse(
            stations = StationTotals(stations.countByKeyTenantId(tenantId), stations.countByKeyTenantIdAndConnectedTrue(tenantId)),
            transactions = SessionTotals(
                sessions.countByTenantId(tenantId),
                sessions.countByTenantIdAndStoppedAtIsNull(tenantId),
                sessions.deliveredEnergyWh(tenantId).divide(BigDecimal(1000))
            ),
            connectors = physicalConnectors.map { (status, count) -> ConnectorTotal(status, count) }
        )
    }
}
