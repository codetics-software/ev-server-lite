package fr.codetics.ev_server.persistence

import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Embeddable
data class ConnectorKey(
    var tenantId: UUID = UUID.randomUUID(),
    var stationId: String = "",
    var connectorId: Int = 0
) : Serializable

@Entity
@Table(name = "connectors")
class ConnectorEntity(
    @EmbeddedId var key: ConnectorKey = ConnectorKey(),
    var status: String = "Unavailable",
    var errorCode: String = "NoError",
    var statusAt: Instant = Instant.EPOCH
)
