package fr.codetics.ev_server.models.entities

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import org.springframework.data.mongodb.core.mapping.Document

@Entity
@Table(name = "stripe_events")
@Document("stripe_events")
class StripeEventEntity(
    @Id var id: String = "",
    var accountId: String = "",
    var eventType: String = "",
    var processedAt: Instant = Instant.now(),
)
