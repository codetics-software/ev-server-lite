package fr.codetics.ev_server.ocpp.v201

import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.msg
import fr.codetics.ev_server.models.entities.ConnectorEntity
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationParameterEntity
import fr.codetics.ev_server.models.entities.StationParameterKey
import fr.codetics.ev_server.models.entities.TransactionEntity
import fr.codetics.ev_server.ocpp.AuthorizationStatus
import fr.codetics.ev_server.ocpp.CertificateRequestRejected
import fr.codetics.ev_server.ocpp.OcppCommandQueue
import fr.codetics.ev_server.ocpp.OcppVersion
import fr.codetics.ev_server.ocpp.MeterReadings
import fr.codetics.ev_server.ocpp.OcppFault
import fr.codetics.ev_server.ocpp.OcppService
import fr.codetics.ev_server.ocpp.OcppTime
import fr.codetics.ev_server.ocpp.StationBooted
import fr.codetics.ev_server.ocpp.StationNotification
import fr.codetics.ev_server.ocpp.TransactionStarted
import fr.codetics.ev_server.ocpp.VehicleIds
import fr.codetics.ev_server.repositories.ConnectorRepository
import fr.codetics.ev_server.repositories.StationParameterRepository
import fr.codetics.ev_server.repositories.TransactionRepository
import fr.codetics.ev_server.repositories.UserRepository
import fr.codetics.ev_server.services.billing.ChargingPaymentLifecycle
import fr.codetics.ev_server.services.organization.Organization
import fr.codetics.ev_server.services.reservations.ReservationService
import fr.codetics.ev_server.services.smartcharging.ChargingProfileService
import java.math.RoundingMode
import java.time.Duration
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode

@Service
class Ocpp201Service(
    private val db: Database,
    private val ocpp: OcppService,
    private val transactions: TransactionRepository,
    private val users: UserRepository,
    private val parameters: StationParameterRepository,
    private val payments: ChargingPaymentLifecycle,
    private val organization: Organization,
    private val reservations: ReservationService,
    private val events: ApplicationEventPublisher,
    private val properties: AppProperties,
    private val connectors: ConnectorRepository,
    private val certificates: Iso15118Certificates,
    private val ocsp: OcspClient,
    private val contracts: ContractCertificateClient,
    private val profiles: ChargingProfileService,
    private val queue: OcppCommandQueue,
) {
    data class AuthorizeCheck(val verdict: ContractVerdict?, val ocsp: List<OcspOutcome>)

    private data class PendingStart(val uid: String?, val reservationId: Int?, val at: Instant)

    private val pendingStarts = ConcurrentHashMap<String, PendingStart>()

    fun prepare(stationId: String, request: Any): Any? =
        when (request) {
            is GetCertificateStatusRequest ->
                if (certificates.enabled) ocsp.status(request.ocspRequestData)
                else OcspOutcome(OcspState.FAILED, reason = "Iso15118Disabled")
            is Get15118EVCertificateRequest ->
                if (certificates.enabled) contracts.forward(stationId, request)
                else ContractOutcome(Iso15118EVCertificateStatusEnum.Failed, "", "Iso15118Disabled")
            is AuthorizeRequest ->
                if (certificates.enabled && (request.certificate != null || request.iso15118CertificateHashData != null)) {
                    AuthorizeCheck(
                        request.certificate?.let(certificates::verifyContract),
                        request.iso15118CertificateHashData.orEmpty().map(ocsp::status),
                    )
                } else {
                    null
                }
            else -> null
        }

    fun decode(action: String, payload: JsonNode): Any {
        val message =
            Ocpp201Messages.fromStation[action]
                ?: throw OcppFault(
                    if (action in Ocpp201Messages.toStation) "NotSupported" else "NotImplemented",
                    msg("ocpp.action.unsupported"),
                )
        return Ocpp201Codec.decode(payload, message.request)
    }

    fun handle(station: StationEntity, action: String, request: Any, prepared: Any? = null): JsonNode =
        Ocpp201Codec.encode(
            when (request) {
                is BootNotificationRequest -> boot(station, request)
                is HeartbeatRequest -> HeartbeatResponse(OcppTime.now())
                is AuthorizeRequest -> authorize(station, request, prepared as? AuthorizeCheck)
                is StatusNotificationRequest -> status(station, request)
                is TransactionEventRequest -> transactionEvent(station, request)
                is MeterValuesRequest -> meterValues(station, request)
                is DataTransferRequest -> dataTransfer(station, request)
                is SecurityEventNotificationRequest -> securityEvent(station, request)
                is SignCertificateRequest -> signCertificate(station, request)
                is LogStatusNotificationRequest -> logStatus(station, request)
                is FirmwareStatusNotificationRequest -> firmwareStatus(station, request)
                is NotifyReportRequest -> notifyReport(station, request)
                is NotifyEventRequest -> notifyEvent(station, request)
                is ReservationStatusUpdateRequest -> reservationStatus(station, request)
                is NotifyChargingLimitRequest -> {
                    ocpp.record(station, "CHARGING_LIMIT", request.evseId, request.chargingLimit.chargingLimitSource.name)
                    NotifyChargingLimitResponse()
                }
                is ClearedChargingLimitRequest -> {
                    ocpp.record(station, "CHARGING_LIMIT", request.evseId, "Cleared", info = request.chargingLimitSource.name)
                    ClearedChargingLimitResponse()
                }
                is NotifyEVChargingNeedsRequest -> chargingNeeds(station, request)
                is NotifyEVChargingScheduleRequest -> {
                    ocpp.record(station, "EV_CHARGING_SCHEDULE", request.evseId, request.chargingSchedule.chargingRateUnit.name)
                    NotifyEVChargingScheduleResponse(GenericStatusEnum.Accepted)
                }
                is NotifyDisplayMessagesRequest -> {
                    ocpp.record(station, "DISPLAY", status = request.messageInfo?.size?.toString(), info = request.requestId.toString())
                    NotifyDisplayMessagesResponse()
                }
                is NotifyCustomerInformationRequest -> {
                    ocpp.record(station, "CUSTOMER", status = request.seqNo.toString(), info = request.data)
                    NotifyCustomerInformationResponse()
                }
                is NotifyMonitoringReportRequest -> {
                    ocpp.record(station, "MONITORING", status = request.monitor?.size?.toString(), info = request.requestId.toString())
                    NotifyMonitoringReportResponse()
                }
                is ReportChargingProfilesRequest -> {
                    ocpp.record(
                        station,
                        "CHARGING_PROFILES",
                        request.evseId,
                        request.chargingLimitSource.name,
                        info = request.chargingProfile.joinToString(" / ") { "${it.id}:${it.chargingProfilePurpose.name}" },
                    )
                    ReportChargingProfilesResponse()
                }
                is PublishFirmwareStatusNotificationRequest -> {
                    ocpp.record(station, "FIRMWARE", status = request.status.name, info = request.location?.joinToString(" "))
                    PublishFirmwareStatusNotificationResponse()
                }
                is Get15118EVCertificateRequest -> {
                    val outcome = prepared as? ContractOutcome ?: ContractOutcome(Iso15118EVCertificateStatusEnum.Failed, "", "NotPrepared")
                    ocpp.record(
                        station,
                        "CERTIFICATE",
                        status = "15118:${request.action.name}:${outcome.status.name}",
                        info = outcome.reason,
                    )
                    Get15118EVCertificateResponse(outcome.status, outcome.reason?.let { StatusInfo(it.take(20)) }, outcome.exiResponse)
                }
                is GetCertificateStatusRequest -> {
                    val outcome = prepared as? OcspOutcome ?: OcspOutcome(OcspState.FAILED, reason = "NotPrepared")
                    ocpp.record(
                        station,
                        "CERTIFICATE",
                        status = "OCSP:${outcome.state.name}",
                        info = listOfNotNull(request.ocspRequestData.serialNumber, outcome.reason).joinToString(" / "),
                    )
                    if (outcome.state == OcspState.FAILED || outcome.response == null) {
                        GetCertificateStatusResponse(GetCertificateStatusEnum.Failed, StatusInfo((outcome.reason ?: "Failed").take(20)))
                    } else {
                        GetCertificateStatusResponse(GetCertificateStatusEnum.Accepted, ocspResult = outcome.response)
                    }
                }
                else -> throw OcppFault("NotSupported", msg("ocpp.action.unsupported"))
            }
        )

    private fun boot(station: StationEntity, request: BootNotificationRequest): BootNotificationResponse {
        val firstBoot = station.lastBoot == null
        station.vendor = request.chargingStation.vendorName.take(100)
        station.model = request.chargingStation.model.take(100)
        station.firmwareVersion = request.chargingStation.firmwareVersion?.take(100)
        station.metadata = db.encode(Ocpp201Codec.encode(request))
        station.lastBoot = Instant.now()
        ocpp.record(station, "BOOT", status = request.reason.name, info = "${station.vendor} ${station.model}")
        events.publishEvent(StationBooted(station, firstBoot))
        return BootNotificationResponse(
            currentTime = OcppTime.now(),
            interval = station.heartbeatInterval ?: properties.ocpp.heartbeatInterval(),
            status = RegistrationStatusEnum.Accepted,
        )
    }

    private fun authorize(station: StationEntity, request: AuthorizeRequest, check: AuthorizeCheck?): AuthorizeResponse {
        val uid = uid(request.idToken)
        val decision = uid?.let { ocpp.authorize(station, it) } ?: OcppService.Decision(AuthorizationStatus.Invalid)
        val certificate =
            check?.let {
                when {
                    it.verdict == ContractVerdict.EXPIRED -> AuthorizeCertificateStatusEnum.CertificateExpired
                    it.ocsp.any { outcome -> outcome.state == OcspState.REVOKED } -> AuthorizeCertificateStatusEnum.CertificateRevoked
                    it.verdict == ContractVerdict.CHAIN_ERROR -> AuthorizeCertificateStatusEnum.CertChainError
                    it.ocsp.any { outcome -> outcome.state != OcspState.GOOD } -> AuthorizeCertificateStatusEnum.CertChainError
                    decision.status != AuthorizationStatus.Accepted -> AuthorizeCertificateStatusEnum.ContractCancelled
                    else -> AuthorizeCertificateStatusEnum.Accepted
                }
            }
        val status =
            when (certificate) {
                AuthorizeCertificateStatusEnum.CertificateExpired -> AuthorizationStatusEnum.Expired
                AuthorizeCertificateStatusEnum.CertificateRevoked, AuthorizeCertificateStatusEnum.CertChainError -> AuthorizationStatusEnum.Invalid
                else -> AuthorizationStatusEnum.valueOf(decision.status.name)
            }
        ocpp.record(
            station,
            "AUTHORIZE",
            status = status.name,
            info = listOfNotNull(uid ?: request.idToken.idToken, certificate?.name).joinToString(" / "),
        )
        uid?.let { ocpp.rejected(station, it, AuthorizationStatus.entries.firstOrNull { entry -> entry.name == status.name } ?: AuthorizationStatus.Invalid) }
        return AuthorizeResponse(info(decision).copy(status = status), certificate)
    }

    private fun chargingNeeds(station: StationEntity, request: NotifyEVChargingNeedsRequest): NotifyEVChargingNeedsResponse {
        val needs = request.chargingNeeds
        val transaction =
            transactions.findFirstByTenantIdAndStationIdAndConnectorIdAndStoppedAtIsNull(station.key.tenantId, station.key.stationId, request.evseId)
                ?.takeIf { it.ocppTransactionId != null }
        val connector = ocpp.connector(station, request.evseId)
        val maximumW = (connector.maximumPowerKw ?: station.maximumPowerKw)?.multiply(BigDecimal(1000))
        val ac = needs.requestedEnergyTransfer != EnergyTransferModeEnum.DC
        val phases =
            when (needs.requestedEnergyTransfer) {
                EnergyTransferModeEnum.AC_single_phase -> 1
                EnergyTransferModeEnum.AC_two_phase -> 2
                else -> 3
            }
        val limit =
            if (ac) {
                val vehicle = needs.acChargingParameters?.evMaxCurrent?.let(::BigDecimal)
                val station = maximumW?.divide(BigDecimal(230 * phases), 1, RoundingMode.DOWN)
                listOfNotNull(vehicle, station).minOrNull()
            } else {
                val parameters = needs.dcChargingParameters
                val vehicle =
                    parameters?.evMaxPower?.let(::BigDecimal)
                        ?: parameters?.let { BigDecimal(it.evMaxCurrent).multiply(BigDecimal(it.evMaxVoltage)) }
                listOfNotNull(vehicle, maximumW).minOrNull()
            }
        ocpp.record(
            station,
            "EV_CHARGING_NEEDS",
            request.evseId,
            needs.requestedEnergyTransfer.name,
            info = listOfNotNull(needs.departureTime, limit?.let { "${it.toPlainString()} ${if (ac) "A" else "W"}" }).joinToString(" / "),
        )
        if (transaction == null || limit == null || limit.signum() <= 0) {
            return NotifyEVChargingNeedsResponse(NotifyEVChargingNeedsStatusEnum.Rejected)
        }
        val now = Instant.now()
        val duration =
            needs.departureTime?.let { OcppTime.parse(it) }?.let { Duration.between(now, it).seconds }?.takeIf { it > 0 }
        val profile =
            db.json.valueToTree<JsonNode>(
                buildMap {
                    put("chargingProfileId", ChargingProfileService.EV_NEEDS_ID + request.evseId)
                    put("transactionId", transaction.id)
                    put("stackLevel", 1)
                    put("chargingProfilePurpose", "TxProfile")
                    put("chargingProfileKind", "Absolute")
                    put(
                        "chargingSchedule",
                        buildMap {
                            put("startSchedule", OcppTime.format(now))
                            duration?.let { put("duration", it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) }
                            put("chargingRateUnit", if (ac) "A" else "W")
                            put(
                                "chargingSchedulePeriod",
                                listOf(
                                    buildMap {
                                        put("startPeriod", 0)
                                        put("limit", limit.setScale(1, RoundingMode.DOWN))
                                        if (ac) put("numberPhases", phases)
                                    }
                                ),
                            )
                        },
                    )
                }
            )
        profiles.apply(station.key.tenantId, station.key.stationId, request.evseId, profile, "EV_NEEDS", transaction.id)
        return NotifyEVChargingNeedsResponse(NotifyEVChargingNeedsStatusEnum.Accepted)
    }

    private fun status(station: StationEntity, request: StatusNotificationRequest): StatusNotificationResponse {
        val timestamp = OcppTime.parse(request.timestamp)
        val number = request.evseId
        val plug = ocpp.connector(station, number)
        val current = plug.status
        val aggregate = aggregate(plug, request.connectorId, request.connectorStatus)
        val status =
            when (aggregate) {
                ConnectorStatusEnum.Available -> "Available"
                ConnectorStatusEnum.Reserved -> "Reserved"
                ConnectorStatusEnum.Unavailable -> "Unavailable"
                ConnectorStatusEnum.Faulted -> "Faulted"
                ConnectorStatusEnum.Occupied -> if (current in VehicleIds.PLUGGED) current else "Preparing"
            }
        val reported = if (aggregate == ConnectorStatusEnum.Faulted) "OtherError" else "NoError"
        ocpp.applyStatus(
            station,
            number,
            status,
            reported,
            timestamp,
            listOf(request.connectorStatus.name.takeIf { it != status }, "connecteur ${request.connectorId}".takeIf { request.connectorId != 1 }),
        )
        return StatusNotificationResponse()
    }

    private fun aggregate(plug: ConnectorEntity, connector: Int, reported: ConnectorStatusEnum): ConnectorStatusEnum {
        val states =
            plug.ocppConnectors
                ?.let { runCatching { db.decode(it).properties().associate { (key, value) -> key to value.asText() } }.getOrNull() }
                .orEmpty()
                .toMutableMap()
        states[connector.toString()] = reported.name
        plug.ocppConnectors = db.encode(states.toSortedMap())
        connectors.save(plug)
        val values = states.values.toSet()
        return when {
            "Occupied" in values -> ConnectorStatusEnum.Occupied
            "Reserved" in values -> ConnectorStatusEnum.Reserved
            "Available" in values -> ConnectorStatusEnum.Available
            "Unavailable" in values -> ConnectorStatusEnum.Unavailable
            else -> ConnectorStatusEnum.Faulted
        }
    }

    private fun transactionEvent(station: StationEntity, request: TransactionEventRequest): TransactionEventResponse {
        val tenant = station.key.tenantId
        val identity = station.key.stationId
        val reference = request.transactionInfo.transactionId
        val at = OcppTime.parse(request.timestamp)
        val readings = meters(request.meterValue)
        val uid = request.idToken?.let(::uid)
        val existing = transactions.findFirstByTenantIdAndStationIdAndOcppTransactionId(tenant, identity, reference)
        val pendingKey = "$tenant/$identity/$reference"
        if (existing == null && request.evse == null) {
            return withoutEvse(station, request, pendingKey, uid)
        }
        val number = existing?.connectorId ?: requireNotNull(request.evse).id
        if (number <= 0) throw OcppFault("PropertyConstraintViolation", msg("ocpp.payload.invalid", "evse.id"))
        if (request.evse != null && existing != null && request.evse.id != existing.connectorId) {
            ocpp.record(station, "TRANSACTION", existing.connectorId, "OtherEvse", info = "$reference → ${request.evse.id}")
        }

        var decision: OcppService.Decision? = null
        var transaction = existing
        if (transaction == null) {
            val pending = pendingStarts.remove(pendingKey)
            val started =
                start(station, number, reference, at, readings, uid ?: pending?.uid, request.reservationId ?: pending?.reservationId)
            transaction = started.first
            decision = started.second
        } else if (uid != null && transaction.tokenUid.isEmpty() && transaction.stoppedAt == null) {
            decision = assign(station, transaction, uid)
        } else if (uid != null && uid != transaction.tokenUid) {
            decision = ocpp.authorize(station, uid, number, stopping = true)
        }

        if (transaction.lastEnergyWh == null) {
            readings.toList().map(MeterReadings::parse).sortedBy { it.at }.firstNotNullOfOrNull { it.energyWh }?.let { energy ->
                transaction.meterStartWh = energy
                transaction.lastEnergyWh = energy
                transactions.save(transaction)
            }
        }
        if (!readings.isEmpty && (transaction.stoppedAt == null || transaction.softStopped)) {
            transaction = ocpp.saveMeters(station, number, transaction.id, readings) ?: transaction
        }

        if (request.eventType == TransactionEventEnum.Ended) {
            end(station, transaction, at, readings, request.transactionInfo.stoppedReason)
        } else if (transaction.stoppedAt == null) {
            request.transactionInfo.chargingState?.let { state -> charging(station, transaction, state, at) }
        }

        val tokenInfo =
            when {
                request.idToken == null || uid == null -> null
                decision != null -> info(decision)
                else -> info(OcppService.Decision(transaction.authorizationStatus, transaction.userId))
            }
        return TransactionEventResponse(idTokenInfo = tokenInfo)
    }

    private fun withoutEvse(
        station: StationEntity,
        request: TransactionEventRequest,
        key: String,
        uid: String?,
    ): TransactionEventResponse {
        val now = Instant.now()
        pendingStarts.entries.removeIf { it.value.at.isBefore(now.minus(PENDING_TTL)) }
        if (request.eventType == TransactionEventEnum.Ended) {
            pendingStarts.remove(key)
        } else if (pendingStarts.size < PENDING_LIMIT || pendingStarts.containsKey(key)) {
            val previous = pendingStarts[key]
            pendingStarts[key] = PendingStart(uid ?: previous?.uid, request.reservationId ?: previous?.reservationId, now)
        }
        ocpp.record(
            station,
            "TRANSACTION",
            status = "${request.eventType.name}:${request.triggerReason.name}",
            info = listOfNotNull(request.transactionInfo.transactionId, uid).joinToString(" / "),
        )
        val decision = uid?.let { ocpp.authorize(station, it) }
        uid?.let { ocpp.rejected(station, it, decision?.status ?: AuthorizationStatus.Invalid) }
        return TransactionEventResponse(idTokenInfo = decision?.let(::info))
    }

    private fun start(
        station: StationEntity,
        number: Int,
        reference: String,
        at: Instant,
        readings: ArrayNode,
        uid: String?,
        reservation: Int?,
    ): Pair<TransactionEntity, OcppService.Decision?> {
        val tenant = station.key.tenantId
        val identity = station.key.stationId
        ocpp.connector(station, number)
        transactions.findFirstByTenantIdAndStationIdAndConnectorIdAndStoppedAtIsNull(tenant, identity, number)?.let { stale ->
            val end = if (at.isAfter(stale.startedAt)) at else stale.startedAt
            ocpp.close(station, stale, end, stale.lastEnergyWh ?: stale.meterStartWh, "Other")
            payments.stopped(stale)
            ocpp.record(station, "TRANSACTION", number, "Replaced", info = "${stale.id} → $reference")
        }

        val begin =
            readings.toList()
                .filter { reading -> reading.path("sampledValue").any { it.path("context").asText() == "Transaction.Begin" } }
                .ifEmpty { readings.toList() }
                .map(MeterReadings::parse)
                .sortedBy { it.at }
                .firstNotNullOfOrNull { it.energyWh }
        val decision = uid?.let { ocpp.authorize(station, it, number) }
        val location = organization.location(station)
        val transaction =
            transactions.save(
                TransactionEntity(
                    tenantId = tenant,
                    stationId = identity,
                    connectorId = number,
                    tokenUid = uid.orEmpty(),
                    userId = decision?.userId,
                    vehicleId = decision?.let { vehicle(station, it) },
                    vid = ocpp.connector(station, number).vid,
                    startedAt = at,
                    meterStartWh = begin ?: BigDecimal.ZERO,
                    authorizationStatus = decision?.status ?: AuthorizationStatus.Invalid,
                    siteAreaId = location.area?.id,
                    siteId = location.site?.id,
                    roamingPartnerId = decision?.partnerId,
                    lastEnergyWh = begin,
                    ocppTransactionId = reference,
                )
            ).also { db.flush() }

        if (uid != null) payments.started(transaction)
        reservation?.let { reservations.used(tenant, identity, it, transaction.id) }
        uid?.let { ocpp.rejected(station, it, transaction.authorizationStatus) }
        events.publishEvent(TransactionStarted(station, transaction))
        return transaction to decision
    }

    private fun assign(station: StationEntity, transaction: TransactionEntity, uid: String): OcppService.Decision {
        val decision = ocpp.authorize(station, uid, transaction.connectorId)
        transaction.tokenUid = uid
        transaction.userId = decision.userId
        transaction.vehicleId = vehicle(station, decision)
        transaction.authorizationStatus = decision.status
        transaction.roamingPartnerId = decision.partnerId
        transaction.vid = transaction.vid ?: ocpp.connector(station, transaction.connectorId).vid
        transactions.save(transaction)
        payments.started(transaction)
        ocpp.rejected(station, uid, decision.status)
        return decision
    }

    private fun vehicle(station: StationEntity, decision: OcppService.Decision) =
        decision.vehicleId ?: decision.userId?.let { users.findByTenantIdAndId(station.key.tenantId, it)?.defaultCarId }

    private fun end(
        station: StationEntity,
        transaction: TransactionEntity,
        at: Instant,
        readings: ArrayNode,
        reason: ReasonEnum?,
    ) {
        if (transaction.stoppedAt != null && !transaction.softStopped) return
        val stoppedAt =
            if (at.isBefore(transaction.startedAt)) {
                ocpp.record(station, "TRANSACTION", transaction.connectorId, "EndBeforeStart", info = "$at < ${transaction.startedAt}")
                transaction.startedAt
            } else {
                at
            }
        val final =
            readings.toList()
                .filter { reading -> reading.path("sampledValue").any { it.path("context").asText() == "Transaction.End" } }
                .map(MeterReadings::parse)
                .sortedBy { it.at }
                .lastOrNull { it.energyWh != null }
                ?.energyWh
        val meterStop = (final ?: transaction.lastEnergyWh ?: transaction.meterStartWh).max(transaction.meterStartWh)
        ocpp.close(station, transaction, stoppedAt, meterStop, reason?.name ?: "Local")
        payments.stopped(transaction)
        val connector = ocpp.connector(station, transaction.connectorId)
        if (connector.status in VehicleIds.PLUGGED) {
            ocpp.applyStatus(station, transaction.connectorId, "Finishing", "NoError", maxOf(stoppedAt, connector.statusAt))
        }
    }

    private fun charging(station: StationEntity, transaction: TransactionEntity, state: ChargingStateEnum, at: Instant) {
        val connector = ocpp.connector(station, transaction.connectorId)
        if (connector.status == "Faulted") return
        val delivered = (transaction.lastEnergyWh ?: transaction.meterStartWh) > transaction.meterStartWh
        val status =
            when (state) {
                ChargingStateEnum.Charging -> "Charging"
                ChargingStateEnum.SuspendedEV -> "SuspendedEV"
                ChargingStateEnum.SuspendedEVSE -> "SuspendedEVSE"
                ChargingStateEnum.EVConnected -> if (delivered) "SuspendedEV" else "Preparing"
                ChargingStateEnum.Idle -> if (delivered) "Finishing" else "Preparing"
            }
        if (status != connector.status) {
            ocpp.applyStatus(station, transaction.connectorId, status, "NoError", maxOf(at, connector.statusAt))
        }
    }

    private fun meterValues(station: StationEntity, request: MeterValuesRequest): MeterValuesResponse {
        ocpp.saveMeters(station, request.evseId, null, meters(request.meterValue))
        return MeterValuesResponse()
    }

    private fun dataTransfer(station: StationEntity, request: DataTransferRequest): DataTransferResponse {
        val accepted = request.vendorId in ocpp.dataTransferVendors(station)
        val status = if (accepted) DataTransferStatusEnum.Accepted else DataTransferStatusEnum.UnknownVendorId
        ocpp.record(
            station,
            "DATA_TRANSFER",
            status = status.name,
            info =
                listOfNotNull(
                        request.vendorId,
                        request.messageId,
                        request.data?.let { if (it.isString) it.asText() else db.encode(it) },
                    )
                    .joinToString(" / "),
        )
        return DataTransferResponse(status)
    }

    private fun securityEvent(station: StationEntity, request: SecurityEventNotificationRequest): SecurityEventNotificationResponse {
        ocpp.record(station, "SECURITY", status = request.type, info = request.techInfo)
        db.audit(station.key.tenantId, "station:${station.key.stationId}", "SecurityEventNotification", request.type)
        events.publishEvent(StationNotification(station, "SECURITY", request.type))
        return SecurityEventNotificationResponse()
    }

    private fun signCertificate(station: StationEntity, request: SignCertificateRequest): SignCertificateResponse {
        if (request.certificateType == CertificateSigningUseEnum.V2GCertificate) {
            if (!certificates.enabled) {
                ocpp.record(station, "CERTIFICATE", status = "V2G:Rejected", info = "ISO 15118 non activé.")
                return SignCertificateResponse(GenericStatusEnum.Rejected, StatusInfo("Iso15118Disabled"))
            }
            return try {
                val signed = certificates.signV2g(request.csr)
                queue.command(
                    station.key.tenantId,
                    station.key.stationId,
                    "CertificateSigned",
                    Ocpp201Codec.encode(CertificateSignedRequest(signed.chain, CertificateSigningUseEnum.V2GCertificate)),
                    version = OcppVersion.V201,
                )
                ocpp.record(
                    station,
                    "CERTIFICATE",
                    status = "V2G:Signed",
                    info = "${signed.certificate.subjectX500Principal.name} / ${signed.certificate.notAfter.toInstant()}",
                )
                SignCertificateResponse(GenericStatusEnum.Accepted)
            } catch (error: CertificateRequestRejected) {
                ocpp.record(station, "CERTIFICATE", status = "V2G:Rejected", info = error.message)
                SignCertificateResponse(GenericStatusEnum.Rejected, StatusInfo("InvalidCSR"))
            }
        }
        val status = ocpp.signCertificate(station, request.csr)
        return SignCertificateResponse(if (status == "Accepted") GenericStatusEnum.Accepted else GenericStatusEnum.Rejected)
    }

    private fun logStatus(station: StationEntity, request: LogStatusNotificationRequest): LogStatusNotificationResponse {
        station.logStatus = request.status.name
        station.diagnosticsStatus =
            when (request.status) {
                UploadLogStatusEnum.Idle -> "Idle"
                UploadLogStatusEnum.Uploading -> "Uploading"
                UploadLogStatusEnum.Uploaded -> "Uploaded"
                UploadLogStatusEnum.AcceptedCanceled -> station.diagnosticsStatus
                else -> "UploadFailed"
            }
        ocpp.record(station, "LOG", status = request.status.name, info = request.requestId?.toString())
        return LogStatusNotificationResponse()
    }

    private fun firmwareStatus(station: StationEntity, request: FirmwareStatusNotificationRequest): FirmwareStatusNotificationResponse {
        val status = request.status.name
        station.firmwareStatus = status
        ocpp.record(station, "FIRMWARE", status = status, info = request.requestId?.toString())
        db.audit(station.key.tenantId, "station:${station.key.stationId}", "FirmwareStatusNotification", status)
        events.publishEvent(StationNotification(station, "FIRMWARE", status))
        return FirmwareStatusNotificationResponse()
    }

    private fun notifyReport(station: StationEntity, request: NotifyReportRequest): NotifyReportResponse {
        val now = Instant.now()
        request.reportData.orEmpty().forEach { data ->
            val key = Ocpp201Variables.key(data.component, data.variable)
            if (key.length > 255) return@forEach
            val actual = data.variableAttribute.firstOrNull { (it.type ?: AttributeEnum.Actual) == AttributeEnum.Actual } ?: return@forEach
            parameters.save(
                StationParameterEntity(
                    StationParameterKey(station.key.tenantId, station.key.stationId, key),
                    if (actual.mutability == MutabilityEnum.WriteOnly || key.equals(Ocpp201Variables.BASIC_AUTH_PASSWORD, true)) null
                    else actual.value?.take(5000),
                    actual.mutability == MutabilityEnum.ReadOnly,
                    now,
                )
            )
        }
        if (request.tbc != true) {
            ocpp.record(station, "REPORT", status = "Received", info = "${request.requestId} / ${request.seqNo}")
        }
        return NotifyReportResponse()
    }

    private fun notifyEvent(station: StationEntity, request: NotifyEventRequest): NotifyEventResponse {
        request.eventData.forEach { event ->
            ocpp.record(
                station,
                "EVENT",
                event.component.evse?.id,
                "${event.component.name}/${event.variable.name}".take(60),
                event.techCode?.take(60),
                listOfNotNull(
                        event.actualValue,
                        event.trigger.name,
                        event.techInfo,
                        event.transactionId,
                        "cleared".takeIf { event.cleared == true },
                    )
                    .joinToString(" / "),
            )
        }
        return NotifyEventResponse()
    }

    private fun reservationStatus(station: StationEntity, request: ReservationStatusUpdateRequest): ReservationStatusUpdateResponse {
        reservations.updated(
            station.key.tenantId,
            station.key.stationId,
            request.reservationId,
            request.reservationUpdateStatus.name.uppercase(),
        )
        ocpp.record(station, "RESERVATION", status = request.reservationUpdateStatus.name, info = request.reservationId.toString())
        return ReservationStatusUpdateResponse()
    }

    private fun meters(values: List<MeterValue>?): ArrayNode {
        val array = db.json.createArrayNode()
        values.orEmpty().forEach { value ->
            val samples = db.json.createArrayNode()
            value.sampledValue.forEach { sample ->
                val raw = samples.addObject()
                val multiplier = sample.unitOfMeasure?.multiplier ?: 0
                raw.put("value", sample.value.scaleByPowerOfTen(multiplier).stripTrailingZeros().toPlainString())
                sample.context?.let { raw.put("context", it.value) }
                raw.put("format", "Raw")
                sample.measurand?.let { raw.put("measurand", it.value) }
                sample.phase?.let { raw.put("phase", it.value) }
                sample.location?.let { raw.put("location", it.name) }
                sample.unitOfMeasure?.unit?.let { raw.put("unit", it) }
                sample.signedMeterValue?.let { signed ->
                    val entry = samples.addObject()
                    entry.put("value", signed.signedMeterData)
                    sample.context?.let { entry.put("context", it.value) }
                    entry.put("format", "SignedData")
                    sample.measurand?.let { entry.put("measurand", it.value) }
                    sample.location?.let { entry.put("location", it.name) }
                    entry.put("signingMethod", signed.signingMethod)
                    entry.put("encodingMethod", signed.encodingMethod)
                }
            }
            array.addObject().put("timestamp", value.timestamp).set("sampledValue", samples)
        }
        return array
    }

    private fun info(decision: OcppService.Decision) =
        IdTokenInfo(
            status = AuthorizationStatusEnum.valueOf(decision.status.name),
            groupIdToken = decision.parentIdTag?.let { IdToken(idToken = it, type = IdTokenEnum.Central) },
        )

    companion object {
        private val PENDING_TTL: Duration = Duration.ofHours(24)
        private const val PENDING_LIMIT = 10_000

        fun uid(token: IdToken): String? =
            when (token.type) {
                IdTokenEnum.NoAuthorization -> null
                IdTokenEnum.MacAddress -> {
                    val raw = VehicleIds.PREFIX + token.idToken
                    (VehicleIds.normalize(raw) ?: raw).take(36)
                }
                else -> token.idToken.takeIf(String::isNotBlank)
            }
    }
}
