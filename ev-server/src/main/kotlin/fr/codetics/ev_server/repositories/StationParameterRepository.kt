package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.StationParameterEntity
import fr.codetics.ev_server.models.entities.StationParameterKey
import java.util.UUID

interface StationParameterRepository : StoreRepository<StationParameterEntity, StationParameterKey> {
    fun findAllByKeyTenantIdAndKeyStationIdOrderByKeyKey(
        tenant: UUID,
        station: String,
    ): List<StationParameterEntity>

    fun findAllByKeyTenantIdOrderByKeyStationIdAscKeyKeyAsc(tenant: UUID): List<StationParameterEntity>
}
