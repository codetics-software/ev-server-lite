package fr.codetics.ev_server.services.statistics

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Csv
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.entities.TransactionEntity
import fr.codetics.ev_server.models.people.UserRole
import fr.codetics.ev_server.models.sessions.ChargingSessionResponse
import fr.codetics.ev_server.persistence.Condition
import fr.codetics.ev_server.persistence.Queries
import fr.codetics.ev_server.persistence.atLeast
import fr.codetics.ev_server.persistence.before
import fr.codetics.ev_server.persistence.eq
import fr.codetics.ev_server.persistence.list
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TransactionRepository
import fr.codetics.ev_server.repositories.UserRepository
import fr.codetics.ev_server.services.organization.Organization
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.ZoneId
import java.util.UUID
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

data class StatisticsFilter(
    val year: Int,
    val groupBy: String = "station",
    val siteId: UUID? = null,
    val siteAreaId: UUID? = null,
    val stationId: String? = null,
    val userId: UUID? = null,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class StatisticsGroup(val key: String, val label: String, val months: List<BigDecimal>, val total: BigDecimal)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class StatisticsResponse(
    val metric: String,
    val year: Int,
    val unit: String,
    val currency: String?,
    val groups: List<StatisticsGroup>,
    val months: List<BigDecimal>,
    val total: BigDecimal,
)

@Service
class StatisticsService(
    private val queries: Queries,
    private val transactions: TransactionRepository,
    private val stations: StationRepository,
    private val users: UserRepository,
    private val organization: Organization,
) {
    private val zone = ZoneId.of("Europe/Paris")
    private val units =
        mapOf("consumption" to "kWh", "usage" to "h", "inactivity" to "h", "sessions" to "sessions", "pricing" to "currency")

    fun years(user: AppPrincipal): List<Int> {
        val first =
            if (user.role == UserRole.DRIVER) {
                queries.list<TransactionEntity>(listOf(eq("tenantId", user.tenantId), eq("userId", user.id)), Sort.by("startedAt"), 1).firstOrNull()
            } else {
                transactions.findFirstByTenantIdOrderByStartedAtAsc(user.tenantId)
            }
        val current = Year.now(zone).value
        val start = first?.startedAt?.atZone(zone)?.year ?: current
        return (start..current).toList()
    }

    fun compute(user: AppPrincipal, metric: String, filter: StatisticsFilter): StatisticsResponse {
        ensure(metric in units) { "Indicateur inconnu." }
        ensure(filter.groupBy in setOf("station", "user", "site", "site-area")) { "Regroupement inconnu." }
        ensure(filter.year in 2000..2100) { "Année invalide." }
        val from = LocalDate.of(filter.year, 1, 1).atStartOfDay(zone).toInstant()
        val to = LocalDate.of(filter.year + 1, 1, 1).atStartOfDay(zone).toInstant()
        val conditions = mutableListOf<Condition>(eq("tenantId", user.tenantId), atLeast("startedAt", from), before("startedAt", to))
        val userId = if (user.role == UserRole.DRIVER) user.id else filter.userId
        userId?.let { conditions += eq("userId", it) }
        filter.siteId?.let { conditions += eq("siteId", it) }
        filter.siteAreaId?.let { conditions += eq("siteAreaId", it) }
        filter.stationId?.takeIf(String::isNotBlank)?.let { conditions += eq("stationId", it) }
        val sessions = queries.list<TransactionEntity>(conditions, Sort.by("startedAt"), 200000)

        val currency =
            if (metric == "pricing") {
                sessions.mapNotNull { it.currency }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "EUR"
            } else {
                null
            }
        val relevant = if (currency != null) sessions.filter { it.currency == null || it.currency == currency } else sessions
        val labels = labels(user.tenantId, filter.groupBy, relevant)
        val grouped =
            relevant.groupBy { key(filter.groupBy, it) }.map { (key, rows) ->
                val months = MutableList(12) { BigDecimal.ZERO }
                rows.forEach { row ->
                    val month = row.startedAt.atZone(zone).monthValue - 1
                    months[month] = months[month] + value(metric, row)
                }
                val rounded = months.map { it.setScale(2, RoundingMode.HALF_UP) }
                StatisticsGroup(key, labels[key] ?: key, rounded, rounded.fold(BigDecimal.ZERO, BigDecimal::add))
            }.sortedByDescending { it.total }
        val totals =
            (0 until 12).map { month -> grouped.fold(BigDecimal.ZERO) { sum, group -> sum + group.months[month] } }
        return StatisticsResponse(
            metric,
            filter.year,
            if (metric == "pricing") currency ?: "EUR" else requireNotNull(units[metric]),
            currency,
            grouped,
            totals,
            totals.fold(BigDecimal.ZERO, BigDecimal::add),
        )
    }

    fun export(user: AppPrincipal, metric: String, filter: StatisticsFilter) =
        compute(user, metric, filter).let { result ->
            Csv.download(
                "statistiques-$metric-${filter.year}",
                listOf("groupe") + (1..12).map { "mois_$it" } + "total",
                (result.groups.asSequence().map { listOf(it.label) + it.months + it.total }) +
                    sequenceOf(listOf<Any?>("Total") + result.months + result.total),
            )
        }

    private fun value(metric: String, row: TransactionEntity): BigDecimal =
        when (metric) {
            "consumption" -> ChargingSessionResponse.energyKwh(row) ?: BigDecimal.ZERO
            "usage" ->
                BigDecimal(Duration.between(row.startedAt, row.stoppedAt ?: Instant.now()).seconds.coerceAtLeast(0))
                    .divide(BigDecimal(3600), 6, RoundingMode.HALF_UP)
            "inactivity" -> BigDecimal(row.inactivitySeconds).divide(BigDecimal(3600), 6, RoundingMode.HALF_UP)
            "sessions" -> BigDecimal.ONE
            else -> row.price ?: BigDecimal.ZERO
        }

    private fun key(groupBy: String, row: TransactionEntity): String =
        when (groupBy) {
            "user" -> row.userId?.toString() ?: "unknown"
            "site" -> row.siteId?.toString() ?: "unknown"
            "site-area" -> row.siteAreaId?.toString() ?: "unknown"
            else -> row.stationId
        }

    private fun labels(tenant: UUID, groupBy: String, rows: List<TransactionEntity>): Map<String, String> {
        val unknown = "unknown" to "Non renseigné"
        return when (groupBy) {
            "user" ->
                users.findAllByTenantIdAndIdIn(tenant, rows.mapNotNull { it.userId }.toSet())
                    .associate { it.id.toString() to it.displayName } + unknown
            "site" -> organization.sites(tenant).associate { it.id.toString() to it.name } + unknown
            "site-area" -> organization.siteAreas(tenant).associate { it.id.toString() to it.name } + unknown
            else ->
                rows.map { it.stationId }.toSet().associateWith { id ->
                    stations.findById(StationKey(tenant, id)).map { it.name }.orElse(id)
                }
        }
    }
}
