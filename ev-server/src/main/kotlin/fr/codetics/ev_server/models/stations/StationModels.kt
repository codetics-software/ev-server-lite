package fr.codetics.ev_server.models.stations

import fr.codetics.ev_server.models.entities.ConnectorEntity
import fr.codetics.ev_server.models.entities.StationEntity
import java.time.Instant
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

data class StationInput(val id: String, val name: String)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class StationResponse(
    val id: String,
    val name: String,
    val vendor: String?,
    val model: String?,
    val firmwareVersion: String?,
    val enabled: Boolean,
    val connected: Boolean,
    val lastSeen: Instant?,
    val connectors: List<ConnectorResponse>? = null,
) {
    companion object {
        fun from(station: StationEntity, connectors: List<ConnectorEntity>? = null) =
            StationResponse(
                id = station.key.stationId,
                name = station.name,
                vendor = station.vendor,
                model = station.model,
                firmwareVersion = station.firmwareVersion,
                enabled = station.enabled,
                connected = station.connected,
                lastSeen = station.lastSeen,
                connectors = connectors?.map(ConnectorResponse::from),
            )
    }
}

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class ConnectorResponse(
    val connectorId: Int,
    val status: String,
    val errorCode: String,
    val statusAt: Instant,
) {
    companion object {
        fun from(connector: ConnectorEntity) =
            ConnectorResponse(
                connector.key.connectorId,
                connector.status,
                connector.errorCode,
                connector.statusAt,
            )
    }
}

data class ProvisionedStation(
    val id: String,
    val secret: String,
    val message: String =
        "Conservez le mot de passe de la borne : il n’est affiché qu’une seule fois.",
)
