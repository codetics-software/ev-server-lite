package fr.codetics.ev_server.persistence

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TransactionRepository : JpaRepository<TransactionEntity, Int>, JpaSpecificationExecutor<TransactionEntity> {
    fun countByTenantId(tenant: UUID): Long
    fun countByTenantIdAndStoppedAtIsNull(tenant: UUID): Long

    @Query(
        """
        select coalesce(sum(t.meterStopWh - t.meterStartWh), 0)
        from TransactionEntity t
        where t.tenantId = :tenant
          and t.meterStopWh >= t.meterStartWh
        """
    )
    fun deliveredEnergyWh(@Param("tenant") tenant: UUID): java.math.BigDecimal
    fun findByTenantIdAndStationIdAndId(tenant: UUID, station: String, id: Int): TransactionEntity?
    fun existsByTenantIdAndStationIdAndConnectorIdAndStoppedAtIsNull(tenant: UUID, station: String, connector: Int): Boolean
}
