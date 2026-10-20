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
@Table(name = "smart_charging_plans")
@Document("smart_charging_plans")
class SmartChargingPlanEntity(
    @Id var siteAreaId: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID.randomUUID(),
    @JdbcTypeCode(SqlTypes.JSON) var plan: String = "{}",
    var computedAt: Instant = Instant.now(),
)
