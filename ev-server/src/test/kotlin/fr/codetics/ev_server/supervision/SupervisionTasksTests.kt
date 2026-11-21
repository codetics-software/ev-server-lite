package fr.codetics.ev_server.supervision

import fr.codetics.ev_server.models.entities.AuditEntity
import fr.codetics.ev_server.models.entities.ChargingTokenEntity
import fr.codetics.ev_server.models.entities.ChargingTokenKey
import fr.codetics.ev_server.models.entities.ConnectorEntity
import fr.codetics.ev_server.models.entities.ConnectorKey
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationEventEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.entities.TenantEntity
import fr.codetics.ev_server.models.entities.TransactionEntity
import fr.codetics.ev_server.models.entities.UserEntity
import fr.codetics.ev_server.models.people.UserRole
import fr.codetics.ev_server.repositories.AuditRepository
import fr.codetics.ev_server.repositories.ChargingTokenRepository
import fr.codetics.ev_server.repositories.ConnectorRepository
import fr.codetics.ev_server.repositories.NotificationRepository
import fr.codetics.ev_server.repositories.StationEventRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.repositories.TransactionRepository
import fr.codetics.ev_server.repositories.UserRepository
import fr.codetics.ev_server.services.supervision.SupervisionTasks
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("local")
@SpringBootTest(
    properties =
        [
            "app.bootstrap.email=",
            "app.mail-enabled=false",
            "GOOGLE_CLIENT_ID=local-test-google",
            "GOOGLE_CLIENT_SECRET=local-test-google-secret",
            "MICROSOFT_CLIENT_ID=local-test-microsoft",
            "MICROSOFT_CLIENT_SECRET=local-test-microsoft-secret",
            "MICROSOFT_TENANT_ID=00000000-0000-0000-0000-000000000001",
            "APPLE_CLIENT_ID=local-test-apple",
            "APPLE_CLIENT_SECRET=local-test-apple-secret",
        ]
)
class SupervisionTasksTests
@Autowired
constructor(
    private val tasks: SupervisionTasks,
    private val tenants: TenantRepository,
    private val users: UserRepository,
    private val stations: StationRepository,
    private val connectors: ConnectorRepository,
    private val transactions: TransactionRepository,
    private val stationEvents: StationEventRepository,
    private val tokens: ChargingTokenRepository,
    private val notifications: NotificationRepository,
    private val audits: AuditRepository,
) {
    private lateinit var tenant: TenantEntity
    private lateinit var admin: UserEntity
    private lateinit var driver: UserEntity
    private val now get() = Instant.now()

    @BeforeEach
    fun prepare() {
        tenant = tenants.save(TenantEntity(slug = "supervision-${UUID.randomUUID().toString().take(8)}", name = "Supervision"))
        admin = users.save(UserEntity(tenantId = tenant.id, email = "admin-${UUID.randomUUID()}@example.test", displayName = "Admin", role = UserRole.ADMIN, emailVerified = true))
        driver = users.save(UserEntity(tenantId = tenant.id, email = "driver-${UUID.randomUUID()}@example.test", displayName = "Driver", role = UserRole.DRIVER, emailVerified = true))
        tokens.save(ChargingTokenEntity(ChargingTokenKey(tenant.id, "TAG-OWNED"), userId = driver.id))
    }

    private fun notified(user: UserEntity, type: String) =
        notifications.findAll().count { it.tenantId == tenant.id && it.userId == user.id && it.type == type }

    private fun station(id: String, lastSeen: Instant? = now) =
        stations.save(StationEntity(StationKey(tenant.id, id), "Borne $id", "unused", connected = true).apply { this.lastSeen = lastSeen })

    private fun connector(station: String, status: String, since: Instant = now) =
        connectors.save(ConnectorEntity(key = ConnectorKey(tenant.id, station, 1)).apply { this.status = status; statusAt = since })

    @Test
    fun `une borne silencieuse est signalee une seule fois aux administrateurs`() {
        station("CS-OFF", now.minus(Duration.ofHours(1)))
        station("CS-ON", now)
        tasks.offlineStations()
        tasks.offlineStations()
        assertEquals(1, notified(admin, "STATION_OFFLINE"))
        assertEquals(0, notified(driver, "STATION_OFFLINE"))
    }

    @Test
    fun `les sessions abandonnees sont fermees sauf si la borne recharge encore`() {
        station("CS-STALE")
        connector("CS-STALE", "Available")
        val started = now.minus(Duration.ofHours(30)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
        val stale =
            transactions.save(
                TransactionEntity(tenantId = tenant.id, stationId = "CS-STALE", connectorId = 1, tokenUid = "TAG-OWNED", startedAt = started, meterStartWh = BigDecimal(100), lastEnergyWh = BigDecimal(900))
            )
        station("CS-BUSY")
        connector("CS-BUSY", "Charging")
        val busy = transactions.save(TransactionEntity(tenantId = tenant.id, stationId = "CS-BUSY", connectorId = 1, tokenUid = "TAG-OWNED", startedAt = started))

        tasks.closeStaleSessions()

        val closed = transactions.findById(requireNotNull(stale.id)).orElseThrow()
        assertEquals("StaleSession", closed.stopReason)
        assertEquals(started, closed.stoppedAt)
        assertEquals(0, BigDecimal(900).compareTo(closed.meterStopWh))
        assertTrue(audits.findAll().any { it.tenantId == tenant.id && it.action == "SESSION_AUTO_CLOSE" && it.target == stale.id.toString() })
        assertNull(transactions.findById(requireNotNull(busy.id)).orElseThrow().stoppedAt)
    }

    @Test
    fun `un conducteur est prevenu quand sa recharge autorisee ne demarre pas`() {
        station("CS-AUTH")
        stationEvents.save(StationEventEntity(tenantId = tenant.id, stationId = "CS-AUTH", type = "AUTHORIZE", status = "Accepted", info = "TAG-OWNED", createdAt = now.minus(Duration.ofMinutes(8))))
        stationEvents.save(StationEventEntity(tenantId = tenant.id, stationId = "CS-AUTH", type = "AUTHORIZE", status = "Accepted", info = "TAG-UNKNOWN", createdAt = now.minus(Duration.ofMinutes(8))))
        tasks.sessionsNotStartedAfterAuthorize()
        tasks.sessionsNotStartedAfterAuthorize()
        assertEquals(1, notified(driver, "SESSION_NOT_STARTED_AFTER_AUTHORIZE"))
    }

    @Test
    fun `une recharge demarree apres autorisation ne declenche aucun rappel`() {
        station("CS-STARTED")
        val authorized = now.minus(Duration.ofMinutes(8))
        stationEvents.save(StationEventEntity(tenantId = tenant.id, stationId = "CS-STARTED", type = "AUTHORIZE", status = "Accepted", info = "TAG-OWNED", createdAt = authorized))
        transactions.save(TransactionEntity(tenantId = tenant.id, stationId = "CS-STARTED", connectorId = 1, tokenUid = "TAG-OWNED", startedAt = authorized.plusSeconds(30)))
        tasks.sessionsNotStartedAfterAuthorize()
        assertEquals(0, notified(driver, "SESSION_NOT_STARTED_AFTER_AUTHORIZE"))
    }

    @Test
    fun `un connecteur reste en preparation trop longtemps previent le conducteur`() {
        station("CS-PREP")
        val since = now.minus(Duration.ofMinutes(30))
        connector("CS-PREP", "Preparing", since)
        stationEvents.save(StationEventEntity(tenantId = tenant.id, stationId = "CS-PREP", type = "AUTHORIZE", status = "Accepted", info = "TAG-OWNED", createdAt = since.minusSeconds(60)))
        station("CS-PREP-TX")
        connector("CS-PREP-TX", "Preparing", since)
        transactions.save(TransactionEntity(tenantId = tenant.id, stationId = "CS-PREP-TX", connectorId = 1, tokenUid = "TAG-OWNED", startedAt = since))

        tasks.preparingNotStarted()
        tasks.preparingNotStarted()

        assertEquals(1, notified(driver, "PREPARING_SESSION_NOT_STARTED"))
    }

    @Test
    fun `un compte inactif est prevenu une seule fois`() {
        val old = now.minus(Duration.ofDays(400))
        val dormant = users.save(UserEntity(tenantId = tenant.id, email = "dormant-${UUID.randomUUID()}@example.test", displayName = "Dormant", role = UserRole.DRIVER, emailVerified = true, createdAt = old))
        val active = users.save(UserEntity(tenantId = tenant.id, email = "active-${UUID.randomUUID()}@example.test", displayName = "Active", role = UserRole.DRIVER, emailVerified = true, createdAt = old, lastLoginAt = now))

        tasks.userInactivity()
        tasks.userInactivity()

        assertNotNull(users.findById(dormant.id).orElseThrow().inactivityNotifiedAt)
        assertEquals(1, notified(dormant, "ACCOUNT_INACTIVITY"))
        assertNull(users.findById(active.id).orElseThrow().inactivityNotifiedAt)
    }

    @Test
    fun `la retention supprime uniquement les historiques expires`() {
        val oldEvent = stationEvents.save(StationEventEntity(tenantId = tenant.id, stationId = "CS-RET", type = "STATUS", createdAt = now.minus(Duration.ofDays(200))))
        val recentEvent = stationEvents.save(StationEventEntity(tenantId = tenant.id, stationId = "CS-RET", type = "STATUS"))
        val oldAudit = audits.save(AuditEntity(tenantId = tenant.id, actor = "test", action = "OLD", target = "x", createdAt = now.minus(Duration.ofDays(400))))
        val recentAudit = audits.save(AuditEntity(tenantId = tenant.id, actor = "test", action = "RECENT", target = "x"))

        tasks.retention()

        assertFalse(stationEvents.existsById(requireNotNull(oldEvent.id)))
        assertTrue(stationEvents.existsById(requireNotNull(recentEvent.id)))
        assertFalse(audits.existsById(requireNotNull(oldAudit.id)))
        assertTrue(audits.existsById(requireNotNull(recentAudit.id)))
    }

    @Test
    fun `les taches planifiees ne laissent pas echapper d erreur`() {
        tasks.everyMinute()
        tasks.everyTenMinutes()
        tasks.everyHour()
        tasks.nightly()
    }
}
