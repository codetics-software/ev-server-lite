package fr.codetics.ev_server.persistence

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
data class OcpiObjectKey(
    var tenantId: UUID = UUID.randomUUID(),
    var partnerId: UUID = UUID.randomUUID(),
    var module: String = "",
    var direction: String = "SENDER",
    var objectPath: String = ""
) : Serializable

@Entity
@Table(name = "ocpi_objects")
class OcpiObjectEntity(
    @EmbeddedId var key: OcpiObjectKey = OcpiObjectKey(),
    @JdbcTypeCode(SqlTypes.JSON) var data: String = "{}",
    var lastUpdated: Instant = Instant.now()
)
