package fr.codetics.ev_server.persistence

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "access_tokens")
class AccessTokenEntity(
    @Id var tokenHash: String = "",
    var userId: UUID = UUID.randomUUID(),
    var expiresAt: Instant = Instant.now(),
    var createdAt: Instant = Instant.now()
)
