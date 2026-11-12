package fr.codetics.ev_server.models.reservations

import fr.codetics.ev_server.models.entities.ReservationEntity
import java.time.Instant
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class ReservationResponse(
    val reservationId: Int,
    val stationId: String,
    val connectorId: Int,
    val idTag: String,
    val parentIdTag: String?,
    val expiryDate: Instant,
    val status: String,
    val transactionId: Int?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(reservation: ReservationEntity, now: Instant) =
            ReservationResponse(
                reservation.key.reservationId,
                reservation.key.stationId,
                reservation.connectorId,
                reservation.idTag,
                reservation.parentIdTag,
                reservation.expiryDate,
                if (reservation.status == "ACCEPTED" && reservation.expiryDate.isBefore(now)) "EXPIRED"
                else reservation.status,
                reservation.transactionId,
                reservation.createdAt,
                reservation.updatedAt,
            )
    }
}
