package fr.codetics.ev_server.models.entities

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
data class OcppCallKey(
    var tenantId: UUID = UUID.randomUUID(),
    var stationId: String = "",
    var messageId: String = "",
) : Serializable

@Entity
@Table(name = "ocpp_calls")
class OcppCallEntity(
    @EmbeddedId var key: OcppCallKey = OcppCallKey(),
    var action: String = "",
    var requestHash: String = "",
    @JdbcTypeCode(SqlTypes.JSON) var response: String = "{}",
    var createdAt: Instant = Instant.now(),
)
