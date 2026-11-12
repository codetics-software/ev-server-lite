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
data class ConnectorResponse(
    val connectorId: Int,
    val status: String,
    val errorCode: String,
    val statusAt: Instant,
    val type: String? = null,
    val maximumPowerKw: Double? = null,
    val phases: Int? = null,
    val currentPowerKw: Double? = null,
    val currentTransactionId: Int? = null,
    val currentEnergyKwh: Double? = null,
    val currentSoc: Int? = null,
    val currentUserName: String? = null,
    val inactiveSince: Instant? = null,
    val evseId: String? = null,
)

@Serializable
data class CustomerEvseLink(val id: UUID, val connectorId: Int, val workspaceSlug: String, val name: String)

@Serializable
data class CustomerTariff(
    val name: String,
    val currency: String,
    val flatPrice: BigDecimal? = null,
    val energyPrice: BigDecimal? = null,
    val timePrice: BigDecimal? = null,
    val parkingPrice: BigDecimal? = null,
)

@Serializable
data class CustomerStationResponse(
    val id: String,
    val name: String,
    val vendor: String? = null,
    val model: String? = null,
    val connected: Boolean,
    val lastSeen: Instant? = null,
    val connectors: List<ConnectorResponse>,
    val public: Boolean,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val maximumPowerKw: Double? = null,
    val autoCharge: Boolean,
    val siteId: UUID? = null,
    val siteAreaId: UUID? = null,
    val siteName: String? = null,
    val address: String? = null,
    val postalCode: String? = null,
    val city: String? = null,
    val country: String? = null,
    val publicEvses: List<CustomerEvseLink>,
    val photoCount: Int? = null,
    val tariff: CustomerTariff? = null,
)

@Serializable
data class StationPhotoResponse(
    val id: UUID,
    val stationId: String,
    val contentType: String,
    val createdAt: Instant,
    val mine: Boolean,
    val path: String,
)

@Serializable
data class ChargingTariff(
    val currency: String,
    val energyPrice: BigDecimal,
    val timePrice: BigDecimal,
    val flatPrice: BigDecimal,
)

@Serializable data class CheckoutRequest(val requestId: UUID, val accessToken: String, val creditMinor: Int)

@Serializable data class CheckoutResponse(val orderId: UUID, val checkoutUrl: String)

@Serializable
enum class PaymentState {
    @SerialName("PENDING_PAYMENT") PendingPayment,
    @SerialName("PAID") Paid,
    @SerialName("START_REQUESTED") StartRequested,
    @SerialName("CHARGING") Charging,
    @SerialName("STOP_REQUESTED") StopRequested,
    @SerialName("SETTLING") Settling,
    @SerialName("REFUND_PENDING") RefundPending,
    @SerialName("COMPLETED") Completed,
    @SerialName("CANCELLED") Cancelled,
    @SerialName("FAILED") Failed,
    @SerialName("DISPUTED") Disputed;

    val isFinished: Boolean
        get() = this == Completed || this == Cancelled || this == Failed || this == Disputed
}

@Serializable
data class PortalEvseResponse(
    val id: UUID,
    val workspace: String,
    val workspaceSlug: String,
    val name: String,
    val stationId: String,
    val connectorId: Int,
    val connected: Boolean,
    val status: String,
    val available: Boolean,
    val tariff: ChargingTariff,
    val minimumCreditMinor: Int,
    val maximumCreditMinor: Int,
)

@Serializable
data class PortalPaymentResponse(
    val id: UUID,
    val state: PaymentState,
    val prepaidMinor: Int,
    val billedMinor: Int? = null,
    val refundedMinor: Int,
    val currency: String,
    val transactionId: Int? = null,
    val message: String? = null,
)
