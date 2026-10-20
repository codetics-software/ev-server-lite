package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.OcpiJobEntity
import java.util.UUID
import org.springframework.data.domain.Pageable

interface OcpiJobRepository : StoreRepository<OcpiJobEntity, UUID> {
    fun findAllByTenantIdAndPartnerIdOrderByStartedAtDesc(
        tenant: UUID,
        partner: UUID,
        pageable: Pageable,
    ): List<OcpiJobEntity>
}
