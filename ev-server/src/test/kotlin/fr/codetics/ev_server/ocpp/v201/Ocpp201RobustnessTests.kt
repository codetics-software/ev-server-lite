package fr.codetics.ev_server.ocpp.v201

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.models.entities.ChargingTokenEntity
import fr.codetics.ev_server.models.entities.ChargingTokenKey
import fr.codetics.ev_server.models.entities.ReservationKey
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.entities.StationParameterKey
import fr.codetics.ev_server.models.entities.TenantEntity
import fr.codetics.ev_server.models.entities.TransactionEntity
import fr.codetics.ev_server.models.entities.UserEntity
import fr.codetics.ev_server.models.people.UserRole
import fr.codetics.ev_server.ocpp.OcppCommandQueue
import fr.codetics.ev_server.ocpp.OcppFault
import fr.codetics.ev_server.ocpp.OcppService
import fr.codetics.ev_server.ocpp.OcppTime
import fr.codetics.ev_server.ocpp.OcppVersion
import fr.codetics.ev_server.ocpp.StationIdentity
import fr.codetics.ev_server.repositories.ChargingTokenRepository
import fr.codetics.ev_server.repositories.CommandRepository
import fr.codetics.ev_server.repositories.ReservationRepository
import fr.codetics.ev_server.repositories.StationParameterRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.repositories.TransactionRepository
import fr.codetics.ev_server.repositories.UserRepository
import fr.codetics.ev_server.security.AccountService
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.JsonNode

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
class Ocpp201RobustnessTests
@Autowired
constructor(
    private val context: WebApplicationContext,
    private val db: Database,
    private val tenants: TenantRepository,
    private val users: UserRepository,
    private val stations: StationRepository,
    private val chargingTokens: ChargingTokenRepository,
    private val transactions: TransactionRepository,
    private val parameters: StationParameterRepository,
    private val reservations: ReservationRepository,
    private val commands: CommandRepository,
    private val passwords: PasswordEncoder,
    private val accounts: AccountService,
    private val ocpp: OcppService,
    private val queue: OcppCommandQueue,
) {
    private lateinit var mvc: MockMvc
    private lateinit var tenant: TenantEntity
    private lateinit var admin: UserEntity
    private val start = Instant.parse("2026-03-01T10:00:00Z")

    @BeforeEach
    fun prepare() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply<DefaultMockMvcBuilder>(springSecurity()).build()
        tenant = tenants.save(TenantEntity(slug = "robust201-${UUID.randomUUID()}", name = "Robustesse 2.0.1"))
        admin =
            users.save(
                UserEntity(
                    tenantId = tenant.id,
                    email = "admin-${UUID.randomUUID()}@example.test",
                    displayName = "Admin",
                    passwordHash = passwords.encode("a-long-password-for-testing"),
                    role = UserRole.ADMIN,
                    emailVerified = true,
                )
            )
        stations.save(
            StationEntity(StationKey(tenant.id, "CS-201"), "Borne 2.0.1", "unused", connected = true).apply {
                ocppProtocol = "2.0.1"
                ocppVersion = "2.0.1"
                lastSeen = Instant.now()
            }
        )
        stations.save(StationEntity(StationKey(tenant.id, "CS-16"), "Borne 1.6", "unused", connected = true))
        chargingTokens.save(ChargingTokenEntity(ChargingTokenKey(tenant.id, "TAG-1")))
    }

    private fun policy(station: String, value: String) {
        stations.save(stations.findById(StationKey(tenant.id, station)).orElseThrow().apply { bootPolicy = value })
    }

    private fun boot201(): JsonNode =
        call("BootNotification", mapOf("reason" to "PowerUp", "chargingStation" to mapOf("model" to "M1", "vendorName" to "Acme")))[2]

    private fun boot16(): JsonNode =
        db.decode(
            ocpp.call(
                tenant.id,
                "CS-16",
                UUID.randomUUID().toString(),
                "BootNotification",
                db.json.valueToTree(mapOf("chargePointVendor" to "Acme", "chargePointModel" to "M1")),
            )
        )[2]

    private fun queued(station: String, action: String, payload: Map<String, Any?>) =
        queue.command(tenant.id, station, action, db.json.valueToTree(payload))

    private fun drain(station: String, version: OcppVersion): List<String> {
        val identity = StationIdentity(tenant.id, station)
        val sent = mutableListOf<String>()
        repeat(10) {
            val command = queue.claim(identity, version).singleOrNull() ?: return sent
            sent += command.action
            queue.complete(identity, db.json.valueToTree(listOf(3, command.id.toString(), mapOf("status" to "Accepted"))))
        }
        return sent
    }

    private fun bearer() = "Bearer ${accounts.issue(requireNotNull(accounts.findUser(admin.id)))}"

    private fun call(action: String, payload: Map<String, Any?>): JsonNode =
        db.decode(
            ocpp.call(tenant.id, "CS-201", UUID.randomUUID().toString(), action, db.json.valueToTree(payload), "connection", OcppVersion.V201)
        )

    private fun at(seconds: Long) = start.plusSeconds(seconds).toString()

    private fun event(
        type: String,
        seconds: Long,
        trigger: String,
        evse: Int? = 1,
        token: Map<String, String>? = null,
        reference: String = "TX-R",
    ): JsonNode =
        call(
            "TransactionEvent",
            buildMap {
                put("eventType", type)
                put("timestamp", at(seconds))
                put("triggerReason", trigger)
                put("seqNo", seconds.toInt())
                put("transactionInfo", mapOf("transactionId" to reference))
                evse?.let { put("evse", mapOf("id" to it, "connectorId" to 1)) }
                token?.let { put("idToken", it) }
            },
        )

    private fun transaction(): TransactionEntity? =
        transactions.findAll().singleOrNull { it.tenantId == tenant.id && it.stationId == "CS-201" }

    private fun native(action: String, payload: Map<String, Any?>, result: Map<String, Any?>) {
        val id = queue.command(tenant.id, "CS-201", action, db.json.valueToTree(payload), version = OcppVersion.V201)
        val identity = StationIdentity(tenant.id, "CS-201")
        assertTrue(queue.claim(identity, OcppVersion.V201).any { it.id == id })
        queue.complete(identity, db.json.valueToTree(listOf(3, id.toString(), result)))
    }

    @Test
    fun `une borne en attente ne recoit que des commandes de configuration et ne peut pas recharger`() {
        policy("CS-201", "Pending")
        val boot = boot201()
        assertEquals("Pending", boot.path("status").asText())
        assertTrue(boot.path("interval").intValue() > 0)

        val fault = assertThrows(OcppFault::class.java) { event("Started", 0, "Authorized", token = mapOf("idToken" to "TAG-1", "type" to "ISO14443")) }
        assertEquals("SecurityError", fault.code)
        assertNull(transaction())
        assertEquals(3, call("Heartbeat", emptyMap())[0].intValue())

        val start = queued("CS-201", "RemoteStartTransaction", mapOf("idTag" to "TAG-1", "connectorId" to 1))
        queued("CS-201", "GetConfiguration", emptyMap())
        val sent = drain("CS-201", OcppVersion.V201)
        assertTrue("GetConfiguration" in sent, sent.toString())
        assertFalse("RemoteStartTransaction" in sent, sent.toString())
        assertEquals("QUEUED", commands.findById(start).orElseThrow().state.name)
    }

    @Test
    fun `une borne refusee ne recoit aucune commande et ses messages sont refuses`() {
        policy("CS-201", "Rejected")
        assertEquals("Rejected", boot201().path("status").asText())
        assertEquals("SecurityError", assertThrows(OcppFault::class.java) { call("Heartbeat", emptyMap()) }.code)
        queued("CS-201", "GetConfiguration", emptyMap())
        assertTrue(queue.claim(StationIdentity(tenant.id, "CS-201"), OcppVersion.V201).isEmpty())

        policy("CS-201", "Accepted")
        assertEquals("Accepted", boot201().path("status").asText())
        assertEquals(3, call("Heartbeat", emptyMap())[0].intValue())
    }

    @Test
    fun `une borne 1_6 suit les memes regles d enregistrement`() {
        policy("CS-16", "Pending")
        assertEquals("Pending", boot16().path("status").asText())
        policy("CS-16", "Rejected")
        assertEquals("Rejected", boot16().path("status").asText())
        policy("CS-16", "Accepted")
        assertEquals("Accepted", boot16().path("status").asText())
    }

    @Test
    fun `accepter une borne en attente lui demande un nouveau BootNotification`() {
        policy("CS-201", "Pending")
        boot201()
        mvc.perform(
                put("/api/v1/stations/CS-201")
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Borne 2.0.1","bootPolicy":"Accepted"}""")
            )
            .andExpect(status().isOk)
        val trigger = commands.findAll().single { it.tenantId == tenant.id && it.stationId == "CS-201" && it.action == "TriggerMessage" }
        assertEquals("BootNotification", db.decode(trigger.request).path("requestedMessage").asText())
        assertTrue("TriggerMessage" in drain("CS-201", OcppVersion.V201))
        assertEquals("Accepted", boot201().path("status").asText())
    }

    @Test
    fun `un debut de session sans evse est accepte puis rattache a l evse annonce ensuite`() {
        val authorized = event("Started", 0, "Authorized", evse = null, token = mapOf("idToken" to "TAG-1", "type" to "ISO14443"))
        assertEquals(3, authorized[0].intValue())
        assertEquals("Accepted", authorized[2].path("idTokenInfo").path("status").asText())
        assertNull(transaction())

        val plugged = event("Updated", 5, "CablePluggedIn")
        assertEquals(3, plugged[0].intValue())
        val created = requireNotNull(transaction())
        assertEquals(1, created.connectorId)
        assertEquals("TAG-1", created.tokenUid)
    }

    @Test
    fun `une session sans autorisation ne renvoie pas de statut invalide`() {
        val response = event("Started", 0, "CablePluggedIn", token = mapOf("idToken" to "", "type" to "NoAuthorization"))
        assertEquals(3, response[0].intValue())
        assertFalse(response[2].has("idTokenInfo"))
        assertNotNull(transaction())
    }

    @Test
    fun `une fin datee avant le debut ferme la session au lieu de la rejeter`() {
        event("Started", 100, "Authorized", token = mapOf("idToken" to "TAG-1", "type" to "ISO14443"))
        val ended = event("Ended", 50, "StopAuthorized")
        assertEquals(3, ended[0].intValue())
        val closed = requireNotNull(transaction())
        assertEquals(closed.startedAt, closed.stoppedAt)
    }

    @Test
    fun `un evse different pour une session connue est journalise sans erreur`() {
        event("Started", 0, "Authorized", token = mapOf("idToken" to "TAG-1", "type" to "ISO14443"))
        val updated = event("Updated", 10, "MeterValuePeriodic", evse = 2)
        assertEquals(3, updated[0].intValue())
        assertEquals(1, requireNotNull(transaction()).connectorId)
    }

    @Test
    fun `les horodatages envoyes ont au plus trois decimales`() {
        val heartbeat = call("Heartbeat", emptyMap())[2].path("currentTime").asText()
        assertTrue(Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,3})?Z$""").matches(heartbeat), heartbeat)
        assertEquals("2026-03-01T10:00:00.123Z", OcppTime.format(Instant.parse("2026-03-01T10:00:00.123456789Z")))
    }

    @Test
    fun `une reservation 2_0_1 native est enregistree a la reponse de la borne`() {
        native(
            "ReserveNow",
            mapOf(
                "id" to 42,
                "expiryDateTime" to "2026-03-01T12:00:00Z",
                "idToken" to mapOf("idToken" to "TAG-1", "type" to "ISO14443"),
                "evseId" to 1,
            ),
            mapOf("status" to "Accepted"),
        )
        val reservation = reservations.findById(ReservationKey(tenant.id, "CS-201", 42)).orElseThrow()
        assertEquals("ACCEPTED", reservation.status)
        assertEquals("TAG-1", reservation.idTag)
        assertEquals(1, reservation.connectorId)
        assertEquals(Instant.parse("2026-03-01T12:00:00Z"), reservation.expiryDate)
    }

    @Test
    fun `les variables lues ou modifiees en 2_0_1 mettent a jour les parametres`() {
        val variable = mapOf("component" to mapOf("name" to "OCPPCommCtrlr"), "variable" to mapOf("name" to "HeartbeatInterval"))
        native(
            "GetVariables",
            mapOf("getVariableData" to listOf(variable)),
            mapOf("getVariableResult" to listOf(variable + mapOf("attributeStatus" to "Accepted", "attributeValue" to "120"))),
        )
        val key = StationParameterKey(tenant.id, "CS-201", "OCPPCommCtrlr/HeartbeatInterval")
        assertEquals("120", parameters.findById(key).orElseThrow().value)

        native(
            "SetVariables",
            mapOf("setVariableData" to listOf(variable + mapOf("attributeValue" to "300"))),
            mapOf("setVariableResult" to listOf(variable + mapOf("attributeStatus" to "Accepted"))),
        )
        assertEquals("300", parameters.findById(key).orElseThrow().value)

        native(
            "SetVariables",
            mapOf("setVariableData" to listOf(variable + mapOf("attributeValue" to "900"))),
            mapOf("setVariableResult" to listOf(variable + mapOf("attributeStatus" to "Rejected"))),
        )
        assertEquals("300", parameters.findById(key).orElseThrow().value)
    }

    @Test
    fun `le delai de reponse d une commande court a partir de son envoi`() {
        val id = queue.command(tenant.id, "CS-201", "GetVariables", db.json.valueToTree(mapOf("getVariableData" to listOf(
            mapOf("component" to mapOf("name" to "OCPPCommCtrlr"), "variable" to mapOf("name" to "HeartbeatInterval"))
        ))), version = OcppVersion.V201)
        val waiting = commands.findById(id).orElseThrow()
        waiting.deadline = Instant.now().plusSeconds(2)
        commands.save(waiting)

        queue.claim(StationIdentity(tenant.id, "CS-201"), OcppVersion.V201)

        val sent = commands.findById(id).orElseThrow()
        assertTrue(sent.deadline.isAfter(Instant.now().plusSeconds(OcppCommandQueue.RESPONSE_SECONDS - 5)))
    }

    @Test
    fun `une session ouverte au branchement sans badge n empeche pas le demarrage a distance`() {
        event("Started", 0, "CablePluggedIn")
        assertEquals("", requireNotNull(transaction()).tokenUid)

        mvc.perform(
                post("/api/v1/transactions/start")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${accounts.issue(requireNotNull(accounts.findUser(admin.id)))}")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"stationId":"CS-201","connectorId":1,"tokenUid":"TAG-1"}""")
            )
            .andExpect(status().isOk)
    }
}
