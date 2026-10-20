package fr.codetics.ev_server.models.entities

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.mongodb.core.mapping.Document

@Entity
@Table(name = "charging_profiles")
@Document("charging_profiles")
class ChargingProfileEntity(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID.randomUUID(),
    var stationId: String = "",
    var connectorId: Int = 0,
    var profileId: Int = 1,
    var purpose: String = "TxDefaultProfile",
    var stackLevel: Int = 0,
    var transactionId: Int? = null,
    @JdbcTypeCode(SqlTypes.JSON) var profile: String = "{}",
    var source: String = "MANUAL",
    var commandId: UUID? = null,
    var createdAt: Instant = Instant.now(),
)
