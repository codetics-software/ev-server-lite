package fr.codetics.ev_server.ocpi

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.lock
import fr.codetics.ev_server.models.entities.ChargingTokenEntity
import fr.codetics.ev_server.models.entities.OcpiObjectEntity
import fr.codetics.ev_server.models.entities.OcpiObjectKey
import fr.codetics.ev_server.models.entities.OcpiOutboxEntity
import fr.codetics.ev_server.models.entities.PartnerEntity
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.entities.TransactionEntity
import fr.codetics.ev_server.models.organization.PricingData
import fr.codetics.ev_server.ocpp.ConnectorStatusChanged
import fr.codetics.ev_server.ocpp.StationConnectionChanged
import fr.codetics.ev_server.ocpp.TransactionStarted
import fr.codetics.ev_server.ocpp.TransactionStopped
import fr.codetics.ev_server.ocpp.TransactionUpdated
import fr.codetics.ev_server.persistence.Queries
import fr.codetics.ev_server.persistence.eq
import fr.codetics.ev_server.persistence.list
import fr.codetics.ev_server.repositories.ChargingTokenRepository
import fr.codetics.ev_server.repositories.ConnectorRepository
import fr.codetics.ev_server.repositories.OcpiObjectRepository
import fr.codetics.ev_server.repositories.OcpiOutboxRepository
import fr.codetics.ev_server.repositories.PartnerRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.repositories.TransactionRepository
import fr.codetics.ev_server.services.organization.Entry
import fr.codetics.ev_server.services.organization.Organization
import fr.codetics.ev_server.services.pricing.PricingService
import fr.codetics.ev_server.services.workspaces.WorkspaceService
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionalEventListener
import tools.jackson.databind.JsonNode

data class OcpiSnapshot(
    val locations: Map<String, Map<String, Any?>>,
    val tariffs: Map<String, Map<String, Any?>>,
    val stationSites: Map<String, String>,
)

@Service
class OcpiPublisher(
    private val db: Database,
    private val partners: PartnerRepository,
    private val objects: OcpiObjectRepository,
    private val outbox: OcpiOutboxRepository,
    private val organization: Organization,
    private val stations: StationRepository,
    private val connectors: ConnectorRepository,
    private val transactions: TransactionRepository,
    private val tokens: ChargingTokenRepository,
    private val tenants: TenantRepository,
    private val pricing: PricingService,
    private val mapper: OcpiMapper,
    private val transport: OcpiTransport,
    private val workspaces: WorkspaceService,
    private val queries: Queries,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val lastSessionPush = ConcurrentHashMap<Int, Instant>()

    fun active(tenant: UUID, role: String): List<PartnerEntity> =
        if (!workspaces.components(tenant).roaming) emptyList()
        else partners.findAllByTenantIdOrderByName(tenant).filter { it.enabled && it.registered && it.localRole == role }

    fun snapshot(tenant: UUID): OcpiSnapshot {
        val sites = organization.sites(tenant).filter { it.data.public }
        val areas = organization.siteAreas(tenant).associateBy { it.id }
        val definitions = organization.pricing(tenant).filter { it.data.active }
        val plugs = connectors.findAllByKeyTenantId(tenant).groupBy { it.key.stationId }
        val published =
            stations.findAllByKeyTenantIdAndDeletedFalse(tenant).filter { it.enabled && it.siteAreaId != null }.groupBy { station ->
                areas[station.siteAreaId]?.data?.siteId
            }
        val stationSites = mutableMapOf<String, String>()
        val locations =
            sites.mapNotNull { site ->
                val siteStations = published[site.id].orEmpty()
                siteStations.forEach { stationSites[it.key.stationId] = site.id.toString() }
                val publishedSite =
                    PublishedSite(site, siteStations.map { it to plugs[it.key.stationId].orEmpty().sortedBy { c -> c.key.connectorId } }, areas)
                mapper.location(publishedSite) { station, connector ->
                    pricing.resolve(pricing.context(station, connector, Instant.now()), definitions)?.id
                }?.let { site.id.toString() to it }
            }.toMap()
        val tariffs = definitions.associate { it.id.toString() to mapper.tariff(it) }
        return OcpiSnapshot(locations, tariffs, stationSites)
    }

    fun rebuild(tenant: UUID) {
        val cpo = active(tenant, "cpo")
        val emsp = active(tenant, "emsp")
        if (cpo.isEmpty() && emsp.isEmpty()) return
        val snapshot = if (cpo.isEmpty()) null else snapshot(tenant)
        db.atomic {
            cpo.forEach { partner ->
                sync(partner, "locations", requireNotNull(snapshot).locations)
                sync(partner, "tariffs", snapshot.tariffs)
            }
            if (emsp.isNotEmpty()) {
                val issuer = tenants.findById(tenant).map { it.name }.orElse("Codetics")
                val all =
                    tokens.findAllByKeyTenantId(tenant, PageRequest.of(0, 100000)).content.filter { it.userId != null }
                        .associate { it.key.uid to mapper.token(it, issuer, it.lastUsedAt ?: Instant.EPOCH) }
                emsp.forEach { sync(it, "tokens", all) }
            }
        }
    }

    private fun sync(partner: PartnerEntity, module: String, current: Map<String, Map<String, Any?>>) {
        val existing =
            queries.list<OcpiObjectEntity>(
                listOf(
                    eq("key.tenantId", partner.tenantId),
                    eq("key.partnerId", partner.id),
                    eq("key.module", module),
                    eq("key.direction", "SENDER"),
                ),
                Sort.by("key.objectPath"),
                100000,
            ).associateBy { it.key.objectPath }
        current.forEach { (id, value) -> store(partner, module, id, value, existing[id]) }
        existing.keys.filter { it !in current }.forEach { stale -> objects.delete(requireNotNull(existing[stale])) }
    }

    private fun store(
        partner: PartnerEntity,
        module: String,
        id: String,
        value: Map<String, Any?>,
        existing: OcpiObjectEntity? = null,
    ): Boolean {
        val encoded = db.encode(value)
        val previous = existing ?: objects.findById(OcpiObjectKey(partner.tenantId, partner.id, module, "SENDER", id)).orElse(null)
        if (previous != null && db.decode(previous.data) == db.decode(encoded)) return false
        objects.save(
            OcpiObjectEntity(
                OcpiObjectKey(partner.tenantId, partner.id, module, "SENDER", id),
                encoded,
                Instant.parse(value["last_updated"] as String),
            )
        )
        return true
    }

    fun enqueue(partner: PartnerEntity, module: String, method: String, path: String, body: Any?, url: String? = null) {
        outbox.save(
            OcpiOutboxEntity(
                tenantId = partner.tenantId,
                partnerId = partner.id,
                module = module,
                method = method,
                url = url,
                path = path,
                body = db.encode(body ?: emptyMap<String, Any>()),
            )
        )
    }

    private fun prefix() = "/${mapper.countryCode}/${mapper.partyId}"

    private fun stationChanged(station: StationEntity) {
        val tenant = station.key.tenantId
        val partnersToNotify = active(tenant, "cpo")
        if (partnersToNotify.isEmpty()) return
        val snapshot = snapshot(tenant)
        val siteId = snapshot.stationSites[station.key.stationId] ?: return
        val location = snapshot.locations[siteId] ?: return
        @Suppress("UNCHECKED_CAST")
        val evses = (location["evses"] as List<Map<String, Any?>>).filter {
            mapper.parseEvse(it["uid"] as String)?.first == station.key.stationId
        }
        db.atomic {
            partnersToNotify.forEach { partner ->
                if (store(partner, "locations", siteId, location)) {
                    evses.forEach { evse ->
                        enqueue(
                            partner,
                            "locations",
                            "PATCH",
                            "${prefix()}/$siteId/${evse["uid"]}",
                            mapOf("status" to evse["status"], "last_updated" to evse["last_updated"]),
                        )
                    }
                }
            }
        }
    }

    private fun sessionChanged(transaction: TransactionEntity, stopped: Boolean) {
        val partnerId = transaction.roamingPartnerId ?: return
        val partner = partners.findByTenantIdAndId(transaction.tenantId, partnerId)?.takeIf { it.enabled && it.registered } ?: return
        if (!workspaces.components(transaction.tenantId).roaming) return
        val snapshot = snapshot(transaction.tenantId)
        val siteId = snapshot.stationSites[transaction.stationId]
        val station = stations.findById(StationKey(transaction.tenantId, transaction.stationId)).orElse(null) ?: return
        val location =
            siteId?.let(snapshot.locations::get)
                ?: fallbackLocation(station)
        val session = mapper.session(transaction, location, "WHITELIST")
        db.atomic {
            store(partner, "sessions", transaction.id.toString(), session)
            enqueue(partner, "sessions", "PUT", "${prefix()}/${transaction.id}", session)
            if (stopped) {
                val tariff =
                    transaction.pricingDefinitionId?.let { id ->
                        organization.find(transaction.tenantId, "pricing-definitions", id, PricingData::class.java)?.let(mapper::tariff)
                    }
                val cdr = mapper.cdr(transaction, location, "WHITELIST", tariff)
                store(partner, "cdrs", cdr["id"] as String, cdr)
                enqueue(partner, "cdrs", "POST", "", cdr)
            }
        }
    }

    private fun fallbackLocation(station: StationEntity): Map<String, Any?> =
        mapOf(
            "id" to station.key.stationId,
            "type" to "OTHER",
            "address" to station.name,
            "city" to "-",
            "postal_code" to "00000",
            "country" to "FRA",
            "coordinates" to
                mapOf(
                    "latitude" to (station.latitude?.toPlainString() ?: "0"),
                    "longitude" to (station.longitude?.toPlainString() ?: "0"),
                ),
            "evses" to
                connectors.findAllByKeyTenantIdAndKeyStationIdOrderByKeyConnectorId(station.key.tenantId, station.key.stationId)
                    .filter { it.key.connectorId > 0 }
                    .map { mapper.evse(station, it, null, null) },
            "last_updated" to mapper.timestamp(Instant.now()),
        )

    private fun safely(name: String, block: () -> Unit) {
        try {
            block()
        } catch (error: RuntimeException) {
            log.warn("Publication OCPI {} interrompue", name, error)
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    fun status(event: ConnectorStatusChanged) = safely("status") { stationChanged(event.station) }

    @TransactionalEventListener(fallbackExecution = true)
    fun connection(event: StationConnectionChanged) = safely("connection") { stationChanged(event.station) }

    @TransactionalEventListener(fallbackExecution = true)
    fun started(event: TransactionStarted) = safely("session") { sessionChanged(event.transaction, false) }

    @TransactionalEventListener(fallbackExecution = true)
    fun updated(event: TransactionUpdated) =
        safely("session") {
            val id = event.transaction.id ?: return@safely
            if (event.transaction.roamingPartnerId == null) return@safely
            val last = lastSessionPush[id]
            if (last != null && last.isAfter(Instant.now().minus(Duration.ofMinutes(5)))) return@safely
            lastSessionPush[id] = Instant.now()
            sessionChanged(event.transaction, false)
        }

    @TransactionalEventListener(fallbackExecution = true)
    fun stopped(event: TransactionStopped) =
        safely("cdr") {
            event.transaction.id?.let(lastSessionPush::remove)
            sessionChanged(event.transaction, true)
        }

    @Scheduled(initialDelay = 120000, fixedDelay = 900000)
    fun periodic() {
        tenants.findAll().filter { it.active }.forEach { tenant -> safely("rebuild") { rebuild(tenant.id) } }
    }

    @Scheduled(initialDelay = 20000, fixedDelay = 10000)
    fun deliver() {
        outbox.findAllBySentAtIsNullAndAttemptsLessThanAndNextAttemptAtBeforeOrderByCreatedAt(MAX_ATTEMPTS, Instant.now(), PageRequest.of(0, 50))
            .forEach { entry -> safely("outbox") { send(entry.id) } }
        safely("cleanup") { db.atomic { outbox.deleteAllBySentAtBefore(Instant.now().minus(Duration.ofDays(7))) } }
    }

    private fun send(id: UUID) {
        val entry = outbox.findById(id).orElse(null)?.takeIf { it.sentAt == null } ?: return
        val partner = partners.findByTenantIdAndId(entry.tenantId, entry.partnerId)
        val failure: String? =
            try {
                if (partner == null || !partner.enabled || !partner.registered) throw OcpiFault(3001, "Partenaire indisponible.")
                val url =
                    entry.url
                        ?: transport.endpoint(partner, entry.module)?.let { it + entry.path }
                        ?: throw OcpiFault(3001, "Le partenaire ne publie pas le module ${entry.module}.")
                val body: JsonNode? = if (entry.method == "GET" || entry.method == "DELETE") null else db.decode(entry.body)
                transport.request(partner, url, entry.method, body)
                null
            } catch (fault: OcpiFault) {
                fault.message
            }
        db.atomic {
            val locked = db.lock<OcpiOutboxEntity>(id) ?: return@atomic
            if (failure == null) {
                locked.sentAt = Instant.now()
                locked.lastError = null
            } else {
                locked.attempts += 1
                locked.lastError = failure.take(500)
                locked.nextAttemptAt = Instant.now().plus(Duration.ofSeconds((30L shl locked.attempts.coerceAtMost(7)).coerceAtMost(3600)))
            }
            outbox.save(locked)
            if (partner != null) {
                val stored = db.lock<PartnerEntity>(partner.id) ?: return@atomic
                if (failure == null) stored.lastPushAt = Instant.now() else stored.lastError = failure.take(500)
                partners.save(stored)
            }
        }
    }

    fun tokensFor(tenant: UUID): List<Pair<ChargingTokenEntity, Map<String, Any?>>> {
        val issuer = tenants.findById(tenant).map { it.name }.orElse("Codetics")
        return tokens.findAllByKeyTenantId(tenant, PageRequest.of(0, 100000)).content.filter { it.userId != null }
            .map { it to mapper.token(it, issuer, it.lastUsedAt ?: Instant.EPOCH) }
    }

    fun sessionsFor(partner: PartnerEntity): List<TransactionEntity> =
        queries.list<TransactionEntity>(
            listOf(eq("tenantId", partner.tenantId), eq("roamingPartnerId", partner.id)),
            Sort.by(Sort.Direction.DESC, "startedAt"),
            5000,
        )

    fun location(transaction: TransactionEntity, snapshot: OcpiSnapshot): Map<String, Any?>? {
        val siteId = snapshot.stationSites[transaction.stationId] ?: return null
        return snapshot.locations[siteId]
    }

    fun tariffEntry(tenant: UUID, id: UUID): Entry<PricingData>? =
        organization.find(tenant, "pricing-definitions", id, PricingData::class.java)

    private companion object {
        const val MAX_ATTEMPTS = 10
    }
}
