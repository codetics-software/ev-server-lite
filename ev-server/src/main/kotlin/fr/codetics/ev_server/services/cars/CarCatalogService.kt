package fr.codetics.ev_server.services.cars

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.ImportResult
import fr.codetics.ev_server.core.InvalidRequest
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.models.entities.RecordEntity
import fr.codetics.ev_server.models.entities.RecordKey
import fr.codetics.ev_server.models.organization.CarCatalogData
import fr.codetics.ev_server.repositories.RecordRepository
import fr.codetics.ev_server.services.assets.AssetConnectionError
import fr.codetics.ev_server.services.assets.AssetHttp
import fr.codetics.ev_server.services.organization.Organization
import java.math.BigDecimal
import java.net.http.HttpRequest
import java.time.Instant
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode

@Service
class CarCatalogService(
    private val db: Database,
    private val http: AssetHttp,
    private val records: RecordRepository,
    private val organization: Organization,
    private val properties: AppProperties,
) {
    fun synchronize(user: AppPrincipal): ImportResult {
        val key = properties.evDatabase.apiKey
        ensure(key.isNotBlank()) { "Configurez EV_DATABASE_API_KEY pour synchroniser le catalogue." }
        val data =
            try {
                http.send(HttpRequest.newBuilder(http.uri(properties.evDatabase.url, "/$key")).GET())
            } catch (error: AssetConnectionError) {
                throw InvalidRequest(error.message ?: "EV Database est indisponible.")
            }
        ensure(data.isArray) { "La réponse d’EV Database est inattendue." }
        val existing =
            organization.raw(user.tenantId, "car-catalog").associateBy { db.decode(it.data).path("externalId").asText() }
        var created = 0
        var updated = 0
        var skipped = 0
        db.atomic {
            data.forEach { vehicle ->
                val catalog = map(vehicle)
                if (catalog == null) {
                    skipped += 1
                    return@forEach
                }
                val name = listOfNotNull(catalog.make, catalog.model, catalog.version).joinToString(" ").take(200)
                val encoded = db.encode(catalog)
                val record = existing[catalog.externalId]
                if (record == null) {
                    records.save(RecordEntity(key = RecordKey(user.tenantId, "car-catalog"), name = name, data = encoded))
                    created += 1
                } else if (record.data != encoded || record.name != name) {
                    record.name = name
                    record.data = encoded
                    record.updatedAt = Instant.now()
                    records.save(record)
                    updated += 1
                }
            }
            db.audit(user.tenantId, user.id.toString(), "CAR_CATALOG_SYNCHRONIZE", "${created + updated}")
        }
        return ImportResult(created = created, updated = updated, skipped = skipped)
    }

    private fun map(vehicle: JsonNode): CarCatalogData? {
        fun text(field: String) = vehicle.get(field)?.takeIf { !it.isNull }?.asText()?.trim()?.ifBlank { null }
        fun decimal(vararg fields: String) =
            fields.firstNotNullOfOrNull { text(it)?.replace(',', '.')?.toBigDecimalOrNull() }?.takeIf { it.signum() >= 0 && it < BigDecimal(1000) }
        val make = text("Vehicle_Make")?.take(100) ?: return null
        val model = text("Vehicle_Model")?.take(100) ?: return null
        val images = vehicle.path("Images")
        val image =
            (if (images.isArray) images.firstOrNull()?.asText() else text("Images"))?.takeIf {
                it.startsWith("https://") && it.length <= 500
            }
        val catalog =
            CarCatalogData(
                make = make,
                model = model,
                version = text("Vehicle_Model_Version")?.take(100),
                batteryKwh = decimal("Battery_Capacity_Useable", "Battery_Capacity_Full"),
                maxAcPowerKw = decimal("Charge_Standard_Power_DE", "Charge_Standard_Power_NL", "Charge_Standard_Power_UK"),
                maxDcPowerKw = decimal("Fastcharge_Power_Max"),
                rangeKm = text("Range_Real")?.toIntOrNull()?.takeIf { it in 0..5000 },
                imageUrl = image,
                externalId = text("Vehicle_ID")?.take(100) ?: return null,
            )
        return runCatching { catalog.also { it.validate() } }.getOrNull()
    }
}
