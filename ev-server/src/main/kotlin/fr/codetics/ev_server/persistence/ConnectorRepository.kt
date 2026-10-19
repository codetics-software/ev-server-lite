package fr.codetics.ev_server.persistence

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface ConnectorRepository : JpaRepository<ConnectorEntity, ConnectorKey> {
    fun findAllByKeyTenantIdAndKeyStationIdOrderByKeyConnectorId(tenant: UUID, station: String): List<ConnectorEntity>
    fun findAllByKeyTenantId(tenant: UUID): List<ConnectorEntity>
}
