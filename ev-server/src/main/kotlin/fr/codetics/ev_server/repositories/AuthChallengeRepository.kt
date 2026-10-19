package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.AuthChallengeEntity
import fr.codetics.ev_server.models.people.ChallengeKind
import java.time.Instant
import java.util.UUID

interface AuthChallengeRepository : StoreRepository<AuthChallengeEntity, String> {
    fun deleteAllByUserIdAndKind(userId: UUID, kind: ChallengeKind)

    fun deleteAllByExpiresAtBefore(expiry: Instant)
}
