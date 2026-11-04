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
    val appSessionDays: Long = 30,
    val mailEnabled: Boolean = false,
    val mailFrom: String = "noreply@localhost",
    val encryptionKey: String = "",
    val platformAdminEmails: String = "",
    val trustedProxies: String = "",
    val retention: Retention = Retention(),
    val supervision: Supervision = Supervision(),
    val evDatabase: EvDatabase = EvDatabase(),
    val ocpi: Ocpi = Ocpi(),
    val ocpp: Ocpp = Ocpp(),
    val bootstrap: Bootstrap = Bootstrap(),
    val push: Push = Push(),
    val wallet: Wallet = Wallet(),
    val googleWallet: GoogleWallet = GoogleWallet(),
) {
    data class GoogleWallet(
        val issuerId: String = "",
        val serviceAccount: String = "",
        val classSuffix: String = "ev_charging_badge",
        val organizationName: String = "",
        val smartTapRedemptionIssuer: String = "",
    )

    data class Push(val apns: Apns = Apns(), val fcm: Fcm = Fcm()) {
        data class Fcm(val serviceAccount: String = "", val projectId: String = "")

        data class Apns(
            val key: String = "",
            val keyId: String = "",
            val teamId: String = "",
            val topic: String = "",
        )
    }

    data class Wallet(
        val passTypeId: String = "",
        val teamId: String = "",
        val organizationName: String = "",
        val certificate: String = "",
        val certificatePassword: String = "",
        val wwdrCertificate: String = "",
        val nfcPublicKey: String = "",
    )

    data class Ocpi(
        val publicUrl: String = "http://localhost:8080",
        val countryCode: String = "FR",
        val partyId: String = "COD",
    )

    data class Ocpp(
        val trustedProxies: String = "",
        val clientCertHeader: String = "",
        val clientCertProxies: String = "",
        val clientCa: String = "",
        val signingCaCertificate: String = "",
        val signingCaKey: String = "",
        val signedCertificateDays: Long = 365,
        val allowUnauthenticated: Boolean = false,
        val heartbeatSeconds: Int = 60,
        val publicUrl: String = "",
    ) {
        fun heartbeatInterval() = heartbeatSeconds.coerceIn(10, 300)
    }

    data class Retention(
        val auditDays: Long = 365,
        val ocppCallDays: Long = 30,
        val stationEventDays: Long = 180,
        val notificationDays: Long = 365,
        val meterValueDays: Long = 730,
        val assetConsumptionDays: Long = 365,
    )

    data class Supervision(
        val offlineMinutes: Long = 10,
        val staleSessionHours: Long = 24,
        val preparingMinutes: Long = 15,
        val authorizeMinutes: Long = 5,
        val inactivityMonths: Long = 6,
        val longInactivityMinutes: Long = 120,
        val maximumSessionKwh: Long = 1000,
    )

    data class EvDatabase(
        val url: String = "https://api.ev-database.org/v1/codetics",
        val apiKey: String = "",
    )

    fun platformAdmins(): Set<String> =
        platformAdminEmails.split(',').map { it.trim().lowercase() }.filter(String::isNotBlank).toSet()

    data class Bootstrap(
        val email: String = "",
        val password: String = "",
        val tenant: String = "codetics",
    )
}
