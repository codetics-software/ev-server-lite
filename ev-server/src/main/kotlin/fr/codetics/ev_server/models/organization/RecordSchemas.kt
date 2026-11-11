package fr.codetics.ev_server.models.organization

import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.core.msg
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

interface RecordData {
    fun validate() {}

    fun references(): List<Reference> = emptyList()
}

data class Reference(val kind: String, val id: UUID, val label: String)

private val COUNTRY = Regex("[A-Z]{2}")
private val CURRENCY = Regex("[A-Z]{3}")
private val CONNECTOR_TYPES =
    setOf("Type2", "Type1", "CCS", "CHAdeMO", "Domestic", "Type3", "Tesla", "Cable", "Other")

private fun text(value: String?, label: String, maximum: Int = 200) {
    ensure(value == null || value.length <= maximum) { msg("records.text_too_long", label, maximum) }
}

private fun positive(value: BigDecimal?, label: String, maximum: BigDecimal = BigDecimal(100000)) {
    ensure(value == null || (value.signum() >= 0 && value <= maximum && value.scale() <= 6)) {
        msg("records.positive", label)
    }
}

private fun country(value: String?) {
    ensure(value == null || value.matches(COUNTRY)) {
        msg("records.country")
    }
}

private fun coordinates(latitude: BigDecimal?, longitude: BigDecimal?) {
    ensure((latitude == null) == (longitude == null)) {
        msg("records.coordinates_together")
    }
    ensure(latitude == null || latitude.abs() <= BigDecimal(90)) { msg("records.latitude") }
    ensure(longitude == null || longitude.abs() <= BigDecimal(180)) { msg("records.longitude") }
}

data class CompanyData(
    val address: String? = null,
    val postalCode: String? = null,
    val city: String? = null,
    val country: String? = null,
    val vatNumber: String? = null,
    val email: String? = null,
    val phone: String? = null,
) : RecordData {
    override fun validate() {
        text(address, msg("field.address"))
        text(postalCode, msg("field.postal_code"), 20)
        text(city, msg("field.city"), 100)
        country(country)
        text(vatNumber, msg("field.vat_number"), 40)
        text(email, msg("field.email"), 320)
        text(phone, msg("field.phone"), 40)
    }
}

data class SiteData(
    val companyId: UUID,
    val address: String? = null,
    val postalCode: String? = null,
    val city: String? = null,
    val country: String? = null,
    val latitude: BigDecimal? = null,
    val longitude: BigDecimal? = null,
    val accessControl: Boolean = false,
    val autoUserAssignment: Boolean = false,
    val public: Boolean = false,
    val timezone: String = "Europe/Paris",
) : RecordData {
    override fun validate() {
        text(address, msg("field.address"))
        text(postalCode, msg("field.postal_code"), 20)
        text(city, msg("field.city"), 100)
        country(country)
        coordinates(latitude, longitude)
        ensure(runCatching { ZoneId.of(timezone) }.isSuccess) { msg("records.time_zone") }
    }

    override fun references() = listOf(Reference("companies", companyId, msg("field.company")))

    fun zone(): ZoneId = ZoneId.of(timezone)
}

data class SiteAreaData(
    val siteId: UUID,
    val maximumPowerKw: BigDecimal? = null,
    val numberOfPhases: Int = 3,
    val voltage: Int = 230,
    val smartCharging: Boolean = false,
    val parentSiteAreaId: UUID? = null,
) : RecordData {
    override fun validate() {
        positive(maximumPowerKw, msg("field.available_power"))
        ensure(numberOfPhases in setOf(1, 3)) { msg("records.area_phases") }
        ensure(voltage in 100..1000) { msg("records.voltage") }
        ensure(!smartCharging || maximumPowerKw != null) {
            msg("records.smart_charging_power")
        }
    }

    override fun references() =
        listOfNotNull(
            Reference("sites", siteId, msg("field.site")),
            parentSiteAreaId?.let { Reference("site-areas", it, msg("field.parent_area")) },
        )
}

data class AssetConnectionData(
    val type: String = "NONE",
    val url: String? = null,
    val authenticationUrl: String? = null,
    val meterId: String? = null,
    val user: String? = null,
    val clientId: String? = null,
    val refreshSeconds: Int = 300,
) {
    fun validate() {
        ensure(type in ASSET_CONNECTIONS) { msg("records.asset_connection_type") }
        text(url, msg("field.address"), 500)
        text(authenticationUrl, msg("field.auth_url"), 500)
        listOfNotNull(url, authenticationUrl).forEach { address ->
            ensure(address.startsWith("https://")) { msg("records.connection_https") }
        }
        ensure(type in setOf("NONE", "PUSH") || (url != null && meterId != null)) {
            msg("records.connection_meter")
        }
        ensure(type != "WIT" || authenticationUrl != null) {
            msg("records.wit_auth_url")
        }
        text(clientId, msg("field.client_id"), 200)
        text(meterId, msg("field.meter_id"), 100)
        text(user, msg("field.user"), 200)
        ensure(refreshSeconds in 60..86400) {
            msg("records.refresh_interval")
        }
    }

    companion object {
        val ASSET_CONNECTIONS =
            setOf("NONE", "PUSH", "SCHNEIDER", "GREENCOM", "IOTHINK", "LACROIX", "WIT")
    }
}

data class AssetData(
    val siteAreaId: UUID? = null,
    val type: String = "CONSUMPTION",
    val kind: String? = null,
    val maximumPowerKw: BigDecimal? = null,
    val staticPowerKw: BigDecimal? = null,
    val excludeFromSmartCharging: Boolean = false,
    val connection: AssetConnectionData? = null,
) : RecordData {
    override fun validate() {
        ensure(type in setOf("CONSUMPTION", "PRODUCTION", "CONSUMPTION_AND_PRODUCTION")) {
            msg("records.asset_type")
        }
        ensure(kind == null || kind in setOf("METER", "SOLAR", "BATTERY", "BUILDING")) {
            msg("records.asset_category")
        }
        positive(maximumPowerKw, msg("field.asset_power"))
        positive(staticPowerKw, msg("field.fixed_power"))
        connection?.validate()
    }

    override fun references() =
        listOfNotNull(siteAreaId?.let { Reference("site-areas", it, msg("field.area")) })

    fun producing() = type == "PRODUCTION"
}

data class CarData(
    val userId: UUID? = null,
    val carCatalogId: UUID? = null,
    val vin: String? = null,
    val licensePlate: String? = null,
    val make: String? = null,
    val model: String? = null,
    val batteryKwh: BigDecimal? = null,
    val maxAcPowerKw: BigDecimal? = null,
    val maxDcPowerKw: BigDecimal? = null,
    val default: Boolean = false,
    val carConnectorId: UUID? = null,
    val carConnectorMeterId: String? = null,
) : RecordData {
    override fun validate() {
        text(carConnectorMeterId, msg("field.connector_vehicle_id"), 100)
        ensure(vin == null || vin.matches(Regex("[A-HJ-NPR-Z0-9]{17}"))) {
            msg("records.vin")
        }
        text(licensePlate, msg("field.license_plate"), 20)
        text(make, msg("field.make"), 100)
        text(model, msg("field.model"), 100)
        positive(batteryKwh, msg("field.battery_capacity"), BigDecimal(1000))
        positive(maxAcPowerKw, msg("field.ac_power"), BigDecimal(1000))
        positive(maxDcPowerKw, msg("field.dc_power"), BigDecimal(1000))
    }

    override fun references() =
        listOfNotNull(
            userId?.let { Reference("users", it, msg("field.user")) },
            carCatalogId?.let { Reference("car-catalog", it, msg("field.catalog_model")) },
            carConnectorId?.let { Reference("car-connectors", it, msg("field.vehicle_connector")) },
        )
}

data class CarConnectorData(
    val type: String,
    val apiUrl: String,
    val authenticationUrl: String? = null,
    val clientId: String,
) : RecordData {
    override fun validate() {
        ensure(type in setOf("TRONITY", "TARGA")) { msg("records.vehicle_connector_type") }
        listOfNotNull(apiUrl, authenticationUrl).forEach {
            ensure(it.startsWith("https://") && it.length <= 500) { msg("records.vehicle_connector_https") }
        }
        ensure(type != "TARGA" || authenticationUrl != null) { msg("records.targa_auth_url") }
        ensure(clientId.isNotBlank() && clientId.length <= 200) { msg("records.client_id_required") }
    }
}

data class CarCatalogData(
    val make: String,
    val model: String,
    val version: String? = null,
    val batteryKwh: BigDecimal? = null,
    val maxAcPowerKw: BigDecimal? = null,
    val maxDcPowerKw: BigDecimal? = null,
    val rangeKm: Int? = null,
    val imageUrl: String? = null,
    val externalId: String? = null,
) : RecordData {
    override fun validate() {
        ensure(make.isNotBlank() && make.length <= 100) { msg("records.make_required") }
        ensure(model.isNotBlank() && model.length <= 100) { msg("records.model_required") }
        text(version, msg("field.version"), 100)
        positive(batteryKwh, msg("field.battery_capacity"), BigDecimal(1000))
        positive(maxAcPowerKw, msg("field.ac_power"), BigDecimal(1000))
        positive(maxDcPowerKw, msg("field.dc_power"), BigDecimal(1000))
        ensure(rangeKm == null || rangeKm in 0..5000) { msg("records.range") }
        ensure(imageUrl == null || (imageUrl.startsWith("https://") && imageUrl.length <= 500)) {
            msg("records.image_https")
        }
        text(externalId, msg("field.external_id"), 100)
    }
}

data class PricingScope(
    val siteId: UUID? = null,
    val siteAreaId: UUID? = null,
    val stationId: String? = null,
    val connectorId: Int? = null,
) {
    fun specificity() =
        when {
            connectorId != null -> 5
            stationId != null -> 4
            siteAreaId != null -> 3
            siteId != null -> 2
            else -> 1
        }
}

data class PricingRestrictions(
    val daysOfWeek: List<Int>? = null,
    val timeFrom: String? = null,
    val timeTo: String? = null,
    val minEnergyKwh: BigDecimal? = null,
    val maxEnergyKwh: BigDecimal? = null,
    val minDurationMinutes: Int? = null,
    val maxDurationMinutes: Int? = null,
) {
    fun validate() {
        ensure(daysOfWeek == null || (daysOfWeek.isNotEmpty() && daysOfWeek.all { it in 1..7 })) {
            msg("records.days")
        }
        ensure((timeFrom == null) == (timeTo == null)) {
            msg("records.hours_together")
        }
        listOfNotNull(timeFrom, timeTo).forEach { time ->
            ensure(runCatching { LocalTime.parse(time) }.isSuccess) {
                msg("records.hours_format")
            }
        }
        positive(minEnergyKwh, msg("field.minimum_energy"))
        positive(maxEnergyKwh, msg("field.maximum_energy"))
        ensure(minDurationMinutes == null || minDurationMinutes >= 0) { msg("records.minimum_duration") }
        ensure(maxDurationMinutes == null || maxDurationMinutes > 0) { msg("records.maximum_duration") }
    }
}

data class PricingData(
    val currency: String = "EUR",
    val flatPrice: BigDecimal? = null,
    val energyPrice: BigDecimal? = null,
    val timePrice: BigDecimal? = null,
    val parkingPrice: BigDecimal? = null,
    val energyStepWh: Int? = null,
    val scope: PricingScope? = null,
    val validFrom: Instant? = null,
    val validTo: Instant? = null,
    val restrictions: PricingRestrictions? = null,
    val priority: Int = 0,
    val active: Boolean = true,
) : RecordData {
    override fun validate() {
        ensure(currency.matches(CURRENCY)) { msg("records.currency") }
        listOf(flatPrice, energyPrice, timePrice, parkingPrice).forEach {
            positive(it, msg("field.price"), BigDecimal(10000))
        }
        ensure(energyStepWh == null || energyStepWh in 1..100000) { msg("records.billing_step") }
        ensure(validFrom == null || validTo == null || validFrom.isBefore(validTo)) {
            msg("records.validity_order")
        }
        ensure(scope?.connectorId == null || scope.stationId != null) {
            msg("records.connector_station")
        }
        ensure(priority in -1000..1000) { msg("records.priority") }
        restrictions?.validate()
    }

    override fun references() =
        listOfNotNull(
            scope?.siteId?.let { Reference("sites", it, msg("field.site")) },
            scope?.siteAreaId?.let { Reference("site-areas", it, msg("field.area")) },
        )
}

data class TemplateConnector(
    val connectorId: Int,
    val type: String? = null,
    val maximumPowerKw: BigDecimal? = null,
    val phases: Int? = null,
) {
    fun validate() {
        ensure(connectorId in 1..100) { msg("connector.invalid_number") }
        ensure(type == null || type in CONNECTOR_TYPES) { msg("records.connector_type") }
        positive(maximumPowerKw, msg("field.connector_power"), BigDecimal(1000))
        ensure(phases == null || phases in setOf(1, 3)) { msg("connector.phases") }
    }
}

data class TemplateParameter(val key: String, val value: String) {
    fun validate() {
        ensure(key.matches(Regex("[A-Za-z0-9_.:-]{1,100}"))) { msg("ocpp.parameter.invalid_name") }
        ensure(value.length <= 500) { msg("ocpp.parameter.value_too_long") }
        ensure(!key.equals("SecurityProfile", true) && !key.equals("AuthorizationKey", true)) {
            msg("records.security_parameter_in_template")
        }
    }
}

data class TemplateData(
    val vendor: String,
    val model: String,
    val firmwareVersion: String? = null,
    val maximumPowerKw: BigDecimal? = null,
    val connectors: List<TemplateConnector> = emptyList(),
    val parameters: List<TemplateParameter> = emptyList(),
) : RecordData {
    override fun validate() {
        listOfNotNull(vendor, model, firmwareVersion).forEach { pattern ->
            ensure(pattern.isNotBlank() && pattern.length <= 200) {
                msg("records.template_patterns_required")
            }
            ensure(runCatching { Regex(pattern) }.isSuccess) {
                msg("records.template_pattern_invalid", pattern)
            }
        }
        positive(maximumPowerKw, msg("field.power"), BigDecimal(10000))
        ensure(connectors.size <= 100 && connectors.map { it.connectorId }.toSet().size == connectors.size) {
            msg("records.connector_once")
        }
        connectors.forEach(TemplateConnector::validate)
        ensure(parameters.size <= 200 && parameters.map { it.key }.toSet().size == parameters.size) {
            msg("records.parameter_once")
        }
        parameters.forEach(TemplateParameter::validate)
    }

    fun matches(vendor: String?, model: String?, firmware: String?): Boolean {
        fun test(pattern: String?, value: String?) =
            pattern == null || (value != null && Regex(pattern, RegexOption.IGNORE_CASE).matches(value))
        return test(this.vendor, vendor) && test(this.model, model) && test(firmwareVersion, firmware)
    }
}

data class SettingData(val value: String) : RecordData {
    override fun validate() {
        ensure(value.length <= 2000) { msg("records.value_too_long") }
    }
}

object RecordKinds {
    val types: Map<String, Class<out RecordData>> =
        mapOf(
            "companies" to CompanyData::class.java,
            "sites" to SiteData::class.java,
            "site-areas" to SiteAreaData::class.java,
            "assets" to AssetData::class.java,
            "cars" to CarData::class.java,
            "car-catalog" to CarCatalogData::class.java,
            "car-connectors" to CarConnectorData::class.java,
            "pricing-definitions" to PricingData::class.java,
            "charging-station-templates" to TemplateData::class.java,
            "settings" to SettingData::class.java,
        )

    val children: Map<String, List<Pair<String, String>>> =
        mapOf(
            "companies" to listOf("sites" to "companyId"),
            "sites" to listOf("site-areas" to "siteId"),
            "site-areas" to listOf("site-areas" to "parentSiteAreaId", "assets" to "siteAreaId"),
            "car-catalog" to listOf("cars" to "carCatalogId"),
            "car-connectors" to listOf("cars" to "carConnectorId"),
        )

    val filters = setOf("companyId", "siteId", "siteAreaId", "userId", "carCatalogId")
}
