package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.ChargingProfileEntity
import java.util.UUID

interface ChargingProfileRepository : StoreRepository<ChargingProfileEntity, UUID> {
    fun findByTenantIdAndId(tenant: UUID, id: UUID): ChargingProfileEntity?

    fun findAllByTenantIdOrderByCreatedAtDesc(tenant: UUID): List<ChargingProfileEntity>

    fun findAllByTenantIdAndStationIdOrderByCreatedAtDesc(
        tenant: UUID,
        station: String,
    ): List<ChargingProfileEntity>

    fun findAllByTenantIdAndStationIdAndSource(
        tenant: UUID,
        station: String,
        source: String,
    ): List<ChargingProfileEntity>
}
