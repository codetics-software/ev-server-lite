package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.AuditEntity
import java.util.UUID
import org.springframework.data.domain.Pageable

interface AuditRepository : StoreRepository<AuditEntity, Long> {
    fun findAllByTenantIdOrderByIdDesc(tenant: UUID, pageable: Pageable): List<AuditEntity>
}
