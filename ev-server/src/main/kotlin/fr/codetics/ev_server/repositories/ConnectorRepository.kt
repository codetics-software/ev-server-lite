package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.ConnectorEntity
import fr.codetics.ev_server.models.entities.ConnectorKey
import java.util.UUID

interface ConnectorRepository : StoreRepository<ConnectorEntity, ConnectorKey> {
    fun findAllByKeyTenantIdAndKeyStationIdOrderByKeyConnectorId(
        tenant: UUID,
        station: String,
    ): List<ConnectorEntity>

    fun findAllByKeyTenantId(tenant: UUID): List<ConnectorEntity>

    fun findAllByStatusAndStatusAtBefore(status: String, before: java.time.Instant): List<ConnectorEntity>
}
