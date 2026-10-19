package fr.codetics.ev_server.models.entities

import fr.codetics.ev_server.models.billing.PaymentState
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "charging_payments")
class ChargingPaymentEntity(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID.randomUUID(),
    var evseId: UUID = UUID.randomUUID(),
    var stationId: String = "",
    var connectorId: Int = 1,
    var tokenUid: String = "",
    var accessTokenHash: String = "",
    @Column(columnDefinition = "text") var accessTokenEncrypted: String = "",
    @Column(columnDefinition = "text") var portalUrl: String = "",
    var description: String = "",
    var requestId: UUID = UUID.randomUUID(),
    var stripeAccountId: String = "",
    var stripeCheckoutId: String? = null,
    var stripePaymentIntentId: String? = null,
    @Column(columnDefinition = "text") var checkoutUrl: String? = null,
    var currency: String = "EUR",
    var prepaidMinor: Long = 0,
    @Column(precision = 22, scale = 6) var energyPrice: BigDecimal = BigDecimal.ZERO,
    @Column(precision = 22, scale = 6) var timePrice: BigDecimal = BigDecimal.ZERO,
    @Column(precision = 22, scale = 6) var flatPrice: BigDecimal = BigDecimal.ZERO,
    @Enumerated(EnumType.STRING) var state: PaymentState = PaymentState.PENDING_PAYMENT,
    var transactionId: Int? = null,
    var startCommandId: UUID? = null,
    var stopCommandId: UUID? = null,
    var billedMinor: Long? = null,
    @Column(precision = 22, scale = 6) var observedEnergyWh: BigDecimal = BigDecimal.ZERO,
    var refundedMinor: Long = 0,
    var refundId: String? = null,
    @Column(columnDefinition = "text") var lastError: String? = null,
    var expiresAt: Instant = Instant.now().plusSeconds(3600),
    var createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
    @Version var revision: Long = 0,
)
