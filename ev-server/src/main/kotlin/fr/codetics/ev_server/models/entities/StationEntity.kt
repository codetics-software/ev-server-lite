package fr.codetics.ev_server.models.entities

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.mongodb.core.mapping.Document

@Embeddable
data class StationKey(
    var tenantId: UUID = UUID.randomUUID(),
    @Column(name = "id") var stationId: String = "",
) : Serializable

@Entity
@Table(name = "stations")
@Document("stations")
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
    var siteAreaId: UUID? = null,
    var publicStation: Boolean = false,
    var latitude: BigDecimal? = null,
    var longitude: BigDecimal? = null,
    var maximumPowerKw: BigDecimal? = null,
    var templateId: UUID? = null,
    var excludeFromSmartCharging: Boolean = false,
    var deleted: Boolean = false,
    var lastBoot: Instant? = null,
    var ocppVersion: String = "1.6",
    var firmwareStatus: String? = null,
    var diagnosticsStatus: String? = null,
    var autoCharge: Boolean = false,
    var createdAt: Instant = Instant.now(),
)
