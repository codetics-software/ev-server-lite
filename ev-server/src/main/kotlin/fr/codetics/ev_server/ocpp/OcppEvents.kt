package fr.codetics.ev_server.ocpp

import fr.codetics.ev_server.models.entities.CommandEntity
import fr.codetics.ev_server.models.entities.ConnectorEntity
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.TransactionEntity
import tools.jackson.databind.JsonNode

data class CommandCompleted(val command: CommandEntity, val success: Boolean, val result: JsonNode)

data class StationBooted(val station: StationEntity, val firstBoot: Boolean)

data class StationConnectionChanged(val station: StationEntity, val connected: Boolean)

data class ConnectorStatusChanged(
    val station: StationEntity,
    val connector: ConnectorEntity,
    val previousStatus: String,
)

data class TransactionStarted(val station: StationEntity, val transaction: TransactionEntity)

data class TransactionUpdated(val station: StationEntity, val transaction: TransactionEntity)

data class TransactionStopped(val station: StationEntity, val transaction: TransactionEntity)

data class BadgeRejected(val station: StationEntity, val uid: String, val status: AuthorizationStatus)

data class StationNotification(val station: StationEntity, val type: String, val status: String?)
