package fr.codetics.ev_server.persistence

import jakarta.persistence.LockModeType
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PartnerRepository : JpaRepository<PartnerEntity, UUID> {
    fun findAllByTenantIdOrderByName(tenant: UUID): List<PartnerEntity>
    fun findByTokenHashAndLocalRoleAndEnabledTrue(hash: String, role: String): PartnerEntity?
    fun findByTenantIdAndId(tenant: UUID, id: UUID): PartnerEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PartnerEntity p where p.id = :id")
    fun findForUpdate(@Param("id") id: UUID): PartnerEntity?
}
