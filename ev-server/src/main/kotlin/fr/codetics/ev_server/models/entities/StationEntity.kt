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

@Embeddable
data class StationKey(
    var tenantId: UUID = UUID.randomUUID(),
    @Column(name = "id") var stationId: String = "",
) : Serializable

@Entity
@Table(name = "stations")
class StationEntity(
    @EmbeddedId var key: StationKey = StationKey(),
    var name: String = "",
    var secretHash: String = "",
    var vendor: String? = null,
    var model: String? = null,
    var firmwareVersion: String? = null,
    var enabled: Boolean = true,
    var lastSeen: Instant? = null,
    var connected: Boolean = false,
    @JdbcTypeCode(SqlTypes.JSON) var metadata: String = "{}",
)
