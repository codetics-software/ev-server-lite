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
            fault("MeterValues", meterValues("""{"value":true}""")),
        )
        assertEquals("OccurrenceConstraintViolation", fault("MeterValues", meterValues("")))
    }

    @Test
    fun `les ecarts courants des bornes certifiees sont toleres`() {
        OcppValidation.validate("MeterValues", json.readTree(meterValues("""{"value":12.5}""")))
        OcppValidation.validate(
            "StartTransaction",
            json.readTree("""{"connectorId":1,"idTag":"TAG","meterStart":1234.5,"timestamp":"2026-01-01T10:00:00"}"""),
        )
        OcppValidation.validate(
            "StopTransaction",
            json.readTree("""{"transactionId":1,"meterStop":2000,"timestamp":"2026-01-01T11:00:00.123+0100"}"""),
        )
        OcppValidation.validate(
            "StatusNotification",
            json.readTree("""{"connectorId":1,"status":"Available","errorCode":"VendorSpecificError"}"""),
        )
        OcppValidation.validate(
            "BootNotification",
            json.readTree("""{"chargePointVendor":"Compleo Charging Solutions AG","chargePointModel":"eBOX professional"}"""),
        )
        assertEquals(
            "TypeConstraintViolation",
            fault("StartTransaction", """{"connectorId":1,"idTag":"TAG","meterStart":-1,"timestamp":"$timestamp"}"""),
        )
    }

    @Test
    fun `les messages de securite OCPP 1_6 sont pris en charge`() {
        OcppValidation.validate(
            "SecurityEventNotification",
            json.readTree("""{"type":"TamperDetectionActivated","timestamp":"$timestamp","techInfo":"door"}"""),
        )
        OcppValidation.validate("SignCertificate", json.readTree("""{"csr":"-----BEGIN CERTIFICATE REQUEST-----"}"""))
        OcppValidation.validate("LogStatusNotification", json.readTree("""{"status":"Uploaded","requestId":3}"""))
        OcppValidation.validate("SignedFirmwareStatusNotification", json.readTree("""{"status":"SignatureVerified"}"""))
        OcppValidation.validate(
            "ExtendedTriggerMessage",
            json.readTree("""{"requestedMessage":"SignChargePointCertificate"}"""),
            serverInitiated = true,
        )
        OcppValidation.validate(
            "GetLog",
            json.readTree("""{"logType":"SecurityLog","requestId":1,"log":{"remoteLocation":"https://logs"}}"""),
            serverInitiated = true,
        )
        assertEquals("PropertyConstraintViolation", fault("LogStatusNotification", """{"status":"Done"}"""))
        assertEquals(
            "PropertyConstraintViolation",
            fault("InstallCertificate", """{"certificateType":"Other","certificate":"x"}""", serverInitiated = true),
        )
        assertEquals(
            "OccurrenceConstraintViolation",
            fault("SignedUpdateFirmware", """{"requestId":1,"firmware":{"location":"https://fw"}}""", serverInitiated = true),
        )
    }
}
