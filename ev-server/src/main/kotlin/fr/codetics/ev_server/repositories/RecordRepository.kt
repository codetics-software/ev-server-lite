package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.RecordEntity
import fr.codetics.ev_server.models.entities.RecordKey
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface RecordRepository : JpaRepository<RecordEntity, RecordKey> {
    fun findAllByKeyTenantIdAndKeyKindAndNameContainingIgnoreCase(
        tenant: UUID,
        kind: String,
        search: String,
        pageable: Pageable,
    ): Page<RecordEntity>
}
