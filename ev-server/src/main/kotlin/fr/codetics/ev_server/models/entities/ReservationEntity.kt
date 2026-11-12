package fr.codetics.ev_server.models.entities

import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID
import org.springframework.data.mongodb.core.mapping.Document

@Embeddable
data class ReservationKey(
    var tenantId: UUID = UUID.randomUUID(),
    var stationId: String = "",
    var reservationId: Int = 0,
) : Serializable

@Entity
@Table(name = "station_reservations")
@Document("station_reservations")
class ReservationEntity(
    @EmbeddedId var key: ReservationKey = ReservationKey(),
    var connectorId: Int = 0,
    var idTag: String = "",
    var parentIdTag: String? = null,
    var expiryDate: Instant = Instant.now(),
    var status: String = "PENDING",
    var transactionId: Int? = null,
    var commandId: UUID? = null,
    var createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
)
