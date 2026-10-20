package fr.codetics.ev_server.ocpi

import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.models.entities.ChargingTokenEntity
import fr.codetics.ev_server.models.entities.ConnectorEntity
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.TransactionEntity
import fr.codetics.ev_server.models.organization.PricingData
import fr.codetics.ev_server.models.organization.SiteAreaData
import fr.codetics.ev_server.models.organization.SiteData
import fr.codetics.ev_server.services.organization.Entry
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.MissingResourceException
import java.util.UUID
import org.springframework.stereotype.Component

data class PublishedSite(
    val site: Entry<SiteData>,
    val stations: List<Pair<StationEntity, List<ConnectorEntity>>>,
    val areas: Map<UUID, Entry<SiteAreaData>>,
)

@Component
class OcpiMapper(private val properties: AppProperties) {
    val countryCode get() = properties.ocpi.countryCode
    val partyId get() = properties.ocpi.partyId

    fun timestamp(value: Instant): String = value.truncatedTo(ChronoUnit.SECONDS).toString()

    fun evseUid(station: String, connector: Int) = "$station*$connector"

    fun evseId(station: String, connector: Int): String {
        val compact = "$station$connector".uppercase().filter { it.isLetterOrDigit() }.take(30)
        return "$countryCode*$partyId*E$compact"
    }

    fun parseEvse(uid: String): Pair<String, Int>? {
        val station = uid.substringBeforeLast('*', "")
        val connector = uid.substringAfterLast('*').toIntOrNull()
        return if (station.isBlank() || connector == null) null else station to connector
    }

    fun status(station: StationEntity, connector: ConnectorEntity?): String {
        if (!station.enabled || station.deleted) return "INOPERATIVE"
        if (!station.connected) return "UNKNOWN"
        return when (connector?.status) {
            "Available" -> "AVAILABLE"
            "Preparing", "Charging", "SuspendedEV", "SuspendedEVSE", "Finishing" -> "CHARGING"
            "Reserved" -> "RESERVED"
            "Unavailable" -> "INOPERATIVE"
            "Faulted" -> "OUTOFORDER"
            else -> "UNKNOWN"
        }
    }

    private fun country(code: String?): String =
        try {
            Locale.of("", code ?: "FR").isO3Country.ifBlank { "FRA" }
        } catch (_: MissingResourceException) {
            "FRA"
        }

    private fun standard(type: String?) =
        when (type) {
            "Type1" -> "IEC_62196_T1"
            "CCS" -> "IEC_62196_T2_COMBO"
            "CHAdeMO" -> "CHADEMO"
            "Domestic" -> "DOMESTIC_F"
            "Type3" -> "IEC_62196_T3C"
            "Tesla" -> "TESLA_S"
            else -> "IEC_62196_T2"
        }

    private fun powerType(type: String?, phases: Int) =
        when {
            type in setOf("CCS", "CHAdeMO") -> "DC"
            phases == 1 -> "AC_1_PHASE"
            else -> "AC_3_PHASE"
        }

    fun connector(
        station: StationEntity,
        connector: ConnectorEntity,
        area: SiteAreaData?,
        tariff: UUID?,
    ): Map<String, Any?> {
        val phases = connector.phases ?: area?.numberOfPhases ?: 3
        val dc = connector.type in setOf("CCS", "CHAdeMO")
        val voltage = if (dc) 400 else area?.voltage ?: 230
        val maximumKw = connector.maximumPowerKw ?: station.maximumPowerKw ?: BigDecimal(22)
        val amperage =
            maximumKw.multiply(BigDecimal(1000))
                .divide(BigDecimal(voltage * (if (dc) 1 else phases)), 0, RoundingMode.DOWN)
                .toInt()
                .coerceAtLeast(1)
        return mapOf(
            "id" to connector.key.connectorId.toString(),
            "standard" to standard(connector.type),
            "format" to if (dc || connector.type == "Cable") "CABLE" else "SOCKET",
            "power_type" to powerType(connector.type, phases),
            "voltage" to voltage,
            "amperage" to amperage,
            "tariff_id" to tariff?.toString(),
            "last_updated" to timestamp(maxOf(connector.statusAt, station.createdAt)),
        ).filterValues { it != null }
    }

    fun evse(
        station: StationEntity,
        connector: ConnectorEntity,
        area: SiteAreaData?,
        tariff: UUID?,
    ): Map<String, Any?> {
        val coordinates =
            if (station.latitude != null && station.longitude != null) {
                mapOf("latitude" to station.latitude!!.toPlainString(), "longitude" to station.longitude!!.toPlainString())
            } else {
                null
            }
        return mapOf(
            "uid" to evseUid(station.key.stationId, connector.key.connectorId),
            "evse_id" to evseId(station.key.stationId, connector.key.connectorId),
            "status" to status(station, connector),
            "capabilities" to listOf("REMOTE_START_STOP_CAPABLE", "RFID_READER", "UNLOCK_CAPABLE", "RESERVABLE"),
            "connectors" to listOf(connector(station, connector, area, tariff)),
            "coordinates" to coordinates,
            "physical_reference" to station.name.take(16),
            "last_updated" to timestamp(maxOf(connector.statusAt, station.lastSeen ?: station.createdAt)),
        ).filterValues { it != null }
    }

    fun location(published: PublishedSite, tariffs: (StationEntity, Int) -> UUID?): Map<String, Any?>? {
        val site = published.site
        val latitude = site.data.latitude ?: published.stations.firstNotNullOfOrNull { it.first.latitude }
        val longitude = site.data.longitude ?: published.stations.firstNotNullOfOrNull { it.first.longitude }
        if (latitude == null || longitude == null) return null
        val evses =
            published.stations.flatMap { (station, connectors) ->
                connectors.filter { it.key.connectorId > 0 }.map { connector ->
                    evse(station, connector, station.siteAreaId?.let(published.areas::get)?.data, tariffs(station, connector.key.connectorId))
                }
            }
        if (evses.isEmpty()) return null
        val updated =
            (listOf(site.updatedAt) + evses.mapNotNull { (it["last_updated"] as String?)?.let(Instant::parse) }).max()
        return mapOf(
            "id" to site.id.toString(),
            "type" to "PARKING_LOT",
            "name" to site.name,
            "address" to (site.data.address ?: site.name),
            "city" to (site.data.city ?: "-"),
            "postal_code" to (site.data.postalCode ?: "00000"),
            "country" to country(site.data.country),
            "coordinates" to mapOf("latitude" to latitude.toPlainString(), "longitude" to longitude.toPlainString()),
            "evses" to evses,
            "operator" to mapOf("name" to properties.ocpi.partyId),
            "time_zone" to site.data.timezone,
            "opening_times" to mapOf("twentyfourseven" to true),
            "last_updated" to timestamp(updated),
        )
    }

    fun tariff(definition: Entry<PricingData>): Map<String, Any?> {
        val data = definition.data
        val components =
            listOfNotNull(
                data.energyPrice?.takeIf { it.signum() > 0 }?.let { component("ENERGY", it, data.energyStepWh ?: 1) },
                data.timePrice?.takeIf { it.signum() > 0 }?.let { component("TIME", it, 60) },
                data.flatPrice?.takeIf { it.signum() > 0 }?.let { component("FLAT", it, 1) },
                data.parkingPrice?.takeIf { it.signum() > 0 }?.let { component("PARKING_TIME", it, 60) },
            ).ifEmpty { listOf(component("FLAT", BigDecimal.ZERO, 1)) }
        val days = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
        val restrictions =
            data.restrictions?.let { r ->
                mapOf(
                    "start_time" to r.timeFrom,
                    "end_time" to r.timeTo,
                    "start_date" to data.validFrom?.toString()?.take(10),
                    "end_date" to data.validTo?.toString()?.take(10),
                    "min_kwh" to r.minEnergyKwh,
                    "max_kwh" to r.maxEnergyKwh,
                    "min_duration" to r.minDurationMinutes?.let { it * 60 },
                    "max_duration" to r.maxDurationMinutes?.let { it * 60 },
                    "day_of_week" to r.daysOfWeek?.map { days[it - 1] },
                ).filterValues { it != null }.takeIf { it.isNotEmpty() }
            }
        return mapOf(
            "id" to definition.id.toString(),
            "currency" to data.currency,
            "tariff_alt_text" to listOf(mapOf("language" to "fr", "text" to definition.name)),
            "elements" to listOf(mapOf("price_components" to components, "restrictions" to restrictions).filterValues { it != null }),
            "last_updated" to timestamp(definition.updatedAt),
        )
    }

    private fun component(type: String, price: BigDecimal, step: Int) =
        mapOf("type" to type, "price" to price.setScale(4, RoundingMode.HALF_UP), "step_size" to step)

    private fun energyKwh(transaction: TransactionEntity): BigDecimal =
        ((transaction.meterStopWh ?: transaction.lastEnergyWh ?: transaction.meterStartWh) - transaction.meterStartWh)
            .max(BigDecimal.ZERO)
            .divide(BigDecimal(1000), 3, RoundingMode.HALF_UP)

    private fun hours(duration: Duration) =
        BigDecimal(duration.seconds.coerceAtLeast(0)).divide(BigDecimal(3600), 4, RoundingMode.HALF_UP)

    private fun sessionLocation(location: Map<String, Any?>, transaction: TransactionEntity): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        val evses = location["evses"] as List<Map<String, Any?>>
        val uid = evseUid(transaction.stationId, transaction.connectorId)
        return location + ("evses" to evses.filter { it["uid"] == uid })
    }

    fun session(transaction: TransactionEntity, location: Map<String, Any?>, authorizationMethod: String): Map<String, Any?> {
        val end = transaction.stoppedAt
        val updated = listOfNotNull(transaction.startedAt, transaction.lastMeterAt, end).max()
        return mapOf(
            "id" to transaction.id.toString(),
            "start_datetime" to timestamp(transaction.startedAt),
            "end_datetime" to end?.let(::timestamp),
            "kwh" to energyKwh(transaction),
            "auth_id" to transaction.tokenUid,
            "auth_method" to authorizationMethod,
            "location" to sessionLocation(location, transaction),
            "meter_id" to transaction.stationId,
            "currency" to (transaction.currency ?: "EUR"),
            "total_cost" to transaction.price,
            "status" to if (end == null) "ACTIVE" else "COMPLETED",
            "last_updated" to timestamp(updated),
        ).filterValues { it != null }
    }

    fun cdr(
        transaction: TransactionEntity,
        location: Map<String, Any?>,
        authorizationMethod: String,
        tariff: Map<String, Any?>?,
    ): Map<String, Any?> {
        val stop = requireNotNull(transaction.stoppedAt)
        val total = Duration.between(transaction.startedAt, stop)
        val parking = Duration.ofSeconds(transaction.inactivitySeconds.coerceAtMost(total.seconds))
        val energy = energyKwh(transaction)
        return mapOf(
            "id" to "CDR-${transaction.id}",
            "start_date_time" to timestamp(transaction.startedAt),
            "stop_date_time" to timestamp(stop),
            "auth_id" to transaction.tokenUid,
            "auth_method" to authorizationMethod,
            "location" to sessionLocation(location, transaction),
            "meter_id" to transaction.stationId,
            "currency" to (transaction.currency ?: "EUR"),
            "tariffs" to tariff?.let { listOf(it) },
            "charging_periods" to
                listOf(
                    mapOf(
                        "start_date_time" to timestamp(transaction.startedAt),
                        "dimensions" to
                            listOfNotNull(
                                mapOf("type" to "ENERGY", "volume" to energy),
                                mapOf("type" to "TIME", "volume" to hours(total - parking)),
                                if (parking.isZero) null else mapOf("type" to "PARKING_TIME", "volume" to hours(parking)),
                            ),
                    )
                ),
            "total_cost" to (transaction.price ?: BigDecimal.ZERO),
            "total_energy" to energy,
            "total_time" to hours(total),
            "total_parking_time" to hours(parking),
            "last_updated" to timestamp(stop),
        ).filterValues { it != null }
    }

    fun token(token: ChargingTokenEntity, issuer: String, updatedAt: Instant): Map<String, Any?> =
        mapOf(
            "uid" to token.key.uid,
            "type" to if (token.type == "RFID") "RFID" else "OTHER",
            "auth_id" to token.key.uid,
            "visual_number" to token.visualId,
            "issuer" to issuer.take(64),
            "valid" to (token.enabled && token.expiresAt?.isBefore(Instant.now()) != true),
            "whitelist" to "ALLOWED",
            "language" to "fr",
            "last_updated" to timestamp(updatedAt),
        ).filterValues { it != null }
}
