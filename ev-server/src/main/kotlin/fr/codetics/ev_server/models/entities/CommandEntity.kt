package fr.codetics.ev_server.models.entities

import fr.codetics.ev_server.models.commands.CommandState
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.mongodb.core.mapping.Document

@Entity
@Table(name = "station_commands")
@Document("station_commands")
class CommandEntity(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID.randomUUID(),
    var stationId: String = "",
    var action: String = "",
    @JdbcTypeCode(SqlTypes.JSON) var request: String = "{}",
    @Enumerated(EnumType.STRING) var state: CommandState = CommandState.QUEUED,
    @JdbcTypeCode(SqlTypes.JSON) var result: String? = null,
    var createdAt: Instant = Instant.now(),
    var deadline: Instant = Instant.now().plusSeconds(60),
    var completedAt: Instant? = null,
)
