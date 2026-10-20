package fr.codetics.ev_server.services.configuration

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.bounds
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.models.api.MutationResponse
import fr.codetics.ev_server.models.api.PageResponse
import fr.codetics.ev_server.models.api.RecordInput
import fr.codetics.ev_server.models.api.RecordResponse
import fr.codetics.ev_server.models.api.SavedRecord
import fr.codetics.ev_server.models.entities.RecordEntity
import fr.codetics.ev_server.models.entities.RecordKey
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.organization.PricingData
import fr.codetics.ev_server.models.organization.RecordData
import fr.codetics.ev_server.models.organization.RecordKinds
import fr.codetics.ev_server.models.organization.SiteAreaData
import fr.codetics.ev_server.models.people.UserRole
import fr.codetics.ev_server.persistence.Queries
import fr.codetics.ev_server.persistence.count
import fr.codetics.ev_server.persistence.eq
import fr.codetics.ev_server.repositories.ChargingTokenRepository
import fr.codetics.ev_server.repositories.PublicEvseRepository
import fr.codetics.ev_server.repositories.RecordRepository
import fr.codetics.ev_server.repositories.SiteUserRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.UserRepository
import fr.codetics.ev_server.services.organization.Organization
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class RecordService(
    private val db: Database,
    private val records: RecordRepository,
    private val organization: Organization,
    private val users: UserRepository,
    private val stations: StationRepository,
    private val siteUsers: SiteUserRepository,
    private val publicEvses: PublicEvseRepository,
    private val chargingTokens: ChargingTokenRepository,
    private val queries: Queries,
) {
    private fun authorize(kind: String, user: AppPrincipal, write: Boolean = false) {
        if (kind !in RecordKinds.types) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }

        if ((kind == "settings" || write) && user.role != UserRole.ADMIN) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN)
        }
    }

    fun list(
        kind: String,
        user: AppPrincipal,
        search: String,
        offset: Int,
        limit: Int,
        filters: Map<String, String> = emptyMap(),
    ): PageResponse<RecordResponse> {
        authorize(kind, user)
        bounds(offset, limit)
        require(search.length <= 200)
        val applied = filters.filterKeys { it in RecordKinds.filters }.filterValues(String::isNotBlank)

        if (applied.isEmpty()) {
            val page =
                records.findAllByKeyTenantIdAndKeyKindAndNameContainingIgnoreCase(
                    user.tenantId,
                    kind,
                    search,
                    PageRequest.of(offset / limit, limit, Sort.by("name", "key.id")),
                )
            return PageResponse(page.content.map(::response), page.totalElements)
        }

        val matching =
            organization.raw(user.tenantId, kind).filter { record ->
                val data = db.decode(record.data)
                record.name.contains(search, true) &&
                    applied.all { (field, value) -> data.path(field).asText() == value }
            }
        return PageResponse(
            matching.drop(offset).take(limit).map(::response),
            matching.size.toLong(),
        )
    }

    fun find(kind: String, id: UUID, user: AppPrincipal): RecordResponse {
        authorize(kind, user)
        val record =
            records.findById(RecordKey(user.tenantId, kind, id)).orElse(null)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return response(record)
    }

    fun create(kind: String, body: RecordInput, user: AppPrincipal): SavedRecord {
        authorize(kind, user, true)
        return save(kind, null, body, user)
    }

    fun update(kind: String, id: UUID, body: RecordInput, user: AppPrincipal): SavedRecord {
        authorize(kind, user, true)
        return save(kind, id, body, user)
    }

    fun delete(kind: String, id: UUID, user: AppPrincipal): MutationResponse {
        authorize(kind, user, true)
        return remove(kind, id, user)
    }

    fun save(kind: String, id: UUID?, body: RecordInput, user: AppPrincipal): SavedRecord {
        require(body.name.isNotBlank() && body.name.length <= 200)
        val data = organization.strict(kind, db.json.valueToTree(body.data))
        verify(user.tenantId, kind, id, data)
        val encoded = db.encode(body.data)
        ensure(encoded.length <= 65536) { "Cet élément est trop volumineux." }

        return db.atomic {
            if (id == null) {
                val record =
                    records.save(
                        RecordEntity(
                            key = RecordKey(user.tenantId, kind),
                            name = body.name.trim(),
                            data = encoded,
                        )
                    ).also { db.flush() }
                db.audit(user.tenantId, user.id.toString(), "RECORD_CREATE", "$kind/${record.key.id}")
                return@atomic SavedRecord(record.key.id, record.revision)
            }

            ensure(body.revision != null) {
                "Renseignez la version actuelle pour éviter de remplacer une modification concurrente."
            }
            val record =
                records.findById(RecordKey(user.tenantId, kind, id)).orElse(null)
                    ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

            if (record.revision != body.revision) {
                throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cet élément a changé. Rechargez-le avant d’enregistrer.",
                )
            }

            if (kind == "cars" && db.decode(record.data).path("userId").textValue() != body.data["userId"]?.toString()) {
                revokeVehicleIds(user, id)
            }
            record.name = body.name.trim()
            record.data = encoded
            record.updatedAt = Instant.now()
            records.save(record).also { db.flush() }
            db.audit(user.tenantId, user.id.toString(), "RECORD_UPDATE", "$kind/$id")

            SavedRecord(id, record.revision)
        }
    }

    fun remove(kind: String, id: UUID, user: AppPrincipal): MutationResponse {
        return db.atomic {
            conflicts(user.tenantId, kind, id)?.let { message ->
                throw ResponseStatusException(HttpStatus.CONFLICT, message)
            }
            if (kind == "sites") {
                siteUsers.deleteAll(siteUsers.findAllByKeyTenantIdAndKeySiteId(user.tenantId, id))
            }
            if (kind == "cars") revokeVehicleIds(user, id)
            records.deleteById(RecordKey(user.tenantId, kind, id))
            db.audit(user.tenantId, user.id.toString(), "RECORD_DELETE", "$kind/$id")
            MutationResponse()
        }
    }

    private fun revokeVehicleIds(user: AppPrincipal, car: UUID) {
        chargingTokens.findAllByKeyTenantIdAndVehicleId(user.tenantId, car).forEach { token ->
            chargingTokens.delete(token)
            db.audit(user.tenantId, user.id.toString(), "AUTOCHARGE_REVOKE", "$car/${token.key.uid}")
        }
    }

    private fun verify(tenant: UUID, kind: String, id: UUID?, data: RecordData) {
        data.references().forEach { reference ->
            val exists =
                if (reference.kind == "users") users.findByTenantIdAndId(tenant, reference.id) != null
                else organization.exists(tenant, reference.kind, reference.id)
            ensure(exists) { "${reference.label} sélectionné(e) n’existe pas dans cet espace." }
        }

        if (data is SiteAreaData && data.parentSiteAreaId != null) {
            ensure(data.parentSiteAreaId != id) { "Une zone ne peut pas être sa propre parente." }
            val areas = organization.siteAreas(tenant).associateBy { it.id }
            var parent: UUID? = data.parentSiteAreaId
            val visited = mutableSetOf<UUID>()
            while (parent != null) {
                ensure(parent != id && visited.add(parent)) {
                    "Cette zone parente créerait une boucle."
                }
                val area = areas[parent] ?: break
                ensure(area.data.siteId == data.siteId) {
                    "La zone parente doit appartenir au même site."
                }
                parent = area.data.parentSiteAreaId
            }
        }

        if (data is PricingData) {
            data.scope?.stationId?.let { station ->
                ensure(stations.existsById(StationKey(tenant, station))) {
                    "La borne sélectionnée n’existe pas dans cet espace."
                }
            }
        }
    }

    private fun conflicts(tenant: UUID, kind: String, id: UUID): String? {
        RecordKinds.children[kind].orEmpty().forEach { (child, field) ->
            val used =
                organization.raw(tenant, child).any { record ->
                    db.decode(record.data).path(field).asText() == id.toString()
                }
            if (used) return "Cet élément est encore utilisé par d’autres éléments ($child)."
        }

        if (kind == "site-areas") {
            val attached =
                queries.count<StationEntity>(
                    listOf(eq("key.tenantId", tenant), eq("siteAreaId", id), eq("deleted", false))
                )
            if (attached > 0) return "Des bornes sont encore rattachées à cette zone."
        }

        if (kind == "sites" || kind == "site-areas") {
            val field = if (kind == "sites") "siteId" else "siteAreaId"
            val priced =
                organization.raw(tenant, "pricing-definitions").any { record ->
                    db.decode(record.data).path("scope").path(field).asText() == id.toString()
                }
            if (priced) return "Un tarif est encore limité à cet élément."
        }

        if (kind == "pricing-definitions") {
            val published =
                publicEvses.findAllByTenantIdOrderByName(tenant).any { it.pricingDefinitionId == id }
            if (published) return "Ce tarif est utilisé par un point de charge public."
        }

        return null
    }

    private fun response(record: RecordEntity) =
        RecordResponse(
            record.key.id,
            record.name,
            db.decode(record.data),
            record.revision,
            record.updatedAt,
        )
}
