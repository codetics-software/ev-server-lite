package fr.codetics.ev_server.api

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.persistence.RecordEntity
import fr.codetics.ev_server.persistence.RecordKey
import fr.codetics.ev_server.persistence.RecordRepository
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class RecordService(private val db: Database, private val records: RecordRepository) {
    private val kinds = setOf(
        "companies",
        "sites",
        "site-areas",
        "assets",
        "cars",
        "car-catalog",
        "pricing-definitions",
        "charging-station-templates",
        "settings"
    )

    private fun authorize(kind: String, user: AppPrincipal, write: Boolean = false) {
        if (kind !in kinds) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }

        if ((kind == "settings" || write) && user.role != "ADMIN") {
            throw ResponseStatusException(HttpStatus.FORBIDDEN)
        }
    }

    fun list(
        kind: String,
        user: AppPrincipal,
        search: String,
        offset: Int,
        limit: Int
    ): PageResponse<RecordResponse> {
        authorize(kind, user)
        bounds(offset, limit)
        require(search.length <= 200)

        val page = records.findAllByKeyTenantIdAndKeyKindAndNameContainingIgnoreCase(
            user.tenantId,
            kind,
            search,
            PageRequest.of(offset / limit, limit, Sort.by("name", "key.id"))
        )

        return PageResponse(
            page.content.map { record ->
                RecordResponse(record.key.id, record.name, db.decode(record.data), record.revision, record.updatedAt)
            },
            page.totalElements
        )
    }

    fun create(
        kind: String,
        body: RecordInput,
        user: AppPrincipal
    ): SavedRecord {
        authorize(kind, user, true)
        validate(body)

        return db.atomic {
            val record = records.saveAndFlush(
                RecordEntity(
                    key = RecordKey(user.tenantId, kind),
                    name = body.name,
                    data = db.encode(body.data)
                )
            )
            db.audit(user.tenantId, user.id.toString(), "RECORD_CREATE", "$kind/${record.key.id}")

            SavedRecord(record.key.id, record.revision)
        }
    }

    fun update(
        kind: String,
        id: UUID,
        body: RecordInput,
        user: AppPrincipal
    ): SavedRecord {
        authorize(kind, user, true)
        validate(body)
        require(body.revision != null) {
            "Provide the current revision to prevent overwriting another edit"
        }

        return db.atomic {
            val record = records.findById(RecordKey(user.tenantId, kind, id)).orElse(null)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

            if (record.revision != body.revision) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Record changed; reload before saving")
            }

            record.name = body.name
            record.data = db.encode(body.data)
            record.updatedAt = Instant.now()
            records.saveAndFlush(record)
            db.audit(user.tenantId, user.id.toString(), "RECORD_UPDATE", "$kind/$id")

            SavedRecord(id, record.revision)
        }
    }

    fun delete(
        kind: String,
        id: UUID,
        user: AppPrincipal
    ): MutationResponse {
        authorize(kind, user, true)

        return db.atomic {
            records.deleteById(RecordKey(user.tenantId, kind, id))
            db.audit(user.tenantId, user.id.toString(), "RECORD_DELETE", "$kind/$id")
            MutationResponse()
        }
    }

    private fun validate(body: RecordInput) {
        require(body.name.isNotBlank() && body.name.length <= 200)
        require(db.encode(body.data).length <= 65536) {
            "Record is too large"
        }
    }
}
