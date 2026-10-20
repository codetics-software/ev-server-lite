package fr.codetics.ev_server.models.people

data class UserInput(
    val email: String,
    val name: String,
    val role: UserRole,
    val enabled: Boolean = true,
    val phone: String? = null,
    val locale: String? = null,
)

data class ProfileInput(val name: String, val phone: String? = null, val locale: String? = null)

data class SiteAssignmentInput(
    val siteId: java.util.UUID? = null,
    val userId: java.util.UUID? = null,
    val siteAdmin: Boolean = false,
    val siteOwner: Boolean = false,
)
