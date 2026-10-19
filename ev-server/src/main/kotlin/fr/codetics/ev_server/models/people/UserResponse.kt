package fr.codetics.ev_server.models.people

import java.util.UUID
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class UserResponse(
    val id: UUID,
    val email: String,
    val displayName: String,
    val role: String,
    val enabled: Boolean,
    val emailVerified: Boolean,
)

data class CreatedUser(
    val id: UUID,
    val message: String =
        "Pour activer ce compte, définissez un mot de passe et vérifiez l’adresse électronique.",
)
