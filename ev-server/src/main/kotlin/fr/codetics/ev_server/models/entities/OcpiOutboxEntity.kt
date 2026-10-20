package fr.codetics.ev_server.models.entities

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.mongodb.core.mapping.Document

@Entity
@Table(name = "ocpi_outbox")
@Document("ocpi_outbox")
class OcpiOutboxEntity(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID.randomUUID(),
    var partnerId: UUID = UUID.randomUUID(),
    var module: String = "",
    var method: String = "PUT",
    var url: String? = null,
    var path: String = "",
    @JdbcTypeCode(SqlTypes.JSON) var body: String = "{}",
    var attempts: Int = 0,
    var nextAttemptAt: Instant = Instant.now(),
    var createdAt: Instant = Instant.now(),
    var sentAt: Instant? = null,
    var lastError: String? = null,
)
