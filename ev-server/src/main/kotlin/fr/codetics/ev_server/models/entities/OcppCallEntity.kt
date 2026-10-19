package fr.codetics.ev_server.models.entities

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
import org.springframework.data.mongodb.core.mapping.Document

@Embeddable
data class OcppCallKey(
    var tenantId: UUID = UUID.randomUUID(),
    var stationId: String = "",
    var messageId: String = "",
) : Serializable

@Entity
@Table(name = "ocpp_calls")
@Document("ocpp_calls")
class OcppCallEntity(
    @EmbeddedId var key: OcppCallKey = OcppCallKey(),
    var action: String = "",
    @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 64) var requestHash: String = "",
    @JdbcTypeCode(SqlTypes.JSON) var response: String = "{}",
    var createdAt: Instant = Instant.now(),
)
