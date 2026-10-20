package fr.codetics.ev_server

import fr.codetics.ev_server.core.Csv
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.models.entities.ChargingTokenEntity
import fr.codetics.ev_server.models.entities.ChargingTokenKey
import fr.codetics.ev_server.models.entities.ConnectorKey
import fr.codetics.ev_server.models.entities.OcpiObjectEntity
import fr.codetics.ev_server.models.entities.OcpiObjectKey
import fr.codetics.ev_server.models.entities.PartnerEntity
import fr.codetics.ev_server.models.entities.RegistrationTokenEntity
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.entities.TenantEntity
import fr.codetics.ev_server.models.entities.UserEntity
import fr.codetics.ev_server.models.people.UserRole
import fr.codetics.ev_server.ocpi.OcpiPublisher
import fr.codetics.ev_server.ocpp.OcppService
import fr.codetics.ev_server.repositories.ChargingTokenRepository
import fr.codetics.ev_server.repositories.ConnectorRepository
import fr.codetics.ev_server.repositories.NotificationRepository
import fr.codetics.ev_server.repositories.OcpiObjectRepository
import fr.codetics.ev_server.repositories.PartnerRepository
import fr.codetics.ev_server.repositories.RegistrationTokenRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.repositories.TransactionRepository
import fr.codetics.ev_server.repositories.UserRepository
import fr.codetics.ev_server.security.AccountService
import fr.codetics.ev_server.services.stations.RegistrationTokenService
import fr.codetics.ev_server.core.randomToken
import fr.codetics.ev_server.core.sha256
import java.time.Instant
import java.time.temporal.ChronoUnit
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
class SupervisionFeatureTests
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
    private val notifications: NotificationRepository,
    private val partners: PartnerRepository,
    private val objects: OcpiObjectRepository,
    private val registrationTokens: RegistrationTokenRepository,
    private val registrations: RegistrationTokenService,
    private val publisher: OcpiPublisher,
    private val passwords: PasswordEncoder,
    private val accounts: AccountService,
    private val ocpp: OcppService,
) {
    private lateinit var mvc: MockMvc
    private lateinit var tenant: TenantEntity
    private lateinit var admin: UserEntity
    private lateinit var driver: UserEntity

    @BeforeEach
    fun prepare() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply<DefaultMockMvcBuilder>(springSecurity()).build()
        tenant = tenants.save(TenantEntity(slug = "feature-${UUID.randomUUID()}", name = "Feature network"))
        admin = account("ADMIN")
        driver = account("DRIVER")
        stations.save(StationEntity(StationKey(tenant.id, "CS-1"), "Borne 1", "unused-test-hash", connected = true))
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

    private fun record(kind: String, name: String, data: Map<String, Any?>): UUID {
        val result =
            mvc.perform(json(post("/api/v1/records/$kind"), admin, mapOf("name" to name, "data" to data)))
                .andExpect(status().isOk)
                .andReturn()
        return UUID.fromString(db.decode(result.response.contentAsString).path("id").asText())
    }

    private fun call(action: String, payload: Map<String, Any?>, station: String = "CS-1") =
        db.decode(ocpp.call(tenant.id, station, UUID.randomUUID().toString(), action, db.json.valueToTree(payload)))[2]

    private fun organization(accessControl: Boolean = false, public: Boolean = false): Pair<UUID, UUID> {
        val company = record("companies", "Codetics", mapOf("country" to "FR"))
        val site =
            record(
                "sites",
                "Atelier",
                mapOf(
                    "companyId" to company.toString(),
                    "address" to "1 rue de Paris",
                    "city" to "Paris",
                    "postalCode" to "75001",
                    "country" to "FR",
                    "latitude" to 48.85,
                    "longitude" to 2.35,
                    "accessControl" to accessControl,
                    "public" to public,
                ),
            )
        val area = record("site-areas", "Parking", mapOf("siteId" to site.toString(), "maximumPowerKw" to 44))
        mvc.perform(json(put("/api/v1/stations/CS-1"), admin, mapOf("name" to "Borne 1", "siteAreaId" to area.toString())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.site_id").value(site.toString()))
        return site to area
    }

    @Test
    fun `record schemas reject unknown fields, missing references and protect parents`() {
        mvc.perform(json(post("/api/v1/records/sites"), admin, mapOf("name" to "Site", "data" to mapOf("companyId" to UUID.randomUUID().toString()))))
            .andExpect(status().isBadRequest)
        val company = record("companies", "Entreprise", emptyMap())
        mvc.perform(
                json(post("/api/v1/records/sites"), admin, mapOf("name" to "Site", "data" to mapOf("companyId" to company.toString(), "unknown" to 1)))
            )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("unknown")))
        val site = record("sites", "Site", mapOf("companyId" to company.toString()))
        mvc.perform(delete("/api/v1/records/companies/$company").with(csrf()).as_(admin)).andExpect(status().isConflict)
        mvc.perform(get("/api/v1/records/sites").param("companyId", company.toString()).as_(admin))
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].id").value(site.toString()))
    }

    @Test
    fun `a completed session is priced, measured and listed with filters`() {
        val (site, _) = organization()
        record(
            "pricing-definitions",
            "Standard",
            mapOf("currency" to "EUR", "energyPrice" to 0.40, "flatPrice" to 1, "scope" to mapOf("siteId" to site.toString())),
        )
        chargingTokens.save(ChargingTokenEntity(ChargingTokenKey(tenant.id, "RFID-1"), driver.id))
        val start = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS)
        val id =
            call("StartTransaction", mapOf("connectorId" to 1, "idTag" to "RFID-1", "meterStart" to 1000, "timestamp" to start.toString()))
                .path("transactionId").intValue()
        call(
            "MeterValues",
            mapOf(
                "connectorId" to 1,
                "transactionId" to id,
                "meterValue" to
                    listOf(
                        mapOf(
                            "timestamp" to start.plusSeconds(1800).toString(),
                            "sampledValue" to
                                listOf(
                                    mapOf("value" to "6000", "measurand" to "Energy.Active.Import.Register", "unit" to "Wh"),
                                    mapOf("value" to "11", "measurand" to "Power.Active.Import", "unit" to "kW"),
                                    mapOf("value" to "55", "measurand" to "SoC", "unit" to "Percent"),
                                ),
                        )
                    ),
            ),
        )
        mvc.perform(get("/api/v1/transactions").param("status", "active").as_(admin))
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].current_power_kw").value(11.0))
            .andExpect(jsonPath("$.items[0].state_of_charge").value(55))
            .andExpect(jsonPath("$.items[0].price").value(3.0))
        call("StopTransaction", mapOf("transactionId" to id, "meterStop" to 11000, "timestamp" to start.plusSeconds(3600).toString()))

        mvc.perform(get("/api/v1/transactions/$id").as_(driver))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.energy_kwh").value(10.0))
            .andExpect(jsonPath("$.price").value(5.0))
            .andExpect(jsonPath("$.site_id").value(site.toString()))
            .andExpect(jsonPath("$.state_of_charge_start").value(55))
        mvc.perform(get("/api/v1/transactions/$id/consumptions").as_(admin))
            .andExpect(jsonPath("$[0].energy_kwh").value(5.0))
            .andExpect(jsonPath("$[0].power_kw").value(11.0))
        mvc.perform(get("/api/v1/transactions").param("status", "completed").param("siteId", site.toString()).as_(admin))
            .andExpect(jsonPath("$.total").value(1))
        mvc.perform(get("/api/v1/statistics/consumption").param("year", start.atZone(java.time.ZoneId.of("Europe/Paris")).year.toString()).as_(admin))
            .andExpect(jsonPath("$.total").value(10.0))
        val notifications = notifications.findAllByTenantIdAndUserId(tenant.id, driver.id, org.springframework.data.domain.PageRequest.of(0, 10)).content
        assertTrue(notifications.any { it.type == "END_OF_SESSION" })
        assertTrue(notifications.any { it.type == "SESSION_STARTED" })
    }

    @Test
    fun `site access control blocks badges of users who are not assigned`() {
        val (site, _) = organization(accessControl = true)
        chargingTokens.save(ChargingTokenEntity(ChargingTokenKey(tenant.id, "RFID-2"), driver.id))
        assertEquals("Blocked", call("Authorize", mapOf("idTag" to "RFID-2")).path("idTagInfo").path("status").asText())
        mvc.perform(json(put("/api/v1/sites/$site/users"), admin, listOf(mapOf("userId" to driver.id.toString()))))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].user_id").value(driver.id.toString()))
        assertEquals("Accepted", call("Authorize", mapOf("idTag" to "RFID-2")).path("idTagInfo").path("status").asText())
    }

    @Test
    fun `stations can be deleted only without an active session and disappear from lists`() {
        chargingTokens.save(ChargingTokenEntity(ChargingTokenKey(tenant.id, "RFID-3"), driver.id))
        val id =
            call("StartTransaction", mapOf("connectorId" to 1, "idTag" to "RFID-3", "meterStart" to 0, "timestamp" to Instant.now().toString()))
                .path("transactionId").intValue()
        mvc.perform(delete("/api/v1/stations/CS-1").with(csrf()).as_(admin)).andExpect(status().isConflict)
        mvc.perform(post("/api/v1/transactions/$id/soft-stop").with(csrf()).as_(admin))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.soft_stopped").value(true))
        mvc.perform(delete("/api/v1/stations/CS-1").with(csrf()).as_(admin)).andExpect(status().isOk)
        mvc.perform(get("/api/v1/stations").as_(admin)).andExpect(jsonPath("$.total").value(0))
        mvc.perform(get("/api/v1/overview").as_(admin)).andExpect(jsonPath("$.stations.total").value(0))
    }

    @Test
    fun `deleting a user anonymises the account and frees the badges`() {
        chargingTokens.save(ChargingTokenEntity(ChargingTokenKey(tenant.id, "RFID-4"), driver.id))
        mvc.perform(delete("/api/v1/users/${driver.id}").with(csrf()).as_(admin)).andExpect(status().isOk)
        val stored = users.findById(driver.id).orElseThrow()
        assertNotNull(stored.deletedAt)
        assertFalse(stored.email.contains("example.test"))
        assertNull(chargingTokens.findById(ChargingTokenKey(tenant.id, "RFID-4")).orElseThrow().userId)
        assertNull(accounts.findUser(driver.id))
        mvc.perform(get("/api/v1/users").as_(admin)).andExpect(jsonPath("$.total").value(1))
    }

    @Test
    fun `users and badges can be imported from csv`() {
        val csv = "email;name;role;badge\nalice@example.test;Alice;DRIVER;IMP-1\nbad-email;Bob;DRIVER;\n"
        mvc.perform(post("/api/v1/users/import").with(csrf()).as_(admin).contentType("text/csv").content(csv))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.created").value(1))
            .andExpect(jsonPath("$.errors[0].line").value(3))
        val alice = requireNotNull(users.findByTenantIdAndEmail(tenant.id, "alice@example.test"))
        assertEquals(alice.id, chargingTokens.findById(ChargingTokenKey(tenant.id, "IMP-1")).orElseThrow().userId)
        val badges = "uid;visualId;description;userEmail\nIMP-2;0042;Badge parking;alice@example.test\n"
        mvc.perform(post("/api/v1/tokens/import").with(csrf()).as_(admin).contentType("text/csv").content(badges))
            .andExpect(jsonPath("$.created").value(1))
        mvc.perform(get("/api/v1/tokens").param("userId", alice.id.toString()).as_(admin)).andExpect(jsonPath("$.total").value(2))
    }

    @Test
    fun `csv exports neutralise spreadsheet formulas`() {
        val output = Csv.write(listOf("a"), sequenceOf(listOf("=HYPERLINK(\"x\")"), listOf("-12.5")))
        assertTrue(output.contains("'=HYPERLINK"))
        assertTrue(output.contains("\r\n-12.5\r\n"))
    }

    @Test
    fun `registration tokens provision unknown stations`() {
        val raw = randomToken()
        registrationTokens.save(RegistrationTokenEntity(tenantId = tenant.id, tokenHash = sha256(raw), description = "Lot"))
        val station = registrations.register(tenant.id, "NEW-1", raw)
        assertNotNull(station)
        assertTrue(passwords.matches(raw, requireNotNull(station).secretHash))
        assertNull(registrations.register(tenant.id, "NEW-1", raw))
        assertNull(registrations.register(tenant.id, "NEW-2", randomToken()))
    }

    @Test
    fun `workspace components and logo are managed by administrators`() {
        mvc.perform(json(put("/api/v1/workspace/components"), admin, mapOf("smartCharging" to true, "billing" to true)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.components.smart_charging").value(true))
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A)
        mvc.perform(put("/api/v1/workspace/logo").with(csrf()).as_(admin).contentType("image/png").content(png))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.logo_url").exists())
        mvc.perform(put("/api/v1/workspace/logo").with(csrf()).as_(admin).contentType("image/png").content("<svg/>".toByteArray()))
            .andExpect(status().isBadRequest)
        mvc.perform(get("/api/v1/public/workspaces/${tenant.slug}/logo")).andExpect(status().isOk).andExpect(content().bytes(png))
        mvc.perform(json(put("/api/v1/workspace/components"), driver, mapOf("billing" to true))).andExpect(status().isForbidden)
    }

    @Test
    fun `roaming tokens authorize sessions and public sites are published as locations`() {
        val (site, _) = organization(public = true)
        connectors.findById(ConnectorKey(tenant.id, "CS-1", 1)).orElse(null)
            ?: call("StatusNotification", mapOf("connectorId" to 1, "status" to "Available", "errorCode" to "NoError"))
        val partner =
            partners.save(
                PartnerEntity(
                    tenantId = tenant.id,
                    name = "eMSP",
                    countryCode = "DE",
                    partyId = "EMS",
                    localRole = "cpo",
                    tokenHash = sha256(randomToken()),
                    remoteUrl = "https://partner.example/ocpi",
                    registered = true,
                )
            )
        objects.save(
            OcpiObjectEntity(
                OcpiObjectKey(tenant.id, partner.id, "tokens", "RECEIVER", "DE/EMS/ROAM-1"),
                db.encode(mapOf("uid" to "ROAM-1", "valid" to true, "whitelist" to "ALWAYS", "last_updated" to Instant.now().toString())),
                Instant.now(),
            )
        )
        assertEquals("Accepted", call("Authorize", mapOf("idTag" to "ROAM-1")).path("idTagInfo").path("status").asText())
        val id =
            call("StartTransaction", mapOf("connectorId" to 1, "idTag" to "ROAM-1", "meterStart" to 0, "timestamp" to Instant.now().toString()))
                .path("transactionId").intValue()
        assertEquals(partner.id, transactions.findById(id).orElseThrow().roamingPartnerId)

        val snapshot = publisher.snapshot(tenant.id)
        val location = requireNotNull(snapshot.locations[site.toString()])
        assertEquals("FRA", location["country"])
        @Suppress("UNCHECKED_CAST")
        val evses = location["evses"] as List<Map<String, Any?>>
        assertEquals("CS-1*1", evses.single()["uid"])
    }

    @Test
    fun `logs are filtered by action`() {
        record("companies", "Journal", emptyMap())
        mvc.perform(get("/api/v1/logs").param("action", "RECORD_CREATE").as_(admin))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(1))
        mvc.perform(get("/api/v1/logs").as_(driver)).andExpect(status().isForbidden)
    }

    @Test
    fun `pricing resolution prefers the most specific scope`() {
        val (_, area) = organization()
        record("pricing-definitions", "Général", mapOf("energyPrice" to 0.30))
        record("pricing-definitions", "Zone", mapOf("energyPrice" to 0.50, "scope" to mapOf("siteAreaId" to area.toString())))
        mvc.perform(json(post("/api/v1/pricing/resolve"), admin, mapOf("stationId" to "CS-1", "energyKwh" to 10, "durationMinutes" to 60)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.definition.name").value("Zone"))
            .andExpect(jsonPath("$.preview.price").value(5.0))
    }
}
