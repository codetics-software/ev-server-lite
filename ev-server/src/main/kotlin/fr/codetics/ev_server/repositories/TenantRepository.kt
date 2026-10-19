package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.TenantEntity
import java.util.UUID

interface TenantRepository : StoreRepository<TenantEntity, UUID> {
    fun findBySlugAndActiveTrue(slug: String): TenantEntity?
}
