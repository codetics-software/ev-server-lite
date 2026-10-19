package fr.codetics.ev_server.services.billing

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.core.invalid
import fr.codetics.ev_server.models.billing.ChargingTariff
import fr.codetics.ev_server.models.entities.RecordKey
import fr.codetics.ev_server.repositories.RecordRepository
import java.math.BigDecimal
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class TariffService(private val records: RecordRepository, private val db: Database) {
    fun find(tenantId: UUID, id: UUID): ChargingTariff {
        val record =
            records.findById(RecordKey(tenantId, "pricing-definitions", id)).orElse(null)
                ?: throw ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Le tarif n’existe pas dans cet espace.",
                )
        val data = db.decode(record.data)
        fun amount(field: String): BigDecimal {
            val value = data.path(field).asText("0")
            ensure(value.length <= 30) { "Le montant du tarif est invalide." }
            val decimal =
                value.toBigDecimalOrNull()
                    ?: invalid("Le montant du tarif est invalide.")
            ensure(decimal <= BigDecimal(10000)) { "Le montant du tarif est trop élevé." }
            return decimal
        }
        return ChargingTariff(
            data.path("currency").asText("EUR"),
            amount("energyPrice"),
            amount("timePrice"),
            amount("flatPrice"),
        )
    }
}
