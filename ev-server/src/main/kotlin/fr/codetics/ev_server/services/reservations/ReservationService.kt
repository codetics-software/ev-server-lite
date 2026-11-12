package fr.codetics.ev_server.services.reservations

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.models.entities.ReservationEntity
import fr.codetics.ev_server.models.entities.ReservationKey
import fr.codetics.ev_server.models.reservations.ReservationResponse
import fr.codetics.ev_server.ocpp.CommandCompleted
import fr.codetics.ev_server.ocpp.OcppTime
import fr.codetics.ev_server.repositories.ReservationRepository
import java.time.Instant
import java.util.UUID
import org.springframework.context.event.EventListener
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class ReservationService(private val reservations: ReservationRepository, private val db: Database) {
    fun list(tenantId: UUID, stationId: String?): List<ReservationResponse> {
        val page = PageRequest.of(0, 100)
        val now = Instant.now()
        return (
                if (stationId.isNullOrBlank()) reservations.findAllByKeyTenantIdOrderByCreatedAtDesc(tenantId, page)
                else reservations.findAllByKeyTenantIdAndKeyStationIdOrderByCreatedAtDesc(tenantId, stationId, page)
            )
            .map { ReservationResponse.from(it, now) }
    }

    fun used(tenantId: UUID, stationId: String, reservationId: Int, transactionId: Int?) {
        val reservation = reservations.findById(ReservationKey(tenantId, stationId, reservationId)).orElse(null) ?: return
        reservation.status = "USED"
        reservation.transactionId = transactionId
        reservation.updatedAt = Instant.now()
        reservations.save(reservation)
    }

    @EventListener
    fun completed(event: CommandCompleted) {
        val command = event.command
        val status = event.result.path("status").asText()
        when (command.action) {
            "ReserveNow" -> {
                val request = db.decode(command.request)
                val key = ReservationKey(command.tenantId, command.stationId, request.path("reservationId").intValue())
                val reservation = reservations.findById(key).orElse(null) ?: ReservationEntity(key)
                reservation.connectorId = request.path("connectorId").intValue()
                reservation.idTag = request.path("idTag").asText()
                reservation.parentIdTag = request.get("parentIdTag")?.asText()
                reservation.expiryDate = OcppTime.parse(request.path("expiryDate").asText())
                reservation.status = if (event.success && status.isNotBlank()) status.uppercase() else "FAILED"
                reservation.transactionId = null
                reservation.commandId = command.id
                reservation.updatedAt = Instant.now()
                reservations.save(reservation)
            }
            "CancelReservation" -> {
                if (!event.success || status != "Accepted") return
                val request = db.decode(command.request)
                val key = ReservationKey(command.tenantId, command.stationId, request.path("reservationId").intValue())
                val reservation = reservations.findById(key).orElse(null) ?: return
                reservation.status = "CANCELLED"
                reservation.updatedAt = Instant.now()
                reservations.save(reservation)
            }
        }
    }
}
