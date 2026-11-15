package fr.codetics.ev_server.ocpp.v201

import fr.codetics.ev_server.ocpp.OcppFault
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode

class Ocpp201CodecTests {
    private fun json(text: String): JsonNode = Ocpp201Codec.mapper.readTree(text)

    private fun fault(text: String, type: kotlin.reflect.KClass<*> = BootNotificationRequest::class): String =
        assertThrows(OcppFault::class.java) { Ocpp201Codec.decode(json(text), type) }.code

    private val boot = """{"reason":"PowerUp","chargingStation":{"model":"M1","vendorName":"Vendor"}}"""

    @Test
    fun `un BootNotification valide est decode en objet type`() {
        val request = Ocpp201Codec.decode(json(boot), BootNotificationRequest::class)
        assertEquals(BootReasonEnum.PowerUp, request.reason)
        assertEquals("Vendor", request.chargingStation.vendorName)
        assertNull(request.chargingStation.firmwareVersion)
    }

    @Test
    fun `les erreurs de contenu donnent les codes RPC OCPP 2_0_1`() {
        assertEquals("FormatViolation", fault("""{"reason":"PowerUp","chargingStation":{"model":"M1","vendorName":"V"},"extra":1}"""))
        assertEquals("OccurrenceConstraintViolation", fault("""{"reason":"PowerUp"}"""))
        assertEquals("OccurrenceConstraintViolation", fault("""{"reason":"PowerUp","chargingStation":{"model":"M1"}}"""))
        assertEquals("PropertyConstraintViolation", fault("""{"reason":"Coffee","chargingStation":{"model":"M1","vendorName":"V"}}"""))
        assertEquals("PropertyConstraintViolation", fault("""{"reason":"PowerUp","chargingStation":{"model":"${"M".repeat(21)}","vendorName":"V"}}"""))
        assertEquals("TypeConstraintViolation", fault("""{"reason":"PowerUp","chargingStation":{"model":12,"vendorName":"V"}}"""))
        assertEquals("TypeConstraintViolation", fault("""{"reason":3,"chargingStation":{"model":"M1","vendorName":"V"}}"""))
        assertEquals("TypeConstraintViolation", fault("[]"))
    }

    @Test
    fun `les entiers, dates et cardinalites sont controles`() {
        val status = StatusNotificationRequest::class
        assertEquals(
            "TypeConstraintViolation",
            fault("""{"timestamp":"2026-01-01T00:00:00Z","connectorStatus":"Available","evseId":"1","connectorId":1}""", status),
        )
        assertEquals(
            "TypeConstraintViolation",
            fault("""{"timestamp":"2026-01-01T00:00:00Z","connectorStatus":"Available","evseId":1.5,"connectorId":1}""", status),
        )
        assertEquals(
            "PropertyConstraintViolation",
            fault("""{"timestamp":"hier","connectorStatus":"Available","evseId":1,"connectorId":1}""", status),
        )
        assertEquals("OccurrenceConstraintViolation", fault("""{"evseId":1,"meterValue":[]}""", MeterValuesRequest::class))
        assertEquals(
            "OccurrenceConstraintViolation",
            fault("""{"evseId":1,"meterValue":[{"timestamp":"2026-01-01T00:00:00Z","sampledValue":[]}]}""", MeterValuesRequest::class),
        )
    }

    @Test
    fun `les donnees personnalisees sont acceptees et conservees`() {
        val request =
            Ocpp201Codec.decode(
                json("""{"customData":{"vendorId":"acme","firmware":"x"},"reason":"PowerUp","chargingStation":{"model":"M1","vendorName":"V"}}"""),
                BootNotificationRequest::class,
            )
        assertEquals("acme", request.customData?.vendorId)
        assertEquals("x", Ocpp201Codec.encode(request).path("customData").path("firmware").asText())
    }

    @Test
    fun `les valeurs a point et tiret circulent sous leur nom OCPP`() {
        val sample =
            Ocpp201Codec.decode(
                json("""{"value":12.5,"measurand":"Energy.Active.Import.Register","phase":"L1-N","context":"Transaction.Begin","unitOfMeasure":{"unit":"kWh","multiplier":3}}"""),
                SampledValue::class,
            )
        assertEquals(MeasurandEnum.ENERGY_ACTIVE_IMPORT_REGISTER, sample.measurand)
        assertEquals(PhaseEnum.L1_N, sample.phase)
        assertEquals(BigDecimal("12.5"), sample.value)
        val encoded = Ocpp201Codec.encode(sample)
        assertEquals("Energy.Active.Import.Register", encoded.path("measurand").asText())
        assertEquals("Transaction.Begin", encoded.path("context").asText())
        assertFalse(encoded.has("location"))
    }

    @Test
    fun `les reponses construites respectent leur propre modele`() {
        val responses =
            listOf(
                BootNotificationResponse("2026-01-01T00:00:00Z", 300, RegistrationStatusEnum.Accepted),
                AuthorizeResponse(IdTokenInfo(AuthorizationStatusEnum.Accepted, groupIdToken = IdToken(idToken = "G", type = IdTokenEnum.Central))),
                TransactionEventResponse(idTokenInfo = IdTokenInfo(AuthorizationStatusEnum.Invalid)),
                Get15118EVCertificateResponse(Iso15118EVCertificateStatusEnum.Failed, StatusInfo("NotSupported"), ""),
                StatusNotificationResponse(),
            )
        responses.forEach { response ->
            val decoded = Ocpp201Codec.decode(Ocpp201Codec.encode(response), response::class)
            assertEquals(response, decoded)
        }
    }

    @Test
    fun `chaque action 2_0_1 a un modele de requete et de reponse`() {
        assertEquals(25, Ocpp201Messages.fromStation.size)
        assertEquals(40, Ocpp201Messages.toStation.size)
        assertEquals(64, (Ocpp201Messages.fromStation.keys + Ocpp201Messages.toStation.keys).size)
        (Ocpp201Messages.fromStation + Ocpp201Messages.toStation).forEach { (action, message) ->
            assertEquals("${action}Request", message.request.simpleName)
            assertEquals("${action}Response", message.response.simpleName)
        }
    }

    @Test
    fun `les cles du modele de donnees sont reversibles et les alias 1_6 resolus`() {
        val target = requireNotNull(Ocpp201Variables.parse("EVSE:main@2.1/Power:L1"))
        assertEquals("EVSE", target.component.name)
        assertEquals("main", target.component.instance)
        assertEquals(2, target.component.evse?.id)
        assertEquals(1, target.component.evse?.connectorId)
        assertEquals("Power", target.variable.name)
        assertEquals("L1", target.variable.instance)
        assertEquals("EVSE:main@2.1/Power:L1", Ocpp201Variables.key(target.component, target.variable))

        val heartbeat = requireNotNull(Ocpp201Variables.parse("HeartbeatInterval"))
        assertEquals("OCPPCommCtrlr", heartbeat.component.name)
        assertEquals("HeartbeatInterval", heartbeat.variable.name)
        assertEquals("TransactionEvent", Ocpp201Variables.parse("TransactionMessageAttempts")?.variable?.instance)
        assertNull(Ocpp201Variables.parse("AutoChargeEnabled"))
        assertTrue(Ocpp201Variables.protected("AuthorizationKey"))
        assertTrue(Ocpp201Variables.protected("SecurityCtrlr/SecurityProfile"))
        assertFalse(Ocpp201Variables.protected("OCPPCommCtrlr/HeartbeatInterval"))
    }
}
