package fr.codetics.ev_server.models.entities

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "station_commands")
class CommandEntity(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID.randomUUID(),
    var stationId: String = "",
    var action: String = "",
    @JdbcTypeCode(SqlTypes.JSON) var request: String = "{}",
    var state: String = "QUEUED",
    @JdbcTypeCode(SqlTypes.JSON) var result: String? = null,
    var createdAt: Instant = Instant.now(),
    var deadline: Instant = Instant.now().plusSeconds(60),
    var completedAt: Instant? = null,
)
