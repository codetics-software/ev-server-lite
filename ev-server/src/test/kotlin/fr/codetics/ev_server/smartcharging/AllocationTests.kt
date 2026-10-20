package fr.codetics.ev_server.smartcharging

import fr.codetics.ev_server.models.entities.TransactionEntity
import fr.codetics.ev_server.models.organization.PricingData
import fr.codetics.ev_server.models.organization.PricingRestrictions
import fr.codetics.ev_server.models.organization.PricingScope
import fr.codetics.ev_server.services.organization.Entry
import fr.codetics.ev_server.services.pricing.PricingContext
import fr.codetics.ev_server.services.pricing.PricingService
import fr.codetics.ev_server.services.smartcharging.Candidate
import fr.codetics.ev_server.services.smartcharging.SmartChargingService
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class AllocationTests {
    private val service =
        Mockito.mock(SmartChargingService::class.java, Mockito.CALLS_REAL_METHODS)

    private fun candidate(id: Int, maximumKw: Int, startedAt: Long) =
        Candidate(
            TransactionEntity(id = id, startedAt = Instant.ofEpochSecond(startedAt)),
            BigDecimal(230 * 3 * 6),
            BigDecimal(maximumKw * 1000),
            230,
            3,
        )

    @Test
    fun `capacity is shared fairly and never exceeds each connector`() {
        val result = service.allocate(BigDecimal(30000), listOf(candidate(1, 22, 1), candidate(2, 7, 2), candidate(3, 22, 3)))
            .associate { it.first.transaction.id to it.second }
        assertEquals(0, BigDecimal(7000).compareTo(result[2]))
        assertEquals(0, BigDecimal(30000).compareTo(result.values.fold(BigDecimal.ZERO, BigDecimal::add).setScale(0, java.math.RoundingMode.HALF_UP)))
        assertEquals(0, result[1]!!.compareTo(result[3]))
    }

    @Test
    fun `sessions beyond the minimum capacity are paused in arrival order`() {
        val result = service.allocate(BigDecimal(5000), listOf(candidate(2, 22, 2), candidate(1, 22, 1)))
            .associate { it.first.transaction.id to it.second }
        assertEquals(0, BigDecimal(5000).compareTo(result[1]))
        assertEquals(0, BigDecimal.ZERO.compareTo(result[2]))
    }

    @Test
    fun `pricing restrictions select the matching period`() {
        val pricing = PricingService(Mockito.mock(fr.codetics.ev_server.services.organization.Organization::class.java))
        val night =
            Entry(
                UUID.randomUUID(),
                "Nuit",
                PricingData(energyPrice = BigDecimal("0.20"), restrictions = PricingRestrictions(timeFrom = "22:00", timeTo = "06:00")),
                0,
                Instant.EPOCH,
            )
        val station =
            Entry(UUID.randomUUID(), "Borne", PricingData(energyPrice = BigDecimal("0.45"), scope = PricingScope(stationId = "CS")), 0, Instant.EPOCH)
        fun context(time: String) =
            PricingContext(UUID.randomUUID(), "OTHER", 1, null, null, ZoneId.of("Europe/Paris"), Instant.parse(time))
        assertEquals("Nuit", pricing.resolve(context("2026-01-10T23:30:00Z"), listOf(night, station))?.name)
        assertNull(pricing.resolve(context("2026-01-10T12:00:00Z"), listOf(night, station)))
        assertEquals("Borne", pricing.resolve(context("2026-01-10T23:30:00Z").copy(stationId = "CS"), listOf(night, station))?.name)
        assertEquals(0, BigDecimal("2.45").compareTo(pricing.amount(station.data.copy(flatPrice = BigDecimal("0.20")), BigDecimal(5000), Duration.ofHours(1))))
    }
}
