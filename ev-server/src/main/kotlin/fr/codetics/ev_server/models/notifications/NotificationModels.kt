package fr.codetics.ev_server.models.notifications

import fr.codetics.ev_server.models.entities.NotificationEntity
import java.time.Instant
import java.util.UUID
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

enum class NotificationAudience {
    DRIVER,
    ADMIN,
}

enum class NotificationType(val audience: NotificationAudience, val label: String) {
    SESSION_STARTED(NotificationAudience.DRIVER, "Début de session"),
    END_OF_CHARGE(NotificationAudience.DRIVER, "Fin de charge"),
    END_OF_SESSION(NotificationAudience.DRIVER, "Fin de session"),
    ACCOUNT_STATUS_CHANGED(NotificationAudience.DRIVER, "Changement d’état du compte"),
    ACCOUNT_INACTIVITY(NotificationAudience.DRIVER, "Compte inactif"),
    PREPARING_SESSION_NOT_STARTED(NotificationAudience.DRIVER, "Véhicule branché sans session"),
    SESSION_NOT_STARTED_AFTER_AUTHORIZE(NotificationAudience.DRIVER, "Badge présenté sans session"),
    INVOICE_AVAILABLE(NotificationAudience.DRIVER, "Facture disponible"),
    BILLING_FAILED(NotificationAudience.DRIVER, "Échec de paiement"),
    STATION_STATUS_ERROR(NotificationAudience.ADMIN, "Borne en erreur"),
    STATION_OFFLINE(NotificationAudience.ADMIN, "Borne hors ligne"),
    STATION_REGISTERED(NotificationAudience.ADMIN, "Nouvelle borne enregistrée"),
    UNKNOWN_BADGE(NotificationAudience.ADMIN, "Badge inconnu"),
    NEW_REGISTERED_USER(NotificationAudience.ADMIN, "Nouvel utilisateur"),
    END_USER_ERROR_REPORT(NotificationAudience.ADMIN, "Signalement d’un utilisateur"),
}

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class NotificationPreferences(
    val email: Boolean = true,
    val types: Map<String, Boolean> = emptyMap(),
) {
    fun accepts(type: NotificationType) = email && types[type.name] != false

    fun normalized() =
        copy(types = NotificationType.entries.associate { it.name to (types[it.name] ?: true) })
}

data class NotificationPreferencesInput(
    val email: Boolean = true,
    val types: Map<String, Boolean> = emptyMap(),
) {
    fun toPreferences() =
        NotificationPreferences(email, types.filterKeys { key -> NotificationType.entries.any { it.name == key } })
}

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class NotificationResponse(
    val id: UUID,
    val type: String,
    val userId: UUID?,
    val recipient: String,
    val subject: String,
    val channel: String,
    val createdAt: Instant,
    val sentAt: Instant?,
) {
    companion object {
        fun from(notification: NotificationEntity, sentAt: Instant?) =
            NotificationResponse(
                notification.id,
                notification.type,
                notification.userId,
                notification.recipient,
                notification.subject,
                notification.channel,
                notification.createdAt,
                sentAt,
            )
    }
}

data class ErrorReportInput(
    val subject: String,
    val description: String,
    val stationId: String? = null,
    val connectorId: Int? = null,
    val phone: String? = null,
)
