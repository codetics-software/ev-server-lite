package fr.codetics.ev_server.core

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app")
data class AppProperties(
    val publicUrl: String = "http://localhost:3000",
    val cookieSecure: Boolean = true,
    val signupEnabled: Boolean = false,
    val workspaceSignupEnabled: Boolean = false,
    val publicTenant: String = "",
    val tokenHours: Long = 8,
    val mailEnabled: Boolean = false,
    val mailFrom: String = "noreply@localhost",
    val encryptionKey: String = "",
    val ocpi: Ocpi = Ocpi(),
    val bootstrap: Bootstrap = Bootstrap(),
) {
    data class Ocpi(
        val publicUrl: String = "http://localhost:8080",
        val countryCode: String = "FR",
        val partyId: String = "COD",
    )

    data class Bootstrap(
        val email: String = "",
        val password: String = "",
        val tenant: String = "codetics_ev_server",
    )
}
