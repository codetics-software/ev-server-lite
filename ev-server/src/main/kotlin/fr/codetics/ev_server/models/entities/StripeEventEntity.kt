package fr.codetics.ev_server.models.entities

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "stripe_events")
class StripeEventEntity(
    @Id var id: String = "",
    var accountId: String = "",
    var eventType: String = "",
    var processedAt: Instant = Instant.now(),
)
