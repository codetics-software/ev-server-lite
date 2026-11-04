// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.models.stations

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class CustomerEvseLink(val id: UUID, val connectorId: Int, val workspaceSlug: String, val name: String)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class CustomerTariff(
    val name: String,
    val currency: String,
    val flatPrice: BigDecimal?,
    val energyPrice: BigDecimal?,
    val timePrice: BigDecimal?,
    val parkingPrice: BigDecimal?,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class CustomerStationResponse(
    val id: String,
    val name: String,
    val vendor: String?,
    val model: String?,
    val connected: Boolean,
    val lastSeen: Instant?,
    val connectors: List<ConnectorResponse>,
    val public: Boolean,
    val latitude: BigDecimal?,
    val longitude: BigDecimal?,
    val maximumPowerKw: BigDecimal?,
    val autoCharge: Boolean,
    val siteId: UUID?,
    val siteAreaId: UUID?,
    val siteName: String?,
    val address: String?,
    val postalCode: String?,
    val city: String?,
    val country: String?,
    val publicEvses: List<CustomerEvseLink>,
    val photoCount: Long? = null,
    val tariff: CustomerTariff? = null,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class StationPhotoResponse(
    val id: UUID,
    val stationId: String,
    val contentType: String,
    val createdAt: Instant,
    val mine: Boolean,
    val path: String,
)
