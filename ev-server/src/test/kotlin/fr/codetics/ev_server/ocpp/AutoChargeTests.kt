package fr.codetics.ev_server.ocpp

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.models.entities.ChargingTokenEntity
import fr.codetics.ev_server.models.entities.ChargingTokenKey
import fr.codetics.ev_server.models.entities.ConnectorKey
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.entities.TenantEntity
import fr.codetics.ev_server.models.entities.UserEntity
import fr.codetics.ev_server.models.people.UserRole
import fr.codetics.ev_server.repositories.ChargingTokenRepository
import fr.codetics.ev_server.repositories.ConnectorRepository
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

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
class AutoChargeTests
@Autowired
constructor(
    private val context: WebApplicationContext,
    private val db: Database,
    private val tenants: TenantRepository,
    private val users: UserRepository,
    private val stations: StationRepository,
    private val connectors: ConnectorRepository,
    private val chargingTokens: ChargingTokenRepository,
    private val transactions: TransactionRepository,
    private val passwords: PasswordEncoder,
    private val accounts: AccountService,
    private val ocpp: OcppService,
) {
    private lateinit var mvc: MockMvc
    private lateinit var tenant: TenantEntity
    private lateinit var admin: UserEntity
    private lateinit var driver: UserEntity
    private lateinit var other: UserEntity

    @BeforeEach
    fun prepare() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply<DefaultMockMvcBuilder>(springSecurity()).build()
        tenant = tenants.save(TenantEntity(slug = "autocharge-${UUID.randomUUID()}", name = "AutoCharge network"))
        admin = account("ADMIN")
        driver = account("DRIVER")
        other = account("DRIVER")
        stations.save(StationEntity(StationKey(tenant.id, "CS-1"), "Borne 1", "unused-test-hash", connected = true))
        chargingTokens.save(ChargingTokenEntity(ChargingTokenKey(tenant.id, "RFID-1"), driver.id))
    }

    private fun account(role: String) =
        users.save(
            UserEntity(
                tenantId = tenant.id,
                email = "${role.lowercase()}-${UUID.randomUUID()}@example.test",
                displayName = "$role test",
                passwordHash = passwords.encode("a-long-password-for-testing"),
                role = UserRole.valueOf(role),
                emailVerified = true,
            )
        )

    private fun MockHttpServletRequestBuilder.as_(user: UserEntity) =
        header(HttpHeaders.AUTHORIZATION, "Bearer ${accounts.issue(requireNotNull(accounts.findUser(user.id)))}")

    private fun json(builder: MockHttpServletRequestBuilder, user: UserEntity, body: Any) =
        builder.with(csrf()).as_(user).contentType(MediaType.APPLICATION_JSON).content(db.encode(body))

    private fun call(action: String, payload: Map<String, Any?>) =
        db.decode(ocpp.call(tenant.id, "CS-1", UUID.randomUUID().toString(), action, db.json.valueToTree(payload)))[2]

    private fun status(value: String, connector: Int = 1) =
        call(
            "StatusNotification",
            mapOf("connectorId" to connector, "status" to value, "errorCode" to "NoError", "timestamp" to Instant.now().toString()),
        )

    private fun authorize(uid: String) = call("Authorize", mapOf("idTag" to uid)).path("idTagInfo").path("status").asText()

    private fun start(uid: String, connector: Int = 1) =
        call("StartTransaction", mapOf("connectorId" to connector, "idTag" to uid, "meterStart" to 0, "timestamp" to Instant.now().toString()))

    private fun stop(id: Int) = call("StopTransaction", mapOf("transactionId" to id, "meterStop" to 1000, "timestamp" to Instant.now().toString()))

    private fun enable(enabled: Boolean = true) =
        mvc.perform(json(put("/api/v1/stations/CS-1/autocharge"), admin, mapOf("enabled" to enabled))).andExpect(status().isOk)

    private fun car(owner: UserEntity): UUID {
        val result =
            mvc.perform(json(post("/api/v1/account/cars"), owner, mapOf("name" to "Megane", "data" to mapOf("make" to "Renault"))))
                .andExpect(status().isOk)
                .andReturn()
        return UUID.fromString(db.decode(result.response.contentAsString).path("id").asText())
    }

    private fun observe(vid: String): Int {
        status("Preparing")
        assertEquals("Invalid", authorize(vid))
        val id = start("RFID-1").path("transactionId").intValue()
        stop(id)
        status("Available")
        return id
    }

    @Test
    fun `vehicle ids are normalized and unusable addresses are refused`() {
        assertEquals("VID:92D50A693F80", VehicleIds.normalize("vid:92:d5:0a:69:3f:80"))
        assertNull(VehicleIds.normalize("VID:000000000000"))
        assertNull(VehicleIds.normalize("VID:FFFFFFFFFFFF"))
        assertNull(VehicleIds.normalize("VID:93D50A693F80"))
        assertNull(VehicleIds.normalize("VID:92D50A693F"))
        assertNull(VehicleIds.normalize("RFID-1"))
    }

    @Test
    fun `a driver enrolls a vehicle seen during a session and then charges by plugging in`() {
        enable()
        mvc.perform(get("/api/v1/stations/CS-1").as_(admin)).andExpect(jsonPath("$.auto_charge").value(true))
        val car = car(driver)
        val session = observe("VID:92D50A693F80")

        assertEquals("VID:92D50A693F80", transactions.findById(session).orElseThrow().vid)
        mvc.perform(get("/api/v1/account/autocharge/candidates").as_(driver))
            .andExpect(jsonPath("$[0].transaction_id").value(session))
            .andExpect(jsonPath("$[0].vid").value("VID:92D50A693F80"))
        mvc.perform(json(post("/api/v1/account/cars/$car/autocharge"), driver, mapOf("transactionId" to session)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.uid").value("VID:92D50A693F80"))
        mvc.perform(get("/api/v1/account/autocharge/candidates").as_(driver)).andExpect(jsonPath("$.length()").value(0))

        status("Preparing")
        assertEquals("Accepted", authorize("VID:92D50A693F80"))
        val started = start("VID:92D50A693F80")
        assertEquals("Accepted", started.path("idTagInfo").path("status").asText())
        val transaction = transactions.findById(started.path("transactionId").intValue()).orElseThrow()
        assertEquals(driver.id, transaction.userId)
        assertEquals(car, transaction.vehicleId)
        mvc.perform(get("/api/v1/transactions/${transaction.id}").as_(driver)).andExpect(jsonPath("$.auto_charge").value(true))
    }

    @Test
    fun `an enrolled vehicle is refused when the station disables autocharge or the plug window has passed`() {
        enable()
        val car = car(driver)
        mvc.perform(json(post("/api/v1/account/cars/$car/autocharge"), driver, mapOf("transactionId" to observe("VID:92D50A693F80"))))
            .andExpect(status().isOk)

        status("Preparing")
        val plug = connectors.findById(ConnectorKey(tenant.id, "CS-1", 1)).orElseThrow()
        plug.pluggedAt = Instant.now().minusSeconds(31)
        connectors.save(plug)
        assertEquals("Invalid", authorize("VID:92D50A693F80"))
        status("Available")

        enable(false)
        status("Preparing")
        assertEquals("Invalid", authorize("VID:92D50A693F80"))
        assertEquals("Invalid", start("VID:92D50A693F80").path("idTagInfo").path("status").asText())
    }

    @Test
    fun `nothing is observed while autocharge is disabled and an unplugged connector forgets the vehicle`() {
        status("Preparing")
        assertEquals("Invalid", authorize("VID:92D50A693F80"))
        assertNull(connectors.findById(ConnectorKey(tenant.id, "CS-1", 1)).orElseThrow().vid)

        enable()
        status("Available")
        status("Preparing")
        authorize("VID:92D50A693F80")
        assertEquals("VID:92D50A693F80", connectors.findById(ConnectorKey(tenant.id, "CS-1", 1)).orElseThrow().vid)
        status("Available")
        assertNull(connectors.findById(ConnectorKey(tenant.id, "CS-1", 1)).orElseThrow().vid)
    }

    @Test
    fun `enrollment is limited to the vehicle owner and cleaned up with the vehicle`() {
        enable()
        val car = car(driver)
        val foreign = car(other)
        val session = observe("VID:92D50A693F80")

        mvc.perform(json(post("/api/v1/account/cars/$foreign/autocharge"), other, mapOf("transactionId" to session)))
            .andExpect(status().isNotFound)
        mvc.perform(json(post("/api/v1/account/cars/$car/autocharge"), other, mapOf("transactionId" to session)))
            .andExpect(status().isNotFound)
        mvc.perform(json(post("/api/v1/account/cars/$car/autocharge"), driver, mapOf("vid" to "VID:92D50A693F80")))
            .andExpect(status().isForbidden)

        mvc.perform(json(post("/api/v1/account/cars/$car/autocharge"), driver, mapOf("transactionId" to session)))
            .andExpect(status().isOk)
        mvc.perform(json(post("/api/v1/cars/$foreign/autocharge"), admin, mapOf("vid" to "VID:92D50A693F80")))
            .andExpect(status().isConflict)
        mvc.perform(post("/api/v1/tokens/VID:92D50A693F80/unassign").with(csrf()).as_(admin)).andExpect(status().isBadRequest)

        mvc.perform(delete("/api/v1/account/cars/$car").with(csrf()).as_(driver)).andExpect(status().isOk)
        assertFalse(chargingTokens.existsById(ChargingTokenKey(tenant.id, "VID:92D50A693F80")))
    }

    @Test
    fun `administrators enroll a vehicle id manually and drivers revoke it`() {
        val car = car(driver)
        mvc.perform(json(post("/api/v1/cars/$car/autocharge"), admin, mapOf("vid" to "VID:02:00:00:AA:BB:CC")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.uid").value("VID:020000AABBCC"))
            .andExpect(jsonPath("$.user_id").value(driver.id.toString()))
        mvc.perform(get("/api/v1/account/cars/$car/autocharge").as_(driver)).andExpect(jsonPath("$[0].uid").value("VID:020000AABBCC"))
        mvc.perform(json(put("/api/v1/account/cars/$car"), driver, mapOf("name" to "Zoe", "data" to mapOf("make" to "Renault"), "revision" to 0)))
            .andExpect(status().isOk)
        mvc.perform(get("/api/v1/account/tokens").as_(driver))
            .andExpect(jsonPath("$[?(@.uid == 'VID:020000AABBCC')].vehicle_id").value(car.toString()))
            .andExpect(jsonPath("$[?(@.uid == 'VID:020000AABBCC')].vehicle_name").value("Zoe"))
        mvc.perform(get("/api/v1/tokens").as_(admin))
            .andExpect(jsonPath("$.items[?(@.uid == 'VID:020000AABBCC')].vehicle_name").value("Zoe"))
        mvc.perform(delete("/api/v1/account/cars/$car/autocharge/VID:020000AABBCC").with(csrf()).as_(driver))
            .andExpect(status().isOk)
        mvc.perform(get("/api/v1/cars/$car/autocharge").as_(admin)).andExpect(jsonPath("$.length()").value(0))
    }
}
