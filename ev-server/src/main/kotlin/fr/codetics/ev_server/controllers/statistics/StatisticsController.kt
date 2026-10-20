package fr.codetics.ev_server.controllers.statistics

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.services.statistics.StatisticsFilter
import fr.codetics.ev_server.services.statistics.StatisticsService
import java.time.Year
import java.time.ZoneId
import java.util.UUID
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/statistics")
@PreAuthorize("isAuthenticated()")
class StatisticsController(private val statistics: StatisticsService) {
    @GetMapping("/years")
    fun years(@AuthenticationPrincipal user: AppPrincipal) = statistics.years(user)

    @GetMapping("/{metric}")
    fun compute(
        @PathVariable metric: String,
        @AuthenticationPrincipal user: AppPrincipal,
        @RequestParam(required = false) year: Int?,
        @RequestParam(defaultValue = "station") groupBy: String,
        @RequestParam(required = false) siteId: UUID?,
        @RequestParam(required = false) siteAreaId: UUID?,
        @RequestParam(required = false) stationId: String?,
        @RequestParam(required = false) userId: UUID?,
    ) =
        statistics.compute(
            user,
            metric,
            StatisticsFilter(year ?: currentYear(), groupBy, siteId, siteAreaId, stationId, userId),
        )

    @GetMapping("/{metric}/export")
    fun export(
        @PathVariable metric: String,
        @AuthenticationPrincipal user: AppPrincipal,
        @RequestParam(required = false) year: Int?,
        @RequestParam(defaultValue = "station") groupBy: String,
        @RequestParam(required = false) siteId: UUID?,
        @RequestParam(required = false) siteAreaId: UUID?,
        @RequestParam(required = false) stationId: String?,
        @RequestParam(required = false) userId: UUID?,
    ) =
        statistics.export(
            user,
            metric,
            StatisticsFilter(year ?: currentYear(), groupBy, siteId, siteAreaId, stationId, userId),
        )

    private fun currentYear() = Year.now(ZoneId.of("Europe/Paris")).value
}
