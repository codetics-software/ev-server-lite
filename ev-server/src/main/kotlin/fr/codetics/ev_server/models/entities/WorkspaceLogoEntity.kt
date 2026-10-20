package fr.codetics.ev_server.models.entities

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.springframework.data.mongodb.core.mapping.Document

@Entity
@Table(name = "workspace_logos")
@Document("workspace_logos")
class WorkspaceLogoEntity(
    @Id var tenantId: UUID = UUID.randomUUID(),
    var contentType: String = "image/png",
    var content: ByteArray = byteArrayOf(),
    var updatedAt: Instant = Instant.now(),
)
