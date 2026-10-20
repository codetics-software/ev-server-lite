package fr.codetics.ev_server.models.entities

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.springframework.data.mongodb.core.mapping.Document

@Entity
@Table(name = "station_events")
@Document("station_events")
class StationEventEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    var tenantId: UUID = UUID.randomUUID(),
    var stationId: String = "",
    var type: String = "",
    var connectorId: Int? = null,
    var status: String? = null,
    var errorCode: String? = null,
    var info: String? = null,
    var createdAt: Instant = Instant.now(),
)
