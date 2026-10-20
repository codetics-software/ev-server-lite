package fr.codetics.ev_server.models.tokens

import java.time.Instant
import java.util.UUID

data class TokenInput(
    val uid: String,
    val userId: UUID? = null,
    val enabled: Boolean = true,
    val expiresAt: Instant? = null,
    val visualId: String? = null,
    val description: String? = null,
    val default: Boolean = false,
)

data class TokenAssignment(val userId: UUID)
