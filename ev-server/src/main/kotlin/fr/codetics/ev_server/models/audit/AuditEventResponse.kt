package fr.codetics.ev_server.models.audit

import java.time.Instant
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class AuditEventResponse(
    val id: Long?,
    val actor: String,
    val action: String,
    val target: String,
    val createdAt: Instant,
)
