package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.TransactionEntity
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface TransactionRepository : StoreRepository<TransactionEntity, Int> {
    fun countByTenantId(tenant: UUID): Long

    fun countByTenantIdAndStoppedAtIsNull(tenant: UUID): Long

    fun findAllByTenantId(tenant: UUID, pageable: Pageable): Page<TransactionEntity>

    fun findAllByTenantIdAndUserId(
        tenant: UUID,
        user: UUID,
        pageable: Pageable,
    ): Page<TransactionEntity>

    fun findByTenantIdAndStationIdAndId(tenant: UUID, station: String, id: Int): TransactionEntity?

    fun findAllByTenantIdAndStoppedAtIsNull(tenant: UUID): List<TransactionEntity>

    fun findAllByTenantIdAndStationIdAndStoppedAtIsNull(
        tenant: UUID,
        station: String,
    ): List<TransactionEntity>

    fun findAllByStoppedAtIsNull(): List<TransactionEntity>

    fun findByTenantIdAndId(tenant: UUID, id: Int): TransactionEntity?

    fun findFirstByTenantIdAndStationIdAndConnectorIdAndStoppedAtIsNull(
        tenant: UUID,
        station: String,
        connector: Int,
    ): TransactionEntity?

    fun existsByTenantIdAndStationIdAndStoppedAtIsNull(tenant: UUID, station: String): Boolean

    fun findFirstByTenantIdOrderByStartedAtAsc(tenant: UUID): TransactionEntity?

    fun existsByTenantIdAndStationIdAndConnectorIdAndStoppedAtIsNull(
        tenant: UUID,
        station: String,
        connector: Int,
    ): Boolean
}
