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
@Table(name = "ocpi_jobs")
@Document("ocpi_jobs")
class OcpiJobEntity(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID.randomUUID(),
    var partnerId: UUID = UUID.randomUUID(),
    var module: String = "",
    var direction: String = "PUSH",
    var total: Int = 0,
    var success: Int = 0,
    var failure: Int = 0,
    @JdbcTypeCode(SqlTypes.JSON) var errors: String = "[]",
    var startedAt: Instant = Instant.now(),
    var finishedAt: Instant? = null,
)
