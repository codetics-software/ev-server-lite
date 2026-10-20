package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.MeterValueEntity
import java.time.Instant
import java.util.UUID

interface MeterValueRepository : StoreRepository<MeterValueEntity, Long> {
    fun findAllByTenantIdAndTransactionIdOrderBySampledAt(
        tenant: UUID,
        transaction: Int,
    ): List<MeterValueEntity>

    fun findAllByTenantIdAndStationIdInAndSampledAtBetweenOrderBySampledAt(
        tenant: UUID,
        stations: Collection<String>,
        from: Instant,
        to: Instant,
    ): List<MeterValueEntity>

    fun deleteAllByTenantIdAndTransactionId(tenant: UUID, transaction: Int)

    fun deleteAllBySampledAtBeforeAndTransactionIdIsNull(before: Instant)
}
