package fr.codetics.ev_server.persistence

import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Embeddable
data class ChargingTokenKey(
    var tenantId: UUID = UUID.randomUUID(),
    var uid: String = ""
) : Serializable

@Entity
@Table(name = "charging_tokens")
class ChargingTokenEntity(
    @EmbeddedId var key: ChargingTokenKey = ChargingTokenKey(),
    var userId: UUID? = null,
    var type: String = "RFID",
    var enabled: Boolean = true,
    var expiresAt: Instant? = null
)
