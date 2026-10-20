package fr.codetics.ev_server.services.sessions

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.core.Csv
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.NotFound
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.core.lock
import fr.codetics.ev_server.core.pageRequest
import fr.codetics.ev_server.models.api.MutationResponse
import fr.codetics.ev_server.models.api.PageResponse
import fr.codetics.ev_server.models.commands.CommandReceipt
import fr.codetics.ev_server.models.entities.ChargingTokenKey
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.entities.TransactionEntity
import fr.codetics.ev_server.models.organization.PricingData
import fr.codetics.ev_server.models.people.UserRole
import fr.codetics.ev_server.models.sessions.ChargingSessionResponse
import fr.codetics.ev_server.models.sessions.ConsumptionPoint
import fr.codetics.ev_server.models.sessions.SessionInError
import fr.codetics.ev_server.models.sessions.SessionStartInput
import fr.codetics.ev_server.ocpp.AuthorizationStatus
import fr.codetics.ev_server.ocpp.MeterReadings
import fr.codetics.ev_server.ocpp.OcppCommandQueue
import fr.codetics.ev_server.ocpp.OcppService
import fr.codetics.ev_server.persistence.Condition
import fr.codetics.ev_server.persistence.Queries
import fr.codetics.ev_server.persistence.anyOf
import fr.codetics.ev_server.persistence.atLeast
import fr.codetics.ev_server.persistence.before
import fr.codetics.ev_server.persistence.contains
import fr.codetics.ev_server.persistence.eq
import fr.codetics.ev_server.persistence.list
import fr.codetics.ev_server.persistence.page
import fr.codetics.ev_server.persistence.present
import fr.codetics.ev_server.repositories.ChargingPaymentRepository
import fr.codetics.ev_server.repositories.ChargingTokenRepository
import fr.codetics.ev_server.repositories.MeterValueRepository
import fr.codetics.ev_server.repositories.PartnerRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TransactionRepository
import fr.codetics.ev_server.repositories.UserRepository
import fr.codetics.ev_server.services.billing.ChargingPaymentLifecycle
import fr.codetics.ev_server.services.organization.Entry
import fr.codetics.ev_server.services.organization.Organization
import fr.codetics.ev_server.services.pricing.PricingService
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

data class SessionFilter(
    val status: String? = null,
    val stationId: String? = null,
    val siteId: UUID? = null,
    val siteAreaId: UUID? = null,
    val userId: UUID? = null,
    val tokenUid: String? = null,
    val from: Instant? = null,
    val to: Instant? = null,
    val search: String = "",
)

@Service
class ChargingSessionService(
    private val db: Database,
    private val sessions: TransactionRepository,
    private val queries: Queries,
    private val users: UserRepository,
    private val partners: PartnerRepository,
    private val payments: ChargingPaymentRepository,
    private val paymentLifecycle: ChargingPaymentLifecycle,
    private val meters: MeterValueRepository,
    private val stations: StationRepository,
    private val tokens: ChargingTokenRepository,
    private val queue: OcppCommandQueue,
    private val ocpp: OcppService,
    private val pricing: PricingService,
    private val organization: Organization,
    private val properties: AppProperties,
) {
    fun list(
        user: AppPrincipal,
        offset: Int,
        limit: Int,
        filter: SessionFilter = SessionFilter(),
    ): PageResponse<ChargingSessionResponse> {
        val page =
            queries.page<TransactionEntity>(
                conditions(user, filter),
                pageRequest(offset, limit, Sort.by(Sort.Direction.DESC, "startedAt", "id")),
            )
        return PageResponse(views(user.tenantId, page.content), page.totalElements)
    }

    fun all(user: AppPrincipal, filter: SessionFilter, limit: Int = 50000): List<TransactionEntity> =
        queries.list(conditions(user, filter), Sort.by(Sort.Direction.DESC, "startedAt", "id"), limit)

    fun conditions(user: AppPrincipal, filter: SessionFilter): List<Condition> {
        ensure(filter.search.length <= 200) { "La recherche est trop longue." }
        val conditions = mutableListOf<Condition>(eq("tenantId", user.tenantId))
        val userId = if (user.role == UserRole.DRIVER) user.id else filter.userId
        userId?.let { conditions += eq("userId", it) }
        when (filter.status) {
            "active" -> conditions += present("stoppedAt", false)
            "completed" -> conditions += present("stoppedAt")
            null, "" -> Unit
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtre d’état inconnu.")
        }
        filter.stationId?.takeIf(String::isNotBlank)?.let { conditions += eq("stationId", it) }
        filter.siteId?.let { conditions += eq("siteId", it) }
        filter.siteAreaId?.let { conditions += eq("siteAreaId", it) }
        filter.tokenUid?.takeIf(String::isNotBlank)?.let { conditions += eq("tokenUid", it) }
        filter.from?.let { conditions += atLeast("startedAt", it) }
        filter.to?.let { conditions += before("startedAt", it) }
        if (filter.search.isNotBlank()) {
            conditions +=
                anyOf(contains("stationId", filter.search.trim()), contains("tokenUid", filter.search.trim()))
        }
        return conditions
    }

    fun find(user: AppPrincipal, id: Int): ChargingSessionResponse {
        val transaction = session(user, id)
        val priced = organization.pricing(user.tenantId).isNotEmpty()
        return views(user.tenantId, listOf(transaction)).first().copy(errors = errors(transaction, null, priced))
    }

    fun session(user: AppPrincipal, id: Int): TransactionEntity {
        val transaction = sessions.findByTenantIdAndId(user.tenantId, id) ?: throw NotFound("Session introuvable.")
        if (user.role == UserRole.DRIVER && transaction.userId != user.id) {
            throw NotFound("Session introuvable.")
        }
        return transaction
    }

    fun views(tenant: UUID, page: List<TransactionEntity>): List<ChargingSessionResponse> {
        if (page.isEmpty()) return emptyList()
        val names =
            users.findAllByTenantIdAndIdIn(tenant, page.mapNotNull { it.userId }.toSet()).associate {
                it.id to it.displayName
            }
        val partnerNames =
            page.mapNotNull { it.roamingPartnerId }.toSet().associateWith { id ->
                partners.findByTenantIdAndId(tenant, id)?.name
            }
        val active = page.filter { it.stoppedAt == null }
        val definitions = if (active.isEmpty()) emptyList() else organization.pricing(tenant)
        val stationsById =
            if (active.isEmpty()) emptyMap()
            else active.map { it.stationId }.toSet().mapNotNull { stations.findById(StationKey(tenant, it)).orElse(null) }
                .associateBy { it.key.stationId }
        val now = Instant.now()
        return page.map { transaction ->
            val estimate =
                if (transaction.stoppedAt == null) {
                    stationsById[transaction.stationId]?.let { station ->
                        pricing.price(transaction, station, now, definitions = definitions)
                    }
                } else {
                    null
                }
            ChargingSessionResponse.from(
                transaction,
                transaction.userId?.let(names::get),
                transaction.roamingPartnerId?.let(partnerNames::get),
                transaction.id?.let { payments.findByTenantIdAndTransactionId(tenant, it) } != null,
                estimate?.price,
                estimate?.currency,
                now,
            )
        }
    }

    fun consumptions(user: AppPrincipal, id: Int): List<ConsumptionPoint> {
        val transaction = session(user, id)
        val station = stations.findById(StationKey(user.tenantId, transaction.stationId)).orElse(null)
        val definition: Entry<PricingData>? =
            transaction.pricingDefinitionId?.let { definitionId ->
                organization.find(user.tenantId, "pricing-definitions", definitionId, PricingData::class.java)
            }
                ?: station?.let { pricing.resolve(pricing.context(it, transaction.connectorId, transaction.startedAt)) }
        val readings =
            meters.findAllByTenantIdAndTransactionIdOrderBySampledAt(user.tenantId, requireNotNull(transaction.id))
                .mapNotNull { row ->
                    runCatching {
                        val reading = db.json.createObjectNode()
                        reading.put("timestamp", row.sampledAt.toString())
                        reading.set("sampledValue", db.decode(row.samples))
                        MeterReadings.parse(reading)
                    }.getOrNull()
                }
                .sortedBy { it.at }

        var previousEnergy: BigDecimal = transaction.meterStartWh
        var previousAt: Instant = transaction.startedAt
        val points =
            readings.map { reading ->
                val energy = (reading.energyWh ?: previousEnergy).max(previousEnergy)
                val seconds = Duration.between(previousAt, reading.at).seconds
                val power =
                    reading.powerW
                        ?: if (seconds > 0) {
                            (energy - previousEnergy).multiply(BigDecimal(3600)).divide(BigDecimal(seconds), 3, RoundingMode.HALF_UP)
                        } else {
                            null
                        }
                previousEnergy = energy
                previousAt = reading.at
                val delivered = (energy - transaction.meterStartWh).max(BigDecimal.ZERO)
                ConsumptionPoint(
                    reading.at,
                    delivered.divide(BigDecimal(1000), 3, RoundingMode.HALF_UP),
                    power?.divide(BigDecimal(1000), 3, RoundingMode.HALF_UP),
                    reading.soc,
                    reading.currentA,
                    reading.voltageV,
                    definition?.let {
                        pricing.amount(it.data, delivered, Duration.between(transaction.startedAt, reading.at).coerceAtLeast(Duration.ZERO))
                    },
                )
            }
        return downsample(points, 600)
    }

    private fun <T> downsample(points: List<T>, maximum: Int): List<T> {
        if (points.size <= maximum) return points
        val step = points.size.toDouble() / maximum
        return (0 until maximum).map { points[(it * step).toInt()] } + points.last()
    }

    fun start(user: AppPrincipal, input: SessionStartInput): CommandReceipt {
        ensure(input.connectorId in 1..100) { "Numéro de connecteur invalide." }
        ensure(input.tokenUid.matches(Regex("[A-Za-z0-9_-]{1,20}"))) { "Badge invalide." }
        val token =
            tokens.findById(ChargingTokenKey(user.tenantId, input.tokenUid)).orElse(null)
                ?: throw NotFound("Badge introuvable.")
        if (user.role == UserRole.DRIVER && token.userId != user.id) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Ce badge ne vous appartient pas.")
        }
        ensure(token.enabled) { "Ce badge est désactivé." }
        if (sessions.existsByTenantIdAndStationIdAndConnectorIdAndStoppedAtIsNull(user.tenantId, input.stationId, input.connectorId)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Une session est déjà en cours sur ce connecteur.")
        }
        val id =
            queue.command(
                user.tenantId,
                input.stationId,
                "RemoteStartTransaction",
                db.json.valueToTree(mapOf("idTag" to input.tokenUid, "connectorId" to input.connectorId)),
            )
        db.audit(user.tenantId, user.id.toString(), "SESSION_REMOTE_START", "${input.stationId}/${input.connectorId}")
        return CommandReceipt(id)
    }

    fun stop(user: AppPrincipal, id: Int): CommandReceipt {
        val transaction = session(user, id)
        ensure(transaction.stoppedAt == null) { "Cette session est déjà terminée." }
        val command =
            queue.command(
                user.tenantId,
                transaction.stationId,
                "RemoteStopTransaction",
                db.json.valueToTree(mapOf("transactionId" to id)),
            )
        db.audit(user.tenantId, user.id.toString(), "SESSION_REMOTE_STOP", id.toString())
        return CommandReceipt(command)
    }

    fun softStop(user: AppPrincipal, id: Int): ChargingSessionResponse {
        db.atomic {
            val candidate = session(user, id)
            val station =
                db.lock<StationEntity>(StationKey(user.tenantId, candidate.stationId))
                    ?: throw NotFound("Borne introuvable.")
            val transaction = requireNotNull(db.lock<TransactionEntity>(id))
            ensure(transaction.stoppedAt == null) { "Cette session est déjà terminée." }
            val stoppedAt = transaction.lastMeterAt?.takeIf { it.isAfter(transaction.startedAt) } ?: Instant.now()
            ocpp.close(
                station,
                transaction,
                stoppedAt,
                transaction.lastEnergyWh ?: transaction.meterStartWh,
                "SoftStop",
                soft = true,
            )
            paymentLifecycle.stopped(transaction)
            db.audit(user.tenantId, user.id.toString(), "SESSION_SOFT_STOP", id.toString())
        }
        return find(user, id)
    }

    fun delete(user: AppPrincipal, id: Int): MutationResponse =
        db.atomic {
            val transaction = session(user, id)
            if (transaction.stoppedAt == null) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Terminez la session avant de la supprimer.")
            }
            if (payments.findByTenantIdAndTransactionId(user.tenantId, id) != null || transaction.invoiceId != null) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Une session payée ou facturée ne peut pas être supprimée.")
            }
            meters.deleteAllByTenantIdAndTransactionId(user.tenantId, id)
            sessions.delete(transaction)
            db.audit(user.tenantId, user.id.toString(), "SESSION_DELETE", id.toString())
            MutationResponse()
        }

    fun inError(user: AppPrincipal, offset: Int, limit: Int): PageResponse<SessionInError> {
        val since = Instant.now().minus(Duration.ofDays(90))
        val candidates =
            queries.list<TransactionEntity>(
                conditions(user, SessionFilter()) + anyOf(atLeast("startedAt", since), present("stoppedAt", false)),
                Sort.by(Sort.Direction.DESC, "startedAt", "id"),
                5000,
            )
        val stationsById =
            stations.findAllByKeyTenantIdAndDeletedFalse(user.tenantId).associateBy { it.key.stationId }
        val priced = organization.pricing(user.tenantId).isNotEmpty()
        val failing =
            candidates.mapNotNull { transaction ->
                errors(transaction, stationsById[transaction.stationId], priced).takeIf { it.isNotEmpty() }?.let { transaction to it }
            }
        val slice = failing.drop(offset).take(limit)
        val views = views(user.tenantId, slice.map { it.first })
        return PageResponse(views.zip(slice).map { (view, entry) -> SessionInError(view, entry.second) }, failing.size.toLong())
    }

    fun errors(transaction: TransactionEntity, knownStation: StationEntity?, priced: Boolean): List<String> {
        val station = knownStation ?: stations.findById(StationKey(transaction.tenantId, transaction.stationId)).orElse(null)
        val supervision = properties.supervision
        val energyWh =
            (transaction.meterStopWh ?: transaction.lastEnergyWh)?.subtract(transaction.meterStartWh)
        val end = transaction.stoppedAt ?: Instant.now()
        val hours = BigDecimal(Duration.between(transaction.startedAt, end).seconds.coerceAtLeast(1)).divide(BigDecimal(3600), 6, RoundingMode.HALF_UP)
        return buildList {
            if (transaction.authorizationStatus != AuthorizationStatus.Accepted) add("INVALID_BADGE")
            if (transaction.stoppedAt != null) {
                if (energyWh != null && energyWh.signum() < 0) add("NEGATIVE_CONSUMPTION")
                else if (energyWh == null || energyWh.signum() == 0) add("NO_CONSUMPTION")
                if (transaction.price == null && priced) add("NO_PRICE")
            } else {
                val last = transaction.lastMeterAt ?: transaction.startedAt
                if (last.isBefore(Instant.now().minus(Duration.ofHours(supervision.staleSessionHours)))) add("STALE")
            }
            if (energyWh != null && energyWh.signum() > 0) {
                val maximumWh = station?.maximumPowerKw?.multiply(BigDecimal(1000))?.multiply(hours)?.multiply(BigDecimal("1.1"))
                if (energyWh > BigDecimal(supervision.maximumSessionKwh * 1000) || (maximumWh != null && energyWh > maximumWh)) {
                    add("OVER_CONSUMPTION")
                }
            }
            if (transaction.inactivitySeconds > supervision.longInactivityMinutes * 60) add("LONG_INACTIVITY")
            if (transaction.billingStatus == "FAILED") add("BILLING_FAILED")
        }
    }

    fun export(user: AppPrincipal, filter: SessionFilter) =
        Csv.download(
            "sessions",
            listOf("id", "borne", "connecteur", "debut", "fin", "duree_min", "inactivite_min", "energie_kwh", "prix", "devise", "badge", "utilisateur", "zone", "site", "autorisation", "motif_arret"),
            all(user, filter).chunked(500).asSequence().flatMap { chunk -> views(user.tenantId, chunk) }.map { session ->
                listOf(
                    session.id,
                    session.stationId,
                    session.connectorId,
                    session.startedAt,
                    session.stoppedAt,
                    session.durationSeconds / 60,
                    session.inactivitySeconds / 60,
                    session.energyKwh,
                    session.price,
                    session.currency,
                    session.tokenUid,
                    session.userName,
                    session.siteAreaId,
                    session.siteId,
                    session.authorizationStatus,
                    session.stopReason,
                )
            },
        )
}
