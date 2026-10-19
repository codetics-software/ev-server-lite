package fr.codetics.ev_server.core

import fr.codetics.ev_server.models.entities.AuditEntity
import fr.codetics.ev_server.repositories.AuditRepository
import java.util.UUID
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

@Component
class Database(
    val json: ObjectMapper,
    private val transactions: TransactionTemplate,
    private val auditEvents: AuditRepository,
) {
    fun encode(value: Any?): String {
        return json.writeValueAsString(value)
    }

    fun decode(value: String): JsonNode {
        return json.readTree(value)
    }

    fun audit(tenant: UUID, actor: String, action: String, target: String) {
        auditEvents.save(
            AuditEntity(tenantId = tenant, actor = actor, action = action, target = target)
        )
    }

    fun <T : Any> atomic(block: () -> T): T {
        return requireNotNull(transactions.execute { block() })
    }
}
