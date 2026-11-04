// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.models.tokens

import fr.codetics.ev_server.models.entities.CardOrderEntity
import java.time.Instant
import java.util.UUID
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

object CardOrderState {
    const val PENDING_PAYMENT = "PENDING_PAYMENT"
    const val PAID = "PAID"
    const val SHIPPED = "SHIPPED"
    const val ACTIVATED = "ACTIVATED"
    const val CANCELLED = "CANCELLED"
}

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class CardOfferResponse(
    val available: Boolean,
    val priceMinor: Long?,
    val currency: String,
    val description: String?,
    val shipping: Boolean,
)

data class CardSaleInput(
    val enabled: Boolean,
    val priceMinor: Long,
    val description: String? = null,
    val shipping: Boolean = true,
)

data class CardOrderInput(
    val requestId: UUID,
    val recipientName: String? = null,
    val address: String? = null,
    val postalCode: String? = null,
    val city: String? = null,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class CardCheckoutResponse(val orderId: UUID, val checkoutUrl: String)

data class CardAssignmentInput(val uid: String, val visualId: String? = null)

data class CardActivationInput(val number: String)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class CardOrderResponse(
    val id: UUID,
    val state: String,
    val amountMinor: Long,
    val currency: String,
    val createdAt: Instant,
    val paidAt: Instant?,
    val shippedAt: Instant?,
    val activatedAt: Instant?,
    val refunded: Boolean,
    val userName: String? = null,
    val userEmail: String? = null,
    val recipientName: String? = null,
    val address: String? = null,
    val postalCode: String? = null,
    val city: String? = null,
    val tokenUid: String? = null,
    val visualId: String? = null,
) {
    companion object {
        fun forDriver(order: CardOrderEntity) =
            CardOrderResponse(
                order.id,
                order.state,
                order.amountMinor,
                order.currency,
                order.createdAt,
                order.paidAt,
                order.shippedAt,
                order.activatedAt,
                order.refundId != null,
                recipientName = order.recipientName,
                address = order.address,
                postalCode = order.postalCode,
                city = order.city,
            )
    }
}
