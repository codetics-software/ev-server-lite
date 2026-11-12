package fr.codetics.ev_server.ocpp

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.models.entities.ChargingTokenEntity
import fr.codetics.ev_server.models.entities.ChargingTokenKey
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.entities.TenantEntity
import fr.codetics.ev_server.models.entities.UserEntity
import fr.codetics.ev_server.models.people.UserRole
import fr.codetics.ev_server.repositories.ChargingTokenRepository
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
class OcppMessageHandlingTests
@Autowired
constructor(
    private val context: WebApplicationContext,
    private val db: Database,
    private val tenants: TenantRepository,
    private val users: UserRepository,
    private val stations: StationRepository,
    private val chargingTokens: ChargingTokenRepository,
    private val transactions: TransactionRepository,
    private val passwords: PasswordEncoder,
    private val accounts: AccountService,
    private val ocpp: OcppService,
    private val queue: OcppCommandQueue,
) {
    private lateinit var mvc: MockMvc
    private lateinit var tenant: TenantEntity
    private lateinit var admin: UserEntity

    @BeforeEach
    fun prepare() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply<DefaultMockMvcBuilder>(springSecurity()).build()
        tenant = tenants.save(TenantEntity(slug = "ocpp-${UUID.randomUUID()}", name = "Réseau OCPP"))
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
        stations.save(StationEntity(StationKey(tenant.id, "CS-1"), "Borne 1", "unused-test-hash", connected = true))
        chargingTokens.save(ChargingTokenEntity(ChargingTokenKey(tenant.id, "TAG-1")))
        chargingTokens.save(ChargingTokenEntity(ChargingTokenKey(tenant.id, "TAG-2")))
    }

    private fun MockHttpServletRequestBuilder.as_(user: UserEntity) =
        header(HttpHeaders.AUTHORIZATION, "Bearer ${accounts.issue(requireNotNull(accounts.findUser(user.id)))}")

    private fun call(action: String, payload: Map<String, Any?>): JsonNode =
        db.decode(ocpp.call(tenant.id, "CS-1", UUID.randomUUID().toString(), action, db.json.valueToTree(payload)))[2]

    private fun start(tag: String, extra: Map<String, Any?> = emptyMap()) =
        call(
            "StartTransaction",
            mapOf("connectorId" to 1, "idTag" to tag, "meterStart" to 0, "timestamp" to Instant.now().toString()) + extra,
        )

    private fun command(action: String, payload: Map<String, Any?>, result: Map<String, Any?>) {
        val id = queue.command(tenant.id, "CS-1", action, db.json.valueToTree(payload))
        val identity = StationIdentity(tenant.id, "CS-1")
        assertTrue(queue.claim(identity).any { it.id == id })
        queue.complete(identity, db.json.valueToTree(listOf(3, id.toString(), result)))
    }

    private fun reservations(): JsonNode =
        db.decode(
            mvc.perform(get("/api/v1/reservations").param("station_id", "CS-1").as_(admin))
                .andExpect(status().isOk)
                .andReturn()
                .response
                .contentAsString
        )

    @Test
    fun `un StartTransaction sur un connecteur occupe est refuse`() {
        val first = start("TAG-1")

        val fault = assertThrows(OcppFault::class.java) { start("TAG-2") }
        assertEquals("GenericError", fault.code)
        val open = transactions.findById(first.path("transactionId").intValue()).orElseThrow()
        assertNull(open.stoppedAt)
        assertEquals(1, transactions.countByTenantId(tenant.id))
    }

    @Test
    fun `le badge parent est renvoye dans idTagInfo`() {
        mvc.perform(
                post("/api/v1/tokens")
                    .with(csrf())
                    .as_(admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(db.encode(mapOf("uid" to "CHILD", "parentIdTag" to "GROUP")))
            )
            .andExpect(status().isOk)
        mvc.perform(get("/api/v1/tokens").as_(admin))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$..parent_id_tag").value(org.hamcrest.Matchers.hasItem("GROUP")))
        mvc.perform(
                post("/api/v1/tokens")
                    .with(csrf())
                    .as_(admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(db.encode(mapOf("uid" to "BAD", "parentIdTag" to "x".repeat(21))))
            )
            .andExpect(status().isBadRequest)

        val authorize = call("Authorize", mapOf("idTag" to "CHILD"))
        assertEquals("Accepted", authorize.path("idTagInfo").path("status").asText())
        assertEquals("GROUP", authorize.path("idTagInfo").path("parentIdTag").asText())
        assertFalse(call("Authorize", mapOf("idTag" to "TAG-1")).path("idTagInfo").has("parentIdTag"))

        val started = start("CHILD")
        assertEquals("GROUP", started.path("idTagInfo").path("parentIdTag").asText())
    }

    @Test
    fun `les reservations sont suivies jusqu’a leur annulation ou leur utilisation`() {
        val expiry = Instant.now().plusSeconds(600).toString()
        command(
            "ReserveNow",
            mapOf("connectorId" to 1, "expiryDate" to expiry, "idTag" to "TAG-1", "parentIdTag" to "GROUP", "reservationId" to 7),
            mapOf("status" to "Accepted"),
        )
        reservations().first().let {
            assertEquals(7, it.path("reservation_id").intValue())
            assertEquals("ACCEPTED", it.path("status").asText())
            assertEquals("GROUP", it.path("parent_id_tag").asText())
        }

        command("CancelReservation", mapOf("reservationId" to 7), mapOf("status" to "Accepted"))
        assertEquals("CANCELLED", reservations().first().path("status").asText())

        command(
            "ReserveNow",
            mapOf("connectorId" to 1, "expiryDate" to expiry, "idTag" to "TAG-1", "reservationId" to 8),
            mapOf("status" to "Accepted"),
        )
        val started = start("TAG-1", mapOf("reservationId" to 8))
        val used = reservations().first { it.path("reservation_id").intValue() == 8 }
        assertEquals("USED", used.path("status").asText())
        assertEquals(started.path("transactionId").intValue(), used.path("transaction_id").intValue())

        command(
            "ReserveNow",
            mapOf("connectorId" to 2, "expiryDate" to expiry, "idTag" to "TAG-1", "reservationId" to 9),
            mapOf("status" to "Occupied"),
        )
        assertEquals("OCCUPIED", reservations().first { it.path("reservation_id").intValue() == 9 }.path("status").asText())
    }
}
