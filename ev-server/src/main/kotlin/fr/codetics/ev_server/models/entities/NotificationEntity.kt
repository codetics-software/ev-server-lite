package fr.codetics.ev_server.models.entities

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.springframework.data.mongodb.core.mapping.Document

@Entity
@Table(name = "notifications")
@Document("notifications")
class NotificationEntity(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID.randomUUID(),
    var type: String = "",
    var userId: UUID? = null,
    var recipient: String = "",
    var subject: String = "",
    var channel: String = "EMAIL",
    var mailId: UUID? = null,
    var dedupeKey: String? = null,
    var createdAt: Instant = Instant.now(),
)
