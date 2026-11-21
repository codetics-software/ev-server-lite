package fr.codetics.ev_server.ocpp.v201

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.models.entities.CommandEntity
import fr.codetics.ev_server.models.entities.ConnectorEntity
import fr.codetics.ev_server.models.entities.ConnectorKey
import fr.codetics.ev_server.models.entities.TenantEntity
import fr.codetics.ev_server.models.entities.TransactionEntity
import fr.codetics.ev_server.ocpp.OcppFault
import fr.codetics.ev_server.ocpp.OcppVersion
import fr.codetics.ev_server.repositories.ConnectorRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.repositories.TransactionRepository
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
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
class Ocpp201TranslationTests
@Autowired
constructor(
    private val db: Database,
    private val translator: Ocpp201Commands,
    private val tenants: TenantRepository,
    private val transactions: TransactionRepository,
    private val connectors: ConnectorRepository,
) {
    private lateinit var tenant: TenantEntity

    @BeforeEach
    fun prepare() {
        tenant = tenants.save(TenantEntity(slug = "translate-${UUID.randomUUID().toString().take(8)}", name = "Traduction"))
    }

    private fun command(action: String, payload: Map<String, Any?>, wire: String? = null) =
        CommandEntity(
            tenantId = tenant.id,
            stationId = "CS-T",
            action = action,
            request = db.encode(payload),
            wireVersion = wire,
        )

    private fun wire(action: String, payload: Map<String, Any?>): WireCommand =
        translator.outbound(command(action, payload), OcppVersion.V201)

    private fun back(action: String, result: Map<String, Any?>): JsonNode =
        translator.inbound(command(action, emptyMap(), OcppVersion.V201.label), db.json.valueToTree(result))

    private val profile =
        mapOf(
            "chargingProfileId" to 7,
            "stackLevel" to 2,
            "chargingProfilePurpose" to "ChargePointMaxProfile",
            "chargingProfileKind" to "Recurring",
            "recurrencyKind" to "Daily",
            "validFrom" to "2026-03-01T00:00:00Z",
            "chargingSchedule" to
                mapOf(
                    "duration" to 3600,
                    "startSchedule" to "2026-03-01T00:00:00Z",
                    "chargingRateUnit" to "A",
                    "minChargingRate" to 6,
                    "chargingSchedulePeriod" to listOf(mapOf("startPeriod" to 0, "limit" to 16, "numberPhases" to 3)),
                ),
        )

    @Test
    fun `les commandes de maintenance sont traduites`() {
        assertEquals("Immediate", wire("Reset", mapOf("type" to "Hard")).payload.path("type").asText())
        assertEquals("OnIdle", wire("Reset", mapOf("type" to "Soft")).payload.path("type").asText())

        val availability = wire("ChangeAvailability", mapOf("connectorId" to 2, "type" to "Inoperative"))
        assertEquals("ChangeAvailability", availability.action)
        assertEquals(2, availability.payload.path("evse").path("id").intValue())
        assertEquals("Inoperative", availability.payload.path("operationalStatus").asText())
        assertFalse(wire("ChangeAvailability", mapOf("connectorId" to 0, "type" to "Operative")).payload.has("evse"))

        assertEquals("ClearCache", wire("ClearCache", emptyMap()).action)
        assertEquals("GetLocalListVersion", wire("GetLocalListVersion", emptyMap()).action)

        val data = wire("DataTransfer", mapOf("vendorId" to "Acme", "messageId" to "Ping", "data" to "x"))
        assertEquals("Acme", data.payload.path("vendorId").asText())
        assertEquals("Ping", data.payload.path("messageId").asText())
    }

    @Test
    fun `la configuration est lue et ecrite par variables`() {
        val all = wire("GetConfiguration", emptyMap())
        assertEquals("GetBaseReport", all.action)
        assertEquals("FullInventory", all.payload.path("reportBase").asText())

        val some = wire("GetConfiguration", mapOf("key" to listOf("HeartbeatInterval", "OCPPCommCtrlr/WebSocketPingInterval")))
        assertEquals("GetVariables", some.action)
        assertEquals(2, some.payload.path("getVariableData").size())

        val set = wire("ChangeConfiguration", mapOf("key" to "HeartbeatInterval", "value" to "120"))
        assertEquals("SetVariables", set.action)
        assertEquals("120", set.payload.path("setVariableData").path(0).path("attributeValue").asText())

        assertEquals("NotSupported", assertThrows(OcppFault::class.java) { wire("ChangeConfiguration", mapOf("key" to "SecurityProfile", "value" to "3")) }.code)
        assertEquals("NotSupported", assertThrows(OcppFault::class.java) { wire("ChangeConfiguration", mapOf("key" to "Unknown Key With Spaces", "value" to "1")) }.code)
    }

    @Test
    fun `les profils de recharge sont convertis`() {
        val set = wire("SetChargingProfile", mapOf("connectorId" to 0, "csChargingProfiles" to profile))
        assertEquals("SetChargingProfile", set.action)
        val converted = set.payload.path("chargingProfile")
        assertEquals("ChargingStationMaxProfile", converted.path("chargingProfilePurpose").asText())
        assertEquals("Daily", converted.path("recurrencyKind").asText())
        assertEquals(16, converted.path("chargingSchedule").path(0).path("chargingSchedulePeriod").path(0).path("limit").intValue())

        val clear = wire("ClearChargingProfile", mapOf("connectorId" to 1, "chargingProfilePurpose" to "TxDefaultProfile", "stackLevel" to 1))
        assertEquals(1, clear.payload.path("chargingProfileCriteria").path("evseId").intValue())
        assertEquals("TxDefaultProfile", clear.payload.path("chargingProfileCriteria").path("chargingProfilePurpose").asText())
        assertFalse(wire("ClearChargingProfile", mapOf("id" to 7)).payload.has("chargingProfileCriteria"))

        val composite = wire("GetCompositeSchedule", mapOf("connectorId" to 1, "duration" to 600, "chargingRateUnit" to "W"))
        assertEquals(600, composite.payload.path("duration").intValue())
        assertEquals("W", composite.payload.path("chargingRateUnit").asText())
    }

    @Test
    fun `les sessions, reservations et listes locales utilisent les jetons 2_0_1`() {
        val transaction = transactions.save(TransactionEntity(tenantId = tenant.id, stationId = "CS-T", connectorId = 1, ocppTransactionId = "TX-ABC"))
        val start = wire("RemoteStartTransaction", mapOf("idTag" to "TAG-1", "connectorId" to 1, "chargingProfile" to profile + mapOf("chargingProfilePurpose" to "TxProfile")))
        assertEquals("RequestStartTransaction", start.action)
        assertEquals("Central", start.payload.path("idToken").path("type").asText())
        assertTrue(start.payload.path("remoteStartId").intValue() >= 0)
        assertEquals("TxProfile", start.payload.path("chargingProfile").path("chargingProfilePurpose").asText())

        val stop = wire("RemoteStopTransaction", mapOf("transactionId" to transaction.id))
        assertEquals("RequestStopTransaction", stop.action)
        assertEquals("TX-ABC", stop.payload.path("transactionId").asText())
        assertEquals("NotSupported", assertThrows(OcppFault::class.java) { wire("RemoteStopTransaction", mapOf("transactionId" to 987654)) }.code)

        val reserve = wire("ReserveNow", mapOf("reservationId" to 5, "connectorId" to 1, "idTag" to "TAG-1", "parentIdTag" to "GROUP", "expiryDate" to "2026-03-01T12:00:00Z"))
        assertEquals(5, reserve.payload.path("id").intValue())
        assertEquals("GROUP", reserve.payload.path("groupIdToken").path("idToken").asText())
        assertEquals(5, wire("CancelReservation", mapOf("reservationId" to 5)).payload.path("reservationId").intValue())

        val list =
            wire(
                "SendLocalList",
                mapOf(
                    "listVersion" to 3,
                    "updateType" to "Full",
                    "localAuthorizationList" to
                        listOf(
                            mapOf("idTag" to "04A1B2C3", "idTagInfo" to mapOf("status" to "Accepted", "parentIdTag" to "GROUP")),
                            mapOf("idTag" to "VID:0123456789AB"),
                        ),
                ),
            )
        val entries = list.payload.path("localAuthorizationList")
        assertEquals("ISO14443", entries.path(0).path("idToken").path("type").asText())
        assertEquals("GROUP", entries.path(0).path("idTokenInfo").path("groupIdToken").path("idToken").asText())
        assertEquals("MacAddress", entries.path(1).path("idToken").path("type").asText())
        assertEquals("0123456789AB", entries.path(1).path("idToken").path("idToken").asText())
        assertFalse(wire("SendLocalList", mapOf("listVersion" to 4, "updateType" to "Differential")).payload.has("localAuthorizationList"))
    }

    @Test
    fun `les declenchements et deverrouillages ciblent le connecteur physique`() {
        connectors.save(ConnectorEntity(key = ConnectorKey(tenant.id, "CS-T", 1)).apply { ocppConnectors = db.encode(mapOf("1" to "Available", "2" to "Occupied")) })

        val status = wire("TriggerMessage", mapOf("requestedMessage" to "StatusNotification", "connectorId" to 1))
        assertEquals(2, status.payload.path("evse").path("connectorId").intValue())
        assertEquals("LogStatusNotification", wire("TriggerMessage", mapOf("requestedMessage" to "DiagnosticsStatusNotification")).payload.path("requestedMessage").asText())
        assertEquals(
            "SignChargingStationCertificate",
            wire("ExtendedTriggerMessage", mapOf("requestedMessage" to "SignChargePointCertificate")).payload.path("requestedMessage").asText(),
        )
        assertFalse(wire("TriggerMessage", mapOf("requestedMessage" to "MeterValues", "connectorId" to 1)).payload.path("evse").has("connectorId"))

        val unlock = wire("UnlockConnector", mapOf("connectorId" to 1))
        assertEquals(1, unlock.payload.path("evseId").intValue())
        assertEquals(2, unlock.payload.path("connectorId").intValue())
        assertEquals(1, wire("UnlockConnector", mapOf("connectorId" to 3)).payload.path("connectorId").intValue())
    }

    @Test
    fun `micrologiciel, journaux et certificats sont traduits`() {
        val firmware = wire("UpdateFirmware", mapOf("location" to "https://fw.example/1.bin", "retrieveDate" to "2026-03-01T00:00:00Z", "retries" to 2))
        assertEquals("https://fw.example/1.bin", firmware.payload.path("firmware").path("location").asText())
        assertEquals(2, firmware.payload.path("retries").intValue())

        val signed =
            wire(
                "SignedUpdateFirmware",
                mapOf(
                    "requestId" to 9,
                    "firmware" to mapOf("location" to "https://fw.example/2.bin", "retrieveDateTime" to "2026-03-01T00:00:00Z", "signingCertificate" to "CERT", "signature" to "SIG"),
                ),
            )
        assertEquals("UpdateFirmware", signed.action)
        assertEquals("SIG", signed.payload.path("firmware").path("signature").asText())
        assertEquals(9, signed.payload.path("requestId").intValue())

        val diagnostics = wire("GetDiagnostics", mapOf("location" to "https://logs.example/up", "startTime" to "2026-03-01T00:00:00Z"))
        assertEquals("GetLog", diagnostics.action)
        assertEquals("DiagnosticsLog", diagnostics.payload.path("logType").asText())
        val log = wire("GetLog", mapOf("logType" to "SecurityLog", "requestId" to 3, "log" to mapOf("remoteLocation" to "https://logs.example/up")))
        assertEquals("SecurityLog", log.payload.path("logType").asText())

        assertEquals("ChargingStationCertificate", wire("CertificateSigned", mapOf("certificateChain" to "PEM")).payload.path("certificateType").asText())
        assertEquals("CSMSRootCertificate", wire("InstallCertificate", mapOf("certificateType" to "CentralSystemRootCertificate", "certificate" to "PEM")).payload.path("certificateType").asText())
        assertEquals(
            "ManufacturerRootCertificate",
            wire("GetInstalledCertificateIds", mapOf("certificateType" to "ManufacturerRootCertificate")).payload.path("certificateType").path(0).asText(),
        )
        val hash = mapOf("hashAlgorithm" to "SHA256", "issuerNameHash" to "a", "issuerKeyHash" to "b", "serialNumber" to "c")
        assertEquals("c", wire("DeleteCertificate", mapOf("certificateHashData" to hash)).payload.path("certificateHashData").path("serialNumber").asText())
    }

    @Test
    fun `les commandes impossibles a traduire sont refusees`() {
        assertEquals("NotSupported", assertThrows(OcppFault::class.java) { wire("Coffee", emptyMap()) }.code)
        assertEquals("OnIdle", wire("Reset", emptyMap()).payload.path("type").asText())
        assertEquals("NotSupported", assertThrows(OcppFault::class.java) { wire("ChangeAvailability", mapOf("type" to "Broken")) }.code)
        assertEquals("NotSupported", assertThrows(OcppFault::class.java) { wire("RemoteStartTransaction", emptyMap()) }.code)
        val native = CommandEntity(tenantId = tenant.id, stationId = "CS-T", action = "ClearCache", request = "{}", ocppVersion = OcppVersion.V201.label)
        assertEquals("ClearCache", translator.outbound(native, OcppVersion.V201).action)
        assertThrows(OcppFault::class.java) { translator.outbound(native, OcppVersion.V16) }
        assertEquals("ClearCache", translator.outbound(command("ClearCache", emptyMap()), OcppVersion.V16).action)
    }

    @Test
    fun `les reponses 2_0_1 sont rendues au format 1_6`() {
        assertEquals("Accepted", back("Reset", mapOf("status" to "Scheduled")).path("status").asText())
        assertEquals("UnlockFailed", back("UnlockConnector", mapOf("status" to "OngoingAuthorizedTransaction")).path("status").asText())
        assertEquals("NotSupported", back("UnlockConnector", mapOf("status" to "UnknownConnector")).path("status").asText())
        assertEquals("Unlocked", back("UnlockConnector", mapOf("status" to "Unlocked")).path("status").asText())

        assertEquals("RebootRequired", back("ChangeConfiguration", mapOf("setVariableResult" to listOf(mapOf("attributeStatus" to "RebootRequired")))).path("status").asText())
        val unknown = back("ChangeConfiguration", mapOf("setVariableResult" to listOf(mapOf("attributeStatus" to "UnknownVariable", "attributeStatusInfo" to mapOf("reasonCode" to "X")))))
        assertEquals("NotSupported", unknown.path("status").asText())
        assertEquals("X", unknown.path("statusInfo").path("reasonCode").asText())

        val configuration =
            back(
                "GetConfiguration",
                mapOf(
                    "getVariableResult" to
                        listOf(
                            mapOf("attributeStatus" to "Accepted", "attributeValue" to "60", "component" to mapOf("name" to "OCPPCommCtrlr"), "variable" to mapOf("name" to "HeartbeatInterval")),
                            mapOf("attributeStatus" to "UnknownVariable", "component" to mapOf("name" to "Foo"), "variable" to mapOf("name" to "Bar")),
                        )
                ),
            )
        assertEquals("60", configuration.path("configurationKey").path(0).path("value").asText())
        assertEquals("Foo/Bar", configuration.path("unknownKey").path(0).asText())

        val schedule =
            back(
                "GetCompositeSchedule",
                mapOf(
                    "status" to "Accepted",
                    "schedule" to
                        mapOf(
                            "evseId" to 1,
                            "duration" to 600,
                            "scheduleStart" to "2026-03-01T00:00:00Z",
                            "chargingRateUnit" to "A",
                            "chargingSchedulePeriod" to listOf(mapOf("startPeriod" to 0, "limit" to 10, "numberPhases" to 1)),
                        ),
                ),
            )
        assertEquals(1, schedule.path("connectorId").intValue())
        assertEquals(10, schedule.path("chargingSchedule").path("chargingSchedulePeriod").path(0).path("limit").intValue())

        assertEquals("diag.zip", back("GetDiagnostics", mapOf("status" to "Accepted", "filename" to "diag.zip")).path("fileName").asText())
        assertEquals("log.zip", back("GetLog", mapOf("status" to "Accepted", "filename" to "log.zip")).path("filename").asText())
        assertEquals(12, back("GetLocalListVersion", mapOf("versionNumber" to 12)).path("listVersion").intValue())
        assertEquals("x", back("DataTransfer", mapOf("status" to "Accepted", "data" to "x")).path("data").asText())
        val certificates = back("GetInstalledCertificateIds", mapOf("status" to "Accepted", "certificateHashDataChain" to listOf(mapOf("certificateHashData" to mapOf("serialNumber" to "1")))))
        assertEquals("1", certificates.path("certificateHashData").path(0).path("serialNumber").asText())
        assertEquals("Rejected", back("ClearCache", mapOf("status" to "Rejected", "statusInfo" to mapOf("reasonCode" to "Busy"))).path("status").asText())

        val untouched = db.json.valueToTree<JsonNode>(mapOf("status" to "Scheduled"))
        assertEquals(untouched, translator.inbound(command("Reset", emptyMap(), OcppVersion.V16.label), untouched))
    }
}
