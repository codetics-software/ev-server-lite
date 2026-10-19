package fr.codetics.ev_server.models.tokens

import java.time.Instant
import java.util.UUID
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class ChargingTokenResponse(
    val uid: String,
    val userId: UUID?,
    val type: String,
    val enabled: Boolean,
    val expiresAt: Instant?,
)
