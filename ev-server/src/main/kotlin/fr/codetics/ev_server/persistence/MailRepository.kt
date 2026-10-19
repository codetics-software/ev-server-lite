package fr.codetics.ev_server.persistence

import jakarta.persistence.LockModeType
import java.util.UUID
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock

interface MailRepository : JpaRepository<MailEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findAllBySentAtIsNullAndAttemptsLessThanOrderByCreatedAt(attempts: Int, pageable: Pageable): List<MailEntity>
}
