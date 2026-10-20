package fr.codetics.ev_server.ocpi

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.models.entities.ConnectorKey
import fr.codetics.ev_server.models.entities.OcpiObjectEntity
import fr.codetics.ev_server.models.entities.OcpiObjectKey
import fr.codetics.ev_server.models.entities.PartnerEntity
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.ocpp.AuthorizationStatus
import fr.codetics.ev_server.ocpp.CommandCompleted
import fr.codetics.ev_server.ocpp.OcppCommandQueue
import fr.codetics.ev_server.ocpp.RoamingAuthorization
import fr.codetics.ev_server.ocpp.RoamingDecision
import fr.codetics.ev_server.persistence.Queries
import fr.codetics.ev_server.persistence.contains
import fr.codetics.ev_server.persistence.eq
import fr.codetics.ev_server.persistence.list
import fr.codetics.ev_server.repositories.ConnectorRepository
import fr.codetics.ev_server.repositories.OcpiObjectRepository
import fr.codetics.ev_server.repositories.PartnerRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TransactionRepository
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionalEventListener
import tools.jackson.databind.JsonNode

@Service
class OcpiRoaming(
    private val db: Database,
    private val partners: PartnerRepository,
    private val objects: OcpiObjectRepository,
    private val stations: StationRepository,
    private val connectors: ConnectorRepository,
    private val transactions: TransactionRepository,
    private val queue: OcppCommandQueue,
    private val transport: OcpiTransport,
    private val publisher: OcpiPublisher,
    private val mapper: OcpiMapper,
    private val queries: Queries,
) : RoamingAuthorization {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun authorize(station: StationEntity, uid: String): RoamingDecision? {
        val tenant = station.key.tenantId
        val cpo = publisher.active(tenant, "cpo")
        if (cpo.isEmpty()) return null
        val candidates =
            queries.list<OcpiObjectEntity>(
                listOf(
                    eq("key.tenantId", tenant),
                    eq("key.module", "tokens"),
                    eq("key.direction", "RECEIVER"),
                    contains("key.objectPath", "/$uid"),
                ),
                Sort.by(Sort.Direction.DESC, "lastUpdated"),
                20,
            ).filter { it.key.objectPath.substringAfterLast('/') == uid }
        val match =
            candidates.firstNotNullOfOrNull { entry ->
                cpo.firstOrNull { it.id == entry.key.partnerId }?.let { it to db.decode(entry.data) }
            } ?: return null
        val (partner, token) = match
        if (!token.path("valid").asBoolean(false)) return RoamingDecision(AuthorizationStatus.Blocked, partner.id)
        return when (token.path("whitelist").asText("ALLOWED")) {
            "ALWAYS", "ALLOWED" -> RoamingDecision(AuthorizationStatus.Accepted, partner.id)
            "ALLOWED_OFFLINE" -> realtime(partner, station, uid) ?: RoamingDecision(AuthorizationStatus.Accepted, partner.id)
            else -> realtime(partner, station, uid) ?: RoamingDecision(AuthorizationStatus.Invalid, partner.id)
        }
    }

    private fun realtime(partner: PartnerEntity, station: StationEntity, uid: String): RoamingDecision? {
        val url = transport.endpoint(partner, "tokens") ?: return null
        val snapshotSite =
            connectors.findAllByKeyTenantIdAndKeyStationIdOrderByKeyConnectorId(station.key.tenantId, station.key.stationId)
                .firstOrNull { it.key.connectorId > 0 }
        return try {
            val info =
                transport.request(
                    partner,
                    "$url/$uid/authorize",
                    "POST",
                    snapshotSite?.let {
                        mapOf("evse_uids" to listOf(mapper.evseUid(station.key.stationId, it.key.connectorId)))
                    },
                )
            val status =
                when (info.path("allowed").asText()) {
                    "ALLOWED" -> AuthorizationStatus.Accepted
                    "EXPIRED" -> AuthorizationStatus.Expired
                    "BLOCKED", "NO_CREDIT", "NOT_ALLOWED" -> AuthorizationStatus.Blocked
                    else -> AuthorizationStatus.Invalid
                }
            RoamingDecision(status, partner.id)
        } catch (fault: OcpiFault) {
            log.info("Autorisation OCPI en temps réel indisponible : {}", fault.message)
            null
        }
    }

    fun command(partner: PartnerEntity, type: String, body: JsonNode?): Any {
        if (partner.localRole != "cpo") {
            throw OcpiFault(2001, "Les commandes sont réservées à l’interface opérateur.", HttpStatus.NOT_FOUND)
        }
        require(body != null && body.isObject)
        val responseUrl = body.path("response_url").asText()
        transport.validateRemote(responseUrl, requireNotNull(partner.remoteUrl))
        val tenant = partner.tenantId
        fun reject(result: String = "REJECTED") = ocpi(mapOf("result" to result))
        val callback = db.encode(mapOf("partnerId" to partner.id, "responseUrl" to responseUrl, "type" to type))

        fun evse(locationId: String, evseUid: String): Pair<StationEntity, Int>? {
            val (stationId, connector) = mapper.parseEvse(evseUid) ?: return null
            val station =
                stations.findById(StationKey(tenant, stationId)).orElse(null)?.takeIf { it.enabled && !it.deleted }
                    ?: return null
            val published = publisher.snapshot(tenant)
            if (published.stationSites[stationId] != locationId) return null
            return station to connector
        }

        return when (type) {
            "START_SESSION" -> {
                val token = body.path("token")
                val uid = token.path("uid").asText()
                require(uid.matches(Regex("[A-Za-z0-9_-]{1,20}")))
                val target = evse(body.path("location_id").asText(), body.path("evse_uid").asText()) ?: return reject()
                if (!target.first.connected) return reject()
                db.atomic {
                    objects.save(
                        OcpiObjectEntity(
                            OcpiObjectKey(tenant, partner.id, "tokens", "RECEIVER", "${partner.countryCode}/${partner.partyId}/$uid"),
                            db.encode(token),
                            Instant.now(),
                        )
                    )
                    queue.command(
                        tenant,
                        target.first.key.stationId,
                        "RemoteStartTransaction",
                        db.json.valueToTree(mapOf("idTag" to uid, "connectorId" to target.second)),
                        callback,
                    )
                }
                ocpi(mapOf("result" to "ACCEPTED"))
            }
            "STOP_SESSION" -> {
                val id = body.path("session_id").asText().toIntOrNull() ?: return reject("UNKNOWN_SESSION")
                val transaction = transactions.findByTenantIdAndId(tenant, id)
                if (transaction == null || transaction.roamingPartnerId != partner.id || transaction.stoppedAt != null) {
                    return reject("UNKNOWN_SESSION")
                }
                queue.command(
                    tenant,
                    transaction.stationId,
                    "RemoteStopTransaction",
                    db.json.valueToTree(mapOf("transactionId" to id)),
                    callback,
                )
                ocpi(mapOf("result" to "ACCEPTED"))
            }
            "RESERVE_NOW" -> {
                val uid = body.path("token").path("uid").asText()
                require(uid.matches(Regex("[A-Za-z0-9_-]{1,20}")))
                val target = evse(body.path("location_id").asText(), body.path("evse_uid").asText()) ?: return reject()
                db.atomic {
                    objects.save(
                        OcpiObjectEntity(
                            OcpiObjectKey(tenant, partner.id, "tokens", "RECEIVER", "${partner.countryCode}/${partner.partyId}/$uid"),
                            db.encode(body.path("token")),
                            Instant.now(),
                        )
                    )
                    queue.command(
                        tenant,
                        target.first.key.stationId,
                        "ReserveNow",
                        db.json.valueToTree(
                            mapOf(
                                "connectorId" to target.second,
                                "expiryDate" to Instant.parse(body.path("expiry_date").asText()).toString(),
                                "idTag" to uid,
                                "reservationId" to body.path("reservation_id").asInt(),
                            )
                        ),
                        callback,
                    )
                }
                ocpi(mapOf("result" to "ACCEPTED"))
            }
            "UNLOCK_CONNECTOR" -> {
                val target = evse(body.path("location_id").asText(), body.path("evse_uid").asText()) ?: return reject()
                val connector = connectors.findById(ConnectorKey(tenant, target.first.key.stationId, target.second)).orElse(null)
                    ?: return reject()
                queue.command(
                    tenant,
                    target.first.key.stationId,
                    "UnlockConnector",
                    db.json.valueToTree(mapOf("connectorId" to connector.key.connectorId)),
                    callback,
                )
                ocpi(mapOf("result" to "ACCEPTED"))
            }
            else -> reject("NOT_SUPPORTED")
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    fun completed(event: CommandCompleted) {
        val callback = event.command.callback ?: return
        try {
            val target = db.decode(callback)
            val partner = partners.findByTenantIdAndId(event.command.tenantId, UUID.fromString(target.path("partnerId").asText())) ?: return
            val status = event.result.path("status").asText()
            val result =
                when {
                    !event.success && event.result.isNull -> "TIMEOUT"
                    !event.success -> "FAILED"
                    status in setOf("Accepted", "Unlocked") -> "ACCEPTED"
                    status == "Occupied" -> "EVSE_OCCUPIED"
                    status in setOf("Faulted", "Unavailable", "UnlockFailed", "NotSupported") -> "EVSE_INOPERATIVE"
                    else -> "REJECTED"
                }
            db.isolated {
                publisher.enqueue(partner, "commands", "POST", "", mapOf("result" to result), target.path("responseUrl").asText())
            }
        } catch (error: RuntimeException) {
            log.warn("Réponse de commande OCPI impossible", error)
        }
    }
}
