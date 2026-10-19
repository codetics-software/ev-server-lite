package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationKey
import jakarta.persistence.LockModeType
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface StationRepository : JpaRepository<StationEntity, StationKey> {
    fun countByKeyTenantId(tenant: UUID): Long

    fun countByKeyTenantIdAndConnectedTrue(tenant: UUID): Long

    fun findAllByKeyTenantId(tenant: UUID, pageable: Pageable): Page<StationEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StationEntity s where s.key = :key")
    fun findForUpdate(@Param("key") key: StationKey): StationEntity?
}
