package fr.codetics.ev_server.models.tokens

import fr.codetics.ev_server.models.entities.ChargingTokenEntity
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
    val visualId: String? = null,
    val description: String? = null,
    val userName: String? = null,
    val default: Boolean = false,
    val lastUsedAt: Instant? = null,
    val vehicleId: UUID? = null,
    val vehicleName: String? = null,
) {
    companion object {
        fun from(token: ChargingTokenEntity, userName: String? = null, vehicleName: String? = null) =
            ChargingTokenResponse(
                token.key.uid,
                token.userId,
                token.type,
                token.enabled,
                token.expiresAt,
                token.visualId,
                token.description,
                userName,
                token.defaultToken,
                token.lastUsedAt,
                token.vehicleId,
                vehicleName,
            )
    }
}
