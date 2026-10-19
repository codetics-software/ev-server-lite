package fr.codetics.ev_server.persistence

import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.io.Serializable
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Embeddable
data class RecordKey(
    var tenantId: UUID = UUID.randomUUID(),
    var kind: String = "",
    var id: UUID = UUID.randomUUID()
) : Serializable

@Entity
@Table(name = "records")
class RecordEntity(
    @EmbeddedId var key: RecordKey = RecordKey(),
    var name: String = "",
    @JdbcTypeCode(SqlTypes.JSON) var data: String = "{}",
    @Version var revision: Long = 0,
    var createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now()
)
