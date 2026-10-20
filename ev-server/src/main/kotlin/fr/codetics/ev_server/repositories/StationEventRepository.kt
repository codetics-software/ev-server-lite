package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.StationEventEntity
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface StationEventRepository : StoreRepository<StationEventEntity, Long> {
    fun findAllByTenantIdAndStationId(
        tenant: UUID,
        station: String,
        pageable: Pageable,
    ): Page<StationEventEntity>

    fun findAllByTenantIdAndStationIdAndType(
        tenant: UUID,
        station: String,
        type: String,
        pageable: Pageable,
    ): Page<StationEventEntity>

    fun findAllByTypeAndStatusAndCreatedAtBetween(
        type: String,
        status: String,
        from: Instant,
        to: Instant,
    ): List<StationEventEntity>

    fun findFirstByTenantIdAndStationIdAndTypeAndCreatedAtAfterOrderByCreatedAtDesc(
        tenant: UUID,
        station: String,
        type: String,
        after: Instant,
    ): StationEventEntity?
}
