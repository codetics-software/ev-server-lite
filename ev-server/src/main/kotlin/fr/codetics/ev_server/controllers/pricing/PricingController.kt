package fr.codetics.ev_server.controllers.pricing

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.models.api.RecordResponse
import fr.codetics.ev_server.services.pricing.PricingService
import fr.codetics.ev_server.services.stations.StationService
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

data class PricingResolveInput(
    val stationId: String,
    val connectorId: Int = 1,
    val at: Instant? = null,
    val energyKwh: BigDecimal = BigDecimal(10),
    val durationMinutes: Long = 60,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class PricingPreview(val energyKwh: BigDecimal, val durationMinutes: Long, val price: BigDecimal, val currency: String)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class PricingResolution(val definition: RecordResponse?, val preview: PricingPreview?)

@RestController
@RequestMapping("/api/v1/pricing")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
class PricingController(
    private val pricing: PricingService,
    private val stations: StationService,
    private val db: Database,
) {
    @PostMapping("/resolve")
    fun resolve(@RequestBody input: PricingResolveInput, @AuthenticationPrincipal user: AppPrincipal): PricingResolution {
        require(input.energyKwh.signum() >= 0 && input.energyKwh <= BigDecimal(10000))
        require(input.durationMinutes in 0..100000)
        val station = stations.station(user.tenantId, input.stationId)
        val context =
            pricing.context(station, input.connectorId, input.at ?: Instant.now(), input.energyKwh, input.durationMinutes)
        val definition = pricing.resolve(context) ?: return PricingResolution(null, null)
        val price =
            pricing.amount(
                definition.data,
                input.energyKwh.multiply(BigDecimal(1000)),
                Duration.ofMinutes(input.durationMinutes),
            )
        return PricingResolution(
            RecordResponse(definition.id, definition.name, db.json.valueToTree(definition.data), definition.revision, definition.updatedAt),
            PricingPreview(input.energyKwh, input.durationMinutes, price, definition.data.currency),
        )
    }
}
