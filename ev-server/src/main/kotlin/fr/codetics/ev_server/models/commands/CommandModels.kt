package fr.codetics.ev_server.models.commands

import java.time.Instant
import java.util.UUID
import tools.jackson.databind.JsonNode
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

data class CommandInput(val action: String, val payload: Map<String, Any?> = emptyMap())

data class CommandReceipt(val id: UUID, val state: CommandState = CommandState.QUEUED)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class CommandResponse(
    val id: UUID,
    val stationId: String,
    val action: String,
    val state: CommandState,
    val result: JsonNode?,
    val createdAt: Instant,
    val completedAt: Instant?,
)
