package fr.codetics.ev_server.persistence

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "auth_attempts")
class AuthAttemptEntity(
    @Id var subjectHash: String = "",
    var windowStart: Instant = Instant.now(),
    var attempts: Int = 0
)
