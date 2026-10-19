package fr.codetics.ev_server.persistence

import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface AccessTokenRepository : JpaRepository<AccessTokenEntity, String> {
    fun deleteAllByUserId(userId: UUID)
    fun deleteAllByExpiresAtBefore(expiry: Instant)
}
