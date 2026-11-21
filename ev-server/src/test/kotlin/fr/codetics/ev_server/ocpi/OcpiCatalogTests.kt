package fr.codetics.ev_server.ocpi

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.randomToken
import fr.codetics.ev_server.core.sha256
import fr.codetics.ev_server.models.entities.OcpiObjectEntity
import fr.codetics.ev_server.models.entities.OcpiObjectKey
import fr.codetics.ev_server.models.entities.PartnerEntity
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.entities.TenantEntity
import fr.codetics.ev_server.models.entities.TransactionEntity
import fr.codetics.ev_server.ocpp.OcppCommandQueue
import fr.codetics.ev_server.ocpp.OcppVersion
import fr.codetics.ev_server.ocpp.StationIdentity
import fr.codetics.ev_server.repositories.CommandRepository
import fr.codetics.ev_server.repositories.OcpiObjectRepository
import fr.codetics.ev_server.repositories.OcpiOutboxRepository
import fr.codetics.ev_server.repositories.PartnerRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.repositories.TransactionRepository
import java.time.Instant
import java.util.UUID
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
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
class OcpiCatalogTests
@Autowired
constructor(
    private val context: WebApplicationContext,
    private val db: Database,
    private val tenants: TenantRepository,
    private val partners: PartnerRepository,
    private val objects: OcpiObjectRepository,
    private val outbox: OcpiOutboxRepository,
    private val stations: StationRepository,
    private val transactions: TransactionRepository,
    private val commands: CommandRepository,
    private val queue: OcppCommandQueue,
) {
    private lateinit var mvc: MockMvc
    private lateinit var tenant: TenantEntity

    @BeforeEach
    fun prepare() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply<DefaultMockMvcBuilder>(springSecurity()).build()
        tenant = tenants.save(TenantEntity(slug = "ocpi-${UUID.randomUUID().toString().take(8)}", name = "Itinérance"))
    }

    private fun partner(role: String, party: String = "CPO", registered: Boolean = true): Pair<PartnerEntity, String> {
        val token = randomToken()
        val partner =
            partners.save(
                PartnerEntity(
                    tenantId = tenant.id,
                    name = "Partenaire $party",
                    countryCode = "FR",
                    partyId = party,
                    localRole = role,
                    tokenHash = sha256(token),
                    remoteUrl = "https://partner.example/ocpi",
                    registered = registered,
                )
            )
        return partner to token
    }

    private fun MockHttpServletRequestBuilder.token(token: String) = header(HttpHeaders.AUTHORIZATION, "Token $token")

    private fun send(request: MockHttpServletRequestBuilder, token: String, body: Any? = null): ResultActions =
        mvc.perform(
            request.token(token).apply {
                if (body != null) contentType(MediaType.APPLICATION_JSON).content(db.encode(body))
            }
        )

    private fun location(updated: String, status: String = "AVAILABLE") =
        mapOf(
            "id" to "LOC1",
            "name" to "Parking",
            "last_updated" to updated,
            "evses" to
                listOf(
                    mapOf(
                        "uid" to "E1",
                        "status" to status,
                        "last_updated" to updated,
                        "connectors" to listOf(mapOf("id" to "1", "standard" to "IEC_62196_T2", "last_updated" to updated)),
                    )
                ),
        )

    @Test
    fun `les versions et modules exposes dependent du role`() {
        val (_, emsp) = partner("emsp", registered = false)
        send(get("/ocpi/emsp/versions"), emsp)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status_code").value(1000))
            .andExpect(jsonPath("$.data[0].version").value("2.1.1"))
        send(get("/ocpi/emsp/2.1.1/"), emsp)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.endpoints[*].identifier", hasItem("locations")))
            .andExpect(jsonPath("$.data.endpoints[*].identifier", hasItem("credentials")))
            .andExpect(jsonPath("$.data.endpoints[*].identifier", not(hasItem("commands"))))

        val (_, cpo) = partner("cpo", "EMS", registered = false)
        send(get("/ocpi/cpo/2.1.1"), cpo).andExpect(jsonPath("$.data.endpoints[*].identifier", hasItem("commands")))
        send(get("/ocpi/other/versions"), cpo).andExpect(status().isNotFound)
        mvc.perform(get("/ocpi/cpo/versions")).andExpect(status().isUnauthorized)
        mvc.perform(get("/ocpi/cpo/versions").header(HttpHeaders.AUTHORIZATION, "Bearer x")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `un partenaire non enregistre ne peut pas acceder aux objets`() {
        val (_, token) = partner("emsp", registered = false)
        send(get("/ocpi/emsp/2.1.1/locations/FR/CPO/LOC1"), token)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status_code").value(2001))
    }

    @Test
    fun `les sites recus se creent, se modifient par niveau et refusent les mises a jour anterieures`() {
        val (_, token) = partner("emsp")
        val base = "/ocpi/emsp/2.1.1/locations/FR/CPO/LOC1"

        send(put(base), token, location("2026-10-01T10:00:00Z")).andExpect(status().isOk).andExpect(jsonPath("$.status_code").value(1000))
        send(get(base), token).andExpect(jsonPath("$.data.name").value("Parking"))
        send(get("$base/E1"), token).andExpect(jsonPath("$.data.status").value("AVAILABLE"))
        send(get("$base/E1/1"), token).andExpect(jsonPath("$.data.standard").value("IEC_62196_T2"))
        send(get("$base/E9"), token).andExpect(status().isNotFound).andExpect(jsonPath("$.status_code").value(2003))
        send(get("$base/E1/9"), token).andExpect(status().isNotFound)

        send(patch("$base/E1"), token, mapOf("status" to "CHARGING", "last_updated" to "2026-10-01T11:00:00Z")).andExpect(status().isOk)
        send(get("$base/E1"), token).andExpect(jsonPath("$.data.status").value("CHARGING"))
        send(get(base), token).andExpect(jsonPath("$.data.last_updated").value("2026-10-01T11:00:00Z"))

        send(
                put("$base/E1/1"),
                token,
                mapOf("id" to "1", "standard" to "IEC_62196_T2_COMBO", "last_updated" to "2026-10-01T12:00:00Z"),
            )
            .andExpect(status().isOk)
        send(get("$base/E1/1"), token).andExpect(jsonPath("$.data.standard").value("IEC_62196_T2_COMBO"))
        send(get("$base/E1"), token).andExpect(jsonPath("$.data.last_updated").value("2026-10-01T12:00:00Z"))

        send(patch(base), token, mapOf("name" to "Parking Nord", "last_updated" to "2026-10-01T13:00:00Z")).andExpect(status().isOk)
        send(get(base), token).andExpect(jsonPath("$.data.name").value("Parking Nord")).andExpect(jsonPath("$.data.evses[0].uid").value("E1"))

        send(put(base), token, location("2026-09-01T10:00:00Z")).andExpect(status().isConflict)
        send(put(base), token, location("2026-10-02T10:00:00Z") + mapOf("id" to "OTHER")).andExpect(status().isBadRequest)
        send(put(base), token, mapOf("id" to "LOC1")).andExpect(status().isBadRequest)
        send(patch("/ocpi/emsp/2.1.1/locations/FR/CPO/NONE"), token, mapOf("last_updated" to "2026-10-01T10:00:00Z"))
            .andExpect(status().isNotFound)
        send(put("/ocpi/emsp/2.1.1/locations/FR/CPO/NONE/E1"), token, mapOf("uid" to "E1", "last_updated" to "2026-10-01T10:00:00Z"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `les methodes et chemins non autorises sont refuses`() {
        val (_, token) = partner("emsp")
        send(put("/ocpi/emsp/2.1.1/locations/FR/CPO/LOC1"), token, location("2026-10-01T10:00:00Z"))

        send(get("/ocpi/emsp/2.1.1/locations"), token).andExpect(status().isMethodNotAllowed)
        send(delete("/ocpi/emsp/2.1.1/locations/FR/CPO/LOC1"), token).andExpect(status().isMethodNotAllowed)
        send(post("/ocpi/emsp/2.1.1/locations/FR/CPO/LOC1"), token, location("2026-10-01T10:00:00Z")).andExpect(status().isMethodNotAllowed)
        send(get("/ocpi/emsp/2.1.1/locations/FR/OTH/LOC1"), token).andExpect(status().isForbidden)
        send(get("/ocpi/emsp/2.1.1/unknown"), token).andExpect(status().isNotFound)
        send(get("/ocpi/emsp/2.1.1/locations/FR/CPO/bad%20id"), token).andExpect(status().isBadRequest)
        send(put("/ocpi/emsp/2.1.1/tokens/FR/CPO/T1"), token, mapOf("uid" to "T1", "last_updated" to "2026-10-01T10:00:00Z"))
            .andExpect(status().isMethodNotAllowed)
        send(put("/ocpi/emsp/2.1.1/cdrs/cdr-1"), token, mapOf("id" to "cdr-1")).andExpect(status().isMethodNotAllowed)
        send(post("/ocpi/emsp/2.1.1/cdrs"), token, mapOf("id" to "cdr-1")).andExpect(status().isBadRequest)
    }

    @Test
    fun `les tarifs recus peuvent etre supprimes`() {
        val (_, token) = partner("emsp")
        val path = "/ocpi/emsp/2.1.1/tariffs/FR/CPO/T1"
        send(put(path), token, mapOf("id" to "T1", "currency" to "EUR", "last_updated" to "2026-10-01T10:00:00Z")).andExpect(status().isOk)
        send(get(path), token).andExpect(jsonPath("$.data.currency").value("EUR"))
        send(delete(path), token).andExpect(status().isOk)
        send(get(path), token).andExpect(status().isNotFound)
    }

    @Test
    fun `les objets envoyes sont pagines avec les en-tetes OCPI`() {
        val (partner, token) = partner("emsp")
        (1..3).forEach { index ->
            objects.save(
                OcpiObjectEntity(
                    OcpiObjectKey(tenant.id, partner.id, "tokens", "SENDER", "TOKEN-$index"),
                    db.encode(mapOf("uid" to "TOKEN-$index", "last_updated" to "2026-10-0${index}T10:00:00Z")),
                    Instant.parse("2026-10-0${index}T10:00:00Z"),
                )
            )
        }

        send(get("/ocpi/emsp/2.1.1/tokens/?limit=2"), token)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(header().string("X-Total-Count", "3"))
            .andExpect(header().string("X-Limit", "2"))
            .andExpect(header().string("Link", containsString("offset=2")))
        send(get("/ocpi/emsp/2.1.1/tokens/?offset=2&limit=2"), token)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].uid").value("TOKEN-3"))
            .andExpect(header().doesNotExist("Link"))
        send(get("/ocpi/emsp/2.1.1/tokens/?offset=1&limit=2"), token)
            .andExpect(jsonPath("$.data[*].uid", hasItem("TOKEN-3")))
        send(get("/ocpi/emsp/2.1.1/tokens/?date_from=2026-10-02T00:00:00Z"), token).andExpect(jsonPath("$.data.length()").value(2))
        send(get("/ocpi/emsp/2.1.1/tokens/TOKEN-2"), token).andExpect(jsonPath("$.data.uid").value("TOKEN-2"))
        send(get("/ocpi/emsp/2.1.1/tokens/?limit=0"), token).andExpect(status().isBadRequest)
        send(get("/ocpi/emsp/2.1.1/tokens/?date_from=2026-10-03T00:00:00Z&date_to=2026-10-01T00:00:00Z"), token)
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `les commandes OCPI pilotent la borne et renvoient le resultat au partenaire`() {
        val (partner, token) = partner("cpo", "EMS")
        stations.save(StationEntity(StationKey(tenant.id, "CS-ROAM"), "Borne itinérance", "unused", connected = true))
        val session =
            transactions.save(
                TransactionEntity(
                    tenantId = tenant.id,
                    stationId = "CS-ROAM",
                    connectorId = 1,
                    tokenUid = "ROAM-1",
                    roamingPartnerId = partner.id,
                )
            )
        val response = "https://partner.example/ocpi/commands/42"

        send(post("/ocpi/cpo/2.1.1/commands/STOP_SESSION"), token, mapOf("response_url" to response, "session_id" to session.id.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.result").value("ACCEPTED"))
        send(post("/ocpi/cpo/2.1.1/commands/STOP_SESSION"), token, mapOf("response_url" to response, "session_id" to "999999"))
            .andExpect(jsonPath("$.data.result").value("UNKNOWN_SESSION"))
        send(
                post("/ocpi/cpo/2.1.1/commands/START_SESSION"),
                token,
                mapOf("response_url" to response, "token" to mapOf("uid" to "ROAM-1"), "location_id" to "LOC", "evse_uid" to "CS-ROAM*1"),
            )
            .andExpect(jsonPath("$.data.result").value("REJECTED"))
        send(post("/ocpi/cpo/2.1.1/commands/UNLOCK_CONNECTOR"), token, mapOf("response_url" to response, "location_id" to "LOC", "evse_uid" to "bad"))
            .andExpect(jsonPath("$.data.result").value("REJECTED"))
        send(post("/ocpi/cpo/2.1.1/commands/CANCEL_RESERVATION"), token, mapOf("response_url" to response))
            .andExpect(jsonPath("$.data.result").value("NOT_SUPPORTED"))
        send(post("/ocpi/cpo/2.1.1/commands/STOP_SESSION"), token, mapOf("response_url" to "https://attacker.example/x", "session_id" to "1"))
            .andExpect(status().isBadRequest)

        val stop = commands.findAll().single { it.tenantId == tenant.id && it.action == "RemoteStopTransaction" }
        val identity = StationIdentity(tenant.id, "CS-ROAM")
        assertTrue(queue.claim(identity, OcppVersion.V16).any { it.id == stop.id })
        queue.complete(identity, db.json.valueToTree(listOf(3, stop.id.toString(), mapOf("status" to "Accepted"))))

        val reply = outbox.findAll().single { it.partnerId == partner.id && it.module == "commands" }
        assertEquals(response, reply.url)
        assertEquals("ACCEPTED", db.decode(reply.body).path("result").asText())
    }

    @Test
    fun `les commandes sont reservees a l interface operateur`() {
        val (_, token) = partner("emsp")
        send(post("/ocpi/emsp/2.1.1/commands/STOP_SESSION"), token, mapOf("response_url" to "https://partner.example/ocpi/x"))
            .andExpect(status().isNotFound)
    }
}
