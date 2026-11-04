// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.models.entities

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID
import org.springframework.data.mongodb.core.mapping.Document

@Entity
@Table(name = "card_orders")
@Document("card_orders")
class CardOrderEntity(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID.randomUUID(),
    var userId: UUID = UUID.randomUUID(),
    var requestId: UUID = UUID.randomUUID(),
    var state: String = "PENDING_PAYMENT",
    var amountMinor: Long = 0,
    var currency: String = "EUR",
    var recipientName: String? = null,
    var address: String? = null,
    var postalCode: String? = null,
    var city: String? = null,
    var stripeAccountId: String = "",
    var stripeCheckoutId: String? = null,
    var stripePaymentIntentId: String? = null,
    var checkoutUrl: String? = null,
    var refundId: String? = null,
    var tokenUid: String? = null,
    var expiresAt: Instant = Instant.now(),
    var createdAt: Instant = Instant.now(),
    var paidAt: Instant? = null,
    var shippedAt: Instant? = null,
    var activatedAt: Instant? = null,
    var updatedAt: Instant = Instant.now(),
    @Version var revision: Long = 0,
)
