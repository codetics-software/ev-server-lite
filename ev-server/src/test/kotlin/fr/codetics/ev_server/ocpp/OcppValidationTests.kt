package fr.codetics.ev_server.ocpp

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class OcppValidationTests {
    private val json = JsonMapper.builder().build()
    private val timestamp = "2026-01-01T10:00:00Z"

    private fun fault(action: String, payload: String, serverInitiated: Boolean = false): String =
        assertThrows(OcppFault::class.java) {
                OcppValidation.validate(action, json.readTree(payload), serverInitiated)
            }
            .code

    private fun meterValues(sample: String): String {
        val reading = """{"timestamp":"$timestamp","sampledValue":[$sample]}"""
        return """{"connectorId":1,"meterValue":[$reading]}"""
    }

    @Test
    fun `les messages conformes sont acceptes`() {
        OcppValidation.validate(
            "BootNotification",
            json.readTree("""{"chargePointVendor":"Codetics","chargePointModel":"AC22"}"""),
        )
        OcppValidation.validate("MeterValues", json.readTree(meterValues("""{"value":"1200"}""")))
        OcppValidation.validate(
            "RemoteStopTransaction",
            json.readTree("""{"transactionId":42}"""),
            serverInitiated = true,
        )
    }

    @Test
    fun `les actions sont filtrees selon le sens du message`() {
        assertEquals("NotSupported", fault("Unknown", "{}"))
        assertEquals("NotSupported", fault("BootNotification", "{}", serverInitiated = true))
        assertEquals("NotSupported", fault("RemoteStopTransaction", """{"transactionId":1}"""))
    }

    @Test
    fun `les parametres obligatoires et leurs types sont verifies`() {
        assertEquals("TypeConstraintViolation", fault("Heartbeat", "[]"))
        assertEquals("OccurrenceConstraintViolation", fault("Authorize", "{}"))
        assertEquals(
            "TypeConstraintViolation",
            fault("UnlockConnector", """{"connectorId":-1}""", serverInitiated = true),
        )
        assertEquals(
            "PropertyConstraintViolation",
            fault("Authorize", """{"idTag":"ABCDEFGHIJKLMNOPQRSTU"}"""),
        )
        assertEquals(
            "PropertyConstraintViolation",
            fault(
                "StopTransaction",
                """{"transactionId":1,"meterStop":10,"timestamp":"hier"}""",
            ),
        )
    }

    @Test
    fun `les regles propres a chaque action sont appliquees`() {
        assertEquals(
            "PropertyConstraintViolation",
            fault(
                "StartTransaction",
                """{"connectorId":0,"idTag":"TAG","meterStart":0,"timestamp":"$timestamp"}""",
            ),
        )
        assertEquals(
            "PropertyConstraintViolation",
            fault(
                "StatusNotification",
                """{"connectorId":1,"status":"Busy","errorCode":"NoError"}""",
            ),
        )
        assertEquals(
            "PropertyConstraintViolation",
            fault("Reset", """{"type":"Warm"}""", serverInitiated = true),
        )
        assertEquals(
            "TypeConstraintViolation",
            fault("MeterValues", meterValues("""{"value":12}""")),
        )
        assertEquals("OccurrenceConstraintViolation", fault("MeterValues", meterValues("")))
    }
}
