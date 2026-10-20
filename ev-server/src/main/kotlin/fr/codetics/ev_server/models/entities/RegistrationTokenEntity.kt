package fr.codetics.ev_server.models.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.mongodb.core.mapping.Document

@Entity
@Table(name = "registration_tokens")
@Document("registration_tokens")
class RegistrationTokenEntity(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID.randomUUID(),
    @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 64) var tokenHash: String = "",
    var description: String = "",
    var siteAreaId: UUID? = null,
    var expiresAt: Instant? = null,
    var revokedAt: Instant? = null,
    var createdAt: Instant = Instant.now(),
)
