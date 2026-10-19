package fr.codetics.ev_server.people

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
    val emailVerified: Boolean
)

data class CreatedUser(
    val id: UUID,
    val message: String = "Use password reset and email verification to activate this account"
)
