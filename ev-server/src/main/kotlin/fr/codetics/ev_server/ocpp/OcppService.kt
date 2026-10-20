package fr.codetics.ev_server.ocpp

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.lock
import fr.codetics.ev_server.core.sha256
import fr.codetics.ev_server.models.entities.ChargingTokenEntity
import fr.codetics.ev_server.models.entities.ChargingTokenKey
import fr.codetics.ev_server.models.entities.ConnectorEntity
import fr.codetics.ev_server.models.entities.ConnectorKey
import fr.codetics.ev_server.models.entities.MeterValueEntity
import fr.codetics.ev_server.models.entities.OcppCallEntity
import fr.codetics.ev_server.models.entities.OcppCallKey
import fr.codetics.ev_server.models.entities.SiteUserKey
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationEventEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.entities.TransactionEntity
import fr.codetics.ev_server.models.organization.CarData
import fr.codetics.ev_server.repositories.ChargingTokenRepository
import fr.codetics.ev_server.repositories.ConnectorRepository
import fr.codetics.ev_server.repositories.MeterValueRepository
import fr.codetics.ev_server.repositories.OcppCallRepository
import fr.codetics.ev_server.repositories.SiteUserRepository
import fr.codetics.ev_server.repositories.StationEventRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TransactionRepository
import fr.codetics.ev_server.repositories.UserRepository
import fr.codetics.ev_server.services.billing.ChargingPaymentLifecycle
import fr.codetics.ev_server.services.organization.Organization
import fr.codetics.ev_server.services.pricing.PricingService
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode

data class RoamingDecision(val status: AuthorizationStatus, val partnerId: UUID)

interface RoamingAuthorization {
    fun authorize(station: StationEntity, uid: String): RoamingDecision?
}

@Service
class OcppService(
    private val db: Database,
    private val stations: StationRepository,
    private val calls: OcppCallRepository,
    private val connectors: ConnectorRepository,
    private val tokens: ChargingTokenRepository,
    private val transactions: TransactionRepository,
    private val meters: MeterValueRepository,
    private val users: UserRepository,
    private val payments: ChargingPaymentLifecycle,
    private val organization: Organization,
    private val siteUsers: SiteUserRepository,
    private val stationEvents: StationEventRepository,
    private val pricing: PricingService,
    private val roaming: List<RoamingAuthorization>,
    private val events: ApplicationEventPublisher,
) {
    fun call(tenant: UUID, station: String, id: String, action: String, payload: JsonNode): String {
        OcppValidation.validate(action, payload)

        return db.atomic {
            val device =
                db.lock<StationEntity>(StationKey(tenant, station))
                    ?: throw OcppFault("SecurityError", "Borne inconnue.")

            if (!device.enabled || device.deleted) {
                throw OcppFault("SecurityError", "La borne est désactivée.")
            }

            val key = OcppCallKey(tenant, station, id)
            val hash = sha256(action + db.encode(payload))
            val previous = calls.findById(key).orElse(null)

            if (previous != null) {
                if (previous.requestHash != hash) {
                    throw OcppFault(
                        "ProtocolError",
                        "Cet identifiant de message a déjà été utilisé pour une autre demande.",
                    )
                }
                return@atomic db.encode(db.decode(previous.response))
            }

            device.lastSeen = Instant.now()
            val result = handle(device, action, payload)
            val response = db.encode(listOf(3, id, result))
            calls.save(
                OcppCallEntity(key = key, action = action, requestHash = hash, response = response)
            )
            stations.save(device)
            response
        }
    }

    private fun handle(station: StationEntity, action: String, payload: JsonNode): Any {
        val tenant = station.key.tenantId
        val identity = station.key.stationId

        return when (action) {
            "BootNotification" -> {
                val firstBoot = station.lastBoot == null
                station.vendor = payload.path("chargePointVendor").asText()
                station.model = payload.path("chargePointModel").asText()
                station.firmwareVersion = payload.get("firmwareVersion")?.asText()
                station.metadata = db.encode(payload)
                station.lastBoot = Instant.now()
                record(station, "BOOT", info = "${station.vendor} ${station.model}")
                events.publishEvent(StationBooted(station, firstBoot))

                mapOf(
                    "status" to "Accepted",
                    "currentTime" to Instant.now().toString(),
                    "interval" to 60,
                )
            }
            "Heartbeat" -> {
                mapOf("currentTime" to Instant.now().toString())
            }
            "Authorize" -> {
                val uid = payload.path("idTag").asText()
                val decision = authorize(station, uid)
                record(station, "AUTHORIZE", status = decision.status.name, info = uid)
                rejected(station, uid, decision.status)
                mapOf("idTagInfo" to mapOf("status" to decision.status))
            }
            "StatusNotification" -> {
                updateStatus(station, payload)
                emptyMap<String, Any>()
            }
            "StartTransaction" -> {
                start(station, payload)
            }
            "StopTransaction" -> {
                stop(station, payload)
            }
            "MeterValues" -> {
                val number = payload.path("connectorId").intValue()
                val transactionId = payload.get("transactionId")?.intValue()

                if (transactionId != null) {
                    verifyTransaction(station, transactionId, number)
                }

                saveMeters(station, number, transactionId, payload.path("meterValue"))
                emptyMap<String, Any>()
            }
            "DataTransfer" -> {
                record(
                    station,
                    "DATA_TRANSFER",
                    info =
                        listOfNotNull(
                                payload.path("vendorId").asText(),
                                payload.get("messageId")?.asText(),
                            )
                            .joinToString(" / "),
                )
                mapOf("status" to "UnknownVendorId")
            }
            "DiagnosticsStatusNotification" -> {
                val status = payload.path("status").asText()
                station.diagnosticsStatus = status
                record(station, "DIAGNOSTICS", status = status)
                db.audit(tenant, "station:$identity", action, status)
                events.publishEvent(StationNotification(station, "DIAGNOSTICS", status))
                emptyMap<String, Any>()
            }
            "FirmwareStatusNotification" -> {
                val status = payload.path("status").asText()
                station.firmwareStatus = status
                record(station, "FIRMWARE", status = status)
                db.audit(tenant, "station:$identity", action, status)
                events.publishEvent(StationNotification(station, "FIRMWARE", status))
                emptyMap<String, Any>()
            }
            else -> {
                throw OcppFault("NotSupported", "Action non prise en charge.")
            }
        }
    }

    private data class Decision(
        val status: AuthorizationStatus,
        val userId: UUID? = null,
        val partnerId: UUID? = null,
        val vehicleId: UUID? = null,
    )

    private fun rejected(station: StationEntity, uid: String, status: AuthorizationStatus) {
        if (status == AuthorizationStatus.Accepted) return
        if (VehicleIds.isVid(uid) && status == AuthorizationStatus.Invalid) return
        events.publishEvent(BadgeRejected(station, uid, status))
    }

    private fun authorize(
        station: StationEntity,
        uid: String,
        connector: Int? = null,
        stopping: Boolean = false,
    ): Decision {
        if (VehicleIds.isVid(uid)) return authorizeVehicle(station, uid, connector, stopping)
        val tenant = station.key.tenantId
        payments.authorization(tenant, station.key.stationId, connector, uid)?.let {
            return Decision(it)
        }
        val token = tokens.findById(ChargingTokenKey(tenant, uid)).orElse(null)

        if (token == null) {
            roaming.firstNotNullOfOrNull { it.authorize(station, uid) }?.let {
                return Decision(it.status, partnerId = it.partnerId)
            }
            return Decision(AuthorizationStatus.Invalid)
        }

        return permit(station, token)
    }

    private fun authorizeVehicle(
        station: StationEntity,
        raw: String,
        number: Int?,
        stopping: Boolean,
    ): Decision {
        val uid = VehicleIds.normalize(raw) ?: return Decision(AuthorizationStatus.Invalid)
        if (!station.autoCharge) return Decision(AuthorizationStatus.Invalid)
        if (!stopping && !plugged(station, uid, number)) return Decision(AuthorizationStatus.Invalid)

        val tenant = station.key.tenantId
        val token =
            tokens.findById(ChargingTokenKey(tenant, uid)).orElse(null)?.takeIf {
                it.type == VehicleIds.TOKEN_TYPE && it.userId != null && it.vehicleId != null
            } ?: return Decision(AuthorizationStatus.Invalid)
        val car = organization.find(tenant, "cars", requireNotNull(token.vehicleId), CarData::class.java)
        if (car == null || car.data.userId != token.userId) return Decision(AuthorizationStatus.Invalid)

        return permit(station, token).copy(vehicleId = token.vehicleId)
    }

    private fun plugged(station: StationEntity, uid: String, number: Int?): Boolean {
        val now = Instant.now()
        fun within(plug: ConnectorEntity, window: Duration) =
            plug.pluggedAt?.let { !it.plus(window).isBefore(now) } == true

        if (number != null) {
            val plug = connector(station, number)
            if (plug.vid != uid && !(plug.vid == null && within(plug, VehicleIds.START_WINDOW))) return false
            plug.vid = uid
            connectors.save(plug)
            return true
        }

        val candidates =
            connectors
                .findAllByKeyTenantIdAndKeyStationIdOrderByKeyConnectorId(station.key.tenantId, station.key.stationId)
                .filter { it.key.connectorId > 0 }
                .filter { it.vid == uid || (it.vid == null && within(it, VehicleIds.AUTHORIZE_WINDOW)) }
        candidates.singleOrNull()?.let {
            it.vid = uid
            connectors.save(it)
        }
        return candidates.isNotEmpty()
    }

    private fun permit(station: StationEntity, token: ChargingTokenEntity): Decision {
        val tenant = station.key.tenantId
        val owner = token.userId?.let { id -> users.findByTenantIdAndId(tenant, id) }
        val userDisabled = token.userId != null && (owner == null || !owner.enabled || owner.deletedAt != null)

        if (!token.enabled || userDisabled) {
            return Decision(AuthorizationStatus.Blocked, token.userId)
        }
        if (token.expiresAt?.isBefore(Instant.now()) == true) {
            return Decision(AuthorizationStatus.Expired, token.userId)
        }

        val site = organization.location(station).site
        if (site != null && site.data.accessControl) {
            val allowed =
                token.userId != null &&
                    siteUsers.existsById(SiteUserKey(tenant, site.id, token.userId!!))
            if (!allowed) {
                return Decision(AuthorizationStatus.Blocked, token.userId)
            }
        }

        token.lastUsedAt = Instant.now()
        tokens.save(token)
        return Decision(AuthorizationStatus.Accepted, token.userId)
    }

    private fun connector(station: StationEntity, number: Int): ConnectorEntity {
        val key = ConnectorKey(station.key.tenantId, station.key.stationId, number)

        return connectors.findById(key).orElse(null)
            ?: connectors.save(ConnectorEntity(key = key)).also { db.flush() }
    }

    private fun updateStatus(station: StationEntity, payload: JsonNode) {
        val connector = connector(station, payload.path("connectorId").intValue())
        val timestamp = payload.get("timestamp")?.asText()?.let(Instant::parse) ?: Instant.now()

        if (timestamp.isBefore(connector.statusAt)) {
            return
        }

        val previous = connector.status
        val previousError = connector.errorCode
        connector.status = payload.path("status").asText()
        connector.errorCode = payload.path("errorCode").asText()
        connector.statusAt = timestamp
        if (connector.status in VehicleIds.PLUGGED && previous !in VehicleIds.PLUGGED) {
            connector.pluggedAt = Instant.now()
            connector.vid = null
        } else if (connector.status !in VehicleIds.PLUGGED && connector.status != "Faulted") {
            connector.pluggedAt = null
            connector.vid = null
        }
        connectors.save(connector)

        if (previous != connector.status || previousError != connector.errorCode) {
            record(
                station,
                "STATUS",
                connector.key.connectorId,
                connector.status,
                connector.errorCode,
                payload.get("info")?.asText()?.take(500),
            )
            events.publishEvent(ConnectorStatusChanged(station, connector, previous))
        }
    }

    private fun start(station: StationEntity, payload: JsonNode): Map<String, Any?> {
        val tenant = station.key.tenantId
        val identity = station.key.stationId
        val number = payload.path("connectorId").intValue()
        connector(station, number)

        if (
            transactions.existsByTenantIdAndStationIdAndConnectorIdAndStoppedAtIsNull(
                tenant,
                identity,
                number,
            )
        ) {
            throw OcppFault("GenericError", "Une session est déjà en cours sur ce connecteur.")
        }

        val uid = payload.path("idTag").asText().let { VehicleIds.normalize(it) ?: it }
        val decision = authorize(station, uid, number)
        val vid = connector(station, number).vid
        val location = organization.location(station)
        val transaction =
            transactions.save(
                TransactionEntity(
                    tenantId = tenant,
                    stationId = identity,
                    connectorId = number,
                    tokenUid = uid,
                    userId = decision.userId,
                    vehicleId =
                        decision.vehicleId
                            ?: decision.userId?.let { users.findByTenantIdAndId(tenant, it)?.defaultCarId },
                    vid = vid,
                    startedAt = Instant.parse(payload.path("timestamp").asText()),
                    meterStartWh = payload.path("meterStart").decimalValue(),
                    authorizationStatus = decision.status,
                    siteAreaId = location.area?.id,
                    siteId = location.site?.id,
                    roamingPartnerId = decision.partnerId,
                    lastEnergyWh = payload.path("meterStart").decimalValue(),
                )
            ).also { db.flush() }

        payments.started(transaction)
        rejected(station, uid, decision.status)
        events.publishEvent(TransactionStarted(station, transaction))
        return mapOf(
            "transactionId" to transaction.id,
            "idTagInfo" to mapOf("status" to decision.status),
        )
    }

    private fun stop(station: StationEntity, payload: JsonNode): Map<String, Any> {
        val transaction = verifyTransaction(station, payload.path("transactionId").intValue())
        val stoppedAt = Instant.parse(payload.path("timestamp").asText())
        val meterStop = payload.path("meterStop").decimalValue()

        if (stoppedAt.isBefore(transaction.startedAt)) {
            throw OcppFault("PropertyConstraintViolation", "La fin de session précède son début.")
        }

        if (transaction.stoppedAt == null || transaction.softStopped) {
            if (payload.has("transactionData")) {
                saveMeters(
                    station,
                    transaction.connectorId,
                    transaction.id,
                    payload.path("transactionData"),
                )
            }
            close(station, transaction, stoppedAt, meterStop, payload.get("reason")?.asText() ?: "Local")
        } else if (
            transaction.stoppedAt != stoppedAt || transaction.meterStopWh?.compareTo(meterStop) != 0
        ) {
            throw OcppFault(
                "PropertyConstraintViolation",
                "Les valeurs finales de la session ne peuvent pas être remplacées.",
            )
        }

        val uid = payload.get("idTag")?.asText() ?: transaction.tokenUid
        payments.stopped(transaction)
        return mapOf("idTagInfo" to mapOf("status" to authorize(station, uid, stopping = true).status))
    }

    fun close(
        station: StationEntity,
        transaction: TransactionEntity,
        stoppedAt: Instant,
        meterStop: BigDecimal,
        reason: String,
        soft: Boolean = false,
    ) {
        if (transaction.chargingEndedAt != null || (transaction.lastPowerW?.let { it < IDLE_W } == true)) {
            val since = transaction.lastMeterAt ?: stoppedAt
            if (stoppedAt.isAfter(since)) {
                transaction.inactivitySeconds += Duration.between(since, stoppedAt).seconds
            }
        }
        transaction.stoppedAt = stoppedAt
        transaction.meterStopWh = meterStop
        transaction.stopReason = reason
        transaction.softStopped = soft
        pricing.price(transaction, station, stoppedAt)?.let { priced ->
            transaction.price = priced.price
            transaction.currency = priced.currency
            transaction.pricingDefinitionId = priced.definitionId
        }
        transactions.save(transaction)

        connectors.findById(
            ConnectorKey(station.key.tenantId, station.key.stationId, transaction.connectorId)
        ).ifPresent { connector ->
            connector.currentPowerW = null
            connector.currentSoc = null
            connectors.save(connector)
        }
        events.publishEvent(TransactionStopped(station, transaction))
    }

    private fun verifyTransaction(
        station: StationEntity,
        id: Int,
        connector: Int? = null,
    ): TransactionEntity {
        val transaction =
            transactions.findByTenantIdAndStationIdAndId(
                station.key.tenantId,
                station.key.stationId,
                id,
            ) ?: throw OcppFault("PropertyConstraintViolation", "Session inconnue.")

        if (connector != null && transaction.connectorId != connector) {
            throw OcppFault(
                "PropertyConstraintViolation",
                "La session appartient à un autre connecteur.",
            )
        }

        return transaction
    }

    private fun saveMeters(
        station: StationEntity,
        connector: Int,
        transaction: Int?,
        values: JsonNode,
    ) {
        val readings =
            values.toList().map { reading ->
                MeterValueEntity(
                    tenantId = station.key.tenantId,
                    stationId = station.key.stationId,
                    connectorId = connector,
                    transactionId = transaction,
                    sampledAt = Instant.parse(reading.path("timestamp").asText()),
                    samples = db.encode(reading.path("sampledValue")),
                )
            }
        meters.saveAll(readings)
        val parsed = values.toList().map(MeterReadings::parse).sortedBy { it.at }
        val latest = parsed.lastOrNull() ?: return

        val plug = connector(station, connector)
        if (plug.meterAt == null || !latest.at.isBefore(plug.meterAt)) {
            latest.powerW?.let { plug.currentPowerW = it }
            latest.soc?.let { plug.currentSoc = it }
            plug.meterAt = latest.at
            connectors.save(plug)
        }

        if (transaction == null) return
        val active = verifyTransaction(station, transaction, connector)
        parsed.forEach { reading ->
            reading.energyWh?.let { payments.observed(active, it, reading.at) }
            if (active.stoppedAt == null) track(active, reading)
        }
        transactions.save(active)
        if (active.stoppedAt == null) {
            if (plug.currentPowerW == null && active.lastPowerW != null) {
                plug.currentPowerW = active.lastPowerW
                connectors.save(plug)
            }
            events.publishEvent(TransactionUpdated(station, active))
        }
    }

    private fun track(transaction: TransactionEntity, reading: MeterReading) {
        val previousAt = transaction.lastMeterAt
        if (previousAt != null && !reading.at.isAfter(previousAt)) return
        if (reading.at.isBefore(transaction.startedAt)) return

        val power =
            reading.powerW
                ?: reading.energyWh?.let { energy ->
                    val previousEnergy = transaction.lastEnergyWh ?: transaction.meterStartWh
                    val since = previousAt ?: transaction.startedAt
                    val seconds = Duration.between(since, reading.at).seconds
                    if (seconds <= 0) null
                    else
                        (energy - previousEnergy)
                            .max(BigDecimal.ZERO)
                            .multiply(BigDecimal(3600))
                            .divide(BigDecimal(seconds), 3, RoundingMode.HALF_UP)
                }

        if (previousAt != null && (transaction.lastPowerW?.let { it < IDLE_W } == true)) {
            transaction.inactivitySeconds += Duration.between(previousAt, reading.at).seconds
        }

        reading.energyWh?.let { energy ->
            transaction.lastEnergyWh = (transaction.lastEnergyWh ?: energy).max(energy)
        }
        reading.soc?.let { soc ->
            if (transaction.socStart == null) transaction.socStart = soc
            transaction.socLast = soc
        }
        if (power != null) {
            val delivered =
                (transaction.lastEnergyWh ?: transaction.meterStartWh) - transaction.meterStartWh
            if (power < IDLE_W && delivered > BigDecimal.ZERO) {
                if (transaction.chargingEndedAt == null) transaction.chargingEndedAt = reading.at
            } else if (power >= IDLE_W) {
                transaction.chargingEndedAt = null
            }
            transaction.lastPowerW = power
        }
        transaction.lastMeterAt = reading.at
    }

    private fun record(
        station: StationEntity,
        type: String,
        connector: Int? = null,
        status: String? = null,
        errorCode: String? = null,
        info: String? = null,
    ) {
        stationEvents.save(
            StationEventEntity(
                tenantId = station.key.tenantId,
                stationId = station.key.stationId,
                type = type,
                connectorId = connector,
                status = status,
                errorCode = errorCode,
                info = info?.take(1000),
            )
        )
    }

    companion object {
        val IDLE_W = BigDecimal(100)
    }
}
