package fr.codetics.ev_server.billing

import fr.codetics.ev_server.core.InvalidRequest
import fr.codetics.ev_server.models.billing.ChargingTariff
import java.math.BigDecimal
import java.time.Instant
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ChargingTariffTests {
    private val start = Instant.parse("2026-01-01T10:00:00Z")

    private fun tariff(energy: String = "0", time: String = "0", flat: String = "0") =
        ChargingTariff("EUR", BigDecimal(energy), BigDecimal(time), BigDecimal(flat))

    @Test
    fun `le montant additionne energie, duree et forfait`() {
        val amount =
            tariff(energy = "0.30", time = "2", flat = "1")
                .amountMinor(BigDecimal(10000), start, start.plusSeconds(3600))

        assertEquals(600, amount)
    }

    @Test
    fun `le montant est arrondi au centime superieur a partir du demi centime`() {
        assertEquals(3, tariff(energy = "0.25").amountMinor(BigDecimal(100), start, start))
        assertEquals(2, tariff(energy = "0.24").amountMinor(BigDecimal(100), start, start))
    }

    @Test
    fun `une duree ou une energie negative ne reduit pas le montant`() {
        val amount =
            tariff(energy = "1", time = "1", flat = "0.5")
                .amountMinor(BigDecimal(-5000), start, start.minusSeconds(3600))

        assertEquals(50, amount)
    }

    @Test
    fun `le temps est facture au prorata des secondes`() {
        val amount = tariff(time = "1").amountMinor(BigDecimal.ZERO, start, start.plusSeconds(1800))

        assertEquals(50, amount)
    }

    @Test
    fun `les tarifs invalides sont refuses avec un message utilisateur`() {
        assertThrows(InvalidRequest::class.java) {
            ChargingTariff("USD", BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO)
        }
        assertThrows(InvalidRequest::class.java) { tariff() }
        assertThrows(InvalidRequest::class.java) { tariff(energy = "-1", flat = "1") }
        assertThrows(InvalidRequest::class.java) { tariff(energy = "0.0000001") }
    }
}
