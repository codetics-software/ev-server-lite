package fr.codetics.ev_server.core

import fr.codetics.ev_server.models.people.UserRole
import java.util.UUID

data class AppPrincipal(
    val id: UUID,
    val tenantId: UUID,
    val email: String,
    val name: String,
    val role: UserRole,
    val platformAdmin: Boolean = false,
    val eulaAccepted: Boolean = true,
)
