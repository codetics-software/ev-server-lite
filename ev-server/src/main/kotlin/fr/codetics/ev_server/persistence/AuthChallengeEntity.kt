package fr.codetics.ev_server.persistence

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "auth_challenges")
class AuthChallengeEntity(
    @Id var tokenHash: String = "",
    var userId: UUID = UUID.randomUUID(),
    var kind: String = "VERIFY",
    var expiresAt: Instant = Instant.now()
)
