package fr.codetics.ev_server.persistence

import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface RecordRepository : JpaRepository<RecordEntity, RecordKey> {
    fun findAllByKeyTenantIdAndKeyKindAndNameContainingIgnoreCase(tenant: UUID, kind: String, search: String, pageable: Pageable): Page<RecordEntity>
}
