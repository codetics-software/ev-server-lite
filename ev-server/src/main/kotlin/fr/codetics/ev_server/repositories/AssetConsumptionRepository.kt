package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.AssetConsumptionEntity
import java.time.Instant
import java.util.UUID

interface AssetConsumptionRepository : StoreRepository<AssetConsumptionEntity, Long> {
    fun findAllByTenantIdAndAssetIdAndSampledAtBetweenOrderBySampledAt(
        tenant: UUID,
        asset: UUID,
        from: Instant,
        to: Instant,
    ): List<AssetConsumptionEntity>

    fun findFirstByTenantIdAndAssetIdOrderBySampledAtDesc(tenant: UUID, asset: UUID): AssetConsumptionEntity?

    fun deleteAllBySampledAtBefore(before: Instant)
}
