package fr.codetics.ev_server.services.billing

import fr.codetics.ev_server.models.billing.OperatorPaymentResponse
import fr.codetics.ev_server.repositories.ChargingPaymentRepository
import java.util.UUID
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class PaymentQueryService(private val payments: ChargingPaymentRepository) {
    fun list(tenantId: UUID, page: Int) =
        payments
            .findAllByTenantIdOrderByCreatedAtDesc(
                tenantId,
                PageRequest.of(page.coerceAtLeast(0), 50),
            )
            .map {
                OperatorPaymentResponse(
                    it.id,
                    it.stationId,
                    it.connectorId,
                    it.state,
                    it.prepaidMinor,
                    it.billedMinor,
                    it.refundedMinor,
                    it.currency,
                    it.transactionId,
                    it.createdAt,
                    it.lastError,
                )
            }
}
