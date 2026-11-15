package fr.codetics.ev_server.ocpp.v201

import com.sun.net.httpserver.HttpServer
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.models.entities.ChargingTokenEntity
import fr.codetics.ev_server.models.entities.ChargingTokenKey
import fr.codetics.ev_server.models.entities.ConnectorEntity
import fr.codetics.ev_server.models.entities.ConnectorKey
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.entities.TenantEntity
import fr.codetics.ev_server.models.entities.UserEntity
import fr.codetics.ev_server.models.people.UserRole
import fr.codetics.ev_server.ocpp.OcppCommandQueue
import fr.codetics.ev_server.ocpp.OcppService
import fr.codetics.ev_server.ocpp.OcppVersion
import fr.codetics.ev_server.ocpp.StationIdentity
import fr.codetics.ev_server.repositories.ChargingTokenRepository
import fr.codetics.ev_server.repositories.ConnectorRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.repositories.UserRepository
import fr.codetics.ev_server.security.AccountService
import java.math.BigDecimal
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import org.bouncycastle.asn1.x509.CRLReason
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.ocsp.CertificateStatus
import org.bouncycastle.cert.ocsp.OCSPReq
import org.bouncycastle.cert.ocsp.OCSPResp
import org.bouncycastle.cert.ocsp.OCSPRespBuilder
import org.bouncycastle.cert.ocsp.RevokedStatus
import org.bouncycastle.cert.ocsp.jcajce.JcaBasicOCSPRespBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
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
    properties =
        [
            "app.bootstrap.email=",
            "app.mail-enabled=false",
            "app.ocpp.iso15118-enabled=true",
            "app.ocpp.ocsp-allow-private=true",
            "GOOGLE_CLIENT_ID=local-test-google",
            "GOOGLE_CLIENT_SECRET=local-test-google-secret",
            "MICROSOFT_CLIENT_ID=local-test-microsoft",
            "MICROSOFT_CLIENT_SECRET=local-test-microsoft-secret",
            "MICROSOFT_TENANT_ID=00000000-0000-0000-0000-000000000001",
            "APPLE_CLIENT_ID=local-test-apple",
            "APPLE_CLIENT_SECRET=local-test-apple-secret",
        ]
)
class Iso15118Tests
@Autowired
constructor(
    private val context: WebApplicationContext,
    private val db: Database,
    private val tenants: TenantRepository,
    private val users: UserRepository,
    private val stations: StationRepository,
    private val chargingTokens: ChargingTokenRepository,
    private val connectors: ConnectorRepository,
    private val passwords: PasswordEncoder,
    private val accounts: AccountService,
    private val ocpp: OcppService,
    private val queue: OcppCommandQueue,
    private val translator: Ocpp201Commands,
) {
    private lateinit var mvc: MockMvc
    private lateinit var tenant: TenantEntity
    private lateinit var admin: UserEntity

    companion object {
        private val material = TestPki.write()
        private val responder = TestPki.authority("CN=OCSP Test")
        private val revokedSerial = BigInteger.valueOf(666)
        private val contractBody = AtomicReference<String>()
        private val server: HttpServer =
            HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                createContext("/ocsp") { exchange ->
                    val request = OCSPReq(exchange.requestBody.readAllBytes())
                    val id = request.requestList.first().certID
                    val builder =
                        JcaBasicOCSPRespBuilder(
                            responder.keys.public,
                            JcaDigestCalculatorProviderBuilder().build().get(org.bouncycastle.cert.ocsp.CertificateID.HASH_SHA1),
                        )
                    builder.addResponse(
                        id,
                        if (id.serialNumber == revokedSerial) RevokedStatus(Date(), CRLReason.keyCompromise) else CertificateStatus.GOOD,
                    )
                    val basic =
                        builder.build(
                            JcaContentSignerBuilder("SHA256withECDSA").build(responder.keys.private),
                            arrayOf(X509CertificateHolder(responder.certificate.encoded)),
                            Date(),
                        )
                    val body = OCSPRespBuilder().build(OCSPRespBuilder.SUCCESSFUL, basic).encoded
                    exchange.responseHeaders.add("Content-Type", "application/ocsp-response")
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                createContext("/contract") { exchange ->
                    contractBody.set(String(exchange.requestBody.readAllBytes()))
                    val body = """{"status":"Accepted","exiResponse":"RVhJLVJFU1BPTlNF"}""".toByteArray()
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                start()
            }

        @JvmStatic
        @DynamicPropertySource
        fun iso15118(registry: DynamicPropertyRegistry) {
            registry.add("app.ocpp.certificates-dir") { material.directory.toString() }
            registry.add("app.ocpp.contract-service-url") { "http://127.0.0.1:${server.address.port}/contract" }
        }
    }

    @BeforeEach
    fun prepare() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply<DefaultMockMvcBuilder>(springSecurity()).build()
        tenant = tenants.save(TenantEntity(slug = "iso-${UUID.randomUUID()}", name = "ISO 15118"))
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
            StationEntity(StationKey(tenant.id, "PNC-1"), "Plug and Charge", "unused", connected = true).apply {
                ocppProtocol = "2.0.1"
                ocppVersion = "2.0.1"
                lastSeen = Instant.now()
            }
        )
        connectors.save(ConnectorEntity(ConnectorKey(tenant.id, "PNC-1", 1), maximumPowerKw = BigDecimal(22)))
        chargingTokens.save(ChargingTokenEntity(ChargingTokenKey(tenant.id, "FRCODC123456789")).apply { type = "EMAID" })
    }

    private fun MockHttpServletRequestBuilder.as_(user: UserEntity) =
        header(HttpHeaders.AUTHORIZATION, "Bearer ${accounts.issue(requireNotNull(accounts.findUser(user.id)))}")

    private fun call(action: String, payload: Map<String, Any?>): JsonNode =
        db.decode(ocpp.call(tenant.id, "PNC-1", UUID.randomUUID().toString(), action, db.json.valueToTree(payload), "c", OcppVersion.V201))[2]

    private fun claim() = queue.claim(StationIdentity(tenant.id, "PNC-1"), OcppVersion.V201)

    private fun complete(id: UUID, result: Map<String, Any?>) =
        queue.complete(StationIdentity(tenant.id, "PNC-1"), db.json.valueToTree(listOf(3, id.toString(), result)))

    private fun hash(serial: BigInteger) =
        mapOf(
            "hashAlgorithm" to "SHA256",
            "issuerNameHash" to MessageDigest.getInstance("SHA-256").digest(material.mo.certificate.subjectX500Principal.encoded).toHex(),
            "issuerKeyHash" to MessageDigest.getInstance("SHA-256").digest(material.mo.certificate.publicKey.encoded).toHex(),
            "serialNumber" to serial.toString(16),
            "responderURL" to "http://127.0.0.1:${server.address.port}/ocsp",
        )

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private fun authorize(token: String, extra: Map<String, Any?>) =
        call("Authorize", mapOf("idToken" to mapOf("idToken" to token, "type" to "eMAID")) + extra)

    @Test
    fun `une demande de certificat V2G est signee et envoyee a la borne`() {
        val response = call("SignCertificate", mapOf("csr" to TestPki.csr("CN=FRCODE0001,O=Test", TestPki.keys()), "certificateType" to "V2GCertificate"))
        assertEquals("Accepted", response.path("status").asText())

        val command = claim().single()
        val wire = translator.outbound(command, OcppVersion.V201)
        assertEquals("CertificateSigned", wire.action)
        assertEquals("V2GCertificate", wire.payload.path("certificateType").asText())
        assertTrue(wire.payload.path("certificateChain").asText().contains(TestPki.pem(material.cpo.certificate).trim()))
        complete(command.id, mapOf("status" to "Accepted"))

        val station = stations.findById(StationKey(tenant.id, "PNC-1")).orElseThrow()
        assertTrue(station.v2gCertificateSubject!!.contains("FRCODE0001"))
        assertNotNull(station.v2gCertificateExpiresAt)
        assertNull(station.certificateFingerprint)
    }

    @Test
    fun `le certificat de contrat decide de l autorisation Plug and Charge`() {
        val valid = TestPki.pem(TestPki.issue("CN=FRCODC123456789", TestPki.keys(), material.mo))
        authorize("FRCODC123456789", mapOf("certificate" to valid)).let {
            assertEquals("Accepted", it.path("idTokenInfo").path("status").asText())
            assertEquals("Accepted", it.path("certificateStatus").asText())
        }
        authorize("FRCODC999999999", mapOf("certificate" to valid)).let {
            assertEquals("Invalid", it.path("idTokenInfo").path("status").asText())
            assertEquals("ContractCancelled", it.path("certificateStatus").asText())
        }
        val expired =
            TestPki.pem(
                TestPki.issue(
                    "CN=FRCODC123456789",
                    TestPki.keys(),
                    material.mo,
                    from = Instant.now().minus(Duration.ofDays(10)),
                    until = Instant.now().minus(Duration.ofDays(1)),
                )
            )
        authorize("FRCODC123456789", mapOf("certificate" to expired)).let {
            assertEquals("Expired", it.path("idTokenInfo").path("status").asText())
            assertEquals("CertificateExpired", it.path("certificateStatus").asText())
        }
        val stranger = TestPki.pem(TestPki.issue("CN=FRCODC123456789", TestPki.keys(), TestPki.authority("CN=Inconnue")))
        authorize("FRCODC123456789", mapOf("certificate" to stranger)).let {
            assertEquals("Invalid", it.path("idTokenInfo").path("status").asText())
            assertEquals("CertChainError", it.path("certificateStatus").asText())
        }
        authorize("FRCODC123456789", mapOf("iso15118CertificateHashData" to listOf(hash(revokedSerial)))).let {
            assertEquals("Invalid", it.path("idTokenInfo").path("status").asText())
            assertEquals("CertificateRevoked", it.path("certificateStatus").asText())
        }
        authorize("FRCODC123456789", mapOf("iso15118CertificateHashData" to listOf(hash(BigInteger.valueOf(42))))).let {
            assertEquals("Accepted", it.path("idTokenInfo").path("status").asText())
            assertEquals("Accepted", it.path("certificateStatus").asText())
        }
    }

    @Test
    fun `le statut OCSP est obtenu aupres du repondeur`() {
        val response = call("GetCertificateStatus", mapOf("ocspRequestData" to hash(BigInteger.valueOf(42))))
        assertEquals("Accepted", response.path("status").asText())
        val parsed = OCSPResp(Base64.getDecoder().decode(response.path("ocspResult").asText()))
        assertEquals(OCSPResp.SUCCESSFUL, parsed.status)

        val unreachable = call("GetCertificateStatus", mapOf("ocspRequestData" to hash(BigInteger.ONE) + ("responderURL" to "ftp://example.test")))
        assertEquals("Failed", unreachable.path("status").asText())
    }

    @Test
    fun `l installation d un certificat de contrat est relayee au service de contrats`() {
        val response =
            call("Get15118EVCertificate", mapOf("iso15118SchemaVersion" to "urn:iso:15118:2:2013:MsgDef", "action" to "Install", "exiRequest" to "RVhJLVJFUVVFU1Q="))
        assertEquals("Accepted", response.path("status").asText())
        assertEquals("RVhJLVJFU1BPTlNF", response.path("exiResponse").asText())
        val forwarded = db.decode(contractBody.get())
        assertEquals("PNC-1", forwarded.path("stationId").asText())
        assertEquals("RVhJLVJFUVVFU1Q=", forwarded.path("exiRequest").asText())
        assertEquals("Install", forwarded.path("action").asText())
    }

    @Test
    fun `les besoins du vehicule recoivent un profil de recharge de transaction`() {
        val needs =
            mapOf(
                "evseId" to 1,
                "chargingNeeds" to
                    mapOf(
                        "requestedEnergyTransfer" to "AC_three_phase",
                        "acChargingParameters" to mapOf("energyAmount" to 20000, "evMinCurrent" to 6, "evMaxCurrent" to 16, "evMaxVoltage" to 400),
                    ),
            )
        assertEquals("Rejected", call("NotifyEVChargingNeeds", needs).path("status").asText())

        call(
            "TransactionEvent",
            mapOf(
                "eventType" to "Started",
                "timestamp" to Instant.now().toString(),
                "triggerReason" to "Authorized",
                "seqNo" to 0,
                "transactionInfo" to mapOf("transactionId" to "PNC-TX"),
                "evse" to mapOf("id" to 1, "connectorId" to 1),
                "idToken" to mapOf("idToken" to "FRCODC123456789", "type" to "eMAID"),
            ),
        )
        assertEquals("Accepted", call("NotifyEVChargingNeeds", needs).path("status").asText())
        val wire = translator.outbound(claim().single(), OcppVersion.V201)
        assertEquals("SetChargingProfile", wire.action)
        val profile = wire.payload.path("chargingProfile")
        assertEquals("TxProfile", profile.path("chargingProfilePurpose").asText())
        assertEquals("PNC-TX", profile.path("transactionId").asText())
        val schedule = profile.path("chargingSchedule").path(0)
        assertEquals("A", schedule.path("chargingRateUnit").asText())
        assertEquals(0, BigDecimal(16).compareTo(schedule.path("chargingSchedulePeriod").path(0).path("limit").decimalValue()))
        assertEquals(3, schedule.path("chargingSchedulePeriod").path(0).path("numberPhases").intValue())
    }

    @Test
    fun `l administrateur installe les racines V2G et MO depuis le dossier du serveur`() {
        mvc.perform(get("/api/v1/stations/security/capabilities").as_(admin))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.iso15118.enabled").value(true))
            .andExpect(jsonPath("$.iso15118.cpo_chain[0].subject").value(material.cpo.certificate.subjectX500Principal.name))
            .andExpect(jsonPath("$.iso15118.contract_service").value(true))

        mvc.perform(post("/api/v1/stations/PNC-1/iso15118/install-roots").with(csrf()).as_(admin))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
        val first = translator.outbound(claim().single(), OcppVersion.V201)
        assertEquals("InstallCertificate", first.action)
        assertEquals("V2GRootCertificate", first.payload.path("certificateType").asText())

        mvc.perform(post("/api/v1/stations/PNC-1/iso15118/coffee").with(csrf()).as_(admin)).andExpect(status().isBadRequest)
    }
}
