package fr.codetics.ev_server.persistence

import java.math.BigDecimal
import java.util.UUID

interface Store {
    fun <T : Any> lock(type: Class<T>, id: Any): T?

    fun <T : Any> insert(entity: T): T

    fun flush()

    fun isTransient(error: Throwable): Boolean
}

interface SessionStatistics {
    fun deliveredEnergyWh(tenantId: UUID): BigDecimal
}
