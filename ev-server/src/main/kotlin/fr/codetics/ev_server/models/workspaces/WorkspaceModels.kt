package fr.codetics.ev_server.models.workspaces

import java.util.UUID

data class WorkspaceResponse(val id: UUID, val slug: String, val name: String, val active: Boolean)

data class WorkspaceUpdate(val name: String)

data class WorkspaceCreation(
    val name: String,
    val slug: String,
    val administratorName: String,
    val email: String,
    val password: String,
)

data class WorkspacePaymentInput(val enabled: Boolean, val country: String = "FR")

data class WorkspacePaymentResponse(
    val configured: Boolean,
    val enabled: Boolean,
    val accountId: String?,
    val ready: Boolean,
    val country: String,
    val currency: String,
)

data class RedirectResponse(val url: String)
