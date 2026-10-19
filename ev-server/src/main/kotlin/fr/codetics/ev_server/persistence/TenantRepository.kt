package fr.codetics.ev_server.persistence

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface TenantRepository : JpaRepository<TenantEntity, UUID> {
    fun findBySlugAndActiveTrue(slug: String): TenantEntity?
}
