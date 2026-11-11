package fr.codetics.ev_server.services.supervision

import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.I18n
import fr.codetics.ev_server.core.lock
import fr.codetics.ev_server.core.msg
import fr.codetics.ev_server.models.entities.AssetConsumptionEntity
import fr.codetics.ev_server.models.entities.AuditEntity
import fr.codetics.ev_server.models.entities.ChargingTokenKey
import fr.codetics.ev_server.models.entities.ConnectorKey
import fr.codetics.ev_server.models.entities.MeterValueEntity
import fr.codetics.ev_server.models.entities.NotificationEntity
import fr.codetics.ev_server.models.entities.OcppCallEntity
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationEventEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.entities.TransactionEntity
import fr.codetics.ev_server.models.notifications.NotificationType
import fr.codetics.ev_server.ocpp.OcppService
import fr.codetics.ev_server.persistence.Queries
import fr.codetics.ev_server.persistence.atLeast
import fr.codetics.ev_server.persistence.before
import fr.codetics.ev_server.persistence.count
import fr.codetics.ev_server.persistence.delete
import fr.codetics.ev_server.persistence.eq
import fr.codetics.ev_server.repositories.ChargingTokenRepository
import fr.codetics.ev_server.repositories.ConnectorRepository
import fr.codetics.ev_server.repositories.StationEventRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TransactionRepository
import fr.codetics.ev_server.repositories.UserRepository
import fr.codetics.ev_server.services.billing.ChargingPaymentLifecycle
import fr.codetics.ev_server.services.notifications.NotificationService
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class SupervisionTasks(
    private val db: Database,
    private val stations: StationRepository,
    private val connectors: ConnectorRepository,
    private val transactions: TransactionRepository,
    private val stationEvents: StationEventRepository,
    private val tokens: ChargingTokenRepository,
    private val users: UserRepository,
    private val notifications: NotificationService,
    private val ocpp: OcppService,
    private val payments: ChargingPaymentLifecycle,
    private val queries: Queries,
    private val properties: AppProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val supervision get() = properties.supervision

    private fun task(name: String, block: () -> Unit) {
        try {
            block()
        } catch (error: RuntimeException) {
            log.warn("Tâche de supervision {} interrompue", name, error)
        }
    }

    @Scheduled(initialDelay = 60000, fixedDelay = 60000)
    fun everyMinute() {
        task("offline", ::offlineStations)
        task("authorize", ::sessionsNotStartedAfterAuthorize)
        task("preparing", ::preparingNotStarted)
    }

    @Scheduled(initialDelay = 120000, fixedDelay = 600000)
    fun everyTenMinutes() {
        task("stale", ::closeStaleSessions)
    }

    @Scheduled(initialDelay = 300000, fixedDelay = 3600000)
    fun everyHour() {
        task("inactivity", ::userInactivity)
    }

    @Scheduled(cron = "0 15 3 * * *")
    fun nightly() {
        task("retention", ::retention)
    }

    fun offlineStations() {
        val threshold = Instant.now(clock).minus(Duration.ofMinutes(supervision.offlineMinutes))
        stations.findAllByDeletedFalseAndEnabledTrueAndLastSeenBefore(threshold).forEach { station ->
            val lastSeen = requireNotNull(station.lastSeen)
            db.isolated {
                notifications.notifyAdmins(
                    station.key.tenantId,
                    NotificationType.STATION_OFFLINE,
                    { msg("notification.station_offline.subject", station.name) },
                    {
                        msg(
                            "notification.station_offline.body",
                            station.name,
                            station.key.stationId,
                            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT)
                                .withLocale(I18n.locale())
                                .withZone(ZoneId.of("Europe/Paris"))
                                .format(lastSeen.truncatedTo(ChronoUnit.MINUTES)),
                        )
                    },
                    dedupeKey = "offline:${station.key.stationId}:${lastSeen.toEpochMilli()}",
                    siteId = null,
                    window = Duration.ofDays(30),
                )
            }
        }
    }

    fun sessionsNotStartedAfterAuthorize() {
        val now = Instant.now(clock)
        val delay = Duration.ofMinutes(supervision.authorizeMinutes)
        stationEvents.findAllByTypeAndStatusAndCreatedAtBetween("AUTHORIZE", "Accepted", now.minus(delay).minus(Duration.ofMinutes(10)), now.minus(delay))
            .forEach { event ->
                val uid = event.info ?: return@forEach
                val started =
                    transactions.findAllByTenantIdAndStationIdAndStoppedAtIsNull(event.tenantId, event.stationId).any { it.tokenUid == uid } ||
                        queries.count<TransactionEntity>(
                            listOf(
                                eq("tenantId", event.tenantId),
                                eq("stationId", event.stationId),
                                eq("tokenUid", uid),
                                atLeast("startedAt", event.createdAt.minusSeconds(60)),
                            )
                        ) > 0
                if (started) return@forEach
                val owner =
                    tokens.findById(ChargingTokenKey(event.tenantId, uid)).orElse(null)?.userId?.let {
                        users.findByTenantIdAndId(event.tenantId, it)
                    } ?: return@forEach
                val station = stations.findById(StationKey(event.tenantId, event.stationId)).orElse(null) ?: return@forEach
                db.isolated {
                    notifications.notify(
                        owner,
                        NotificationType.SESSION_NOT_STARTED_AFTER_AUTHORIZE,
                        { msg("notification.authorize_not_started.subject") },
                        { msg("notification.authorize_not_started.body", station.name) },
                        dedupeKey = "authorize:${event.id}",
                        window = Duration.ofDays(1),
                    )
                }
            }
    }

    fun preparingNotStarted() {
        val threshold = Instant.now(clock).minus(Duration.ofMinutes(supervision.preparingMinutes))
        connectors.findAllByStatusAndStatusAtBefore("Preparing", threshold).forEach { connector ->
            val key = connector.key
            if (transactions.existsByTenantIdAndStationIdAndConnectorIdAndStoppedAtIsNull(key.tenantId, key.stationId, key.connectorId)) {
                return@forEach
            }
            val authorization =
                stationEvents.findFirstByTenantIdAndStationIdAndTypeAndCreatedAtAfterOrderByCreatedAtDesc(
                    key.tenantId,
                    key.stationId,
                    "AUTHORIZE",
                    connector.statusAt.minus(Duration.ofMinutes(10)),
                ) ?: return@forEach
            val owner =
                authorization.info?.let { tokens.findById(ChargingTokenKey(key.tenantId, it)).orElse(null) }?.userId?.let {
                    users.findByTenantIdAndId(key.tenantId, it)
                } ?: return@forEach
            val station = stations.findById(StationKey(key.tenantId, key.stationId)).orElse(null) ?: return@forEach
            db.isolated {
                notifications.notify(
                    owner,
                    NotificationType.PREPARING_SESSION_NOT_STARTED,
                    { msg("notification.preparing.subject") },
                    { msg("notification.preparing.body", key.connectorId, station.name, supervision.preparingMinutes) },
                    dedupeKey = "preparing:${key.stationId}:${key.connectorId}:${connector.statusAt.toEpochMilli()}",
                    window = Duration.ofDays(1),
                )
            }
        }
    }

    fun closeStaleSessions() {
        val threshold = Instant.now(clock).minus(Duration.ofHours(supervision.staleSessionHours))
        transactions.findAllByStoppedAtIsNull()
            .filter { (it.lastMeterAt ?: it.startedAt).isBefore(threshold) }
            .forEach { candidate ->
                val connector =
                    connectors.findById(ConnectorKey(candidate.tenantId, candidate.stationId, candidate.connectorId)).orElse(null)
                if (connector != null && connector.status !in setOf("Available", "Unavailable", "Faulted")) return@forEach
                db.atomic {
                    val station = db.lock<StationEntity>(StationKey(candidate.tenantId, candidate.stationId)) ?: return@atomic
                    val transaction = db.lock<TransactionEntity>(requireNotNull(candidate.id)) ?: return@atomic
                    if (transaction.stoppedAt != null) return@atomic
                    val stoppedAt = transaction.lastMeterAt?.takeIf { it.isAfter(transaction.startedAt) } ?: transaction.startedAt
                    ocpp.close(station, transaction, stoppedAt, transaction.lastEnergyWh ?: transaction.meterStartWh, "StaleSession", soft = true)
                    payments.stopped(transaction)
                    db.audit(transaction.tenantId, "supervision", "SESSION_AUTO_CLOSE", transaction.id.toString())
                }
            }
    }

    fun userInactivity() {
        val threshold = Instant.now(clock).minus(supervision.inactivityMonths * 30, ChronoUnit.DAYS)
        users.findAllByEnabledTrueAndDeletedAtIsNullAndInactivityNotifiedAtIsNullAndCreatedAtBefore(threshold)
            .filter { (it.lastLoginAt ?: it.createdAt).isBefore(threshold) }
            .forEach { candidate ->
                db.isolated {
                    val user = users.findById(candidate.id).orElse(null) ?: return@isolated
                    user.inactivityNotifiedAt = Instant.now(clock)
                    users.save(user)
                    notifications.notify(
                        user,
                        NotificationType.ACCOUNT_INACTIVITY,
                        { msg("notification.inactivity.subject") },
                        { msg("notification.inactivity.body", supervision.inactivityMonths) },
                    )
                }
            }
    }

    fun retention() {
        val retention = properties.retention
        val now = Instant.now(clock)
        fun ago(days: Long) = now.minus(Duration.ofDays(days))
        db.atomic { queries.delete<AuditEntity>(listOf(before("createdAt", ago(retention.auditDays)))) }
        db.atomic { queries.delete<OcppCallEntity>(listOf(before("createdAt", ago(retention.ocppCallDays)))) }
        db.atomic { queries.delete<StationEventEntity>(listOf(before("createdAt", ago(retention.stationEventDays)))) }
        db.atomic { queries.delete<NotificationEntity>(listOf(before("createdAt", ago(retention.notificationDays)))) }
        db.atomic {
            queries.delete<MeterValueEntity>(
                listOf(before("sampledAt", ago(retention.meterValueDays)), eq("transactionId", null))
            )
        }
        db.atomic {
            queries.delete<AssetConsumptionEntity>(listOf(before("sampledAt", ago(retention.assetConsumptionDays))))
        }
        log.info("Nettoyage des historiques terminé")
    }

}
