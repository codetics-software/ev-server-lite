package fr.codetics.ev_server.services.billing

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.lock
import fr.codetics.ev_server.models.billing.PaymentState
import fr.codetics.ev_server.models.billing.PortalPaymentResponse
import fr.codetics.ev_server.models.entities.ChargingPaymentEntity
import fr.codetics.ev_server.ocpp.OcppCommandQueue
import fr.codetics.ev_server.repositories.ChargingPaymentRepository
import fr.codetics.ev_server.security.PortalAccessToken
import java.time.Instant
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class PortalSessionService(
    private val payments: ChargingPaymentRepository,
    private val queue: OcppCommandQueue,
    private val db: Database,
) {
    fun read(id: UUID, accessToken: String): PortalPaymentResponse {
        val order =
            payments.findById(id).orElse(null)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Session introuvable.")
        PortalAccessToken.verify(order.accessTokenHash, accessToken)
        return response(order)
    }

    fun stop(id: UUID, accessToken: String): PortalPaymentResponse =
        db.atomic {
            val order =
                db.lock<ChargingPaymentEntity>(id)
                    ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Session introuvable.")
            PortalAccessToken.verify(order.accessTokenHash, accessToken)
            if (order.state == PaymentState.CHARGING) {
                val transaction = requireNotNull(order.transactionId)
                order.stopCommandId =
                    queue.command(
                        order.tenantId,
                        order.stationId,
                        "RemoteStopTransaction",
                        db.json.valueToTree(mapOf("transactionId" to transaction)),
                    )
                order.state = PaymentState.STOP_REQUESTED
                order.updatedAt = Instant.now()
                payments.save(order)
            }
            response(order)
        }

    private fun response(order: ChargingPaymentEntity) =
        PortalPaymentResponse(
            order.id,
            order.state,
            order.prepaidMinor,
            order.billedMinor,
            order.refundedMinor,
            order.currency,
            order.transactionId,
            order.lastError,
        )
}
