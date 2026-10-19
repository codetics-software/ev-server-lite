package fr.codetics.ev_server.models.evses

import fr.codetics.ev_server.models.billing.ChargingTariff
import java.util.UUID

data class EvseInput(
    val name: String,
    val stationId: String,
    val connectorId: Int,
    val pricingDefinitionId: UUID,
    val published: Boolean = false,
)

data class EvseResponse(
    val id: UUID,
    val name: String,
    val stationId: String,
    val connectorId: Int,
    val pricingDefinitionId: UUID,
    val published: Boolean,
    val portalUrl: String,
)

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
    val minimumCreditMinor: Long = 500,
    val maximumCreditMinor: Long = 10000,
)
