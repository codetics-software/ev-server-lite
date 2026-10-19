package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.PublicEvseEntity
import java.util.UUID

interface PublicEvseRepository : StoreRepository<PublicEvseEntity, UUID> {
    fun findByTenantIdAndId(tenantId: UUID, id: UUID): PublicEvseEntity?

    fun findAllByTenantIdOrderByName(tenantId: UUID): List<PublicEvseEntity>

    fun findByTenantIdAndStationIdAndConnectorId(
        tenantId: UUID,
        stationId: String,
        connectorId: Int,
    ): PublicEvseEntity?
}
