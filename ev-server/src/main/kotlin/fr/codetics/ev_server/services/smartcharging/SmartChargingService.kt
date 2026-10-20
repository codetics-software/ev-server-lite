package fr.codetics.ev_server.services.smartcharging

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.NotFound
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.models.entities.ConnectorEntity
import fr.codetics.ev_server.models.entities.SmartChargingPlanEntity
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.TransactionEntity
import fr.codetics.ev_server.models.organization.AssetData
import fr.codetics.ev_server.models.organization.SiteAreaData
import fr.codetics.ev_server.ocpp.MeterReadings
import fr.codetics.ev_server.ocpp.TransactionStarted
import fr.codetics.ev_server.ocpp.TransactionStopped
import fr.codetics.ev_server.repositories.AssetConsumptionRepository
import fr.codetics.ev_server.repositories.ChargingProfileRepository
import fr.codetics.ev_server.repositories.ConnectorRepository
import fr.codetics.ev_server.repositories.MeterValueRepository
import fr.codetics.ev_server.repositories.SmartChargingPlanRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.repositories.TransactionRepository
import fr.codetics.ev_server.services.organization.Entry
import fr.codetics.ev_server.services.organization.Organization
import fr.codetics.ev_server.services.workspaces.WorkspaceService
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionalEventListener
import tools.jackson.databind.JsonNode
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class Allocation(
    val stationId: String,
    val connectorId: Int,
    val transactionId: Int,
    val limitKw: BigDecimal,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SmartChargingPlan(
    val enabled: Boolean,
    val maximumPowerKw: BigDecimal?,
    val availablePowerKw: BigDecimal?,
    val computedAt: Instant?,
    val allocations: List<Allocation>,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class AreaConsumption(
    val at: Instant,
    val powerKw: BigDecimal,
    val limitKw: BigDecimal?,
    val assetsPowerKw: BigDecimal,
)

internal data class Candidate(
    val transaction: TransactionEntity,
    val minimumW: BigDecimal,
    val maximumW: BigDecimal,
    val voltage: Int,
    val phases: Int,
)

@Service
class SmartChargingService(
    private val db: Database,
    private val organization: Organization,
    private val stations: StationRepository,
    private val connectors: ConnectorRepository,
    private val transactions: TransactionRepository,
    private val assetsConsumption: AssetConsumptionRepository,
    private val meters: MeterValueRepository,
    private val plans: SmartChargingPlanRepository,
    private val profiles: ChargingProfileRepository,
    private val chargingProfiles: ChargingProfileService,
    private val tenants: TenantRepository,
    private val workspaces: WorkspaceService,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val dirty = ConcurrentHashMap.newKeySet<UUID>()

    @TransactionalEventListener(fallbackExecution = true)
    fun started(event: TransactionStarted) {
        dirty += event.transaction.tenantId
    }

    @TransactionalEventListener(fallbackExecution = true)
    fun stopped(event: TransactionStopped) {
        dirty += event.transaction.tenantId
        try {
            db.isolated {
                profiles.findAllByTenantIdAndStationIdAndSource(event.transaction.tenantId, event.transaction.stationId, "SMART_CHARGING")
                    .filter { it.transactionId == event.transaction.id }
                    .forEach(profiles::delete)
            }
        } catch (error: RuntimeException) {
            log.warn("Nettoyage des profils de recharge intelligente impossible", error)
        }
    }

    @Scheduled(initialDelay = 30000, fixedDelay = 15000)
    fun pending() {
        val tenantsToCompute = dirty.toList()
        dirty.removeAll(tenantsToCompute.toSet())
        tenantsToCompute.forEach(::safeCompute)
    }

    @Scheduled(initialDelay = 90000, fixedDelay = 300000)
    fun periodic() {
        tenants.findAll().filter { it.active }.forEach { safeCompute(it.id) }
    }

    private fun safeCompute(tenant: UUID) {
        try {
            compute(tenant)
        } catch (error: RuntimeException) {
            log.warn("Calcul de recharge intelligente impossible pour l’espace {}", tenant, error)
        }
    }

    fun trigger(user: AppPrincipal, areaId: UUID): SmartChargingPlan {
        organization.siteArea(user.tenantId, areaId) ?: throw NotFound("Zone introuvable.")
        compute(user.tenantId)
        db.audit(user.tenantId, user.id.toString(), "SMART_CHARGING_TRIGGER", areaId.toString())
        return plan(user, areaId)
    }

    fun plan(user: AppPrincipal, areaId: UUID): SmartChargingPlan {
        val area = organization.siteArea(user.tenantId, areaId) ?: throw NotFound("Zone introuvable.")
        val stored = plans.findById(areaId).orElse(null)?.takeIf { it.tenantId == user.tenantId }
        return stored?.let { db.json.readValue(it.plan, SmartChargingPlan::class.java) }
            ?: SmartChargingPlan(area.data.smartCharging, area.data.maximumPowerKw, null, null, emptyList())
    }

    fun compute(tenant: UUID) {
        if (!workspaces.components(tenant).smartCharging) return
        val areas = organization.siteAreas(tenant)
        val smart = areas.filter { it.data.smartCharging && it.data.maximumPowerKw != null }
        if (smart.isEmpty()) return

        val children = areas.groupBy { it.data.parentSiteAreaId }
        val allStations = stations.findAllByKeyTenantIdAndDeletedFalse(tenant).filter { it.enabled }
        val stationsByArea = allStations.groupBy { it.siteAreaId }
        val stationsById = allStations.associateBy { it.key.stationId }
        val plugs = connectors.findAllByKeyTenantId(tenant).associateBy { it.key.stationId to it.key.connectorId }
        val active = transactions.findAllByTenantIdAndStoppedAtIsNull(tenant)
        val assets = organization.assets(tenant)
        val now = Instant.now()

        val limits = mutableMapOf<Int, BigDecimal>()
        val details = mutableMapOf<UUID, Pair<BigDecimal, List<Pair<Candidate, BigDecimal>>>>()
        smart.forEach { area ->
            val subtree = subtree(area.id, children)
            val inside = subtree.flatMap { stationsByArea[it].orEmpty() }.associateBy { it.key.stationId }
            val sessions = active.filter { it.stationId in inside }
            val excludedLoad =
                sessions.filter { inside[it.stationId]?.excludeFromSmartCharging == true }
                    .fold(BigDecimal.ZERO) { sum, it -> sum + (it.lastPowerW ?: BigDecimal.ZERO) }
            val assetLoad = assetLoad(tenant, assets.filter { it.data.siteAreaId in subtree }, now)
            val maximumW = requireNotNull(area.data.maximumPowerKw).multiply(KW)
            val available = (maximumW - excludedLoad - assetLoad).max(BigDecimal.ZERO)
            val candidates =
                sessions.filter { inside[it.stationId]?.excludeFromSmartCharging != true }
                    .map { candidate(it, requireNotNull(inside[it.stationId]), plugs[it.stationId to it.connectorId], area.data) }
            val allocation = allocate(available, candidates)
            allocation.forEach { (candidate, watts) ->
                val id = requireNotNull(candidate.transaction.id)
                limits[id] = limits[id]?.min(watts) ?: watts
            }
            details[area.id] = available to allocation
        }

        db.atomic {
            details.forEach { (areaId, entry) ->
                val (available, allocation) = entry
                val area = smart.first { it.id == areaId }
                val previous =
                    plans.findById(areaId).orElse(null)?.let { db.json.readValue(it.plan, SmartChargingPlan::class.java) }
                val previousLimits = previous?.allocations.orEmpty().associate { it.transactionId to it.limitKw }
                val result =
                    allocation.map { (candidate, _) ->
                        val transaction = candidate.transaction
                        val watts = requireNotNull(limits[transaction.id])
                        val kw = watts.divide(KW, 3, RoundingMode.HALF_DOWN)
                        val before = previousLimits[transaction.id]
                        if (before == null || changed(before, kw)) {
                            send(stationsById.getValue(transaction.stationId), transaction, watts, candidate)
                        }
                        Allocation(transaction.stationId, transaction.connectorId, requireNotNull(transaction.id), kw)
                    }
                val plan =
                    SmartChargingPlan(
                        true,
                        area.data.maximumPowerKw,
                        available.divide(KW, 3, RoundingMode.HALF_DOWN),
                        now,
                        result,
                    )
                plans.save(SmartChargingPlanEntity(areaId, tenant, db.encode(plan), now))
            }
        }
    }

    private fun changed(before: BigDecimal, after: BigDecimal): Boolean {
        if (before.signum() == 0 || after.signum() == 0) return before.compareTo(after) != 0
        return (before - after).abs().divide(before, 4, RoundingMode.HALF_UP) > BigDecimal("0.05")
    }

    private fun send(station: StationEntity, transaction: TransactionEntity, watts: BigDecimal, candidate: Candidate) {
        val amps =
            watts.divide(BigDecimal(candidate.voltage * candidate.phases), 1, RoundingMode.DOWN).max(BigDecimal.ZERO)
        val profile =
            db.json.valueToTree<JsonNode>(
                mapOf(
                    "chargingProfileId" to ChargingProfileService.SMART_CHARGING_ID + transaction.connectorId,
                    "transactionId" to transaction.id,
                    "stackLevel" to 5,
                    "chargingProfilePurpose" to "TxProfile",
                    "chargingProfileKind" to "Relative",
                    "chargingSchedule" to
                        mapOf(
                            "chargingRateUnit" to "A",
                            "chargingSchedulePeriod" to
                                listOf(mapOf("startPeriod" to 0, "limit" to amps, "numberPhases" to candidate.phases)),
                        ),
                )
            )
        chargingProfiles.apply(station.key.tenantId, station.key.stationId, transaction.connectorId, profile, "SMART_CHARGING", transaction.id)
    }

    private fun candidate(
        transaction: TransactionEntity,
        station: StationEntity,
        connector: ConnectorEntity?,
        area: SiteAreaData,
    ): Candidate {
        val phases = connector?.phases ?: area.numberOfPhases
        val voltage = area.voltage
        val electrical = BigDecimal(voltage * phases * MAXIMUM_AMPS)
        val maximum =
            listOfNotNull(connector?.maximumPowerKw?.multiply(KW), station.maximumPowerKw?.multiply(KW), electrical).min()
        val minimum = BigDecimal(voltage * phases * MINIMUM_AMPS).min(maximum)
        return Candidate(transaction, minimum, maximum, voltage, phases)
    }

    internal fun allocate(available: BigDecimal, candidates: List<Candidate>): List<Pair<Candidate, BigDecimal>> {
        val ordered = candidates.sortedWith(compareBy({ it.transaction.startedAt }, { it.transaction.id }))
        var remaining = available
        val granted = linkedMapOf<Candidate, BigDecimal>()
        ordered.forEach { candidate ->
            if (remaining >= candidate.minimumW) {
                granted[candidate] = candidate.minimumW
                remaining -= candidate.minimumW
            } else {
                granted[candidate] = BigDecimal.ZERO
            }
        }
        var open = granted.filter { (candidate, watts) -> watts.signum() > 0 && watts < candidate.maximumW }.keys.toMutableList()
        while (remaining > BigDecimal.ONE && open.isNotEmpty()) {
            val share = remaining.divide(BigDecimal(open.size), 3, RoundingMode.DOWN)
            var consumed = BigDecimal.ZERO
            open.forEach { candidate ->
                val current = requireNotNull(granted[candidate])
                val increase = share.min(candidate.maximumW - current)
                granted[candidate] = current + increase
                consumed += increase
            }
            remaining -= consumed
            open = open.filter { requireNotNull(granted[it]) < it.maximumW }.toMutableList()
            if (consumed.signum() == 0) break
        }
        return granted.map { it.key to it.value }
    }

    private fun assetLoad(tenant: UUID, assets: List<Entry<AssetData>>, now: Instant): BigDecimal =
        assets.filter { !it.data.excludeFromSmartCharging }.fold(BigDecimal.ZERO) { sum, asset ->
            val latest =
                assetsConsumption.findFirstByTenantIdAndAssetIdOrderBySampledAtDesc(tenant, asset.id)
                    ?.takeIf { it.sampledAt.isAfter(now.minus(Duration.ofMinutes(30))) }
            val watts = latest?.powerW ?: asset.data.staticPowerKw?.multiply(KW) ?: BigDecimal.ZERO
            if (asset.data.producing()) sum - watts.abs() else sum + watts
        }

    private fun subtree(root: UUID, children: Map<UUID?, List<Entry<SiteAreaData>>>): Set<UUID> {
        val result = mutableSetOf(root)
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty()) {
            children[queue.removeFirst()].orEmpty().forEach { child ->
                if (result.add(child.id)) queue.add(child.id)
            }
        }
        return result
    }

    fun consumptions(user: AppPrincipal, areaId: UUID, from: Instant?, to: Instant?): List<AreaConsumption> {
        val area = organization.siteArea(user.tenantId, areaId) ?: throw NotFound("Zone introuvable.")
        val end = to ?: Instant.now()
        val start = from ?: end.minus(Duration.ofDays(1))
        ensure(start.isBefore(end) && Duration.between(start, end) <= Duration.ofDays(31)) {
            "Choisissez une période de 31 jours au plus."
        }
        val children = organization.siteAreas(user.tenantId).groupBy { it.data.parentSiteAreaId }
        val subtree = subtree(areaId, children)
        val inside =
            stations.findAllByKeyTenantIdAndDeletedFalse(user.tenantId).filter { it.siteAreaId in subtree }.map { it.key.stationId }
        val bucket = if (Duration.between(start, end) > Duration.ofDays(2)) Duration.ofHours(1) else Duration.ofMinutes(5)
        fun slot(at: Instant) = at.toEpochMilli() / bucket.toMillis()

        val stationPower = mutableMapOf<Long, MutableMap<Pair<String, Int>, MutableList<BigDecimal>>>()
        if (inside.isNotEmpty()) {
            meters.findAllByTenantIdAndStationIdInAndSampledAtBetweenOrderBySampledAt(user.tenantId, inside, start, end).forEach { row ->
                val reading =
                    runCatching {
                        val node = db.json.createObjectNode()
                        node.put("timestamp", row.sampledAt.toString())
                        node.set("sampledValue", db.decode(row.samples))
                        MeterReadings.parse(node)
                    }.getOrNull() ?: return@forEach
                val power = reading.powerW ?: return@forEach
                stationPower.getOrPut(slot(row.sampledAt)) { mutableMapOf() }
                    .getOrPut(row.stationId to row.connectorId) { mutableListOf() }
                    .add(power)
            }
        }
        val assetPower = mutableMapOf<Long, MutableMap<UUID, MutableList<BigDecimal>>>()
        organization.assets(user.tenantId).filter { it.data.siteAreaId in subtree }.forEach { asset ->
            assetsConsumption.findAllByTenantIdAndAssetIdAndSampledAtBetweenOrderBySampledAt(user.tenantId, asset.id, start, end).forEach {
                val watts = if (asset.data.producing()) it.powerW.abs().negate() else it.powerW
                assetPower.getOrPut(slot(it.sampledAt)) { mutableMapOf() }.getOrPut(asset.id) { mutableListOf() }.add(watts)
            }
        }
        fun average(values: Collection<List<BigDecimal>>) =
            values.fold(BigDecimal.ZERO) { sum, list -> sum + list.fold(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal(list.size), 3, RoundingMode.HALF_UP) }
        return (stationPower.keys + assetPower.keys).sorted().map { key ->
            AreaConsumption(
                Instant.ofEpochMilli(key * bucket.toMillis()),
                average(stationPower[key]?.values.orEmpty()).divide(KW, 3, RoundingMode.HALF_UP),
                area.data.maximumPowerKw,
                average(assetPower[key]?.values.orEmpty()).divide(KW, 3, RoundingMode.HALF_UP),
            )
        }
    }

    private companion object {
        val KW = BigDecimal(1000)
        const val MINIMUM_AMPS = 6
        const val MAXIMUM_AMPS = 32
    }
}
