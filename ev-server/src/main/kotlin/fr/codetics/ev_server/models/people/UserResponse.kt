package fr.codetics.ev_server.models.people

import fr.codetics.ev_server.models.entities.UserEntity
import java.time.Instant
import java.util.UUID
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class UserResponse(
    val id: UUID,
    val email: String,
    val displayName: String,
    val role: UserRole,
    val enabled: Boolean,
    val emailVerified: Boolean,
    val phone: String? = null,
    val locale: String = "fr",
    val createdAt: Instant? = null,
    val lastLoginAt: Instant? = null,
    val billingCustomer: Boolean = false,
    val defaultCarId: UUID? = null,
    val siteCount: Int = 0,
    val eulaAccepted: Boolean = false,
) {
    companion object {
        fun from(user: UserEntity, sites: Int = 0, eulaVersion: String? = null) =
            UserResponse(
                user.id,
                user.email,
                user.displayName,
                user.role,
                user.enabled,
                user.emailVerified,
                user.phone,
                user.locale,
                user.createdAt,
                user.lastLoginAt,
                user.stripeCustomerId != null,
                user.defaultCarId,
                sites,
                eulaVersion != null && user.eulaVersion == eulaVersion,
            )
    }
}

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class UserInError(val user: UserResponse, val errors: List<String>)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class UserSiteResponse(val siteId: UUID, val name: String, val siteAdmin: Boolean, val siteOwner: Boolean)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SiteUserResponse(
    val userId: UUID,
    val name: String,
    val email: String,
    val siteAdmin: Boolean,
    val siteOwner: Boolean,
)

data class CreatedUser(
    val id: UUID,
    val message: String =
        "Un lien pour définir le mot de passe est envoyé à l’adresse électronique de l’utilisateur.",
)
