package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.MailEntity
import java.util.UUID
import org.springframework.data.domain.Pageable

interface MailRepository : StoreRepository<MailEntity, UUID> {
    fun findAllBySentAtIsNullAndAttemptsLessThanOrderByCreatedAt(
        attempts: Int,
        pageable: Pageable,
    ): List<MailEntity>
}
