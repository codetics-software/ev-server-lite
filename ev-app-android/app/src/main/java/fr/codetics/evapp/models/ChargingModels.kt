// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

@file:UseSerializers(UuidSerializer::class, InstantSerializer::class, DecimalSerializer::class)

package fr.codetics.evapp.models

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
data class ChargingSessionResponse(
    val id: Int,
    val stationId: String,
    val connectorId: Int,
    val startedAt: Instant,
    val stoppedAt: Instant? = null,
    val energyKwh: Double? = null,
    val authorizationStatus: String,
    val stopReason: String? = null,
    val tokenUid: String? = null,
    val userId: UUID? = null,
    val userName: String? = null,
    val siteAreaId: UUID? = null,
    val siteId: UUID? = null,
    val durationSeconds: Int,
    val inactivitySeconds: Int,
    val currentPowerKw: Double? = null,
    val stateOfChargeStart: Int? = null,
    val stateOfCharge: Int? = null,
    val price: BigDecimal? = null,
    val currency: String? = null,
    val pricingDefinitionId: UUID? = null,
    val billingStatus: String,
    val invoiceId: UUID? = null,
    val roamingPartner: String? = null,
    val publicPayment: Boolean,
    val softStopped: Boolean,
    val vehicleId: UUID? = null,
    val autoCharge: Boolean,
    val vid: String? = null,
    val errors: List<String>? = null,
    val signedMeterData: Boolean,
    val signedMeterFormat: String? = null,
) {
    val isActive: Boolean
        get() = stoppedAt == null
}

@Serializable data class CommandReceipt(val id: UUID, val state: String)

@Serializable
data class ConsumptionPoint(
    val at: Instant,
    val energyKwh: Double? = null,
    val powerKw: Double? = null,
    val soc: Int? = null,
    val amperageA: Double? = null,
    val voltageV: Double? = null,
    val cumulatedPrice: BigDecimal? = null,
)

@Serializable data class SessionStartInput(val stationId: String, val connectorId: Int, val tokenUid: String)

@Serializable
data class SignedMeterData(
    val sessionId: Int? = null,
    val stationId: String,
    val format: String? = null,
    val start: String? = null,
    val stop: String? = null,
)

@Serializable
data class StatisticsGroup(val key: String, val label: String, val months: List<Double>, val total: Double)

@Serializable
data class StatisticsResponse(
    val metric: String,
    val year: Int,
    val unit: String,
    val currency: String? = null,
    val groups: List<StatisticsGroup>,
    val months: List<Double>,
    val total: Double,
)

@Serializable
data class InvoiceResponse(
    val id: UUID,
    val number: String? = null,
    val userId: UUID,
    val userName: String? = null,
    val status: String,
    val amountMinor: Int,
    val currency: String,
    val createdAt: Instant,
    val sessionIds: List<Int>,
    val hostedUrl: String? = null,
    val lastError: String? = null,
)

@Serializable
data class PaymentMethodResponse(
    val id: String,
    val brand: String? = null,
    val last4: String? = null,
    val expMonth: Int? = null,
    val expYear: Int? = null,
    val default: Boolean,
    val createdAt: Instant,
)

@Serializable
data class AutoChargeCandidate(
    val transactionId: Int,
    val stationId: String,
    val connectorId: Int,
    val startedAt: Instant,
    val vid: String,
    val vehicleId: UUID? = null,
)

@Serializable data class AutoChargeInput(val transactionId: Int)

@Serializable
data class CarCatalogData(
    val make: String,
    val model: String,
    val version: String? = null,
    val batteryKwh: Double? = null,
    val maxAcPowerKw: Double? = null,
    val maxDcPowerKw: Double? = null,
    val rangeKm: Int? = null,
    val imageUrl: String? = null,
)

@Serializable
data class CarData(
    val userId: UUID? = null,
    val carCatalogId: UUID? = null,
    val vin: String? = null,
    val licensePlate: String? = null,
    val make: String? = null,
    val model: String? = null,
    val batteryKwh: Double? = null,
    val maxAcPowerKw: Double? = null,
    val maxDcPowerKw: Double? = null,
    val default: Boolean? = null,
)

@Serializable
data class VehicleIdResponse(
    val uid: String,
    val vehicleId: UUID,
    val userId: UUID? = null,
    val enabled: Boolean,
    val lastUsedAt: Instant? = null,
)

@Serializable
data class ErrorReportInput(
    val subject: String,
    val description: String,
    val stationId: String? = null,
    val connectorId: Int? = null,
    val phone: String? = null,
)

@Serializable data class NotificationPreferences(val email: Boolean, val push: Boolean, val types: Map<String, Boolean>)

@Serializable
data class NotificationResponse(
    val id: UUID,
    val type: String,
    val userId: UUID? = null,
    val recipient: String,
    val subject: String,
    val channel: String,
    val createdAt: Instant,
    val sentAt: Instant? = null,
)

@Serializable data class NotificationTypeInfo(val type: String, val label: String, val audience: String)

@Serializable
data class PushDeviceInput(val token: String, val platform: String, val environment: String, val locale: String? = null)

@Serializable data class CardActivationInput(val number: String)

@Serializable
data class CardOfferResponse(
    val available: Boolean,
    val priceMinor: Int? = null,
    val currency: String,
    val description: String? = null,
    val shipping: Boolean,
)

@Serializable
enum class CardOrderState {
    @SerialName("PENDING_PAYMENT") PendingPayment,
    @SerialName("PAID") Paid,
    @SerialName("SHIPPED") Shipped,
    @SerialName("ACTIVATED") Activated,
    @SerialName("CANCELLED") Cancelled,
}

@Serializable
data class CardOrderResponse(
    val id: UUID,
    val state: CardOrderState,
    val amountMinor: Int,
    val currency: String,
    val createdAt: Instant,
    val paidAt: Instant? = null,
    val shippedAt: Instant? = null,
    val activatedAt: Instant? = null,
    val refunded: Boolean,
)

@Serializable
data class ChargingTokenResponse(
    val uid: String,
    val userId: UUID? = null,
    val type: String,
    val enabled: Boolean,
    val expiresAt: Instant? = null,
    val visualId: String? = null,
    val description: String? = null,
    val userName: String? = null,
    val default: Boolean,
    val lastUsedAt: Instant? = null,
    val vehicleId: UUID? = null,
    val vehicleName: String? = null,
)

@Serializable data class GoogleWalletPassResponse(val jwt: String, val saveUrl: String)
