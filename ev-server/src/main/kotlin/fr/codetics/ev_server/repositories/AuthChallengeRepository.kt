package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.AuthChallengeEntity
import jakarta.persistence.LockModeType
import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AuthChallengeRepository : JpaRepository<AuthChallengeEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AuthChallengeEntity c where c.tokenHash = :hash")
    fun findForConsumption(@Param("hash") hash: String): AuthChallengeEntity?

    fun deleteAllByUserIdAndKind(userId: UUID, kind: String)

    fun deleteAllByExpiresAtBefore(expiry: Instant)
}
