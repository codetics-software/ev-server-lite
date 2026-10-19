package fr.codetics.ev_server.api

import java.time.Instant
import java.util.UUID
import tools.jackson.databind.JsonNode
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class RecordResponse(val id: UUID, val name: String, val data: JsonNode, val revision: Long, val updatedAt: Instant)

data class SavedRecord(val id: UUID, val revision: Long)
