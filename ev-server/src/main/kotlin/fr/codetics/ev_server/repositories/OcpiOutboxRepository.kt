package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.OcpiOutboxEntity
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.Pageable

interface OcpiOutboxRepository : StoreRepository<OcpiOutboxEntity, UUID> {
    fun findAllBySentAtIsNullAndAttemptsLessThanAndNextAttemptAtBeforeOrderByCreatedAt(
        attempts: Int,
        now: Instant,
        pageable: Pageable,
    ): List<OcpiOutboxEntity>

    fun deleteAllBySentAtBefore(before: Instant)
}
