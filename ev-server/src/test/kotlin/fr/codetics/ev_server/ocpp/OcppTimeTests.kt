package fr.codetics.ev_server.ocpp

import java.math.BigDecimal
import java.time.Instant
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class OcppTimeTests {
    private val json = JsonMapper.builder().build()

    @Test
    fun `les horodatages des bornes sont lus avec ou sans fuseau`() {
        val expected = Instant.parse("2026-03-01T10:15:30Z")
        assertEquals(expected, OcppTime.parse("2026-03-01T10:15:30Z"))
        assertEquals(expected, OcppTime.parse("2026-03-01T10:15:30.000Z"))
        assertEquals(expected, OcppTime.parse("2026-03-01T11:15:30+01:00"))
        assertEquals(expected, OcppTime.parse("2026-03-01T11:15:30+0100"))
        assertEquals(expected, OcppTime.parse("2026-03-01T10:15:30"))
        assertEquals(expected, OcppTime.parse("2026-03-01 10:15:30"))
        assertEquals(Instant.parse("2026-03-01T10:15:30.123456Z"), OcppTime.parse("2026-03-01T10:15:30.123456"))
        assertNull(OcppTime.parseOrNull("hier"))
        assertNull(OcppTime.parseOrNull(""))
    }

    @Test
    fun `les releves signes OCMF sont conserves et servent de repli`() {
        val ocmf =
            "OCMF|{\"FV\":\"1.0\",\"RD\":[{\"TM\":\"2026-03-01T10:15:30,000+0100 S\",\"TX\":\"B\",\"RV\":12.345," +
                "\"RI\":\"1-b:1.8.0\",\"RU\":\"kWh\",\"ST\":\"G\"}]}|{\"SA\":\"ECDSA-secp256r1-SHA256\",\"SD\":\"3045\"}"
        val reading =
            json.readTree(
                """{"timestamp":"2026-03-01T10:15:30Z","sampledValue":[{"value":${json.writeValueAsString(ocmf)},
                "format":"SignedData","context":"Transaction.Begin","measurand":"Energy.Active.Import.Register"}]}"""
            )

        assertEquals(0, BigDecimal("12345").compareTo(MeterReadings.parse(reading).energyWh))
        val signed = MeterReadings.signed(reading).single()
        assertEquals("Transaction.Begin", signed.context)
        assertEquals("OCMF", signed.format)
        assertEquals(ocmf, signed.value)
    }

    @Test
    fun `la valeur brute est preferee au releve signe`() {
        val reading =
            json.readTree(
                """{"timestamp":"2026-03-01T10:15:30Z","sampledValue":[
                {"value":"OCMF|{\"RD\":[{\"RV\":1,\"RU\":\"kWh\"}]}|{}","format":"SignedData"},
                {"value":5000,"unit":"Wh"}]}"""
            )
        assertEquals(0, BigDecimal("5000").compareTo(MeterReadings.parse(reading).energyWh))
    }
}
