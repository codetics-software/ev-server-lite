package fr.codetics.ev_server.models.people

data class UserInput(
    val email: String,
    val name: String,
    val role: String,
    val enabled: Boolean = true,
)
