package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.OcpiObjectEntity
import fr.codetics.ev_server.models.entities.OcpiObjectKey
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface OcpiObjectRepository : StoreRepository<OcpiObjectEntity, OcpiObjectKey> {
    fun findAllByKeyTenantIdAndKeyPartnerIdAndKeyModuleAndKeyDirectionAndLastUpdatedGreaterThanEqualAndLastUpdatedLessThan(
        tenant: UUID,
        partner: UUID,
        module: String,
        direction: String,
        from: Instant,
        to: Instant,
        pageable: Pageable,
    ): Page<OcpiObjectEntity>
}
