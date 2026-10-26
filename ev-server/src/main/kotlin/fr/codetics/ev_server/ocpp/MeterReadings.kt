package fr.codetics.ev_server.ocpp

import java.math.BigDecimal
import java.time.Instant
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

data class MeterReading(
    val at: Instant,
    val energyWh: BigDecimal? = null,
    val powerW: BigDecimal? = null,
    val soc: Int? = null,
    val currentA: BigDecimal? = null,
    val voltageV: BigDecimal? = null,
)

data class SignedReading(val context: String, val value: String, val format: String)

object MeterReadings {
    private val json = JsonMapper.builder().build()
    private const val ENERGY = "Energy.Active.Import.Register"
    private const val POWER = "Power.Active.Import"
    private const val SOC = "SoC"
    private const val CURRENT = "Current.Import"
    private const val VOLTAGE = "Voltage"
    private val phaseVoltages = setOf("L1-N", "L2-N", "L3-N", "L1", "L2", "L3")

    fun parse(reading: JsonNode): MeterReading {
        val at = OcppTime.parse(reading.path("timestamp").asText())
        val samples = reading.path("sampledValue").toList()
        return MeterReading(
            at = at,
            energyWh = energy(samples),
            powerW = power(samples),
            soc = soc(samples),
            currentA = summed(samples, CURRENT, mapOf("A" to BigDecimal.ONE)),
            voltageV = voltage(samples),
        )
    }

    fun signed(reading: JsonNode): List<SignedReading> =
        reading.path("sampledValue").mapNotNull { sample ->
            if (sample.path("format").asText("Raw") != "SignedData") return@mapNotNull null
            val value = sample.path("value").asText().takeIf { it.isNotBlank() && it.length <= 20000 } ?: return@mapNotNull null
            SignedReading(sample.path("context").asText("Sample.Periodic"), value, signatureFormat(value))
        }

    fun signatureFormat(value: String): String =
        when {
            value.startsWith("OCMF|") -> "OCMF"
            value.trimStart().startsWith("<") -> "XML"
            value.startsWith("AP;") -> "ALFEN"
            else -> "OTHER"
        }

    fun ocmfEnergy(value: String): BigDecimal? {
        if (!value.startsWith("OCMF|")) return null
        val payload = value.substringAfter('|').substringBeforeLast('|')
        val readings =
            runCatching { json.readTree(payload).path("RD") }.getOrNull()?.takeIf { it.isArray } ?: return null
        return readings.toList().asReversed().firstNotNullOfOrNull { reading ->
            val identifier = reading.path("RI").asText()
            if (identifier.isNotEmpty() && !identifier.contains("1.8.0") && !identifier.contains("1.08.00")) {
                return@firstNotNullOfOrNull null
            }
            val amount = reading.path("RV").let { if (it.isNumber) it.decimalValue() else it.asText().toBigDecimalOrNull() }
            when (reading.path("RU").asText("kWh")) {
                "kWh" -> amount?.multiply(BigDecimal(1000))
                "Wh" -> amount
                else -> null
            }?.takeIf { it.signum() >= 0 }
        }
    }

    private fun value(sample: JsonNode): BigDecimal? =
        sample
            .path("value")
            .asText()
            .takeIf { it.length <= 30 && sample.path("format").asText("Raw") == "Raw" }
            ?.toBigDecimalOrNull()

    private fun measurand(sample: JsonNode) = sample.path("measurand").asText(ENERGY)

    private fun outlet(sample: JsonNode) =
        sample.path("location").asText("Outlet") in setOf("Outlet", "EV")

    private fun energy(samples: List<JsonNode>): BigDecimal? =
        samples
            .filter {
                measurand(it) == ENERGY &&
                    !it.has("phase") &&
                    it.path("location").asText("Outlet") == "Outlet"
            }
            .sortedBy { it.path("format").asText("Raw") == "SignedData" }
            .firstNotNullOfOrNull { sample ->
                if (sample.path("format").asText("Raw") == "SignedData") {
                    return@firstNotNullOfOrNull ocmfEnergy(sample.path("value").asText())
                }
                val value = value(sample)?.takeIf { it.signum() >= 0 } ?: return@firstNotNullOfOrNull null
                when (sample.path("unit").asText("Wh")) {
                    "Wh" -> value
                    "kWh" -> value * BigDecimal(1000)
                    else -> null
                }
            }

    private fun power(samples: List<JsonNode>): BigDecimal? =
        summed(samples, POWER, mapOf("W" to BigDecimal.ONE, "kW" to BigDecimal(1000)), "W")

    private fun summed(
        samples: List<JsonNode>,
        name: String,
        units: Map<String, BigDecimal>,
        defaultUnit: String = units.keys.first(),
    ): BigDecimal? {
        val relevant = samples.filter { measurand(it) == name && outlet(it) }
        fun scaled(sample: JsonNode): BigDecimal? {
            val factor = units[sample.path("unit").asText(defaultUnit)] ?: return null
            return value(sample)?.multiply(factor)
        }
        relevant.firstOrNull { !it.has("phase") }?.let { return scaled(it) }
        val phases =
            relevant.filter { it.path("phase").asText() in setOf("L1", "L2", "L3") }.mapNotNull(::scaled)
        return if (phases.isEmpty()) null else phases.fold(BigDecimal.ZERO, BigDecimal::add)
    }

    private fun soc(samples: List<JsonNode>): Int? =
        samples
            .firstOrNull { measurand(it) == SOC }
            ?.let(::value)
            ?.toInt()
            ?.takeIf { it in 0..100 }

    private fun voltage(samples: List<JsonNode>): BigDecimal? {
        val relevant = samples.filter { measurand(it) == VOLTAGE }
        val sample =
            relevant.firstOrNull { !it.has("phase") }
                ?: relevant.firstOrNull { it.path("phase").asText() in phaseVoltages }
        return sample?.let(::value)
    }
}
