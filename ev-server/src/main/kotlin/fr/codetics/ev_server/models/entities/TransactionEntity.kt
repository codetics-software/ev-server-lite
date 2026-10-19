package fr.codetics.ev_server.models.entities

import fr.codetics.ev_server.ocpp.AuthorizationStatus
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.mongodb.core.mapping.Document

@Entity
@Table(name = "charging_transactions")
@Document("charging_transactions")
class TransactionEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Int? = null,
    var tenantId: UUID = UUID.randomUUID(),
    var stationId: String = "",
    var connectorId: Int = 0,
    var tokenUid: String = "",
    var userId: UUID? = null,
    var vehicleId: UUID? = null,
    var startedAt: Instant = Instant.now(),
    var stoppedAt: Instant? = null,
    var meterStartWh: BigDecimal = BigDecimal.ZERO,
    var meterStopWh: BigDecimal? = null,
    var stopReason: String? = null,
    @Enumerated(EnumType.STRING)
    var authorizationStatus: AuthorizationStatus = AuthorizationStatus.Invalid,
    @JdbcTypeCode(SqlTypes.JSON) var tariffSnapshot: String = "{}",
)
