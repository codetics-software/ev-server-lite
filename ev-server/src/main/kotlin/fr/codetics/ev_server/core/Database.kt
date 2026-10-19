package fr.codetics.ev_server.core

import fr.codetics.ev_server.models.entities.AuditEntity
import fr.codetics.ev_server.persistence.Store
import fr.codetics.ev_server.repositories.AuditRepository
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

@Component
class Database(
    val json: ObjectMapper,
    manager: PlatformTransactionManager,
    private val store: Store,
    private val auditEvents: AuditRepository,
) {
    private val transactions = TransactionTemplate(manager)
    private val isolated =
        TransactionTemplate(manager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

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

    fun <T> atomic(block: () -> T): T =
        run(transactions, TransactionSynchronizationManager.isActualTransactionActive(), block)

    fun <T> isolated(block: () -> T): T = run(isolated, false, block)

    fun <T : Any> lock(type: Class<T>, id: Any): T? = store.lock(type, id)

    fun flush() = store.flush()

    @Suppress("UNCHECKED_CAST")
    private fun <T> run(template: TransactionTemplate, nested: Boolean, block: () -> T): T {
        val deadline = System.nanoTime() + RETRY_BUDGET.toNanos()
        var attempt = 1
        while (true) {
            try {
                return template.execute { block() } as T
            } catch (error: RuntimeException) {
                if (nested || System.nanoTime() > deadline || !store.isTransient(error)) {
                    throw error
                }
                val ceiling = (BACKOFF_MILLIS shl attempt.coerceAtMost(5)).coerceAtMost(250)
                Thread.sleep(ThreadLocalRandom.current().nextLong(1, ceiling + 1))
                attempt += 1
            }
        }
    }

    private companion object {
        val RETRY_BUDGET: Duration = Duration.ofSeconds(10)
        const val BACKOFF_MILLIS = 5L
    }
}

inline fun <reified T : Any> Database.lock(id: Any): T? = lock(T::class.java, id)
