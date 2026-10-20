package fr.codetics.ev_server.models.entities

import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import org.springframework.data.mongodb.core.mapping.Document

@Embeddable
data class ConnectorKey(
    var tenantId: UUID = UUID.randomUUID(),
    var stationId: String = "",
    var connectorId: Int = 0,
) : Serializable

@Entity
@Table(name = "connectors")
@Document("connectors")
class ConnectorEntity(
    @EmbeddedId var key: ConnectorKey = ConnectorKey(),
    var status: String = "Unavailable",
    var errorCode: String = "NoError",
    var statusAt: Instant = Instant.EPOCH,
    var type: String? = null,
    var maximumPowerKw: BigDecimal? = null,
    var phases: Int? = null,
    @Column(name = "current_power_w") var currentPowerW: BigDecimal? = null,
    var currentSoc: Int? = null,
    var meterAt: Instant? = null,
    var pluggedAt: Instant? = null,
    var vid: String? = null,
)
