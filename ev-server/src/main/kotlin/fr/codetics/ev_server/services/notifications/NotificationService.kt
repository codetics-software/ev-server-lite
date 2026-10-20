package fr.codetics.ev_server.services.notifications

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.ensure
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
import java.util.Locale
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
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun safely(name: String, block: () -> Unit) {
        try {
            db.isolated(block)
        } catch (error: RuntimeException) {
            log.warn("Notification {} non créée", name, error)
        }
    }

    private val dates =
        DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH:mm", Locale.FRENCH).withZone(ZoneId.of("Europe/Paris"))

    fun notify(
        user: UserEntity,
        type: NotificationType,
        subject: String,
        body: String,
        dedupeKey: String? = null,
        window: Duration = Duration.ofHours(24),
    ): Boolean {
        if (!user.enabled || user.deletedAt != null || user.email.isBlank()) return false
        if (!preferences(user).accepts(type)) return false
        val key = dedupeKey?.let { "$it:${user.id}" }
        if (key != null && notifications.existsByTenantIdAndDedupeKeyAndCreatedAtAfter(user.tenantId, key, Instant.now().minus(window))) {
            return false
        }
        val message =
            if (properties.mailEnabled) {
                mail.save(
                    MailEntity(
                        recipient = user.email,
                        subject = "${workspace(user.tenantId)} — $subject",
                        body = "$body\n\n${properties.publicUrl.trimEnd('/')}\n\nVous pouvez gérer vos notifications depuis votre compte.",
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
                mailId = message?.id,
                dedupeKey = key,
            )
        )
        return true
    }

    fun notifyAdmins(
        tenant: UUID,
        type: NotificationType,
        subject: String,
        body: String,
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
        ensure(input.subject.isNotBlank() && input.subject.length <= 200) { "Le sujet doit contenir entre 1 et 200 caractères." }
        ensure(input.description.isNotBlank() && input.description.length <= 4000) {
            "La description doit contenir entre 1 et 4 000 caractères."
        }
        ensure(input.phone == null || input.phone.length <= 40) { "Numéro de téléphone invalide." }
        ensure(input.stationId == null || input.stationId.length <= 100) { "Borne invalide." }
        val station = input.stationId?.let { stations.findById(StationKey(user.tenantId, it)).orElse(null) }
        val body =
            buildString {
                appendLine("${user.name} (${user.email}) signale un problème.")
                input.phone?.takeIf(String::isNotBlank)?.let { appendLine("Téléphone : $it") }
                station?.let { appendLine("Borne : ${it.name} (${it.key.stationId})${input.connectorId?.let { c -> ", connecteur $c" } ?: ""}") }
                appendLine()
                append(input.description.trim())
            }
        db.atomic {
            notifyAdmins(user.tenantId, NotificationType.END_USER_ERROR_REPORT, "Signalement : ${input.subject.trim()}", body)
            db.audit(user.tenantId, user.id.toString(), "ERROR_REPORT", input.stationId ?: "-")
        }
        return MutationResponse()
    }

    private fun owner(transaction: TransactionEntity) =
        transaction.userId?.let { users.findByTenantIdAndId(transaction.tenantId, it) }

    private fun summary(transaction: TransactionEntity): String {
        val energy = ChargingSessionResponse.energyKwh(transaction) ?: "0"
        val minutes = Duration.between(transaction.startedAt, transaction.stoppedAt ?: Instant.now()).toMinutes()
        val price = transaction.price?.let { " · ${it.toPlainString()} ${transaction.currency ?: "EUR"}" }.orEmpty()
        return "Énergie : $energy kWh · Durée : ${minutes / 60} h ${minutes % 60} min$price"
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
                "Votre session de recharge a commencé",
                "Votre session a démarré le ${dates.format(transaction.startedAt)} sur la borne ${event.station.name}, connecteur ${transaction.connectorId}.",
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
                "La charge de votre véhicule est terminée",
                "Votre véhicule ne consomme plus d’énergie sur la borne ${event.station.name}. Libérez la place pour les autres conducteurs.\n${summary(transaction)}",
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
                "Votre session de recharge est terminée",
                "Votre session sur la borne ${event.station.name} s’est terminée le ${dates.format(requireNotNull(transaction.stoppedAt))}.\n${summary(transaction)}",
                dedupeKey = "end-of-session:${transaction.id}",
                window = Duration.ofDays(365),
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
                "La borne ${event.station.name} signale une erreur",
                "Connecteur ${connector.key.connectorId} : état ${connector.status}, code ${connector.errorCode}.",
                dedupeKey = "status:${event.station.key.stationId}:${connector.key.connectorId}:${connector.errorCode}",
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
                "Badge inconnu présenté sur ${event.station.name}",
                "Le badge ${event.uid} a été présenté sur la borne ${event.station.name} (${event.station.key.stationId}) mais n’est pas enregistré dans l’espace.",
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
                "Nouvelle borne enregistrée : ${event.station.key.stationId}",
                "La borne ${event.station.key.stationId} s’est enregistrée avec un jeton d’enregistrement. Vérifiez sa configuration dans le tableau de bord.",
            )
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    fun userRegistered(event: UserRegistered) {
        safely("userRegistered") {
            notifyAdmins(
                event.user.tenantId,
                NotificationType.NEW_REGISTERED_USER,
                "Nouvel utilisateur : ${event.user.displayName}",
                "${event.user.displayName} (${event.user.email}) a créé un compte.",
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
                "Votre compte est activé",
                "Votre compte a été activé par un administrateur. Vous pouvez désormais vous connecter et recharger.",
            )
        }
    }
}
