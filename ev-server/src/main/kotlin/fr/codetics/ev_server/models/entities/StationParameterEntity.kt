package fr.codetics.ev_server.models.entities

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID
import org.springframework.data.mongodb.core.mapping.Document

@Embeddable
data class StationParameterKey(
    var tenantId: UUID = UUID.randomUUID(),
    var stationId: String = "",
    @Column(name = "parameter_key") var key: String = "",
) : Serializable

@Entity
@Table(name = "station_parameters")
@Document("station_parameters")
class StationParameterEntity(
    @EmbeddedId var key: StationParameterKey = StationParameterKey(),
    @Column(name = "parameter_value") var value: String? = null,
    var readonly: Boolean = false,
    var updatedAt: Instant = Instant.now(),
)
