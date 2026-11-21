package fr.codetics.ev_server.ocpp.v201

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.entities.TenantEntity
import fr.codetics.ev_server.models.entities.UserEntity
import fr.codetics.ev_server.models.people.UserRole
import fr.codetics.ev_server.repositories.UserRepository
import fr.codetics.ev_server.security.AccountService
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import fr.codetics.ev_server.repositories.StationEventRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TenantRepository
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.JsonNode

@ActiveProfiles("local")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties =
        [
            "app.bootstrap.email=",
            "app.mail-enabled=false",
            "app.ocpp.trusted-proxies=127.0.0.1/32,::1/128",
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
class Ocpp201WebSocketTests
@Autowired
constructor(
    private val db: Database,
    private val tenants: TenantRepository,
    private val stations: StationRepository,
    private val stationEvents: StationEventRepository,
    private val passwords: PasswordEncoder,
    private val users: UserRepository,
    private val accounts: AccountService,
    private val context: WebApplicationContext,
    @LocalServerPort private val port: Int,
) {
    private lateinit var tenant: TenantEntity
    private val password = "0123456789abcdef0123456789abcdef01234567"
    private val sockets = mutableListOf<WebSocket>()
    private val client = HttpClient.newHttpClient()

    private class Frames : WebSocket.Listener {
        val received = LinkedBlockingQueue<String>()
        private val buffer = StringBuilder()

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            buffer.append(data)
            if (last) {
                received.add(buffer.toString())
                buffer.setLength(0)
            }
            webSocket.request(1)
            return null
        }
    }

    private class Connection(val socket: WebSocket, val frames: Frames)

    @BeforeEach
    fun prepare() {
        tenant = tenants.save(TenantEntity(slug = "ws201-${UUID.randomUUID().toString().take(8)}", name = "WS 2.0.1"))
    }

    @AfterEach
    fun close() {
        sockets.forEach { it.abort() }
    }

    private fun station(id: String, protocol: String) =
        stations.save(
            StationEntity(StationKey(tenant.id, id), id, requireNotNull(passwords.encode(password))).apply {
                ocppProtocol = protocol
            }
        )

    private fun connect(id: String, vararg protocols: String, tls: Boolean = false): Connection? {
        val frames = Frames()
        val builder =
            client.newWebSocketBuilder()
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString("$id:$password".toByteArray()))
        if (tls) builder.header("X-Forwarded-Proto", "https")
        if (protocols.isNotEmpty()) builder.subprotocols(protocols.first(), *protocols.drop(1).toTypedArray())
        return try {
            val socket =
                builder.buildAsync(URI("ws://localhost:$port/ocpp/${tenant.slug}/$id"), frames).get(10, TimeUnit.SECONDS)
            sockets += socket
            Connection(socket, frames)
        } catch (_: Exception) {
            null
        }
    }

    private fun Connection.exchange(frame: String, id: String): JsonNode {
        socket.sendText(frame, true).get(5, TimeUnit.SECONDS)
        return awaitFrame { it[1].asText() == id && it[0].intValue() != 2 }
    }

    private fun Connection.awaitFrame(match: (JsonNode) -> Boolean): JsonNode {
        val deadline = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < deadline) {
            val text = frames.received.poll(200, TimeUnit.MILLISECONDS) ?: continue
            val frame = db.json.readTree(text)
            if (match(frame)) return frame
        }
        fail<Unit>("Trame attendue non reçue")
        throw IllegalStateException()
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
        stationEvents.findAll()
            .filter { it.tenantId == tenant.id && it.stationId == id && it.type == "CONNECTION_REJECTED" }
            .map { it.status }

    @Test
    fun `en mode automatique la borne obtient la version qu elle prefere`() {
        station("AUTO-1", "AUTO")
        val connection = requireNotNull(connect("AUTO-1", "ocpp2.0.1", "ocpp1.6"))
        assertEquals("ocpp2.0.1", connection.socket.subprotocol)
        assertEquals("2.0.1", reload("AUTO-1").ocppVersion)

        station("AUTO-2", "AUTO")
        val legacy = requireNotNull(connect("AUTO-2", "ocpp1.6", "ocpp2.0.1"))
        assertEquals("ocpp1.6", legacy.socket.subprotocol)
        assertEquals("1.6", reload("AUTO-2").ocppVersion)
    }

    @Test
    fun `la version choisie par l administrateur s impose`() {
        station("FORCED-16", "1.6")
        assertEquals("ocpp1.6", requireNotNull(connect("FORCED-16", "ocpp2.0.1", "ocpp1.6")).socket.subprotocol)

        station("FORCED-201", "2.0.1")
        assertNull(connect("FORCED-201", "ocpp1.6"))
        assertTrue("OCPP 2.0.1 non proposé par la borne" in rejections("FORCED-201"))
        assertEquals("ocpp2.0.1", requireNotNull(connect("FORCED-201", "ocpp1.6", "ocpp2.0.1")).socket.subprotocol)

        station("LEGACY", "1.6")
        assertNull(connect("LEGACY", "ocpp2.0.1"))
        assertNull(connect("LEGACY", "ocpp1.5"))
    }

    @Test
    fun `une borne 2_0_1 echange des trames OCPP 2_0_1 et recoit les commandes traduites`() {
        station("CS-WS", "2.0.1")
        val connection = requireNotNull(connect("CS-WS", "ocpp2.0.1"))
        val boot =
            connection.exchange(
                """[2,"boot-1","BootNotification",{"reason":"PowerUp","chargingStation":{"model":"M1","vendorName":"Acme"}}]""",
                "boot-1",
            )
        assertEquals(3, boot[0].intValue())
        assertEquals("Accepted", boot[2].path("status").asText())

        val report = connection.awaitFrame { it[0].intValue() == 2 && it[2].asText() == "GetBaseReport" }
        assertEquals("FullInventory", report[3].path("reportBase").asText())
        connection.socket.sendText("""[3,"${report[1].asText()}",{"status":"Accepted"}]""", true).get(5, TimeUnit.SECONDS)

        assertEquals(
            "OccurrenceConstraintViolation",
            connection.exchange("""[2,"bad-1","BootNotification",{"reason":"PowerUp"}]""", "bad-1")[2].asText(),
        )
        assertEquals(
            "FormatViolation",
            connection.exchange(
                """[2,"bad-2","BootNotification",{"reason":"PowerUp","chargingStation":{"model":"M","vendorName":"V"},"x":1}]""",
                "bad-2",
            )[2].asText(),
        )
        assertEquals("NotImplemented", connection.exchange("""[2,"bad-3","Coffee",{}]""", "bad-3")[2].asText())
        assertEquals("MessageTypeNotSupported", connection.exchange("""[7,"bad-4",{}]""", "bad-4")[2].asText())
        connection.socket.sendText("pas du json", true).get(5, TimeUnit.SECONDS)
        val unreadable = connection.awaitFrame { it[1].asText() == "-1" }
        assertEquals("RpcFrameworkError", unreadable[2].asText())
    }

    @Test
    fun `une reponse mal formee de la borne n entraine jamais de CALLERROR`() {
        station("CS-FRAME", "2.0.1")
        val connection = requireNotNull(connect("CS-FRAME", "ocpp2.0.1"))
        connection.socket.sendText("""[3,"orphan-result"]""", true).get(5, TimeUnit.SECONDS)
        connection.socket.sendText("""[4,"orphan-error","GenericError"]""", true).get(5, TimeUnit.SECONDS)
        connection.socket.sendText("""[3,"${UUID.randomUUID()}",{"status":"Accepted"}]""", true).get(5, TimeUnit.SECONDS)
        connection.socket.sendText("""[2,"frame-1","Heartbeat"]""", true).get(5, TimeUnit.SECONDS)

        val seen = mutableListOf<JsonNode>()
        val reply = connection.awaitFrame { frame -> seen.add(frame) && frame[1].asText() == "frame-1" }
        assertEquals(4, reply[0].intValue())
        assertEquals("RpcFrameworkError", reply[2].asText())
        assertTrue(seen.none { it[0].intValue() == 4 && it[1].asText() != "frame-1" }, seen.toString())

        val heartbeat = connection.exchange("""[2,"frame-2","Heartbeat",{}]""", "frame-2")
        assertEquals(3, heartbeat[0].intValue())
    }

    @Test
    fun `une borne 1_6 garde les codes d erreur OCPP 1_6`() {
        station("CS-16", "1.6")
        val connection = requireNotNull(connect("CS-16", "ocpp1.6"))
        assertEquals("OccurrenceConstraintViolation", connection.exchange("""[2,"e-1","BootNotification",{}]""", "e-1")[2].asText())
        assertEquals("ProtocolError", connection.exchange("""[7,"e-2",{}]""", "e-2")[2].asText())
        assertEquals("NotSupported", connection.exchange("""[2,"e-3","Coffee",{}]""", "e-3")[2].asText())
    }

    @Test
    fun `le profil de securite d une borne 2_0_1 est releve par profil reseau, priorite puis redemarrage`() {
        val admin =
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
        station("SEC-201", "2.0.1")
        val connection = requireNotNull(connect("SEC-201", "ocpp2.0.1", tls = true))
        assertEquals(2, reload("SEC-201").connectionSecurityProfile)

        MockMvcBuilders.webAppContextSetup(context).apply<DefaultMockMvcBuilder>(springSecurity()).build()
            .perform(
                put("/api/v1/stations/SEC-201/security")
                    .with(csrf())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${accounts.issue(requireNotNull(accounts.findUser(admin.id)))}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(db.encode(mapOf("securityProfile" to 2)))
            )
            .andExpect(status().isOk)

        val network = connection.awaitFrame { it[0].intValue() == 2 && it[2].asText() == "SetNetworkProfile" }
        assertEquals(2, network[3].path("configurationSlot").intValue())
        assertEquals(2, network[3].path("connectionData").path("securityProfile").intValue())
        assertEquals("wss://ocpp.example.test/ocpp/${tenant.slug}", network[3].path("connectionData").path("ocppCsmsUrl").asText())
        assertEquals("OCPP20", network[3].path("connectionData").path("ocppVersion").asText())
        Thread.sleep(2500)
        assertTrue(connection.frames.received.none { db.json.readTree(it)[0].intValue() == 2 })
        connection.socket.sendText("""[3,"${network[1].asText()}",{"status":"Accepted"}]""", true).get(5, TimeUnit.SECONDS)

        val priority = connection.awaitFrame { it[0].intValue() == 2 && it[2].asText() == "SetVariables" }
        val data = priority[3].path("setVariableData").path(0)
        assertEquals("NetworkConfigurationPriority", data.path("variable").path("name").asText())
        assertEquals("2,1", data.path("attributeValue").asText())
        connection.socket.sendText(
            """[3,"${priority[1].asText()}",{"setVariableResult":[{"attributeStatus":"RebootRequired","component":{"name":"OCPPCommCtrlr"},"variable":{"name":"NetworkConfigurationPriority"}}]}]""",
            true,
        ).get(5, TimeUnit.SECONDS)

        val reset = connection.awaitFrame { it[0].intValue() == 2 && it[2].asText() == "Reset" }
        assertEquals("OnIdle", reset[3].path("type").asText())
        connection.socket.sendText("""[3,"${reset[1].asText()}",{"status":"Accepted"}]""", true).get(5, TimeUnit.SECONDS)
        Thread.sleep(500)
        assertEquals(2, stations.findById(StationKey(tenant.id, "SEC-201")).orElseThrow().requestedSecurityProfile)

        connection.socket.abort()
        Thread.sleep(300)
        requireNotNull(connect("SEC-201", "ocpp2.0.1", tls = true))
        val deadline = System.currentTimeMillis() + 5000
        var upgraded = stations.findById(StationKey(tenant.id, "SEC-201")).orElseThrow()
        while (upgraded.securityProfile != 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
            upgraded = stations.findById(StationKey(tenant.id, "SEC-201")).orElseThrow()
        }
        assertEquals(2, upgraded.securityProfile)
        assertNull(upgraded.requestedSecurityProfile)
    }
}
