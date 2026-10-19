package fr.codetics.ev_server.models.entities

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID
import org.springframework.data.mongodb.core.mapping.Document

@Entity
@Table(name = "public_evses")
@Document("public_evses")
class PublicEvseEntity(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID.randomUUID(),
    var stationId: String = "",
    var connectorId: Int = 1,
    var name: String = "",
    var pricingDefinitionId: UUID = UUID.randomUUID(),
    var published: Boolean = false,
    var createdAt: Instant = Instant.now(),
    @Version var revision: Long = 0,
)
