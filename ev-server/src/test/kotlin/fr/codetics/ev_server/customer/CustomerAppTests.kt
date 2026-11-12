// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.customer

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.models.entities.ChargingTokenEntity
import fr.codetics.ev_server.models.entities.ChargingTokenKey
import fr.codetics.ev_server.models.entities.PublicEvseEntity
import fr.codetics.ev_server.models.entities.PushDeviceEntity
import fr.codetics.ev_server.models.entities.PushMessageEntity
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.entities.TenantEntity
import fr.codetics.ev_server.models.entities.UserEntity
import fr.codetics.ev_server.models.people.UserRole
import fr.codetics.ev_server.ocpp.OcppService
import fr.codetics.ev_server.repositories.ChargingTokenRepository
import fr.codetics.ev_server.repositories.PublicEvseRepository
import fr.codetics.ev_server.repositories.PushDeviceRepository
import fr.codetics.ev_server.repositories.PushMessageRepository
import fr.codetics.ev_server.repositories.StationPhotoRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.repositories.UserRepository
import fr.codetics.ev_server.security.AccountService
import fr.codetics.ev_server.services.notifications.PushGateway
import fr.codetics.ev_server.services.notifications.PushResult
import fr.codetics.ev_server.services.notifications.PushService
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
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

class RecordingPushGateway : PushGateway {
    override val configured = true
    val sent = ConcurrentHashMap<String, MutableList<PushMessageEntity>>()
    val results = ConcurrentHashMap<String, PushResult>()

    override fun send(device: PushDeviceEntity, message: PushMessageEntity): PushResult {
        sent.computeIfAbsent(device.token) { java.util.concurrent.CopyOnWriteArrayList() }.add(message)
        return results[device.token] ?: PushResult.DELIVERED
    }
}

@TestConfiguration(proxyBeanMethods = false)
class PushTestConfiguration {
    @Bean @Primary fun recordingPushGateway() = RecordingPushGateway()
}

@ActiveProfiles("local")
@SpringBootTest(properties = ["app.bootstrap.email=", "app.mail-enabled=false"])
@Import(PushTestConfiguration::class)
class CustomerAppTests
@Autowired
constructor(
    private val context: WebApplicationContext,
    private val db: Database,
    private val tenants: TenantRepository,
    private val users: UserRepository,
    private val stations: StationRepository,
    private val tokens: ChargingTokenRepository,
    private val evses: PublicEvseRepository,
    private val devices: PushDeviceRepository,
    private val messages: PushMessageRepository,
    private val photos: StationPhotoRepository,
    private val passwords: PasswordEncoder,
    private val accounts: AccountService,
    private val ocpp: OcppService,
    private val push: PushService,
    private val gateway: RecordingPushGateway,
) {
    private lateinit var mvc: MockMvc
    private lateinit var tenant: TenantEntity
    private lateinit var admin: UserEntity
    private lateinit var driver: UserEntity
    private lateinit var other: UserEntity

    private val password = "a-long-password-for-testing"

    @BeforeEach
    fun prepare() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply<DefaultMockMvcBuilder>(springSecurity()).build()
        tenant = tenants.save(TenantEntity(slug = "app-${UUID.randomUUID()}", name = "Réseau App"))
        admin = account(UserRole.ADMIN)
        driver = account(UserRole.DRIVER)
        other = account(UserRole.DRIVER)
    }

    private fun account(role: UserRole) =
        users.save(
            UserEntity(
                tenantId = tenant.id,
                email = "${role.name.lowercase()}-${UUID.randomUUID()}@example.test",
                displayName = "${role.name} test",
                passwordHash = passwords.encode(password),
                role = role,
                emailVerified = true,
            )
        )

    private fun MockHttpServletRequestBuilder.as_(user: UserEntity) =
        with(csrf()).header(HttpHeaders.AUTHORIZATION, "Bearer ${accounts.issue(requireNotNull(accounts.findUser(user.id)))}")

    private fun body(builder: MockHttpServletRequestBuilder) =
        db.decode(mvc.perform(builder).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun record(kind: String, name: String, data: Map<String, Any?>): UUID {
        val result =
            body(post("/api/v1/records/$kind").as_(admin).contentType(MediaType.APPLICATION_JSON).content(db.encode(mapOf("name" to name, "data" to data))))
        return UUID.fromString(result.path("id").asText())
    }

    private fun area(accessControl: Boolean, public: Boolean = false): Pair<UUID, UUID> {
        val company = record("companies", "Codetics", mapOf("country" to "FR"))
        val site =
            record(
                "sites",
                "Gare",
                mapOf(
                    "companyId" to company.toString(),
                    "address" to "1 place de la Gare",
                    "city" to "Lyon",
                    "postalCode" to "69002",
                    "country" to "FR",
                    "latitude" to 45.76,
                    "longitude" to 4.83,
                    "accessControl" to accessControl,
                    "public" to public,
                ),
            )
        return site to record("site-areas", "Parking", mapOf("siteId" to site.toString()))
    }

    private fun station(id: String, area: UUID? = null, public: Boolean = false) =
        stations.save(
            StationEntity(
                StationKey(tenant.id, id),
                "Borne $id",
                "secret-hash",
                connected = true,
                siteAreaId = area,
                publicStation = public,
            )
        )

    private fun call(station: String, action: String, payload: Map<String, Any?>) =
        db.decode(ocpp.call(tenant.id, station, UUID.randomUUID().toString(), action, db.json.valueToTree(payload)))[2]

    private fun connector(station: String, status: String = "Available") {
        call(
            station,
            "StatusNotification",
            mapOf("connectorId" to 1, "status" to status, "errorCode" to "NoError"),
        )
    }

    @Test
    fun `une session applicative est longue et authentifie par jeton porteur`() {
        val session =
            body(
                post("/api/v1/auth/app/signin")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(db.encode(mapOf("tenant" to tenant.slug, "email" to driver.email, "password" to password)))
            )
        val token = session.path("token").asText()
        assertEquals(driver.id.toString(), session.path("user").path("id").asText())
        val expires = java.time.Instant.parse(session.path("expires_at").asText())
        assertTrue(expires.isAfter(java.time.Instant.now().plus(java.time.Duration.ofDays(29))))

        mvc.perform(
            post("/api/v1/account/devices")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(db.encode(mapOf("token" to "ab".repeat(32), "environment" to "sandbox")))
        ).andExpect(status().isOk)

        mvc.perform(
            post("/api/v1/auth/app/signin")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(db.encode(mapOf("tenant" to tenant.slug, "email" to driver.email, "password" to "wrong")))
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `une requete par cookie reste protegee par CSRF`() {
        val cookie = jakarta.servlet.http.Cookie("EV_SESSION", accounts.issue(requireNotNull(accounts.findUser(driver.id))))
        mvc.perform(
            post("/api/v1/account/devices")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(db.encode(mapOf("token" to "cd".repeat(32))))
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `un conducteur ne voit que les bornes accessibles sans donnee d autrui`() {
        val (restrictedSite, restricted) = area(accessControl = true)
        val (_, open) = area(accessControl = false)
        station("OPEN", open)
        station("LOCKED", restricted)
        connector("OPEN", "Charging")
        tokens.save(ChargingTokenEntity(ChargingTokenKey(tenant.id, "OTHER"), other.id))
        call(
            "OPEN",
            "StartTransaction",
            mapOf("connectorId" to 1, "idTag" to "OTHER", "meterStart" to 0, "timestamp" to java.time.Instant.now().toString()),
        )

        val list = body(get("/api/v1/account/stations").as_(driver))
        assertEquals(listOf("OPEN"), list.toList().map { it.path("id").asText() })
        val row = list[0]
        assertEquals(45.76, row.path("latitude").asDouble(), 0.001)
        assertEquals("Gare", row.path("site_name").asText())
        val plug = row.path("connectors")[0]
        assertEquals("Charging", plug.path("status").asText())
        assertEquals("FR*COD*EOPEN1", plug.path("evse_id").asText())
        assertTrue(plug.path("current_user_name").isNull || plug.path("current_user_name").isMissingNode)
        assertTrue(plug.path("current_transaction_id").isNull || plug.path("current_transaction_id").isMissingNode)
        assertFalse(row.has("pinned_certificate_fingerprint"))

        val mine = body(get("/api/v1/account/stations").as_(other))[0].path("connectors")[0]
        assertTrue(mine.path("current_transaction_id").isNumber)

        mvc.perform(get("/api/v1/account/stations/LOCKED").as_(driver)).andExpect(status().isNotFound)
        mvc.perform(get("/api/v1/stations").as_(driver)).andExpect(status().isForbidden)

        mvc.perform(
            put("/api/v1/sites/$restrictedSite/users")
                .as_(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(db.encode(listOf(mapOf("userId" to driver.id.toString()))))
        ).andExpect(status().isOk)
        assertEquals(2, body(get("/api/v1/account/stations").as_(driver)).size())
    }

    @Test
    fun `la liste publique n expose que les bornes publiques`() {
        val (_, publicArea) = area(accessControl = false, public = true)
        val (_, privateArea) = area(accessControl = false)
        station("PUBLIC-SITE", publicArea)
        station("PUBLIC-FLAG", privateArea, public = true)
        station("PRIVATE", privateArea)
        station("QR", privateArea)
        connector("QR")
        evses.save(
            PublicEvseEntity(tenantId = tenant.id).apply {
                name = "Point QR"
                stationId = "QR"
                connectorId = 1
                pricingDefinitionId = UUID.randomUUID()
                published = true
            }
        )

        val list = body(get("/api/v1/public/workspaces/${tenant.slug}/stations"))
        assertEquals(setOf("PUBLIC-SITE", "PUBLIC-FLAG", "QR"), list.toList().map { it.path("id").asText() }.toSet())
        val qr = list.toList().first { it.path("id").asText() == "QR" }
        assertEquals("Point QR", qr.path("public_evses")[0].path("name").asText())
        assertEquals(tenant.slug, qr.path("public_evses")[0].path("workspace_slug").asText())
        mvc.perform(get("/api/v1/public/workspaces/${tenant.slug}/stations/PRIVATE")).andExpect(status().isNotFound)
        mvc.perform(get("/api/v1/public/workspaces/inconnu-${UUID.randomUUID()}/stations")).andExpect(status().isNotFound)
    }

    @Test
    fun `les photos sont validees et supprimables par leur auteur ou l exploitant`() {
        val (_, open) = area(accessControl = false)
        station("PHOTO", open)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(2 * 1024 * 1024) { 7 }

        mvc.perform(
            post("/api/v1/account/stations/PHOTO/photos").as_(driver).contentType(MediaType.IMAGE_JPEG).content(byteArrayOf(1, 2, 3, 4))
        ).andExpect(status().isBadRequest)

        val created = body(post("/api/v1/account/stations/PHOTO/photos").as_(driver).contentType(MediaType.IMAGE_JPEG).content(jpeg))
        val id = created.path("id").asText()
        assertTrue(created.path("mine").asBoolean())
        assertEquals("/api/v1/account/stations/PHOTO/photos/$id", created.path("path").asText())

        val seen = body(get("/api/v1/account/stations/PHOTO/photos").as_(other))
        assertFalse(seen[0].path("mine").asBoolean())
        assertFalse(seen[0].has("user_id"))
        assertEquals(1, body(get("/api/v1/account/stations/PHOTO").as_(other)).path("photo_count").asInt())

        val content = mvc.perform(get("/api/v1/account/stations/PHOTO/photos/$id").as_(other)).andExpect(status().isOk).andReturn().response
        assertArrayEquals(jpeg, content.contentAsByteArray)
        assertEquals("nosniff", content.getHeader("X-Content-Type-Options"))

        mvc.perform(get("/api/v1/account/stations/PHOTO/photos/$id")).andExpect(status().isUnauthorized)
        mvc.perform(delete("/api/v1/account/stations/PHOTO/photos/$id").as_(other)).andExpect(status().isForbidden)
        mvc.perform(delete("/api/v1/account/stations/PHOTO/photos/$id").as_(driver)).andExpect(status().isOk)
        assertEquals(0, photos.countByTenantIdAndStationId(tenant.id, "PHOTO"))

        mvc.perform(
            post("/api/v1/account/stations/PHOTO/photos").as_(driver).contentType(MediaType.IMAGE_JPEG).content(jpeg + ByteArray(4 * 1024 * 1024))
        ).andExpect(status().isBadRequest)

        val again = body(post("/api/v1/account/stations/PHOTO/photos").as_(driver).contentType(MediaType.IMAGE_JPEG).content(jpeg)).path("id").asText()
        mvc.perform(delete("/api/v1/account/stations/PHOTO/photos/$again").as_(admin)).andExpect(status().isOk)
    }

    @Test
    fun `la fin de session envoie une notification a l appareil du conducteur`() {
        val (_, open) = area(accessControl = false)
        station("PUSH", open)
        connector("PUSH")
        tokens.save(ChargingTokenEntity(ChargingTokenKey(tenant.id, "PUSHER"), driver.id))
        val token = UUID.randomUUID().toString().replace("-", "") + "ab"
        val dead = UUID.randomUUID().toString().replace("-", "") + "cd"
        listOf(token, dead).forEach { device ->
            mvc.perform(
                post("/api/v1/account/devices")
                    .as_(driver)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(db.encode(mapOf("token" to device, "environment" to "sandbox", "locale" to "fr")))
            ).andExpect(status().isOk)
        }
        gateway.results[dead] = PushResult.INVALID_DEVICE

        val started =
            call(
                "PUSH",
                "StartTransaction",
                mapOf("connectorId" to 1, "idTag" to "PUSHER", "meterStart" to 0, "timestamp" to java.time.Instant.now().toString()),
            )
        val id = started.path("transactionId").asInt()
        call(
            "PUSH",
            "StopTransaction",
            mapOf("transactionId" to id, "meterStop" to 5000, "timestamp" to java.time.Instant.now().toString(), "idTag" to "PUSHER"),
        )
        push.deliver()

        val received = gateway.sent[token].orEmpty()
        assertEquals(setOf("SESSION_STARTED", "END_OF_SESSION"), received.map { it.type }.toSet())
        assertTrue(received.all { it.transactionId == id })
        assertFalse(devices.existsById(dead))
        assertTrue(devices.existsById(token))
        assertTrue(messages.findAll().filter { it.userId == driver.id }.all { it.sentAt != null && it.body.isEmpty() })
    }

    @Test
    fun `les preferences separent courriel et notification`() {
        val (_, open) = area(accessControl = false)
        station("PREF", open)
        connector("PREF")
        tokens.save(ChargingTokenEntity(ChargingTokenKey(tenant.id, "PREFS"), driver.id))
        val device = UUID.randomUUID().toString().replace("-", "") + "ef"
        mvc.perform(
            post("/api/v1/account/devices")
                .as_(driver)
                .contentType(MediaType.APPLICATION_JSON)
                .content(db.encode(mapOf("token" to device)))
        ).andExpect(status().isOk)
        val saved =
            body(
                put("/api/v1/account/notifications")
                    .as_(driver)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(db.encode(mapOf("email" to false, "push" to true, "types" to emptyMap<String, Boolean>())))
            )
        assertFalse(saved.path("email").asBoolean())
        assertTrue(saved.path("push").asBoolean())

        call(
            "PREF",
            "StartTransaction",
            mapOf("connectorId" to 1, "idTag" to "PREFS", "meterStart" to 0, "timestamp" to java.time.Instant.now().toString()),
        )
        push.deliver()
        assertEquals(listOf("SESSION_STARTED"), gateway.sent[device].orEmpty().map { it.type })

        val listed = body(get("/api/v1/notifications").as_(driver)).path("items")
        assertEquals("PUSH", listed[0].path("channel").asText())
    }

    @Test
    fun `un conducteur peut supprimer son compte sauf pendant une session`() {
        val (_, open) = area(accessControl = false)
        station("DEL", open)
        connector("DEL")
        tokens.save(ChargingTokenEntity(ChargingTokenKey(tenant.id, "DELETED"), driver.id))
        val started =
            call(
                "DEL",
                "StartTransaction",
                mapOf("connectorId" to 1, "idTag" to "DELETED", "meterStart" to 0, "timestamp" to java.time.Instant.now().toString()),
            )
        val bearer = accounts.issue(requireNotNull(accounts.findUser(driver.id)))
        mvc.perform(delete("/api/v1/account").with(csrf()).header(HttpHeaders.AUTHORIZATION, "Bearer $bearer")).andExpect(status().isConflict)
        call(
            "DEL",
            "StopTransaction",
            mapOf("transactionId" to started.path("transactionId").asInt(), "meterStop" to 10, "timestamp" to java.time.Instant.now().toString(), "idTag" to "DELETED"),
        )
        val device = UUID.randomUUID().toString().replace("-", "") + "aa"
        mvc.perform(
            post("/api/v1/account/devices").with(csrf()).header(HttpHeaders.AUTHORIZATION, "Bearer $bearer").contentType(MediaType.APPLICATION_JSON).content(db.encode(mapOf("token" to device)))
        ).andExpect(status().isOk)

        mvc.perform(delete("/api/v1/account").with(csrf()).header(HttpHeaders.AUTHORIZATION, "Bearer $bearer")).andExpect(status().isOk)

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer $bearer")).andExpect(status().isUnauthorized)
        val erased = users.findById(driver.id).orElseThrow()
        assertNotNull(erased.deletedAt)
        assertFalse(erased.email.contains("example.test"))
        assertFalse(devices.existsById(device))
        val token = tokens.findById(ChargingTokenKey(tenant.id, "DELETED")).orElseThrow()
        assertNull(token.userId)
        assertFalse(token.enabled)

        mvc.perform(delete("/api/v1/account").as_(admin)).andExpect(status().isBadRequest)
    }

    @Test
    fun `la configuration mobile et le catalogue sont disponibles pour l application`() {
        val config = body(get("/api/v1/public/mobile/configuration"))
        assertEquals("ev-supervision-lite", config.path("server").asText())
        assertEquals(1, config.path("api_version").asInt())
        assertTrue(config.path("push_enabled").asBoolean())
        assertFalse(config.path("wallet_enabled").asBoolean())

        record("car-catalog", "Renault Zoé", mapOf("make" to "Renault", "model" to "Zoé"))
        val catalog = body(get("/api/v1/account/car-catalog").as_(driver))
        assertEquals("Renault", catalog.path("items")[0].path("data").path("make").asText())
    }

    @Test
    fun `sans certificat le passe Wallet est indisponible`() {
        tokens.save(ChargingTokenEntity(ChargingTokenKey(tenant.id, "NOWALLET"), driver.id))
        mvc.perform(get("/api/v1/account/tokens/NOWALLET/wallet-pass").as_(driver)).andExpect(status().isServiceUnavailable)
        mvc.perform(get("/api/v1/account/tokens/NOWALLET/wallet-pass").as_(other)).andExpect(status().isNotFound)
    }
}
