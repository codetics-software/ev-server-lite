package fr.codetics.ev_server.persistence

import java.util.UUID
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface AuditRepository : JpaRepository<AuditEntity, Long> {
    fun findAllByTenantIdOrderByIdDesc(tenant: UUID, pageable: Pageable): List<AuditEntity>
}
