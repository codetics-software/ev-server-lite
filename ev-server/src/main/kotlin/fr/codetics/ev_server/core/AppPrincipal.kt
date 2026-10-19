package fr.codetics.ev_server.core

import java.util.UUID

data class AppPrincipal(
    val id: UUID,
    val tenantId: UUID,
    val email: String,
    val name: String,
    val role: String
)
