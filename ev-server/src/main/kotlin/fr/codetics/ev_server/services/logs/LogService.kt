package fr.codetics.ev_server.services.logs

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Csv
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.core.pageRequest
import fr.codetics.ev_server.models.api.PageResponse
import fr.codetics.ev_server.models.audit.AuditEventResponse
import fr.codetics.ev_server.models.entities.AuditEntity
import fr.codetics.ev_server.persistence.Condition
import fr.codetics.ev_server.persistence.Queries
import fr.codetics.ev_server.persistence.anyOf
import fr.codetics.ev_server.persistence.atLeast
import fr.codetics.ev_server.persistence.before
import fr.codetics.ev_server.persistence.contains
import fr.codetics.ev_server.persistence.eq
import fr.codetics.ev_server.persistence.list
import fr.codetics.ev_server.persistence.page
import java.time.Instant
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

data class LogFilter(
    val action: String? = null,
    val actor: String? = null,
    val target: String? = null,
    val from: Instant? = null,
    val to: Instant? = null,
    val search: String = "",
)

@Service
class LogService(private val queries: Queries) {
    fun list(user: AppPrincipal, filter: LogFilter, offset: Int, limit: Int): PageResponse<AuditEventResponse> {
        val page =
            queries.page<AuditEntity>(conditions(user, filter), pageRequest(offset, limit, Sort.by(Sort.Direction.DESC, "id")))
        return PageResponse(page.content.map(::response), page.totalElements)
    }

    fun export(user: AppPrincipal, filter: LogFilter) =
        Csv.download(
            "journal",
            listOf("id", "date", "auteur", "action", "cible"),
            queries.list<AuditEntity>(conditions(user, filter), Sort.by(Sort.Direction.DESC, "id"), 100000).asSequence().map {
                listOf(it.id, it.createdAt, it.actor, it.action, it.target)
            },
        )

    private fun conditions(user: AppPrincipal, filter: LogFilter): List<Condition> {
        ensure(listOfNotNull(filter.action, filter.actor, filter.target, filter.search).all { it.length <= 200 }) {
            "Le filtre est trop long."
        }
        val conditions = mutableListOf<Condition>(eq("tenantId", user.tenantId))
        filter.action?.takeIf(String::isNotBlank)?.let { conditions += eq("action", it) }
        filter.actor?.takeIf(String::isNotBlank)?.let { conditions += eq("actor", it) }
        filter.target?.takeIf(String::isNotBlank)?.let { conditions += contains("target", it) }
        filter.from?.let { conditions += atLeast("createdAt", it) }
        filter.to?.let { conditions += before("createdAt", it) }
        if (filter.search.isNotBlank()) {
            val text = filter.search.trim()
            conditions += anyOf(contains("action", text), contains("target", text), contains("actor", text))
        }
        return conditions
    }

    private fun response(event: AuditEntity) =
        AuditEventResponse(event.id, event.actor, event.action, event.target, event.createdAt)
}
