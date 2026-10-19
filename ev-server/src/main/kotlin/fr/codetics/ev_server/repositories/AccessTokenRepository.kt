package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.AccessTokenEntity
import java.time.Instant
import java.util.UUID

interface AccessTokenRepository : StoreRepository<AccessTokenEntity, String> {
    fun deleteAllByUserId(userId: UUID)

    fun deleteAllByExpiresAtBefore(expiry: Instant)
}
