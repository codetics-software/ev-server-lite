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
@Table(name = "access_tokens")
@Document("access_tokens")
class AccessTokenEntity(
    @Id @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 64) var tokenHash: String = "",
    var userId: UUID = UUID.randomUUID(),
    var expiresAt: Instant = Instant.now(),
    var createdAt: Instant = Instant.now(),
)
