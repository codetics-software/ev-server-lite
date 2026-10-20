package fr.codetics.ev_server.persistence

import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import java.math.BigDecimal
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository

@Repository
@ConditionalOnProperty(DatabaseKind.PROPERTY, havingValue = "sql", matchIfMissing = true)
class SqlStore(private val entities: EntityManager) : Store {
    override fun <T : Any> lock(type: Class<T>, id: Any): T? =
        entities.find(type, id, LockModeType.PESSIMISTIC_WRITE)

    override fun <T : Any> insert(entity: T): T {
        entities.persist(entity)
        entities.flush()
        return entity
    }

    override fun flush() = entities.flush()

    override fun isTransient(error: Throwable) = false
}

@Repository
@ConditionalOnProperty(DatabaseKind.PROPERTY, havingValue = "sql", matchIfMissing = true)
class SqlSessionStatistics(private val entities: EntityManager) : SessionStatistics {
    override fun deliveredEnergyWh(tenantId: UUID): BigDecimal =
        entities
            .createQuery(
                """
                select coalesce(sum(t.meterStopWh - t.meterStartWh), 0)
                from TransactionEntity t
                where t.tenantId = :tenant
                  and t.meterStopWh >= t.meterStartWh
                """,
                BigDecimal::class.java,
            )
            .setParameter("tenant", tenantId)
            .singleResult
}
