package fr.codetics.ev_server.services.notifications

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.I18n
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.core.msg
import fr.codetics.ev_server.core.pageRequest
import fr.codetics.ev_server.models.api.MutationResponse
import fr.codetics.ev_server.models.api.PageResponse
import fr.codetics.ev_server.models.entities.MailEntity
import fr.codetics.ev_server.models.entities.NotificationEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.entities.TransactionEntity
import fr.codetics.ev_server.models.entities.UserEntity
import fr.codetics.ev_server.models.notifications.ErrorReportInput
import fr.codetics.ev_server.models.notifications.NotificationAudience
import fr.codetics.ev_server.models.notifications.NotificationPreferences
import fr.codetics.ev_server.models.notifications.NotificationResponse
import fr.codetics.ev_server.models.notifications.NotificationType
import fr.codetics.ev_server.models.people.UserRole
import fr.codetics.ev_server.models.sessions.ChargingSessionResponse
import fr.codetics.ev_server.ocpp.AuthorizationStatus
import fr.codetics.ev_server.ocpp.BadgeRejected
import fr.codetics.ev_server.ocpp.ConnectorStatusChanged
import fr.codetics.ev_server.ocpp.StationNotification
import fr.codetics.ev_server.ocpp.TransactionStarted
import fr.codetics.ev_server.ocpp.TransactionStopped
import fr.codetics.ev_server.ocpp.TransactionUpdated
import fr.codetics.ev_server.repositories.MailRepository
import fr.codetics.ev_server.repositories.NotificationRepository
import fr.codetics.ev_server.repositories.SiteUserRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.repositories.TransactionRepository
import fr.codetics.ev_server.repositories.UserRepository
import fr.codetics.ev_server.services.people.UserRegistered
import fr.codetics.ev_server.services.people.UserStatusChanged
import fr.codetics.ev_server.services.stations.StationRegistered
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

@Service
class NotificationService(
    private val db: Database,
    private val notifications: NotificationRepository,
    private val mail: MailRepository,
    private val users: UserRepository,
    private val siteUsers: SiteUserRepository,
    private val stations: StationRepository,
    private val tenants: TenantRepository,
    private val transactions: TransactionRepository,
    private val properties: AppProperties,
    private val push: PushService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun safely(name: String, block: () -> Unit) {
        try {
            db.isolated(block)
        } catch (error: RuntimeException) {
            log.warn("Notification {} non créée", name, error)
        }
    }

    private fun date(instant: Instant): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT)
            .withLocale(I18n.locale())
            .withZone(ZoneId.of("Europe/Paris"))
            .format(instant)

    fun notify(
        user: UserEntity,
        type: NotificationType,
        subject: () -> String,
        body: () -> String,
        dedupeKey: String? = null,
        window: Duration = Duration.ofHours(24),
        transactionId: Int? = null,
    ): Boolean = I18n.with(user.locale) { deliver(user, type, subject(), body(), dedupeKey, window, transactionId) }

    private fun deliver(
        user: UserEntity,
        type: NotificationType,
        subject: String,
        body: String,
        dedupeKey: String?,
        window: Duration,
        transactionId: Int?,
    ): Boolean {
        if (!user.enabled || user.deletedAt != null || user.email.isBlank()) return false
        val preferences = preferences(user)
        if (!preferences.accepts(type)) return false
        val pushed =
            preferences.push && type.audience == NotificationAudience.DRIVER && push.deliverable(user.tenantId, user.id)
        if (!preferences.email && !pushed) return false
        val key = dedupeKey?.let { "$it:${user.id}" }
        if (key != null && notifications.existsByTenantIdAndDedupeKeyAndCreatedAtAfter(user.tenantId, key, Instant.now().minus(window))) {
            return false
        }
        val message =
            if (preferences.email && properties.mailEnabled) {
                mail.save(
                    MailEntity(
                        recipient = user.email,
                        subject = "${workspace(user.tenantId)} — $subject",
                        body = "$body\n\n${properties.publicUrl.trimEnd('/')}\n\n${msg("notification.mail.footer")}",
                    )
                )
            } else {
                null
            }
        notifications.save(
            NotificationEntity(
                tenantId = user.tenantId,
                type = type.name,
                userId = user.id,
                recipient = user.email,
                subject = subject,
                channel = if (preferences.email) "EMAIL" else "PUSH",
                mailId = message?.id,
                dedupeKey = key,
            )
        )
        if (pushed) push.enqueue(user.tenantId, user.id, type.name, subject, body, transactionId)
        return true
    }

    fun notifyAdmins(
        tenant: UUID,
        type: NotificationType,
        subject: () -> String,
        body: () -> String,
        dedupeKey: String? = null,
        siteId: UUID? = null,
        window: Duration = Duration.ofHours(24),
    ) {
        val admins = users.findAllByTenantIdAndRoleAndEnabledTrue(tenant, UserRole.ADMIN)
        val siteAdmins =
            siteId?.let { site ->
                val ids = siteUsers.findAllByKeyTenantIdAndKeySiteId(tenant, site).filter { it.siteAdmin }.map { it.key.userId }
                users.findAllByTenantIdAndIdIn(tenant, ids)
            }.orEmpty()
        (admins + siteAdmins).distinctBy { it.id }.forEach { notify(it, type, subject, body, dedupeKey, window) }
    }

    private fun preferences(user: UserEntity): NotificationPreferences {
        val stored =
            runCatching { db.json.readValue(user.notificationPreferences, NotificationPreferences::class.java) }
                .getOrDefault(NotificationPreferences())
        return stored
    }

    private fun workspace(tenant: UUID) = tenants.findById(tenant).map { it.name }.orElse("Supervision")

    fun list(user: AppPrincipal, type: String?, userId: UUID?, offset: Int, limit: Int): PageResponse<NotificationResponse> {
        val request = pageRequest(offset, limit, Sort.by(Sort.Direction.DESC, "createdAt"))
        val target = if (user.role == UserRole.ADMIN) userId else user.id
        val page =
            when {
                target != null && !type.isNullOrBlank() -> notifications.findAllByTenantIdAndUserIdAndType(user.tenantId, target, type, request)
                target != null -> notifications.findAllByTenantIdAndUserId(user.tenantId, target, request)
                !type.isNullOrBlank() -> notifications.findAllByTenantIdAndType(user.tenantId, type, request)
                else -> notifications.findAllByTenantId(user.tenantId, request)
            }
        val sent = mail.findAllById(page.content.mapNotNull { it.mailId }).associate { it.id to it.sentAt }
        return PageResponse(page.content.map { NotificationResponse.from(it, it.mailId?.let(sent::get)) }, page.totalElements)
    }

    fun types() =
        NotificationType.entries.map {
            mapOf("type" to it.name, "label" to it.label, "audience" to it.audience.name)
        }

    fun report(user: AppPrincipal, input: ErrorReportInput): MutationResponse {
        ensure(input.subject.isNotBlank() && input.subject.length <= 200) { msg("notifications.report.subject_length") }
        ensure(input.description.isNotBlank() && input.description.length <= 4000) {
            msg("notifications.report.description_length")
        }
        ensure(input.phone == null || input.phone.length <= 40) { msg("validation.phone") }
        ensure(input.stationId == null || input.stationId.length <= 100) { msg("notifications.report.invalid_station") }
        val station = input.stationId?.let { stations.findById(StationKey(user.tenantId, it)).orElse(null) }
        val body = {
            buildString {
                appendLine(msg("notification.report.body", user.name, user.email))
                input.phone?.takeIf(String::isNotBlank)?.let { appendLine(msg("notification.report.phone", it)) }
                station?.let {
                    appendLine(
                        input.connectorId?.let { c -> msg("notification.report.station_connector", it.name, it.key.stationId, c) }
                            ?: msg("notification.report.station", it.name, it.key.stationId)
                    )
                }
                appendLine()
                append(input.description.trim())
            }
        }
        db.atomic {
            notifyAdmins(user.tenantId, NotificationType.END_USER_ERROR_REPORT, { msg("notification.report.subject", input.subject.trim()) }, body)
            db.audit(user.tenantId, user.id.toString(), "ERROR_REPORT", input.stationId ?: "-")
        }
        return MutationResponse()
    }

    private fun owner(transaction: TransactionEntity) =
        transaction.userId?.let { users.findByTenantIdAndId(transaction.tenantId, it) }

    private fun summary(transaction: TransactionEntity): String {
        val energy = ChargingSessionResponse.energyKwh(transaction) ?: "0"
        val minutes = Duration.between(transaction.startedAt, transaction.stoppedAt ?: Instant.now()).toMinutes()
        val summary = msg("notification.session.summary", energy, minutes / 60, "%02d".format(minutes % 60))
        return transaction.price?.let { "$summary · ${it.toPlainString()} ${transaction.currency ?: "EUR"}" } ?: summary
    }

    @TransactionalEventListener(fallbackExecution = true)
    fun started(event: TransactionStarted) {
        safely("started") {
            val transaction = event.transaction
            if (transaction.authorizationStatus != AuthorizationStatus.Accepted) return@safely
            val user = owner(transaction) ?: return@safely
            notify(
                user,
                NotificationType.SESSION_STARTED,
                { msg("notification.session_started.subject") },
                { msg("notification.session_started.body", date(transaction.startedAt), event.station.name, transaction.connectorId) },
                transactionId = transaction.id,
            )
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    fun updated(event: TransactionUpdated) {
        safely("updated") {
            val transaction = event.transaction
            if (event.transaction.chargingEndedAt == null || event.transaction.endOfChargeNotified) return@safely
            val stored = transactions.findById(requireNotNull(event.transaction.id)).orElse(null) ?: return@safely
            if (stored.chargingEndedAt == null || stored.endOfChargeNotified) return@safely
            stored.endOfChargeNotified = true
            transactions.save(stored)
            val user = owner(transaction) ?: return@safely
            notify(
                user,
                NotificationType.END_OF_CHARGE,
                { msg("notification.end_of_charge.subject") },
                { msg("notification.end_of_charge.body", event.station.name) + "\n" + summary(transaction) },
                transactionId = transaction.id,
            )
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    fun stopped(event: TransactionStopped) {
        safely("stopped") {
            val transaction = event.transaction
            val user = owner(transaction) ?: return@safely
            notify(
                user,
                NotificationType.END_OF_SESSION,
                { msg("notification.end_of_session.subject") },
                { msg("notification.end_of_session.body", event.station.name, date(requireNotNull(transaction.stoppedAt))) + "\n" + summary(transaction) },
                dedupeKey = "end-of-session:${transaction.id}",
                window = Duration.ofDays(365),
                transactionId = transaction.id,
            )
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    fun status(event: ConnectorStatusChanged) {
        safely("status") {
            val connector = event.connector
            if (connector.status != "Faulted" && connector.errorCode == "NoError") return@safely
            notifyAdmins(
                event.station.key.tenantId,
                NotificationType.STATION_STATUS_ERROR,
                { msg("notification.station_error.subject", event.station.name) },
                { msg("notification.station_error.body", connector.key.connectorId, connector.status, connector.errorCode) },
                dedupeKey = "status:${event.station.key.stationId}:${connector.key.connectorId}:${connector.errorCode}",
                window = Duration.ofHours(1),
            )
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    fun security(event: StationNotification) {
        safely("security") {
            if (event.type != "SECURITY" || event.status !in CRITICAL_SECURITY_EVENTS) return@safely
            notifyAdmins(
                event.station.key.tenantId,
                NotificationType.STATION_SECURITY_EVENT,
                { msg("notification.security_event.subject", event.station.name) },
                { msg("notification.security_event.body", event.station.name, event.station.key.stationId, event.status) },
                dedupeKey = "security:${event.station.key.stationId}:${event.status}",
                window = Duration.ofHours(1),
            )
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    fun rejected(event: BadgeRejected) {
        safely("rejected") {
            if (event.status != AuthorizationStatus.Invalid) return@safely
            notifyAdmins(
                event.station.key.tenantId,
                NotificationType.UNKNOWN_BADGE,
                { msg("notification.unknown_badge.subject", event.station.name) },
                { msg("notification.unknown_badge.body", event.uid, event.station.name, event.station.key.stationId) },
                dedupeKey = "badge:${event.uid}",
                window = Duration.ofHours(1),
            )
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    fun registered(event: StationRegistered) {
        safely("registered") {
            notifyAdmins(
                event.station.key.tenantId,
                NotificationType.STATION_REGISTERED,
                { msg("notification.station_registered.subject", event.station.key.stationId) },
                { msg("notification.station_registered.body", event.station.key.stationId) },
            )
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    fun userRegistered(event: UserRegistered) {
        safely("userRegistered") {
            notifyAdmins(
                event.user.tenantId,
                NotificationType.NEW_REGISTERED_USER,
                { msg("notification.new_user.subject", event.user.displayName) },
                { msg("notification.new_user.body", event.user.displayName, event.user.email) },
            )
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    fun statusChanged(event: UserStatusChanged) {
        safely("statusChanged") {
            if (!event.enabled) return@safely
            notify(
                event.user,
                NotificationType.ACCOUNT_STATUS_CHANGED,
                { msg("notification.account_enabled.subject") },
                { msg("notification.account_enabled.body") },
            )
        }
    }

    companion object {
        val CRITICAL_SECURITY_EVENTS =
            setOf(
                "TamperDetectionActivated",
                "SecurityLogWasCleared",
                "MemoryExhaustion",
                "InvalidFirmwareSignature",
                "InvalidFirmwareSigningCertificate",
                "InvalidCentralSystemCertificate",
                "InvalidChargePointCertificate",
                "FirmwareUpdated",
            )
    }
}
