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
@Table(name = "asset_connections")
@Document("asset_connections")
class AssetConnectionEntity(
    @Id var assetId: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID.randomUUID(),
    var secretsEncrypted: String? = null,
    @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 64) var pushTokenHash: String? = null,
    var lastSuccessAt: Instant? = null,
    var lastError: String? = null,
    var lastAttemptAt: Instant? = null,
)
