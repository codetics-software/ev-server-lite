package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationKey
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface StationRepository : StoreRepository<StationEntity, StationKey> {
    fun countByKeyTenantId(tenant: UUID): Long

    fun countByKeyTenantIdAndConnectedTrue(tenant: UUID): Long

    fun findAllByKeyTenantId(tenant: UUID, pageable: Pageable): Page<StationEntity>

    fun findAllByKeyTenantIdAndDeletedFalse(tenant: UUID): List<StationEntity>

    fun findAllByKeyTenantIdAndSiteAreaIdAndDeletedFalse(tenant: UUID, area: UUID): List<StationEntity>

    fun findAllByDeletedFalseAndConnectedTrue(): List<StationEntity>

    fun findAllByDeletedFalseAndEnabledTrueAndLastSeenBefore(before: java.time.Instant): List<StationEntity>

    fun countByKeyTenantIdAndDeletedFalse(tenant: UUID): Long

    fun countByKeyTenantIdAndConnectedTrueAndDeletedFalse(tenant: UUID): Long
}
