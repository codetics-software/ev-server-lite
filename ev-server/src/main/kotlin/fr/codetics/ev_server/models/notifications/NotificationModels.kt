package fr.codetics.ev_server.models.notifications

import fr.codetics.ev_server.core.msg
import fr.codetics.ev_server.models.entities.NotificationEntity
import java.time.Instant
import java.util.UUID
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

enum class NotificationAudience {
    DRIVER,
    ADMIN,
}

enum class NotificationType(val audience: NotificationAudience) {
    SESSION_STARTED(NotificationAudience.DRIVER),
    END_OF_CHARGE(NotificationAudience.DRIVER),
    END_OF_SESSION(NotificationAudience.DRIVER),
    ACCOUNT_STATUS_CHANGED(NotificationAudience.DRIVER),
    ACCOUNT_INACTIVITY(NotificationAudience.DRIVER),
    PREPARING_SESSION_NOT_STARTED(NotificationAudience.DRIVER),
    SESSION_NOT_STARTED_AFTER_AUTHORIZE(NotificationAudience.DRIVER),
    INVOICE_AVAILABLE(NotificationAudience.DRIVER),
    BILLING_FAILED(NotificationAudience.DRIVER),
    CARD_READY(NotificationAudience.DRIVER),
    CARD_ORDER_PAID(NotificationAudience.ADMIN),
    STATION_STATUS_ERROR(NotificationAudience.ADMIN),
    STATION_OFFLINE(NotificationAudience.ADMIN),
    STATION_REGISTERED(NotificationAudience.ADMIN),
    UNKNOWN_BADGE(NotificationAudience.ADMIN),
    STATION_SECURITY_EVENT(NotificationAudience.ADMIN),
    STATION_CERTIFICATE_EXPIRING(NotificationAudience.ADMIN),
    NEW_REGISTERED_USER(NotificationAudience.ADMIN),
    END_USER_ERROR_REPORT(NotificationAudience.ADMIN);

    val label: String
        get() = msg("notification.type.${name.lowercase()}")
}

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class NotificationPreferences(
    val email: Boolean = true,
    val push: Boolean = true,
    val types: Map<String, Boolean> = emptyMap(),
) {
    fun accepts(type: NotificationType) = types[type.name] != false

    fun normalized() =
        copy(types = NotificationType.entries.associate { it.name to (types[it.name] ?: true) })
}

data class NotificationPreferencesInput(
    val email: Boolean = true,
    val push: Boolean = true,
    val types: Map<String, Boolean> = emptyMap(),
) {
    fun toPreferences() =
        NotificationPreferences(email, push, types.filterKeys { key -> NotificationType.entries.any { it.name == key } })
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
