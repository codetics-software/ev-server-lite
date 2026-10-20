package fr.codetics.ev_server.services.pricing

import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.TransactionEntity
import fr.codetics.ev_server.models.organization.PricingData
import fr.codetics.ev_server.services.organization.Entry
import fr.codetics.ev_server.services.organization.Organization
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.springframework.stereotype.Service

data class PricingContext(
    val tenant: UUID,
    val stationId: String,
    val connectorId: Int,
    val siteAreaId: UUID?,
    val siteId: UUID?,
    val zone: ZoneId,
    val startedAt: Instant,
    val energyKwh: BigDecimal? = null,
    val durationMinutes: Long? = null,
)

data class PricedSession(
    val definitionId: UUID,
    val currency: String,
    val price: BigDecimal,
)

@Service
class PricingService(private val organization: Organization) {
    fun context(
        station: StationEntity,
        connectorId: Int,
        at: Instant,
        energyKwh: BigDecimal? = null,
        durationMinutes: Long? = null,
    ): PricingContext {
        val location = organization.location(station)
        return PricingContext(
            station.key.tenantId,
            station.key.stationId,
            connectorId,
            location.area?.id,
            location.site?.id,
            location.site?.data?.zone() ?: ZoneId.of("Europe/Paris"),
            at,
            energyKwh,
            durationMinutes,
        )
    }

    fun resolve(
        context: PricingContext,
        definitions: List<Entry<PricingData>> = organization.pricing(context.tenant),
    ): Entry<PricingData>? =
        definitions
            .filter { applies(it.data, context) }
            .sortedWith(
                compareByDescending<Entry<PricingData>> { it.data.scope?.specificity() ?: 1 }
                    .thenByDescending { it.data.priority }
                    .thenBy { it.name }
                    .thenBy { it.id }
            )
            .firstOrNull()

    fun price(
        transaction: TransactionEntity,
        station: StationEntity,
        stoppedAt: Instant = transaction.stoppedAt ?: Instant.now(),
        energyWh: BigDecimal? = null,
        definitions: List<Entry<PricingData>>? = null,
    ): PricedSession? {
        val delivered =
            (energyWh
                    ?: transaction.meterStopWh?.subtract(transaction.meterStartWh)
                    ?: transaction.lastEnergyWh?.subtract(transaction.meterStartWh)
                    ?: BigDecimal.ZERO)
                .max(BigDecimal.ZERO)
        val duration = Duration.between(transaction.startedAt, stoppedAt).coerceAtLeast(Duration.ZERO)
        val context =
            context(station, transaction.connectorId, transaction.startedAt)
                .copy(
                    energyKwh = delivered.divide(BigDecimal(1000)),
                    durationMinutes = duration.toMinutes(),
                )
        val definition =
            resolve(context, definitions ?: organization.pricing(transaction.tenantId)) ?: return null
        val amount =
            amount(
                definition.data,
                delivered,
                duration,
                Duration.ofSeconds(transaction.inactivitySeconds.coerceAtMost(duration.seconds)),
            )
        return PricedSession(definition.id, definition.data.currency, amount)
    }

    fun amount(
        data: PricingData,
        energyWh: BigDecimal,
        duration: Duration,
        parking: Duration = Duration.ZERO,
    ): BigDecimal {
        val step = data.energyStepWh?.toBigDecimal()
        val billedWh =
            if (step == null) energyWh
            else energyWh.divide(step, 0, RoundingMode.CEILING).multiply(step)
        val hours = hours(duration)
        val parkingHours = hours(parking)
        val total =
            (data.flatPrice ?: BigDecimal.ZERO) +
                billedWh.divide(BigDecimal(1000)) * (data.energyPrice ?: BigDecimal.ZERO) +
                hours * (data.timePrice ?: BigDecimal.ZERO) +
                parkingHours * (data.parkingPrice ?: BigDecimal.ZERO)
        return total.setScale(2, RoundingMode.HALF_UP)
    }

    private fun hours(duration: Duration) =
        BigDecimal(duration.seconds).divide(BigDecimal(3600), 12, RoundingMode.HALF_UP)

    private fun applies(data: PricingData, context: PricingContext): Boolean {
        if (!data.active) return false
        if (data.validFrom?.isAfter(context.startedAt) == true) return false
        if (data.validTo?.isAfter(context.startedAt) == false) return false

        val scope = data.scope
        if (scope != null) {
            if (scope.siteId != null && scope.siteId != context.siteId) return false
            if (scope.siteAreaId != null && scope.siteAreaId != context.siteAreaId) return false
            if (scope.stationId != null && scope.stationId != context.stationId) return false
            if (scope.connectorId != null && scope.connectorId != context.connectorId) return false
        }

        val restrictions = data.restrictions ?: return true
        val local = context.startedAt.atZone(context.zone)
        if (restrictions.daysOfWeek != null && local.dayOfWeek.value !in restrictions.daysOfWeek) {
            return false
        }
        if (restrictions.timeFrom != null && restrictions.timeTo != null) {
            val from = LocalTime.parse(restrictions.timeFrom)
            val to = LocalTime.parse(restrictions.timeTo)
            val time = local.toLocalTime()
            val inside =
                if (from <= to) time >= from && time < to else time >= from || time < to
            if (!inside) return false
        }
        val energy = context.energyKwh
        if (energy != null) {
            if (restrictions.minEnergyKwh != null && energy < restrictions.minEnergyKwh) return false
            if (restrictions.maxEnergyKwh != null && energy > restrictions.maxEnergyKwh) return false
        }
        val minutes = context.durationMinutes
        if (minutes != null) {
            if (restrictions.minDurationMinutes != null && minutes < restrictions.minDurationMinutes) {
                return false
            }
            if (restrictions.maxDurationMinutes != null && minutes > restrictions.maxDurationMinutes) {
                return false
            }
        }
        return true
    }
}
