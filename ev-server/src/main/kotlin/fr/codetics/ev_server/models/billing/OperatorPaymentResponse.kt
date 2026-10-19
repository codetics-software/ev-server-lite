package fr.codetics.ev_server.models.billing

import java.time.Instant
import java.util.UUID

data class OperatorPaymentResponse(
    val id: UUID,
    val stationId: String,
    val connectorId: Int,
    val state: PaymentState,
    val prepaidMinor: Long,
    val billedMinor: Long?,
    val refundedMinor: Long,
    val currency: String,
    val transactionId: Int?,
    val createdAt: Instant,
    val message: String?,
)
