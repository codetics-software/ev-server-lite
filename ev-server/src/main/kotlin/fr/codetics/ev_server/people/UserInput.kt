package fr.codetics.ev_server.people

data class UserInput(val email: String, val name: String, val role: String, val enabled: Boolean = true)
