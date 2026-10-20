package fr.codetics.ev_server.services.organization

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.InvalidRequest
import fr.codetics.ev_server.models.entities.RecordEntity
import fr.codetics.ev_server.models.entities.RecordKey
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.organization.AssetData
import fr.codetics.ev_server.models.organization.CarData
import fr.codetics.ev_server.models.organization.PricingData
import fr.codetics.ev_server.models.organization.RecordData
import fr.codetics.ev_server.models.organization.RecordKinds
import fr.codetics.ev_server.models.organization.SiteAreaData
import fr.codetics.ev_server.models.organization.SiteData
import fr.codetics.ev_server.models.organization.TemplateData
import fr.codetics.ev_server.repositories.RecordRepository
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import tools.jackson.core.JacksonException
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode

data class Entry<T : RecordData>(
    val id: UUID,
    val name: String,
    val data: T,
    val revision: Long,
    val updatedAt: Instant,
)

@Service
class Organization(private val records: RecordRepository, private val db: Database) {
    fun strict(kind: String, data: JsonNode): RecordData {
        val type = RecordKinds.types[kind] ?: throw InvalidRequest("Type d’élément inconnu.")
        val parsed =
            try {
                db.json
                    .readerFor(type)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .readValue<RecordData>(data)
            } catch (error: JacksonException) {
                val field =
                    error.path.joinToString(".") { it.propertyName ?: "[${it.index}]" }.ifBlank {
                        "données"
                    }
                throw InvalidRequest("Le champ « $field » est absent, inconnu ou invalide.")
            }
        parsed.validate()
        return parsed
    }

    fun <T : RecordData> parse(record: RecordEntity, type: Class<T>): Entry<T>? {
        val data =
            runCatching { db.json.readerFor(type).readValue<T>(db.decode(record.data)) }.getOrNull()
                ?: return null
        return Entry(record.key.id, record.name, data, record.revision, record.updatedAt)
    }

    fun raw(tenant: UUID, kind: String): List<RecordEntity> =
        records
            .findAllByKeyTenantIdAndKeyKindAndNameContainingIgnoreCase(
                tenant,
                kind,
                "",
                PageRequest.of(0, LIMIT, Sort.by("name", "key.id")),
            )
            .content

    fun <T : RecordData> all(tenant: UUID, kind: String, type: Class<T>): List<Entry<T>> =
        raw(tenant, kind).mapNotNull { parse(it, type) }

    fun <T : RecordData> find(tenant: UUID, kind: String, id: UUID, type: Class<T>): Entry<T>? =
        records.findById(RecordKey(tenant, kind, id)).orElse(null)?.let { parse(it, type) }

    fun exists(tenant: UUID, kind: String, id: UUID) = records.existsById(RecordKey(tenant, kind, id))

    fun sites(tenant: UUID) = all(tenant, "sites", SiteData::class.java)

    fun siteAreas(tenant: UUID) = all(tenant, "site-areas", SiteAreaData::class.java)

    fun site(tenant: UUID, id: UUID) = find(tenant, "sites", id, SiteData::class.java)

    fun siteArea(tenant: UUID, id: UUID) = find(tenant, "site-areas", id, SiteAreaData::class.java)

    fun pricing(tenant: UUID) = all(tenant, "pricing-definitions", PricingData::class.java)

    fun templates(tenant: UUID) = all(tenant, "charging-station-templates", TemplateData::class.java)

    fun assets(tenant: UUID) = all(tenant, "assets", AssetData::class.java)

    fun asset(tenant: UUID, id: UUID) = find(tenant, "assets", id, AssetData::class.java)

    fun cars(tenant: UUID) = all(tenant, "cars", CarData::class.java)

    fun location(station: StationEntity): Location {
        val area = station.siteAreaId?.let { siteArea(station.key.tenantId, it) }
        val site = area?.let { site(station.key.tenantId, it.data.siteId) }
        return Location(area, site)
    }

    data class Location(val area: Entry<SiteAreaData>?, val site: Entry<SiteData>?)

    private companion object {
        const val LIMIT = 10000
    }
}
