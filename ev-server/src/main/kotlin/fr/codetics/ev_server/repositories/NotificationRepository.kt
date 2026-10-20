package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.NotificationEntity
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface NotificationRepository : StoreRepository<NotificationEntity, UUID> {
    fun findAllByTenantId(tenant: UUID, pageable: Pageable): Page<NotificationEntity>

    fun findAllByTenantIdAndType(tenant: UUID, type: String, pageable: Pageable): Page<NotificationEntity>

    fun findAllByTenantIdAndUserId(tenant: UUID, user: UUID, pageable: Pageable): Page<NotificationEntity>

    fun findAllByTenantIdAndUserIdAndType(
        tenant: UUID,
        user: UUID,
        type: String,
        pageable: Pageable,
    ): Page<NotificationEntity>

    fun existsByTenantIdAndDedupeKeyAndCreatedAtAfter(tenant: UUID, key: String, after: Instant): Boolean

    fun deleteAllByCreatedAtBefore(before: Instant)
}
