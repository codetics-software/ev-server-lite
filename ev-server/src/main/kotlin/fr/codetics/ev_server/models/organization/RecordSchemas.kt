package fr.codetics.ev_server.models.organization

import fr.codetics.ev_server.core.ensure
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
    ensure(value == null || value.length <= maximum) { "$label : $maximum caractères au plus." }
}

private fun positive(value: BigDecimal?, label: String, maximum: BigDecimal = BigDecimal(100000)) {
    ensure(value == null || (value.signum() >= 0 && value <= maximum && value.scale() <= 6)) {
        "$label doit être un nombre positif."
    }
}

private fun country(value: String?) {
    ensure(value == null || value.matches(COUNTRY)) {
        "Le pays s’écrit avec son code ISO à deux lettres majuscules."
    }
}

private fun coordinates(latitude: BigDecimal?, longitude: BigDecimal?) {
    ensure((latitude == null) == (longitude == null)) {
        "Renseignez la latitude et la longitude ensemble."
    }
    ensure(latitude == null || latitude.abs() <= BigDecimal(90)) { "Latitude invalide." }
    ensure(longitude == null || longitude.abs() <= BigDecimal(180)) { "Longitude invalide." }
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
        text(address, "L’adresse")
        text(postalCode, "Le code postal", 20)
        text(city, "La ville", 100)
        country(country)
        text(vatNumber, "Le numéro de TVA", 40)
        text(email, "L’adresse électronique", 320)
        text(phone, "Le téléphone", 40)
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
        text(address, "L’adresse")
        text(postalCode, "Le code postal", 20)
        text(city, "La ville", 100)
        country(country)
        coordinates(latitude, longitude)
        ensure(runCatching { ZoneId.of(timezone) }.isSuccess) { "Fuseau horaire inconnu." }
    }

    override fun references() = listOf(Reference("companies", companyId, "L’entreprise"))

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
        positive(maximumPowerKw, "La puissance disponible")
        ensure(numberOfPhases in setOf(1, 3)) { "Une zone est monophasée ou triphasée." }
        ensure(voltage in 100..1000) { "La tension doit être comprise entre 100 et 1 000 V." }
        ensure(!smartCharging || maximumPowerKw != null) {
            "Renseignez la puissance disponible pour activer la recharge intelligente."
        }
    }

    override fun references() =
        listOfNotNull(
            Reference("sites", siteId, "Le site"),
            parentSiteAreaId?.let { Reference("site-areas", it, "La zone parente") },
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
        ensure(type in ASSET_CONNECTIONS) { "Type de connexion d’équipement inconnu." }
        text(url, "L’adresse", 500)
        text(authenticationUrl, "L’adresse d’authentification", 500)
        listOfNotNull(url, authenticationUrl).forEach { address ->
            ensure(address.startsWith("https://")) { "L’adresse de connexion doit utiliser HTTPS." }
        }
        ensure(type in setOf("NONE", "PUSH") || (url != null && meterId != null)) {
            "Renseignez l’adresse et l’identifiant du compteur de la connexion."
        }
        ensure(type != "WIT" || authenticationUrl != null) {
            "Renseignez l’adresse d’authentification WIT."
        }
        text(clientId, "L’identifiant client", 200)
        text(meterId, "L’identifiant du compteur", 100)
        text(user, "L’utilisateur", 200)
        ensure(refreshSeconds in 60..86400) {
            "La fréquence de relevé doit être comprise entre 60 secondes et un jour."
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
            "Type d’équipement inconnu."
        }
        ensure(kind == null || kind in setOf("METER", "SOLAR", "BATTERY", "BUILDING")) {
            "Catégorie d’équipement inconnue."
        }
        positive(maximumPowerKw, "La puissance de l’équipement")
        positive(staticPowerKw, "La puissance fixe")
        connection?.validate()
    }

    override fun references() =
        listOfNotNull(siteAreaId?.let { Reference("site-areas", it, "La zone") })

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
        text(carConnectorMeterId, "L’identifiant du véhicule chez le connecteur", 100)
        ensure(vin == null || vin.matches(Regex("[A-HJ-NPR-Z0-9]{17}"))) {
            "Le numéro VIN contient 17 caractères (sans I, O ni Q)."
        }
        text(licensePlate, "L’immatriculation", 20)
        text(make, "Le fabricant", 100)
        text(model, "Le modèle", 100)
        positive(batteryKwh, "La capacité de la batterie", BigDecimal(1000))
        positive(maxAcPowerKw, "La puissance AC", BigDecimal(1000))
        positive(maxDcPowerKw, "La puissance DC", BigDecimal(1000))
    }

    override fun references() =
        listOfNotNull(
            userId?.let { Reference("users", it, "L’utilisateur") },
            carCatalogId?.let { Reference("car-catalog", it, "Le modèle du catalogue") },
            carConnectorId?.let { Reference("car-connectors", it, "Le connecteur de véhicule") },
        )
}

data class CarConnectorData(
    val type: String,
    val apiUrl: String,
    val authenticationUrl: String? = null,
    val clientId: String,
) : RecordData {
    override fun validate() {
        ensure(type in setOf("TRONITY", "TARGA")) { "Type de connecteur de véhicule inconnu." }
        listOfNotNull(apiUrl, authenticationUrl).forEach {
            ensure(it.startsWith("https://") && it.length <= 500) { "Les adresses du connecteur doivent utiliser HTTPS." }
        }
        ensure(type != "TARGA" || authenticationUrl != null) { "Renseignez l’adresse d’authentification Targa." }
        ensure(clientId.isNotBlank() && clientId.length <= 200) { "L’identifiant client est requis." }
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
        ensure(make.isNotBlank() && make.length <= 100) { "Le fabricant est requis." }
        ensure(model.isNotBlank() && model.length <= 100) { "Le modèle est requis." }
        text(version, "La version", 100)
        positive(batteryKwh, "La capacité de la batterie", BigDecimal(1000))
        positive(maxAcPowerKw, "La puissance AC", BigDecimal(1000))
        positive(maxDcPowerKw, "La puissance DC", BigDecimal(1000))
        ensure(rangeKm == null || rangeKm in 0..5000) { "Autonomie invalide." }
        ensure(imageUrl == null || (imageUrl.startsWith("https://") && imageUrl.length <= 500)) {
            "L’image doit être une adresse HTTPS."
        }
        text(externalId, "L’identifiant externe", 100)
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
            "Les jours vont de 1 (lundi) à 7 (dimanche)."
        }
        ensure((timeFrom == null) == (timeTo == null)) {
            "Renseignez l’heure de début et l’heure de fin ensemble."
        }
        listOfNotNull(timeFrom, timeTo).forEach { time ->
            ensure(runCatching { LocalTime.parse(time) }.isSuccess) {
                "Les heures s’écrivent au format HH:mm."
            }
        }
        positive(minEnergyKwh, "L’énergie minimale")
        positive(maxEnergyKwh, "L’énergie maximale")
        ensure(minDurationMinutes == null || minDurationMinutes >= 0) { "Durée minimale invalide." }
        ensure(maxDurationMinutes == null || maxDurationMinutes > 0) { "Durée maximale invalide." }
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
        ensure(currency.matches(CURRENCY)) { "La devise s’écrit avec son code ISO à trois lettres." }
        listOf(flatPrice, energyPrice, timePrice, parkingPrice).forEach {
            positive(it, "Le prix", BigDecimal(10000))
        }
        ensure(energyStepWh == null || energyStepWh in 1..100000) { "Pas de facturation invalide." }
        ensure(validFrom == null || validTo == null || validFrom.isBefore(validTo)) {
            "La fin de validité doit suivre son début."
        }
        ensure(scope?.connectorId == null || scope.stationId != null) {
            "Choisissez la borne du connecteur."
        }
        ensure(priority in -1000..1000) { "La priorité doit être comprise entre -1 000 et 1 000." }
        restrictions?.validate()
    }

    override fun references() =
        listOfNotNull(
            scope?.siteId?.let { Reference("sites", it, "Le site") },
            scope?.siteAreaId?.let { Reference("site-areas", it, "La zone") },
        )
}

data class TemplateConnector(
    val connectorId: Int,
    val type: String? = null,
    val maximumPowerKw: BigDecimal? = null,
    val phases: Int? = null,
) {
    fun validate() {
        ensure(connectorId in 1..100) { "Numéro de connecteur invalide." }
        ensure(type == null || type in CONNECTOR_TYPES) { "Type de connecteur inconnu." }
        positive(maximumPowerKw, "La puissance du connecteur", BigDecimal(1000))
        ensure(phases == null || phases in setOf(1, 3)) { "Un connecteur est monophasé ou triphasé." }
    }
}

data class TemplateParameter(val key: String, val value: String) {
    fun validate() {
        ensure(key.matches(Regex("[A-Za-z0-9_.:-]{1,100}"))) { "Nom de paramètre OCPP invalide." }
        ensure(value.length <= 500) { "La valeur d’un paramètre OCPP contient 500 caractères au plus." }
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
                "Les motifs du modèle de configuration sont requis."
            }
            ensure(runCatching { Regex(pattern) }.isSuccess) {
                "Le motif « $pattern » n’est pas une expression régulière valide."
            }
        }
        positive(maximumPowerKw, "La puissance", BigDecimal(10000))
        ensure(connectors.size <= 100 && connectors.map { it.connectorId }.toSet().size == connectors.size) {
            "Chaque connecteur ne peut apparaître qu’une fois."
        }
        connectors.forEach(TemplateConnector::validate)
        ensure(parameters.size <= 200 && parameters.map { it.key }.toSet().size == parameters.size) {
            "Chaque paramètre ne peut apparaître qu’une fois."
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
        ensure(value.length <= 2000) { "La valeur contient 2 000 caractères au plus." }
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
