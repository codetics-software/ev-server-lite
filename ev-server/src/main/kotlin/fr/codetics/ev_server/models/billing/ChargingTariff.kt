package fr.codetics.ev_server.models.billing

import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.models.entities.ChargingPaymentEntity
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant

data class ChargingTariff(
    val currency: String,
    val energyPrice: BigDecimal,
    val timePrice: BigDecimal,
    val flatPrice: BigDecimal,
) {
    init {
        ensure(currency == "EUR") { "Le paiement public est disponible en euros." }
        ensure(
            listOf(energyPrice, timePrice, flatPrice).all { it.signum() >= 0 && it.scale() <= 6 }
        ) {
            "Les tarifs doivent être positifs ou nuls, avec au plus six décimales."
        }
        ensure(energyPrice.signum() > 0 || timePrice.signum() > 0 || flatPrice.signum() > 0) {
            "Renseignez au moins un tarif supérieur à zéro."
        }
    }

    fun amountMinor(energyWh: BigDecimal, startedAt: Instant, stoppedAt: Instant): Long {
        val hours =
            BigDecimal(Duration.between(startedAt, stoppedAt).seconds.coerceAtLeast(0))
                .divide(BigDecimal(3600), 12, RoundingMode.HALF_UP)
        val energy = energyWh.max(BigDecimal.ZERO).divide(BigDecimal(1000))
        return (energy * energyPrice + hours * timePrice + flatPrice)
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    }
}

fun ChargingPaymentEntity.tariff() = ChargingTariff(currency, energyPrice, timePrice, flatPrice)
