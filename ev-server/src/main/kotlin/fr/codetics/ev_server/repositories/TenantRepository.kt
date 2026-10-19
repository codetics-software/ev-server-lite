package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.TenantEntity
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface TenantRepository : JpaRepository<TenantEntity, UUID> {
    fun findBySlugAndActiveTrue(slug: String): TenantEntity?
}
