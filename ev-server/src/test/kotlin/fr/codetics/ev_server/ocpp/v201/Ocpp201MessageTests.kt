package fr.codetics.ev_server.ocpp.v201

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.InvalidRequest
import fr.codetics.ev_server.models.entities.ChargingTokenEntity
import fr.codetics.ev_server.models.entities.ChargingTokenKey
import fr.codetics.ev_server.models.entities.CommandEntity
import fr.codetics.ev_server.models.entities.ConnectorKey
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
import fr.codetics.ev_server.ocpp.OcppVersion
import fr.codetics.ev_server.ocpp.StationIdentity
import fr.codetics.ev_server.repositories.ChargingTokenRepository
import fr.codetics.ev_server.repositories.CommandRepository
import fr.codetics.ev_server.repositories.ConnectorRepository
import fr.codetics.ev_server.repositories.ReservationRepository
import fr.codetics.ev_server.repositories.StationEventRepository
import fr.codetics.ev_server.repositories.StationParameterRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.repositories.TransactionRepository
import fr.codetics.ev_server.repositories.UserRepository
import fr.codetics.ev_server.security.AccountService
import java.math.BigDecimal
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
class Ocpp201MessageTests
@Autowired
constructor(
    private val context: WebApplicationContext,
    private val db: Database,
    private val tenants: TenantRepository,
    private val users: UserRepository,
    private val stations: StationRepository,
    private val chargingTokens: ChargingTokenRepository,
    private val transactions: TransactionRepository,
    private val connectors: ConnectorRepository,
    private val stationEvents: StationEventRepository,
    private val parameters: StationParameterRepository,
    private val reservations: ReservationRepository,
    private val commands: CommandRepository,
    private val passwords: PasswordEncoder,
    private val accounts: AccountService,
    private val ocpp: OcppService,
    private val queue: OcppCommandQueue,
    private val translator: Ocpp201Commands,
) {
    private lateinit var mvc: MockMvc
    private lateinit var tenant: TenantEntity
    private lateinit var admin: UserEntity
    private val start = Instant.parse("2026-03-01T10:00:00Z")

    @BeforeEach
    fun prepare() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply<DefaultMockMvcBuilder>(springSecurity()).build()
        tenant = tenants.save(TenantEntity(slug = "ocpp201-${UUID.randomUUID()}", name = "Réseau 2.0.1"))
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
        stations.save(StationEntity(StationKey(tenant.id, "CS-16"), "Borne 1.6", "unused", connected = true))
        stations.save(
            StationEntity(StationKey(tenant.id, "CS-201"), "Borne 2.0.1", "unused", connected = true).apply {
                ocppProtocol = "2.0.1"
                ocppVersion = "2.0.1"
                lastSeen = Instant.now()
            }
        )
        chargingTokens.save(ChargingTokenEntity(ChargingTokenKey(tenant.id, "TAG-1")))
        chargingTokens.save(ChargingTokenEntity(ChargingTokenKey(tenant.id, "TAG-2")))
    }

    private fun MockHttpServletRequestBuilder.as_(user: UserEntity) =
        header(HttpHeaders.AUTHORIZATION, "Bearer ${accounts.issue(requireNotNull(accounts.findUser(user.id)))}")

    private fun call16(action: String, payload: Map<String, Any?>): JsonNode =
        db.decode(ocpp.call(tenant.id, "CS-16", UUID.randomUUID().toString(), action, db.json.valueToTree(payload)))[2]

    private fun call(action: String, payload: Map<String, Any?>, message: String = UUID.randomUUID().toString()): JsonNode =
        db.decode(
            ocpp.call(tenant.id, "CS-201", message, action, db.json.valueToTree(payload), "connection", OcppVersion.V201)
        )[2]

    private fun at(seconds: Long) = start.plusSeconds(seconds).toString()

    private fun meter(seconds: Long, vararg samples: Map<String, Any?>) =
        mapOf("timestamp" to at(seconds), "sampledValue" to samples.toList())

    private fun event(
        type: String,
        seconds: Long,
        trigger: String,
        reference: String = "TX-201",
        state: String? = null,
        token: String? = null,
        meters: List<Map<String, Any?>>? = null,
        reason: String? = null,
        seq: Int = seconds.toInt(),
    ): JsonNode =
        call(
            "TransactionEvent",
            buildMap {
                put("eventType", type)
                put("timestamp", at(seconds))
                put("triggerReason", trigger)
                put("seqNo", seq)
                put(
                    "transactionInfo",
                    buildMap {
                        put("transactionId", reference)
                        state?.let { put("chargingState", it) }
                        reason?.let { put("stoppedReason", it) }
                    },
                )
                put("evse", mapOf("id" to 1, "connectorId" to 1))
                token?.let { put("idToken", mapOf("idToken" to it, "type" to "ISO14443")) }
                meters?.let { put("meterValue", it) }
            },
        )

    private fun statuses(station: String) =
        stationEvents.findAll()
            .filter { it.tenantId == tenant.id && it.stationId == station && it.type == "STATUS" }
            .sortedBy { it.id }
            .map { it.status }

    private fun transaction(station: String): TransactionEntity =
        transactions.findAll().single { it.tenantId == tenant.id && it.stationId == station }

    private fun dispatch(
        station: String,
        action: String,
        payload: Map<String, Any?>,
        result: Map<String, Any?>,
        version: OcppVersion = OcppVersion.V201,
    ): Pair<WireCommand, CommandEntity> {
        val id = queue.command(tenant.id, station, action, db.json.valueToTree(payload))
        val identity = StationIdentity(tenant.id, station)
        val claimed = queue.claim(identity, version).single { it.id == id }
        val wire = translator.outbound(claimed, version)
        queue.complete(identity, db.json.valueToTree(listOf(3, id.toString(), result)))
        return wire to commands.findById(id).orElseThrow()
    }

    @Test
    fun `une session 2_0_1 produit les memes donnees qu une session 1_6`() {
        call16("StatusNotification", mapOf("connectorId" to 1, "status" to "Preparing", "errorCode" to "NoError", "timestamp" to at(0)))
        val started = call16("StartTransaction", mapOf("connectorId" to 1, "idTag" to "TAG-1", "meterStart" to 1000, "timestamp" to at(1)))
        call16(
            "MeterValues",
            mapOf(
                "connectorId" to 1,
                "transactionId" to started.path("transactionId").intValue(),
                "meterValue" to
                    listOf(
                        meter(
                            60,
                            mapOf("value" to "3000", "measurand" to "Energy.Active.Import.Register", "unit" to "Wh"),
                            mapOf("value" to "7000", "measurand" to "Power.Active.Import", "unit" to "W"),
                        )
                    ),
            ),
        )
        call16("StatusNotification", mapOf("connectorId" to 1, "status" to "Charging", "errorCode" to "NoError", "timestamp" to at(61)))
        call16(
            "StopTransaction",
            mapOf(
                "transactionId" to started.path("transactionId").intValue(),
                "meterStop" to 5000,
                "timestamp" to at(120),
                "reason" to "EVDisconnected",
                "transactionData" to listOf(meter(120, mapOf("value" to "5000", "context" to "Transaction.End"))),
            ),
        )
        call16("StatusNotification", mapOf("connectorId" to 1, "status" to "Finishing", "errorCode" to "NoError", "timestamp" to at(121)))

        call("StatusNotification", mapOf("timestamp" to at(0), "connectorStatus" to "Occupied", "evseId" to 1, "connectorId" to 1))
        val response =
            event(
                "Started",
                1,
                "Authorized",
                state = "EVConnected",
                token = "TAG-1",
                meters = listOf(meter(1, mapOf("value" to 1000, "context" to "Transaction.Begin"))),
            )
        assertEquals("Accepted", response.path("idTokenInfo").path("status").asText())
        event(
            "Updated",
            60,
            "MeterValuePeriodic",
            state = "Charging",
            meters =
                listOf(
                    meter(
                        60,
                        mapOf("value" to 3, "unitOfMeasure" to mapOf("unit" to "kWh")),
                        mapOf("value" to 7, "measurand" to "Power.Active.Import", "unitOfMeasure" to mapOf("unit" to "kW")),
                    )
                ),
        )
        event(
            "Ended",
            120,
            "EVDeparted",
            reason = "EVDisconnected",
            meters = listOf(meter(120, mapOf("value" to 5, "context" to "Transaction.End", "unitOfMeasure" to mapOf("unit" to "Wh", "multiplier" to 3)))),
        )

        val legacy = transaction("CS-16")
        val modern = transaction("CS-201")
        assertEquals("TX-201", modern.ocppTransactionId)
        assertNull(legacy.ocppTransactionId)
        listOf(legacy, modern).forEach { session ->
            assertEquals("TAG-1", session.tokenUid)
            assertEquals(fr.codetics.ev_server.ocpp.AuthorizationStatus.Accepted, session.authorizationStatus)
            assertEquals(0, BigDecimal(1000).compareTo(session.meterStartWh))
            assertEquals(0, BigDecimal(5000).compareTo(session.meterStopWh))
            assertEquals("EVDisconnected", session.stopReason)
            assertEquals(start.plusSeconds(1), session.startedAt)
            assertEquals(start.plusSeconds(120), session.stoppedAt)
        }
        assertEquals(0, legacy.lastPowerW!!.compareTo(modern.lastPowerW))
        assertEquals(0, legacy.lastEnergyWh!!.compareTo(modern.lastEnergyWh))
        assertEquals(legacy.lastMeterAt, modern.lastMeterAt)
        assertEquals(legacy.inactivitySeconds, modern.inactivitySeconds)
        assertEquals(legacy.chargingEndedAt, modern.chargingEndedAt)
        assertEquals(listOf("Preparing", "Charging", "Finishing"), statuses("CS-16"))
        assertEquals(statuses("CS-16"), statuses("CS-201"))
        assertEquals("Finishing", connectors.findById(ConnectorKey(tenant.id, "CS-201", 1)).orElseThrow().status)
    }

    @Test
    fun `une transaction demarree au branchement est autorisee plus tard`() {
        val first = event("Started", 0, "CablePluggedIn", reference = "TX-LATE", state = "EVConnected")
        assertFalse(first.has("idTokenInfo"))
        assertEquals("", transaction("CS-201").tokenUid)

        val authorized = event("Updated", 5, "Authorized", reference = "TX-LATE", token = "TAG-2")
        assertEquals("Accepted", authorized.path("idTokenInfo").path("status").asText())
        val session = transaction("CS-201")
        assertEquals("TAG-2", session.tokenUid)
        assertEquals(fr.codetics.ev_server.ocpp.AuthorizationStatus.Accepted, session.authorizationStatus)
        assertEquals("Preparing", connectors.findById(ConnectorKey(tenant.id, "CS-201", 1)).orElseThrow().status)
    }

    @Test
    fun `un TransactionEvent rejoue avec le meme identifiant est idempotent`() {
        val message = UUID.randomUUID().toString()
        val payload =
            mapOf(
                "eventType" to "Started",
                "timestamp" to at(0),
                "triggerReason" to "Authorized",
                "seqNo" to 0,
                "transactionInfo" to mapOf("transactionId" to "TX-RETRY"),
                "evse" to mapOf("id" to 1),
                "idToken" to mapOf("idToken" to "TAG-1", "type" to "ISO14443"),
            )
        val first = call("TransactionEvent", payload, message)
        val second = call("TransactionEvent", payload, message)
        assertEquals(first, second)
        assertEquals(1, transactions.findAll().count { it.tenantId == tenant.id && it.stationId == "CS-201" })
    }

    @Test
    fun `les messages de base 2_0_1 sont traites`() {
        val boot =
            call("BootNotification", mapOf("reason" to "PowerUp", "chargingStation" to mapOf("vendorName" to "Acme", "model" to "AC22", "firmwareVersion" to "2.4")))
        assertEquals("Accepted", boot.path("status").asText())
        assertTrue(boot.path("interval").intValue() > 0)
        val station = stations.findById(StationKey(tenant.id, "CS-201")).orElseThrow()
        assertEquals("Acme", station.vendor)
        assertEquals("AC22", station.model)
        assertEquals("2.4", station.firmwareVersion)

        assertTrue(call("Heartbeat", emptyMap()).path("currentTime").isString)
        assertEquals("Accepted", call("Authorize", mapOf("idToken" to mapOf("idToken" to "TAG-1", "type" to "ISO14443"))).path("idTokenInfo").path("status").asText())
        assertEquals("Invalid", call("Authorize", mapOf("idToken" to mapOf("idToken" to "NOPE", "type" to "ISO14443"))).path("idTokenInfo").path("status").asText())
        assertEquals("UnknownVendorId", call("DataTransfer", mapOf("vendorId" to "acme")).path("status").asText())
        assertEquals("Failed", call("Get15118EVCertificate", mapOf("iso15118SchemaVersion" to "x", "action" to "Install", "exiRequest" to "x")).path("status").asText())

        call("FirmwareStatusNotification", mapOf("status" to "Installed", "requestId" to 4))
        call("LogStatusNotification", mapOf("status" to "Uploaded", "requestId" to 5))
        val updated = stations.findById(StationKey(tenant.id, "CS-201")).orElseThrow()
        assertEquals("Installed", updated.firmwareStatus)
        assertEquals("Uploaded", updated.logStatus)
        assertEquals("Uploaded", updated.diagnosticsStatus)

        call("StatusNotification", mapOf("timestamp" to at(0), "connectorStatus" to "Faulted", "evseId" to 2, "connectorId" to 1))
        val faulted = connectors.findById(ConnectorKey(tenant.id, "CS-201", 2)).orElseThrow()
        assertEquals("Faulted", faulted.status)
        assertNotEquals("NoError", faulted.errorCode)

        assertEquals("NotImplemented", assertThrows(OcppFault::class.java) { call("Coffee", emptyMap()) }.code)
        assertEquals("NotSupported", assertThrows(OcppFault::class.java) { call("Reset", mapOf("type" to "Immediate")) }.code)
        assertEquals("OccurrenceConstraintViolation", assertThrows(OcppFault::class.java) { call("Authorize", emptyMap()) }.code)
    }

    @Test
    fun `NotifyReport alimente les parametres de la borne`() {
        call(
            "NotifyReport",
            mapOf(
                "requestId" to 1,
                "generatedAt" to at(0),
                "seqNo" to 0,
                "reportData" to
                    listOf(
                        mapOf(
                            "component" to mapOf("name" to "OCPPCommCtrlr"),
                            "variable" to mapOf("name" to "HeartbeatInterval"),
                            "variableAttribute" to listOf(mapOf("value" to "300", "mutability" to "ReadWrite")),
                        ),
                        mapOf(
                            "component" to mapOf("name" to "EVSE", "evse" to mapOf("id" to 1)),
                            "variable" to mapOf("name" to "Power"),
                            "variableAttribute" to listOf(mapOf("type" to "MaxSet", "value" to "22000"), mapOf("value" to "11000", "mutability" to "ReadOnly")),
                        ),
                        mapOf(
                            "component" to mapOf("name" to "SecurityCtrlr"),
                            "variable" to mapOf("name" to "BasicAuthPassword"),
                            "variableAttribute" to listOf(mapOf("value" to "secret", "mutability" to "WriteOnly")),
                        ),
                    ),
            ),
        )
        val heartbeat = parameters.findById(StationParameterKey(tenant.id, "CS-201", "OCPPCommCtrlr/HeartbeatInterval")).orElseThrow()
        assertEquals("300", heartbeat.value)
        assertFalse(heartbeat.readonly)
        val power = parameters.findById(StationParameterKey(tenant.id, "CS-201", "EVSE@1/Power")).orElseThrow()
        assertEquals("11000", power.value)
        assertTrue(power.readonly)
        assertNull(parameters.findById(StationParameterKey(tenant.id, "CS-201", "SecurityCtrlr/BasicAuthPassword")).orElseThrow().value)
    }

    @Test
    fun `les commandes de la plateforme sont traduites pour une borne 2_0_1`() {
        val (startWire, startCommand) =
            dispatch("CS-201", "RemoteStartTransaction", mapOf("idTag" to "TAG-1", "connectorId" to 1), mapOf("status" to "Accepted", "transactionId" to "TX-9"))
        assertEquals("RequestStartTransaction", startWire.action)
        assertEquals(1, startWire.payload.path("evseId").intValue())
        assertEquals("TAG-1", startWire.payload.path("idToken").path("idToken").asText())
        assertEquals("Central", startWire.payload.path("idToken").path("type").asText())
        assertTrue(startWire.payload.path("remoteStartId").isIntegralNumber)
        assertEquals("""{"status":"Accepted"}""", startCommand.result)
        assertEquals("2.0.1", startCommand.wireVersion)
        assertNull(startCommand.ocppVersion)

        event("Started", 0, "RemoteStart", reference = "TX-9", token = "TAG-1")
        val (stopWire, _) =
            dispatch("CS-201", "RemoteStopTransaction", mapOf("transactionId" to transaction("CS-201").id), mapOf("status" to "Accepted"))
        assertEquals("RequestStopTransaction", stopWire.action)
        assertEquals("TX-9", stopWire.payload.path("transactionId").asText())

        val (resetWire, resetCommand) = dispatch("CS-201", "Reset", mapOf("type" to "Soft"), mapOf("status" to "Scheduled"))
        assertEquals("OnIdle", resetWire.payload.path("type").asText())
        assertEquals("Accepted", db.decode(resetCommand.result!!).path("status").asText())

        val (unlockWire, unlockCommand) = dispatch("CS-201", "UnlockConnector", mapOf("connectorId" to 2), mapOf("status" to "UnknownConnector"))
        assertEquals(2, unlockWire.payload.path("evseId").intValue())
        assertEquals(1, unlockWire.payload.path("connectorId").intValue())
        assertEquals("NotSupported", db.decode(unlockCommand.result!!).path("status").asText())

        val (availabilityWire, _) = dispatch("CS-201", "ChangeAvailability", mapOf("connectorId" to 0, "type" to "Inoperative"), mapOf("status" to "Accepted"))
        assertFalse(availabilityWire.payload.has("evse"))
        assertEquals("Inoperative", availabilityWire.payload.path("operationalStatus").asText())

        val (reportWire, _) = dispatch("CS-201", "GetConfiguration", emptyMap(), mapOf("status" to "Accepted"))
        assertEquals("GetBaseReport", reportWire.action)
        assertEquals("FullInventory", reportWire.payload.path("reportBase").asText())

        val (triggerWire, _) = dispatch("CS-201", "TriggerMessage", mapOf("requestedMessage" to "DiagnosticsStatusNotification"), mapOf("status" to "Accepted"))
        assertEquals("LogStatusNotification", triggerWire.payload.path("requestedMessage").asText())
    }

    @Test
    fun `ChangeConfiguration devient SetVariables et met a jour le parametre 2_0_1`() {
        val (wire, command) =
            dispatch(
                "CS-201",
                "ChangeConfiguration",
                mapOf("key" to "HeartbeatInterval", "value" to "120"),
                mapOf(
                    "setVariableResult" to
                        listOf(
                            mapOf(
                                "attributeStatus" to "Accepted",
                                "component" to mapOf("name" to "OCPPCommCtrlr"),
                                "variable" to mapOf("name" to "HeartbeatInterval"),
                            )
                        )
                ),
            )
        assertEquals("SetVariables", wire.action)
        val data = wire.payload.path("setVariableData").path(0)
        assertEquals("OCPPCommCtrlr", data.path("component").path("name").asText())
        assertEquals("HeartbeatInterval", data.path("variable").path("name").asText())
        assertEquals("120", data.path("attributeValue").asText())
        assertEquals("Accepted", db.decode(command.result!!).path("status").asText())
        assertEquals("120", parameters.findById(StationParameterKey(tenant.id, "CS-201", "OCPPCommCtrlr/HeartbeatInterval")).orElseThrow().value)
        assertFalse(parameters.findById(StationParameterKey(tenant.id, "CS-201", "HeartbeatInterval")).isPresent)
    }

    @Test
    fun `une reservation 2_0_1 suit le meme cycle qu en 1_6`() {
        val expiry = Instant.now().plusSeconds(3600).toString()
        val (wire, _) =
            dispatch("CS-201", "ReserveNow", mapOf("connectorId" to 1, "expiryDate" to expiry, "idTag" to "TAG-1", "reservationId" to 7), mapOf("status" to "Accepted"))
        assertEquals(7, wire.payload.path("id").intValue())
        assertEquals(expiry, wire.payload.path("expiryDateTime").asText())
        assertEquals(1, wire.payload.path("evseId").intValue())
        assertEquals("ACCEPTED", reservations.findById(ReservationKey(tenant.id, "CS-201", 7)).orElseThrow().status)

        call("ReservationStatusUpdate", mapOf("reservationId" to 7, "reservationUpdateStatus" to "Expired"))
        assertEquals("EXPIRED", reservations.findById(ReservationKey(tenant.id, "CS-201", 7)).orElseThrow().status)
    }

    @Test
    fun `une borne 1_6 recoit ses commandes sans aucune traduction`() {
        val payload = mapOf("idTag" to "TAG-1", "connectorId" to 1)
        val (wire, command) = dispatch("CS-16", "RemoteStartTransaction", payload, mapOf("status" to "Accepted"), OcppVersion.V16)
        assertEquals("RemoteStartTransaction", wire.action)
        assertEquals(db.json.valueToTree<JsonNode>(payload), wire.payload)
        assertEquals("1.6", command.wireVersion)
        assertEquals("""{"status":"Accepted"}""", command.result)
    }

    @Test
    fun `les commandes sans equivalent 2_0_1 sont refusees des la demande`() {
        listOf(
            mapOf("key" to "SecurityProfile", "value" to "2"),
            mapOf("key" to "AutoChargeEnabled", "value" to "true"),
        ).forEach { payload ->
            assertThrows(InvalidRequest::class.java) {
                queue.command(tenant.id, "CS-201", "ChangeConfiguration", db.json.valueToTree(payload))
            }
        }
        assertThrows(InvalidRequest::class.java) {
            queue.command(tenant.id, "CS-201", "RemoteStopTransaction", db.json.valueToTree(mapOf("transactionId" to 999999)))
        }
    }

    @Test
    fun `les commandes natives 2_0_1 sont validees et reservees aux bornes 2_0_1`() {
        val variables =
            mapOf("getVariableData" to listOf(mapOf("component" to mapOf("name" to "OCPPCommCtrlr"), "variable" to mapOf("name" to "HeartbeatInterval"))))
        val id = queue.command(tenant.id, "CS-201", "GetVariables", db.json.valueToTree(variables), version = OcppVersion.V201)
        val claimed = queue.claim(StationIdentity(tenant.id, "CS-201"), OcppVersion.V201).single { it.id == id }
        assertEquals("2.0.1", claimed.ocppVersion)
        assertEquals("GetVariables", translator.outbound(claimed, OcppVersion.V201).action)
        assertThrows(OcppFault::class.java) { translator.outbound(claimed, OcppVersion.V16) }

        assertThrows(InvalidRequest::class.java) {
            queue.command(tenant.id, "CS-16", "GetVariables", db.json.valueToTree(variables), version = OcppVersion.V201)
        }
        assertThrows(InvalidRequest::class.java) {
            queue.command(tenant.id, "CS-201", "GetVariables", db.json.valueToTree(mapOf("getVariableData" to emptyList<Any>())), version = OcppVersion.V201)
        }
    }

    @Test
    fun `l API accepte la version OCPP a la creation et protege les variables de securite`() {
        mvc.perform(
                post("/api/v1/stations")
                    .with(csrf())
                    .as_(admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(db.encode(mapOf("id" to "NEW-201", "name" to "Nouvelle", "ocppProtocol" to "2.0.1")))
            )
            .andExpect(status().isOk)
        assertEquals("2.0.1", stations.findById(StationKey(tenant.id, "NEW-201")).orElseThrow().ocppProtocol)

        mvc.perform(
                post("/api/v1/stations")
                    .with(csrf())
                    .as_(admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(db.encode(mapOf("id" to "NEW-AUTO", "name" to "Auto")))
            )
            .andExpect(status().isOk)
        assertEquals("AUTO", stations.findById(StationKey(tenant.id, "NEW-AUTO")).orElseThrow().ocppProtocol)

        mvc.perform(
                post("/api/v1/stations")
                    .with(csrf())
                    .as_(admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(db.encode(mapOf("id" to "NEW-BAD", "name" to "Mauvaise", "ocppProtocol" to "3.0")))
            )
            .andExpect(status().isBadRequest)

        mvc.perform(get("/api/v1/stations/CS-201").as_(admin))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ocpp_protocol").value("2.0.1"))
            .andExpect(jsonPath("$.ocpp_version").value("2.0.1"))

        mvc.perform(
                post("/api/v1/stations/CS-201/commands")
                    .with(csrf())
                    .as_(admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        db.encode(
                            mapOf(
                                "action" to "SetVariables",
                                "ocppVersion" to "2.0.1",
                                "payload" to
                                    mapOf(
                                        "setVariableData" to
                                            listOf(
                                                mapOf(
                                                    "attributeValue" to "x",
                                                    "component" to mapOf("name" to "SecurityCtrlr"),
                                                    "variable" to mapOf("name" to "BasicAuthPassword"),
                                                )
                                            )
                                    ),
                            )
                        )
                    )
            )
            .andExpect(status().isBadRequest)

        mvc.perform(
                post("/api/v1/stations/CS-201/commands")
                    .with(csrf())
                    .as_(admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(db.encode(mapOf("action" to "ClearCache", "ocppVersion" to "2.0.1", "payload" to emptyMap<String, Any>())))
            )
            .andExpect(status().isOk)
    }

    @Test
    fun `une seule commande est envoyee a la fois jusqu a la reponse de la borne`() {
        val first = queue.command(tenant.id, "CS-201", "ClearCache", db.json.createObjectNode())
        val second = queue.command(tenant.id, "CS-201", "ClearCache", db.json.createObjectNode())
        val identity = StationIdentity(tenant.id, "CS-201")
        assertEquals(listOf(first), queue.claim(identity, OcppVersion.V201).map { it.id })
        assertTrue(queue.claim(identity, OcppVersion.V201).isEmpty())
        queue.complete(identity, db.json.valueToTree(listOf(3, first.toString(), mapOf("status" to "Accepted"))))
        assertEquals(listOf(second), queue.claim(identity, OcppVersion.V201).map { it.id })

        queue.interrupted(identity)
        assertEquals(fr.codetics.ev_server.models.commands.CommandState.TIMED_OUT, commands.findById(second).orElseThrow().state)
        assertTrue(queue.claim(identity, OcppVersion.V201).isEmpty())
    }

    @Test
    fun `un EVSE a plusieurs connecteurs garde l etat du connecteur utilise`() {
        call("StatusNotification", mapOf("timestamp" to at(0), "connectorStatus" to "Available", "evseId" to 1, "connectorId" to 1))
        call("StatusNotification", mapOf("timestamp" to at(1), "connectorStatus" to "Occupied", "evseId" to 1, "connectorId" to 2))
        call("StatusNotification", mapOf("timestamp" to at(2), "connectorStatus" to "Available", "evseId" to 1, "connectorId" to 1))
        assertEquals("Preparing", connectors.findById(ConnectorKey(tenant.id, "CS-201", 1)).orElseThrow().status)

        val (unlock, _) = dispatch("CS-201", "UnlockConnector", mapOf("connectorId" to 1), mapOf("status" to "Unlocked"))
        assertEquals(2, unlock.payload.path("connectorId").intValue())

        call("StatusNotification", mapOf("timestamp" to at(3), "connectorStatus" to "Available", "evseId" to 1, "connectorId" to 2))
        assertEquals("Available", connectors.findById(ConnectorKey(tenant.id, "CS-201", 1)).orElseThrow().status)
    }

    @Test
    fun `les refus de traduction et de validation sont traduits`() {
        val english =
            fr.codetics.ev_server.core.I18n.with("en") {
                assertThrows(InvalidRequest::class.java) {
                    queue.command(tenant.id, "CS-201", "ChangeConfiguration", db.json.valueToTree(mapOf("key" to "AutoChargeEnabled", "value" to "true")))
                }.message
            }
        assertEquals("The AutoChargeEnabled parameter has no equivalent in the OCPP 2.0.1 device model.", english)
        val spanish =
            fr.codetics.ev_server.core.I18n.with("es") {
                assertThrows(InvalidRequest::class.java) {
                    queue.command(tenant.id, "CS-201", "GetVariables", db.json.valueToTree(mapOf("getVariableData" to emptyList<Any>())), version = OcppVersion.V201)
                }.message
            }
        assertTrue(spanish!!.startsWith("Valor no válido"))

        val body =
            mvc.perform(
                    post("/api/v1/stations/CS-201/commands")
                        .with(csrf())
                        .as_(admin)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(db.encode(mapOf("action" to "Reset", "ocppVersion" to "2.0.1", "payload" to mapOf("type" to "Later"))))
                )
                .andExpect(status().isBadRequest)
                .andReturn()
                .response
                .contentAsString
        assertTrue(body.contains("Invalid value: type"), body)
    }
}
