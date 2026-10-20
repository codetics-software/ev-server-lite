package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.AssetConnectionEntity
import java.util.UUID

interface AssetConnectionRepository : StoreRepository<AssetConnectionEntity, UUID> {
    fun findByTenantIdAndAssetId(tenant: UUID, asset: UUID): AssetConnectionEntity?

    fun findByPushTokenHash(hash: String): AssetConnectionEntity?
}
