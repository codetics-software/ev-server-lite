package fr.codetics.ev_server.models.entities

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.springframework.data.mongodb.core.mapping.Document

@Entity
@Table(name = "integration_secrets")
@Document("integration_secrets")
class IntegrationSecretEntity(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID.randomUUID(),
    var secretsEncrypted: String = "",
    var updatedAt: Instant = Instant.now(),
)
