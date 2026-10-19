package fr.codetics.ev_server.tokens

import java.time.Instant
import java.util.UUID

data class TokenInput(
    val uid: String,
    val userId: UUID? = null,
    val enabled: Boolean = true,
    val expiresAt: Instant? = null
)
