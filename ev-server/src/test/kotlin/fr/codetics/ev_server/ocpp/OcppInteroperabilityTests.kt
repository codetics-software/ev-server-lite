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
import fr.codetics.ev_server.repositories.CommandRepository
import fr.codetics.ev_server.repositories.StationEventRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.repositories.TransactionRepository
import fr.codetics.ev_server.repositories.UserRepository
import fr.codetics.ev_server.security.AccountService
import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
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
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties =
        [
            "app.bootstrap.email=",
            "app.mail-enabled=false",
            "app.ocpp.trusted-proxies=127.0.0.1/32,::1/128",
            "app.ocpp.client-cert-header=X-Client-Cert",
            "app.ocpp.allow-unauthenticated=true",
            "app.ocpp.heartbeat-seconds=120",
            "app.ocpp.public-url=wss://ocpp.example.test/ocpp/",
            "GOOGLE_CLIENT_ID=local-test-google",
            "GOOGLE_CLIENT_SECRET=local-test-google-secret",
            "MICROSOFT_CLIENT_ID=local-test-microsoft",
            "MICROSOFT_CLIENT_SECRET=local-test-microsoft-secret",
            "MICROSOFT_TENANT_ID=00000000-0000-0000-0000-000000000001",
            "APPLE_CLIENT_ID=local-test-apple",
            "APPLE_CLIENT_SECRET=local-test-apple-secret",
        ],
)
class OcppInteroperabilityTests
@Autowired
constructor(
    private val context: WebApplicationContext,
    private val db: Database,
    private val tenants: TenantRepository,
    private val users: UserRepository,
    private val stations: StationRepository,
    private val stationEvents: StationEventRepository,
    private val commands: CommandRepository,
    private val chargingTokens: ChargingTokenRepository,
    private val transactions: TransactionRepository,
    private val passwords: PasswordEncoder,
    private val accounts: AccountService,
    private val ocpp: OcppService,
    @LocalServerPort private val port: Int,
) {
    private lateinit var mvc: MockMvc
    private lateinit var tenant: TenantEntity
    private lateinit var admin: UserEntity
    private lateinit var operator: UserEntity
    private val password = "0123456789abcdef0123456789abcdef01234567"
    private val sockets = mutableListOf<WebSocket>()
    private val client = HttpClient.newHttpClient()

    companion object {
        private val authorityKeys: KeyPair = keys()
        private val authority: X509Certificate = issue("CN=Test OCPP CA", authorityKeys, ca = true)

        private fun keys(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()

        private fun issue(subject: String, subjectKeys: KeyPair, ca: Boolean = false): X509Certificate {
            val builder =
                JcaX509v3CertificateBuilder(
                    X500Name("CN=Test OCPP CA"),
                    BigInteger.valueOf(System.nanoTime()),
                    Date.from(Instant.now().minus(Duration.ofDays(1))),
                    Date.from(Instant.now().plus(Duration.ofDays(60))),
                    X500Name(subject),
                    subjectKeys.public,
                )
            if (ca) builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
            return JcaX509CertificateConverter()
                .getCertificate(builder.build(JcaContentSignerBuilder("SHA256withECDSA").build(authorityKeys.private)))
        }

        @JvmStatic
        @DynamicPropertySource
        fun ocppAuthority(registry: DynamicPropertyRegistry) {
            registry.add("app.ocpp.client-ca") { ChargerCertificates.encode(authority) }
        }
    }

    @BeforeEach
    fun prepare() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply<DefaultMockMvcBuilder>(springSecurity()).build()
        tenant = tenants.save(TenantEntity(slug = "interop-${UUID.randomUUID().toString().take(8)}", name = "Interop"))
        admin = account(UserRole.ADMIN)
        operator = account(UserRole.OPERATOR)
        chargingTokens.save(ChargingTokenEntity(ChargingTokenKey(tenant.id, "RFID-1")))
    }

    @AfterEach
    fun close() {
        sockets.forEach { it.abort() }
    }

    private fun account(role: UserRole) =
        users.save(
            UserEntity(
                tenantId = tenant.id,
                email = "${role.name.lowercase()}-${UUID.randomUUID()}@example.test",
                displayName = role.name,
                passwordHash = passwords.encode("a-long-password-for-testing"),
                role = role,
                emailVerified = true,
            )
        )

    private fun station(id: String, profile: Int = 1, configure: StationEntity.() -> Unit = {}) =
        stations.save(
            StationEntity(StationKey(tenant.id, id), id, requireNotNull(passwords.encode(password))).apply {
                securityProfile = profile
                configure()
            }
        )

    private fun basic(id: String, secret: String = password) =
        "Basic " + Base64.getEncoder().encodeToString("$id:$secret".toByteArray())

    private fun connect(id: String, headers: Map<String, String> = emptyMap(), protocol: Boolean = true): Boolean {
        val builder = client.newWebSocketBuilder()
        if (protocol) builder.subprotocols("ocpp1.6")
        headers.forEach { (name, value) -> builder.header(name, value) }
        return try {
            val socket =
                builder
                    .buildAsync(URI("ws://localhost:$port/ocpp/${tenant.slug}/$id"), object : WebSocket.Listener {})
                    .get(10, TimeUnit.SECONDS)
            sockets += socket
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun reload(id: String): StationEntity {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            val station = stations.findById(StationKey(tenant.id, id)).orElseThrow()
            if (station.connected) return station
            Thread.sleep(50)
        }
        return stations.findById(StationKey(tenant.id, id)).orElseThrow()
    }

    private fun rejections(id: String) =
        stationEvents.findAll().filter {
            it.tenantId == tenant.id && it.stationId == id && it.type == "CONNECTION_REJECTED"
        }.map { it.status }

    private fun call(
        id: String,
        action: String,
        payload: Map<String, Any?>,
        message: String = UUID.randomUUID().toString(),
        connection: String = "connection-1",
    ): JsonNode = db.decode(ocpp.call(tenant.id, id, message, action, db.json.valueToTree(payload), connection))

    private fun MockHttpServletRequestBuilder.as_(user: UserEntity) =
        header(HttpHeaders.AUTHORIZATION, "Bearer ${accounts.issue(requireNotNull(accounts.findUser(user.id)))}")

    private fun json(builder: MockHttpServletRequestBuilder, user: UserEntity, body: Any) =
        builder.with(csrf()).as_(user).contentType(MediaType.APPLICATION_JSON).content(db.encode(body))

    @Test
    fun `profil 1 exige le mot de passe et trace les refus`() {
        station("CP.01:A")
        assertFalse(connect("CP.01:A", mapOf("Authorization" to basic("CP.01:A", "mauvais"))))
        assertFalse(connect("CP.01:A"))
        assertTrue(connect("CP.01:A", mapOf("Authorization" to basic("CP.01:A")), protocol = false))
        assertEquals(1, reload("CP.01:A").connectionSecurityProfile)
        assertTrue("mot de passe invalide" in rejections("CP.01:A"))
        assertTrue("mot de passe absent" in rejections("CP.01:A"))
    }

    @Test
    fun `profil 2 exige TLS indique par un mandataire de confiance`() {
        station("CP-02", profile = 2)
        assertFalse(connect("CP-02", mapOf("Authorization" to basic("CP-02"))))
        assertTrue("TLS requis" in rejections("CP-02"))
        assertTrue(
            connect("CP-02", mapOf("Authorization" to basic("CP-02"), "X-Forwarded-Proto" to "https"))
        )
        assertEquals(2, reload("CP-02").connectionSecurityProfile)
    }

    @Test
    fun `profil 3 exige un certificat client valide au nom de la borne`() {
        station("CP-03", profile = 3)
        val good = ChargerCertificates.encode(issue("CN=CP-03,O=CPO", keys()))
        val wrong = ChargerCertificates.encode(issue("CN=CP-99", keys()))
        val tls = mapOf("X-Forwarded-Proto" to "https")

        assertFalse(connect("CP-03", tls + mapOf("Authorization" to basic("CP-03"))))
        assertTrue("certificat absent" in rejections("CP-03"))
        assertFalse(connect("CP-03", tls + mapOf("X-Client-Cert" to wrong.replace("\n", ""))))
        assertTrue("CN ≠ identifiant et empreinte non épinglée" in rejections("CP-03"))
        assertTrue(connect("CP-03", tls + mapOf("X-Client-Cert" to good.replace("\n", ""))))

        val station = reload("CP-03")
        assertEquals(3, station.connectionSecurityProfile)
        assertTrue(station.certificateSubject!!.contains("CN=CP-03"))
        assertNotNull(station.certificateExpiresAt)

        sockets.forEach { it.abort() }
        Thread.sleep(200)
        stations.save(station.apply { securityProfile = 2 })
        assertTrue(connect("CP-03", tls + mapOf("Authorization" to basic("CP-03"))))
        Thread.sleep(1000)
        val again = reload("CP-03")
        assertEquals(2, again.connectionSecurityProfile)
        assertEquals(station.certificateFingerprint, again.certificateFingerprint)
    }

    @Test
    fun `profil 0 n’est accepte que pour une borne configuree ainsi`() {
        station("CP-00", profile = 0)
        station("CP-01")
        assertTrue(connect("CP-00"))
        assertEquals(0, reload("CP-00").connectionSecurityProfile)
        assertFalse(connect("CP-01"))
    }

    @Test
    fun `le nouveau mot de passe remplace l’ancien a la reconnexion`() {
        val next = "fedcba9876543210fedcba9876543210fedcba98"
        station("CP-04") { pendingSecretHash = passwords.encode(next) }

        assertTrue(connect("CP-04", mapOf("Authorization" to basic("CP-04"))))
        assertNotNull(reload("CP-04").pendingSecretHash)
        sockets.forEach { it.abort() }
        Thread.sleep(200)

        assertTrue(connect("CP-04", mapOf("Authorization" to basic("CP-04", next))))
        val station = reload("CP-04")
        assertNull(station.pendingSecretHash)
        assertTrue(passwords.matches(next, station.secretHash))
    }

    @Test
    fun `un identifiant de message reutilise apres redemarrage est traite comme nouveau`() {
        station("CP-05")
        val boot = mapOf("chargePointVendor" to "Etrel", "chargePointModel" to "INCH")
        val first = call("CP-05", "BootNotification", boot, message = "1", connection = "a")
        val again = call("CP-05", "BootNotification", boot, message = "1", connection = "a")
        assertEquals(first, again)
        assertEquals(120, first[2].path("interval").intValue())

        Thread.sleep(5)
        val reboot = call("CP-05", "BootNotification", boot, message = "1", connection = "b")
        assertEquals(3, reboot[0].intValue())
        assertNotEquals(first[2].path("currentTime"), reboot[2].path("currentTime"))
        assertEquals(3, call("CP-05", "Heartbeat", emptyMap(), message = "1", connection = "c")[0].intValue())

        val error = call("CP-05", "Heartbeat", emptyMap(), message = "1", connection = "c")
        assertEquals(3, error[0].intValue())
        assertThrows(OcppFault::class.java) {
            call("CP-05", "Authorize", mapOf("idTag" to "RFID-1"), message = "1", connection = "c")
        }
    }

    @Test
    fun `une session rejouee apres reconnexion n’est pas dupliquee`() {
        station("CP-06")
        val timestamp = "2026-05-01T08:00:00"
        val start = mapOf("connectorId" to 1, "idTag" to "RFID-1", "meterStart" to 1000.0, "timestamp" to timestamp)
        val first = call("CP-06", "StartTransaction", start, message = "42", connection = "a")
        val replayed = call("CP-06", "StartTransaction", start, message = "42", connection = "b")
        val retried = call("CP-06", "StartTransaction", start, message = "77", connection = "b")
        val id = first[2].path("transactionId").intValue()

        assertEquals(id, replayed[2].path("transactionId").intValue())
        assertEquals(id, retried[2].path("transactionId").intValue())
        assertEquals(1, transactions.findAllByTenantIdAndStoppedAtIsNull(tenant.id).count { it.stationId == "CP-06" })
        assertEquals(Instant.parse("2026-05-01T08:00:00Z"), transactions.findByTenantIdAndId(tenant.id, id)!!.startedAt)
    }

    @Test
    fun `les releves signes et les codes d’erreur constructeur sont conserves`() {
        station("CP-07") { dataTransferVendors = "com.alfen" }
        val id =
            call(
                    "CP-07",
                    "StartTransaction",
                    mapOf("connectorId" to 1, "idTag" to "RFID-1", "meterStart" to 0, "timestamp" to Instant.now().toString()),
                )[2]
                .path("transactionId")
                .intValue()
        val ocmf = "OCMF|{\"RD\":[{\"RV\":2.5,\"RI\":\"1-b:1.8.0\",\"RU\":\"kWh\"}]}|{\"SD\":\"30\"}"
        call(
            "CP-07",
            "StopTransaction",
            mapOf(
                "transactionId" to id,
                "meterStop" to 2500,
                "timestamp" to Instant.now().toString(),
                "transactionData" to
                    listOf(
                        mapOf(
                            "timestamp" to Instant.now().toString(),
                            "sampledValue" to
                                listOf(mapOf("value" to ocmf, "format" to "SignedData", "context" to "Transaction.End")),
                        )
                    ),
            ),
        )
        val transaction = transactions.findByTenantIdAndId(tenant.id, id)!!
        assertEquals(ocmf, transaction.signedMeterStop)
        assertEquals("OCMF", transaction.signedMeterFormat)

        val periodic =
            call(
                    "CP-07",
                    "StartTransaction",
                    mapOf("connectorId" to 2, "idTag" to "RFID-1", "meterStart" to 0, "timestamp" to Instant.now().toString()),
                )[2]
                .path("transactionId")
                .intValue()
        listOf("OCMF|{\"RD\":[{\"RV\":0.1,\"RU\":\"kWh\"}]}|{}", "OCMF|{\"RD\":[{\"RV\":0.9,\"RU\":\"kWh\"}]}|{}")
            .forEach { value ->
                call(
                    "CP-07",
                    "MeterValues",
                    mapOf(
                        "connectorId" to 2,
                        "transactionId" to periodic,
                        "meterValue" to
                            listOf(
                                mapOf(
                                    "timestamp" to Instant.now().toString(),
                                    "sampledValue" to listOf(mapOf("value" to value, "format" to "SignedData")),
                                )
                            ),
                    ),
                )
            }
        val sampled = transactions.findByTenantIdAndId(tenant.id, periodic)!!
        assertTrue(sampled.signedMeterStart!!.contains("0.1"))
        assertTrue(sampled.signedMeterStop!!.contains("0.9"))

        call(
            "CP-07",
            "StatusNotification",
            mapOf("connectorId" to 1, "status" to "Faulted", "errorCode" to "E42", "vendorErrorCode" to "0x2A"),
        )
        val status = stationEvents.findAll().last { it.stationId == "CP-07" && it.type == "STATUS" }
        assertEquals("OtherError", status.errorCode)
        assertTrue(status.info!!.contains("E42"))

        assertEquals(
            "Accepted",
            call("CP-07", "DataTransfer", mapOf("vendorId" to "com.alfen", "data" to "x"))[2].path("status").asText(),
        )
        assertEquals(
            "UnknownVendorId",
            call("CP-07", "DataTransfer", mapOf("vendorId" to "com.other"))[2].path("status").asText(),
        )
        assertEquals(
            "Rejected",
            call("CP-07", "SignCertificate", mapOf("csr" to "-----BEGIN CERTIFICATE REQUEST-----"))[2]
                .path("status")
                .asText(),
        )
        call(
            "CP-07",
            "SecurityEventNotification",
            mapOf("type" to "TamperDetectionActivated", "timestamp" to Instant.now().toString()),
        )
        assertTrue(stationEvents.findAll().any { it.stationId == "CP-07" && it.status == "TamperDetectionActivated" })
    }

    @Test
    fun `les parametres de securite passent par les parcours dedies`() {
        station("CP-08")
        mvc.perform(json(put("/api/v1/stations/CP-08/security"), operator, mapOf("securityProfile" to 2)))
            .andExpect(status().isForbidden)
        mvc.perform(
                json(
                    post("/api/v1/stations/CP-08/commands"),
                    admin,
                    mapOf("action" to "ChangeConfiguration", "payload" to mapOf("key" to "AuthorizationKey", "value" to "x")),
                )
            )
            .andExpect(status().isBadRequest)
        mvc.perform(
                json(
                    put("/api/v1/stations/CP-08/parameters"),
                    admin,
                    mapOf("key" to "SecurityProfile", "value" to "3"),
                )
            )
            .andExpect(status().isBadRequest)
        mvc.perform(
                json(
                    post("/api/v1/stations/CP-08/commands"),
                    admin,
                    mapOf("action" to "InstallCertificate", "payload" to mapOf("certificateType" to "CentralSystemRootCertificate", "certificate" to "x")),
                )
            )
            .andExpect(status().isBadRequest)

        mvc.perform(json(put("/api/v1/stations/CP-08/security"), admin, mapOf("securityProfile" to 2)))
            .andExpect(status().isConflict)
        mvc.perform(json(post("/api/v1/stations/CP-08/secret/remote"), admin, emptyMap<String, Any>()))
            .andExpect(status().isConflict)

        mvc.perform(
                json(
                    put("/api/v1/stations/CP-08/security"),
                    admin,
                    mapOf("securityProfile" to 2, "sendToStation" to false),
                )
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.station.security_profile").value(2))
        mvc.perform(json(put("/api/v1/stations/CP-08/security"), admin, mapOf("securityProfile" to 1)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.station.security_profile").value(1))
        mvc.perform(
                json(
                    put("/api/v1/stations/CP-08/security"),
                    admin,
                    mapOf("securityProfile" to 1, "pinnedCertificateFingerprint" to "abc"),
                )
            )
            .andExpect(status().isBadRequest)

        mvc.perform(
                json(
                    put("/api/v1/stations/CP-08/ocpp-settings"),
                    operator,
                    mapOf("heartbeatInterval" to 900, "dataTransferVendors" to listOf("com.alfen")),
                )
            )
            .andExpect(status().isBadRequest)
        mvc.perform(
                json(
                    put("/api/v1/stations/CP-08/ocpp-settings"),
                    operator,
                    mapOf("heartbeatInterval" to 300, "dataTransferVendors" to listOf("com.alfen", " ")),
                )
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.station.heartbeat_interval").value(300))
            .andExpect(jsonPath("$.station.data_transfer_vendors[0]").value("com.alfen"))

        mvc.perform(get("/api/v1/stations/security/capabilities").as_(operator))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.client_certificate_authority").value(true))
            .andExpect(jsonPath("$.certificate_signing").value(false))
            .andExpect(jsonPath("$.default_heartbeat_interval").value(120))
            .andExpect(jsonPath("$.ocpp_public_url").value("wss://ocpp.example.test/ocpp"))
        mvc.perform(
                json(
                    post("/api/v1/stations/CP-08/certificate/commands"),
                    admin,
                    mapOf("action" to "ExtendedTriggerMessage", "payload" to emptyMap<String, Any>()),
                )
            )
            .andExpect(status().isConflict)
        mvc.perform(json(post("/api/v1/stations"), admin, mapOf("id" to "..", "name" to "Point")))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `le mot de passe est renouvele a distance sans etre conserve en clair`() {
        station("CP-09")
        assertTrue(connect("CP-09", mapOf("Authorization" to basic("CP-09"))))
        reload("CP-09")

        val response =
            mvc.perform(json(post("/api/v1/stations/CP-09/secret/remote"), admin, emptyMap<String, Any>()))
                .andExpect(status().isOk)
                .andReturn()
        val secret = db.decode(response.response.contentAsString).path("secret").asText()
        assertTrue(secret.matches(Regex("[0-9a-f]{40}")))
        val station = stations.findById(StationKey(tenant.id, "CP-09")).orElseThrow()
        assertTrue(passwords.matches(secret, station.pendingSecretHash))
        assertTrue(passwords.matches(password, station.secretHash))

        Thread.sleep(1500)
        mvc.perform(json(delete("/api/v1/stations/CP-09/secret/pending"), admin, emptyMap<String, Any>()))
            .andExpect(status().isConflict)
        mvc.perform(json(post("/api/v1/stations/CP-09/secret/revoke-previous"), admin, emptyMap<String, Any>()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.password_rotation_pending").value(false))
        assertTrue(passwords.matches(secret, stations.findById(StationKey(tenant.id, "CP-09")).orElseThrow().secretHash))
        assertTrue(
            commands.findAll().none { it.stationId == "CP-09" && it.request.contains(secret) && it.state.name !in setOf("QUEUED", "SENT") }
        )
    }
}
