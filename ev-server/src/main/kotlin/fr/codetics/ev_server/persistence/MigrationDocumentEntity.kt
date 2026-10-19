package fr.codetics.ev_server.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Embeddable
data class MigrationDocumentKey(
    var tenantId: UUID = UUID.randomUUID(),
    var collectionName: String = "",
    var sourceId: String = ""
) : Serializable

@Entity
@Table(name = "migration_documents")
class MigrationDocumentEntity(
    @EmbeddedId var key: MigrationDocumentKey = MigrationDocumentKey(),
    var sourceSha256: String = "",
    @JdbcTypeCode(SqlTypes.JSON) var document: String = "{}",
    @Column(columnDefinition = "text") var rawDocument: String = "",
    var importedAt: Instant = Instant.now()
)
