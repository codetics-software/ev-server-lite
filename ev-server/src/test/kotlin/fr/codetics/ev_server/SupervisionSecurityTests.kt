package fr.codetics.ev_server

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.randomToken
import fr.codetics.ev_server.core.sha256
import fr.codetics.ev_server.ocpp.OcppFault
import fr.codetics.ev_server.ocpp.OcppService
import fr.codetics.ev_server.persistence.*
import fr.codetics.ev_server.security.AccountService
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@ActiveProfiles("local")
@SpringBootTest(
    properties = [
        "app.bootstrap.email=",
        "app.mail-enabled=false",
        "GOOGLE_CLIENT_ID=local-test-google",
        "GOOGLE_CLIENT_SECRET=local-test-google-secret",
        "MICROSOFT_CLIENT_ID=local-test-microsoft",
        "MICROSOFT_CLIENT_SECRET=local-test-microsoft-secret",
        "MICROSOFT_TENANT_ID=00000000-0000-0000-0000-000000000001",
        "APPLE_CLIENT_ID=local-test-apple",
        "APPLE_CLIENT_SECRET=local-test-apple-secret"
    ]
)
class SupervisionSecurityTests @Autowired constructor(
    private val context: WebApplicationContext,
    private val db: Database,
    private val tenants: TenantRepository,
    private val users: UserRepository,
    private val stations: StationRepository,
    private val records: RecordRepository,
    private val chargingTokens: ChargingTokenRepository,
    private val transactions: TransactionRepository,
    private val partners: PartnerRepository,
    private val migrationDocuments: MigrationDocumentRepository,
    private val mail: MailRepository,
    private val passwords: PasswordEncoder,
    private val accounts: AccountService,
    private val ocpp: OcppService
) {
    private lateinit var mvc: MockMvc
    private lateinit var tenant: TenantEntity
    private lateinit var admin: UserEntity
    private lateinit var viewer: UserEntity
    private lateinit var driver: UserEntity

    @BeforeEach
    fun prepare() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()
        tenant = tenants.save(TenantEntity(slug = "test-${UUID.randomUUID()}", name = "Test network"))
        admin = account("ADMIN")
        viewer = account("VIEWER")
        driver = account("DRIVER")
        stations.save(StationEntity(StationKey(tenant.id, "TEST-01"), "Test station", "unused-test-hash"))
    }

    private fun account(role: String): UserEntity {
        return users.save(
            UserEntity(
                tenantId = tenant.id,
                email = "${role.lowercase()}@example.test",
                displayName = "$role test account",
                passwordHash = passwords.encode("a-long-password-for-testing"),
                role = role,
                emailVerified = true
            )
        )
    }

    private fun authorization(user: UserEntity): String {
        val principal = requireNotNull(accounts.findUser(user.id))
        return "Bearer ${accounts.issue(principal)}"
    }

    @Test
    fun `password encoding supports unicode passphrases and legacy bcrypt verification`() {
        val passphrase = "Une longue phrase de passe électrique 🔌 répétée"
        val encoded = passwords.encode(passphrase)
        assertTrue(passwords.matches(passphrase, encoded))
        assertFalse(passwords.matches("A different passphrase", encoded))

        val legacy = org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4)
            .encode("legacy-password-for-test")
        assertTrue(passwords.matches("legacy-password-for-test", legacy))
    }

    @Test
    fun `oauth initiation uses expected providers pkce and apple form post`() {
        val google = mvc.perform(get("/oauth2/authorization/google"))
            .andExpect(status().is3xxRedirection)
            .andReturn().response.getHeader(HttpHeaders.LOCATION)
        assertNotNull(google)
        assertTrue(google!!.startsWith("https://accounts.google.com/"))
        assertTrue(google.contains("code_challenge="))
        assertTrue(google.contains("code_challenge_method=S256"))

        val microsoft = mvc.perform(get("/oauth2/authorization/microsoft"))
            .andExpect(status().is3xxRedirection)
            .andReturn().response.getHeader(HttpHeaders.LOCATION)
        assertNotNull(microsoft)
        assertTrue(microsoft!!.startsWith("https://login.microsoftonline.com/00000000-0000-0000-0000-000000000001/"))
        assertTrue(microsoft.contains("code_challenge="))

        val apple = mvc.perform(get("/oauth2/authorization/apple"))
            .andExpect(status().is3xxRedirection)
            .andReturn().response.getHeader(HttpHeaders.LOCATION)
        assertNotNull(apple)
        assertTrue(apple!!.startsWith("https://appleid.apple.com/"))
        assertTrue(apple.contains("response_mode=form_post"))
    }

    @Test
    fun `openapi document is administrator only and identifies codetics`() {
        mvc.perform(get("/api/v1/openapi").header(HttpHeaders.AUTHORIZATION, authorization(viewer)))
            .andExpect(status().isForbidden)

        mvc.perform(get("/api/v1/openapi").header(HttpHeaders.AUTHORIZATION, authorization(admin)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.info.title").value("EV Supervision Lite"))
            .andExpect(jsonPath("$.info.description").value("Generated by Codetics with OpenApi"))
            .andExpect(jsonPath("$.paths['/api/v1/stations']").exists())
    }

    @Test
    fun `anonymous requests and csrf-free mutations are rejected`() {
        mvc.perform(get("/api/v1/stations"))
            .andExpect(status().isUnauthorized)

        mvc.perform(
            post("/api/v1/stations")
                .header(HttpHeaders.AUTHORIZATION, authorization(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"id":"NEW-01","name":"New station"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `viewer cannot issue commands and driver cannot enumerate stations`() {
        mvc.perform(
            post("/api/v1/stations/TEST-01/commands")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, authorization(viewer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"action":"Reset","payload":{"type":"Soft"}}""")
        ).andExpect(status().isForbidden)

        mvc.perform(get("/api/v1/stations").header(HttpHeaders.AUTHORIZATION, authorization(driver)))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `cross tenant identifiers and sql-like search strings remain isolated`() {
        val otherTenant = tenants.save(TenantEntity(slug = "other-${UUID.randomUUID()}", name = "Other network"))
        stations.save(StationEntity(StationKey(otherTenant.id, "PRIVATE"), "Private station", "unused"))
        records.save(RecordEntity(RecordKey(otherTenant.id, "assets"), "Private meter"))

        mvc.perform(get("/api/v1/stations/PRIVATE").header(HttpHeaders.AUTHORIZATION, authorization(admin)))
            .andExpect(status().isNotFound)

        mvc.perform(
            get("/api/v1/records/assets")
                .param("search", "' OR 1=1 --")
                .header(HttpHeaders.AUTHORIZATION, authorization(admin))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(0))
    }

    @Test
    fun `ocpp replay does not duplicate transactions and rejects changed replay payload`() {
        chargingTokens.save(ChargingTokenEntity(ChargingTokenKey(tenant.id, "RFID-01"), driver.id))
        val message = UUID.randomUUID().toString()
        val payload = db.json.readTree(
            """{"connectorId":1,"idTag":"RFID-01","meterStart":1000,"timestamp":"2026-10-01T10:00:00Z"}"""
        )

        val first = ocpp.call(tenant.id, "TEST-01", message, "StartTransaction", payload)
        val replay = ocpp.call(tenant.id, "TEST-01", message, "StartTransaction", payload)
        assertEquals(first, replay)
        assertEquals(1, transactions.countByTenantId(tenant.id))

        val changed = db.json.readTree(
            """{"connectorId":2,"idTag":"RFID-01","meterStart":1000,"timestamp":"2026-10-01T10:00:00Z"}"""
        )
        assertThrows(OcppFault::class.java) {
            ocpp.call(tenant.id, "TEST-01", message, "StartTransaction", changed)
        }
    }

    @Test
    fun `driver sessions are owner filtered and vehicle is optional`() {
        chargingTokens.save(ChargingTokenEntity(ChargingTokenKey(tenant.id, "OWNER"), driver.id))
        chargingTokens.save(ChargingTokenEntity(ChargingTokenKey(tenant.id, "OTHER"), viewer.id))

        listOf("OWNER", "OTHER").forEachIndexed { index, token ->
            ocpp.call(
                tenant.id,
                "TEST-01",
                UUID.randomUUID().toString(),
                "StartTransaction",
                db.json.valueToTree(
                    mapOf(
                        "connectorId" to index + 1,
                        "idTag" to token,
                        "meterStart" to 1000,
                        "timestamp" to Instant.now().toString()
                    )
                )
            )
        }

        mvc.perform(get("/api/v1/transactions").header(HttpHeaders.AUTHORIZATION, authorization(driver)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].connector_id").value(1))
    }

    @Test
    fun `password reset is single use and revokes existing sessions`() {
        val session = accounts.issue(requireNotNull(accounts.findUser(admin.id)))
        accounts.requestChallenge(tenant.slug, admin.email, "RESET")
        val delivery = mail.findAll().last { entry ->
            entry.recipient == admin.email && entry.body.contains("reset-password")
        }
        val raw = delivery.body.substringAfter("token=").substringBefore('\n')

        assertTrue(accounts.completeChallenge(raw, "RESET", "a-different-long-password"))
        assertNull(accounts.authenticateToken(session))
        assertThrows(ResponseStatusException::class.java) {
            accounts.completeChallenge(raw, "RESET", "a-different-long-password")
        }
    }

    @Test
    fun `oauth email does not silently link an existing account`() {
        assertThrows(ResponseStatusException::class.java) {
            accounts.oauth("https://accounts.google.com", "subject-01", admin.email, "Provider name", true, null)
        }

        val linked = accounts.oauth("https://accounts.google.com", "subject-01", admin.email, null, true, admin.id)
        assertEquals(admin.id, linked.id)

        assertThrows(ResponseStatusException::class.java) {
            accounts.oauth("https://accounts.google.com", "subject-01", viewer.email, null, true, viewer.id)
        }
    }

    @Test
    fun `ocpi token is role scoped and received object ownership is enforced`() {
        val token = randomToken()
        partners.save(
            PartnerEntity(
                tenantId = tenant.id,
                name = "Trusted test CPO",
                countryCode = "FR",
                partyId = "TRU",
                localRole = "emsp",
                tokenHash = sha256(token),
                registered = true
            )
        )

        mvc.perform(get("/ocpi/cpo/versions").header(HttpHeaders.AUTHORIZATION, "Token $token"))
            .andExpect(status().isUnauthorized)

        mvc.perform(
            put("/ocpi/emsp/2.1.1/locations/NL/EVL/location-01")
                .header(HttpHeaders.AUTHORIZATION, "Token $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"id":"location-01","last_updated":"2026-10-01T12:00:00Z"}""")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status_code").value(2001))
    }

    @Test
    fun `received cdr is idempotent and cannot be overwritten`() {
        val token = randomToken()
        partners.save(
            PartnerEntity(
                tenantId = tenant.id,
                name = "CDR test partner",
                countryCode = "FR",
                partyId = "CDR",
                localRole = "emsp",
                tokenHash = sha256(token),
                registered = true
            )
        )
        val payload = db.encode(
            mapOf(
                "id" to "cdr-01",
                "start_date_time" to "2026-10-01T10:00:00Z",
                "stop_date_time" to "2026-10-01T11:00:00Z",
                "auth_id" to "driver-contract",
                "auth_method" to "AUTH_REQUEST",
                "location" to mapOf("id" to "location-01"),
                "currency" to "EUR",
                "charging_periods" to emptyList<Any>(),
                "total_cost" to 5,
                "total_energy" to 10,
                "total_time" to 1,
                "last_updated" to "2026-10-01T11:00:00Z"
            )
        )

        repeat(2) {
            mvc.perform(
                post("/ocpi/emsp/2.1.1/cdrs")
                    .header(HttpHeaders.AUTHORIZATION, "Token $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status_code").value(1000))
                .andExpect(header().exists(HttpHeaders.LOCATION))
        }

        val changed = payload.replace("\"total_cost\":5", "\"total_cost\":99")
        assertNotEquals(payload, changed)
        mvc.perform(
            post("/ocpi/emsp/2.1.1/cdrs")
                .header(HttpHeaders.AUTHORIZATION, "Token $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(changed)
        ).andExpect(status().isConflict)
    }

    @Test
    fun `mongo extended json archive preserves unknown fields and is replay safe`() {
        val exported = """{"_id":{"${'$'}oid":"507f1f77bcf86cd799439011"},"unknown":{"${'$'}numberDecimal":"0.1234567890123456789"},"name":"Énergie"}"""
        val authorization = authorization(admin)

        repeat(2) { attempt ->
            mvc.perform(
                post("/api/v1/migrations/assets")
                    .with(csrf())
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .contentType("application/x-ndjson")
                    .content(exported)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.imported").value(if (attempt == 0) 1 else 0))
        }

        val sourceId = db.encode(db.json.readTree(exported).path("_id"))
        val archived = migrationDocuments.findById(MigrationDocumentKey(tenant.id, "assets", sourceId)).orElseThrow()
        assertEquals(db.json.readTree(exported), db.json.readTree(archived.document))
        assertEquals(sha256(exported), archived.sourceSha256)
        assertEquals(exported, archived.rawDocument)
    }

    @Test
    fun `archive conflict rolls back preceding documents without overwriting the original`() {
        val original = """{"_id":"original","amount":{"${'$'}numberDecimal":"123.4567890123456789"}}"""
        val headers = authorization(admin)
        fun archive(content: String) = mvc.perform(
            post("/api/v1/migrations/assets")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, headers)
                .contentType("application/x-ndjson")
                .content(content)
        )

        archive(original).andExpect(status().isOk)
        archive("""{"_id":"new","name":"Must roll back"}""" + "\n" + original.replace("123.", "999."))
            .andExpect(status().isConflict)

        assertFalse(migrationDocuments.existsById(MigrationDocumentKey(tenant.id, "assets", "\"new\"")))
        val saved = migrationDocuments.findById(MigrationDocumentKey(tenant.id, "assets", "\"original\"")).orElseThrow()
        assertEquals(original, saved.rawDocument)
        assertEquals(sha256(original), saved.sourceSha256)
    }

    @Test
    fun `archive is tenant scoped administrator only and does not populate operational records`() {
        val content = """{"_id":"shared-id","name":"Original asset","futureField":1234567890123456789}"""
        mvc.perform(
            post("/api/v1/migrations/assets").with(csrf())
                .header(HttpHeaders.AUTHORIZATION, authorization(viewer))
                .contentType("application/x-ndjson").content(content)
        ).andExpect(status().isForbidden)

        val other = tenants.save(TenantEntity(slug = "archive-${UUID.randomUUID()}", name = "Other tenant"))
        val otherAdmin = users.save(UserEntity(tenantId = other.id, email = "other@example.test", role = "ADMIN"))
        listOf(admin, otherAdmin).forEach { user ->
            mvc.perform(
                post("/api/v1/migrations/assets").with(csrf())
                    .header(HttpHeaders.AUTHORIZATION, authorization(user))
                    .contentType("application/x-ndjson").content(content)
            ).andExpect(status().isOk).andExpect(jsonPath("$.imported").value(1))
            val key = MigrationDocumentKey(user.tenantId, "assets", "\"shared-id\"")
            assertEquals(content, migrationDocuments.findById(key).orElseThrow().rawDocument)
        }

        mvc.perform(get("/api/v1/records/assets").header(HttpHeaders.AUTHORIZATION, authorization(admin)))
            .andExpect(status().isOk).andExpect(jsonPath("$.total").value(0))
    }

    @Test
    fun `invalid archive json rolls back the entire batch and returns a client error`() {
        mvc.perform(
            post("/api/v1/migrations/assets").with(csrf())
                .header(HttpHeaders.AUTHORIZATION, authorization(admin))
                .contentType("application/x-ndjson")
                .content("""{"_id":"before-invalid"}""" + "\n" + "{invalid}")
        ).andExpect(status().isBadRequest)

        assertFalse(migrationDocuments.existsById(MigrationDocumentKey(tenant.id, "assets", "\"before-invalid\"")))
    }

    @Test
    fun `invalid utf8 archive returns a client error without replacement characters`() {
        mvc.perform(
            post("/api/v1/migrations/assets").with(csrf())
                .header(HttpHeaders.AUTHORIZATION, authorization(admin))
                .contentType("application/x-ndjson")
                .content(byteArrayOf(0xc3.toByte(), 0x28))
        ).andExpect(status().isBadRequest)
    }
}
