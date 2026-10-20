package fr.codetics.ev_server.models.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import org.springframework.data.mongodb.core.mapping.Document

@Entity
@Table(name = "asset_consumptions")
@Document("asset_consumptions")
class AssetConsumptionEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    var tenantId: UUID = UUID.randomUUID(),
    var assetId: UUID = UUID.randomUUID(),
    var sampledAt: Instant = Instant.now(),
    @Column(name = "power_w") var powerW: BigDecimal = BigDecimal.ZERO,
    var energyWh: BigDecimal? = null,
    var soc: Int? = null,
)
