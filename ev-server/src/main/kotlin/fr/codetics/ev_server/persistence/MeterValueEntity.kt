package fr.codetics.ev_server.persistence

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "meter_values")
class MeterValueEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    var tenantId: UUID = UUID.randomUUID(),
    var stationId: String = "",
    var connectorId: Int = 0,
    var transactionId: Int? = null,
    var sampledAt: Instant = Instant.now(),
    @JdbcTypeCode(SqlTypes.JSON) var samples: String = "[]"
)
