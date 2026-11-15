package fr.codetics.ev_server.ocpp.v201

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.msg
import fr.codetics.ev_server.models.entities.CommandEntity
import fr.codetics.ev_server.ocpp.OcppFault
import fr.codetics.ev_server.ocpp.OcppVersion
import fr.codetics.ev_server.ocpp.VehicleIds
import fr.codetics.ev_server.models.entities.ConnectorKey
import fr.codetics.ev_server.repositories.ConnectorRepository
import fr.codetics.ev_server.repositories.TransactionRepository
import java.util.UUID
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode

data class WireCommand(val action: String, val payload: JsonNode)

@Service
class Ocpp201Commands(
    private val db: Database,
    private val transactions: TransactionRepository,
    private val connectors: ConnectorRepository,
) {
    fun native(action: String, payload: JsonNode): Any {
        val message = Ocpp201Messages.toStation[action] ?: throw OcppFault("NotSupported", msg("ocpp.translation.unknown_action", action))
        return Ocpp201Codec.decode(payload, message.request)
    }

    fun outbound(command: CommandEntity, version: OcppVersion): WireCommand {
        val dialect = OcppVersion.fromLabel(command.ocppVersion) ?: OcppVersion.V16
        val request = db.decode(command.request)
        return when {
            dialect == version -> WireCommand(command.action, request)
            dialect == OcppVersion.V201 -> throw OcppFault("NotSupported", msg("ocpp.translation.station_16"))
            else -> {
                val translated = translate(command.id, command.tenantId, command.stationId, command.action, request)
                WireCommand(requireNotNull(translated::class.simpleName).removeSuffix("Request"), Ocpp201Codec.encode(translated))
            }
        }
    }

    fun check(id: UUID, tenant: UUID, station: String, action: String, request: JsonNode) {
        translate(id, tenant, station, action, request)
    }

    fun inbound(command: CommandEntity, result: JsonNode): JsonNode {
        if (command.ocppVersion == OcppVersion.V201.label || command.wireVersion != OcppVersion.V201.label) return result
        val out = db.json.createObjectNode()
        val status = result.path("status").asText()
        when (command.action) {
            "Reset" -> out.put("status", if (status == "Scheduled") "Accepted" else status)
            "UnlockConnector" ->
                out.put(
                    "status",
                    when (status) {
                        "OngoingAuthorizedTransaction" -> "UnlockFailed"
                        "UnknownConnector" -> "NotSupported"
                        else -> status
                    },
                )
            "ChangeConfiguration" -> {
                val attribute = result.path("setVariableResult").path(0).path("attributeStatus").asText()
                out.put(
                    "status",
                    when (attribute) {
                        "Accepted", "Rejected", "RebootRequired" -> attribute
                        else -> "NotSupported"
                    },
                )
                result.path("setVariableResult").path(0).get("attributeStatusInfo")?.let { out.set("statusInfo", it) }
            }
            "GetConfiguration" -> {
                val known = out.putArray("configurationKey")
                val unknown = db.json.createArrayNode()
                result.path("getVariableResult").forEach { entry ->
                    val component = Ocpp201Codec.mapper.treeToValue(entry.path("component"), Component::class.java)
                    val variable = Ocpp201Codec.mapper.treeToValue(entry.path("variable"), Variable::class.java)
                    val key = Ocpp201Variables.key(component, variable)
                    if (entry.path("attributeStatus").asText() == "Accepted") {
                        known.addObject().put("key", key).put("readonly", false).apply {
                            entry.get("attributeValue")?.let { put("value", it.asText()) }
                        }
                    } else {
                        unknown.add(key)
                    }
                }
                if (!unknown.isEmpty) out.set("unknownKey", unknown)
                if (result.has("status")) out.put("status", status)
            }
            "GetCompositeSchedule" -> {
                out.put("status", status)
                result.get("schedule")?.let { schedule ->
                    out.put("connectorId", schedule.path("evseId").intValue())
                    out.put("scheduleStart", schedule.path("scheduleStart").asText())
                    val target = out.putObject("chargingSchedule")
                    target.put("duration", schedule.path("duration").intValue())
                    target.put("startSchedule", schedule.path("scheduleStart").asText())
                    target.put("chargingRateUnit", schedule.path("chargingRateUnit").asText())
                    val periods = target.putArray("chargingSchedulePeriod")
                    schedule.path("chargingSchedulePeriod").forEach { period ->
                        periods.addObject().apply {
                            put("startPeriod", period.path("startPeriod").intValue())
                            set("limit", period.path("limit"))
                            period.get("numberPhases")?.let { put("numberPhases", it.intValue()) }
                        }
                    }
                }
            }
            "GetDiagnostics" -> {
                result.get("filename")?.let { out.put("fileName", it.asText()) }
                out.put("status", status)
            }
            "GetLocalListVersion" -> out.put("listVersion", result.path("versionNumber").intValue())
            "GetInstalledCertificateIds" -> {
                out.put("status", status)
                val chain = result.path("certificateHashDataChain")
                if (chain.isArray && !chain.isEmpty) {
                    val hashes = out.putArray("certificateHashData")
                    chain.forEach { hashes.add(it.path("certificateHashData")) }
                }
            }
            "DataTransfer" -> {
                out.put("status", status)
                result.get("data")?.let { out.set("data", it) }
            }
            "GetLog" -> {
                out.put("status", status)
                result.get("filename")?.let { out.put("filename", it.asText()) }
            }
            else -> out.put("status", status)
        }
        if (!out.has("statusInfo")) result.get("statusInfo")?.let { out.set("statusInfo", it) }
        return out
    }

    private fun translate(id: UUID, tenant: UUID, station: String, action: String, r: JsonNode): Any {
        val request =
            try {
                build(id, tenant, station, action, r)
            } catch (error: IllegalArgumentException) {
                throw OcppFault("NotSupported", msg("ocpp.translation.invalid_parameters", action))
            } catch (_: NullPointerException) {
                throw OcppFault("NotSupported", msg("ocpp.translation.missing_parameter", action))
            }
        Ocpp201Codec.validate(request)
        return request
    }

    private fun build(id: UUID, tenant: UUID, station: String, action: String, r: JsonNode): Any =
        when (action) {
            "RemoteStartTransaction" ->
                RequestStartTransactionRequest(
                    evseId = int(r, "connectorId")?.takeIf { it > 0 },
                    idToken = IdToken(idToken = text(r, "idTag")!!, type = IdTokenEnum.Central),
                    remoteStartId = number(id),
                    chargingProfile = r.get("chargingProfile")?.let { profile(tenant, station, it) },
                )
            "RemoteStopTransaction" ->
                RequestStopTransactionRequest(reference(tenant, station, r.path("transactionId").intValue()))
            "Reset" -> ResetRequest(if (text(r, "type") == "Hard") ResetEnum.Immediate else ResetEnum.OnIdle)
            "ChangeAvailability" ->
                ChangeAvailabilityRequest(
                    evse = int(r, "connectorId")?.takeIf { it > 0 }?.let { EVSE(it) },
                    operationalStatus = OperationalStatusEnum.valueOf(text(r, "type")!!),
                )
            "ChangeConfiguration" -> {
                val key = text(r, "key")!!
                if (Ocpp201Variables.canonical(key).equals(Ocpp201Variables.SECURITY_PROFILE, true)) {
                    throw OcppFault("NotSupported", msg("ocpp.translation.security_profile"))
                }
                val target = variable(key)
                SetVariablesRequest(
                    listOf(SetVariableData(attributeValue = text(r, "value").orEmpty(), component = target.component, variable = target.variable))
                )
            }
            "GetConfiguration" -> {
                val keys = r.path("key").toList().map { it.asText() }
                if (keys.isEmpty()) GetBaseReportRequest(number(id), ReportBaseEnum.FullInventory)
                else GetVariablesRequest(keys.map { variable(it).let { target -> GetVariableData(component = target.component, variable = target.variable) } })
            }
            "ClearCache" -> ClearCacheRequest()
            "ClearChargingProfile" -> {
                val connector = int(r, "connectorId")
                val purpose = text(r, "chargingProfilePurpose")?.let(::purpose)
                val stack = int(r, "stackLevel")
                ClearChargingProfileRequest(
                    chargingProfileId = int(r, "id"),
                    chargingProfileCriteria =
                        if (connector == null && purpose == null && stack == null) null
                        else ClearChargingProfile(connector, purpose, stack),
                )
            }
            "DataTransfer" -> DataTransferRequest(text(r, "messageId"), r.get("data"), text(r, "vendorId")!!)
            "GetCompositeSchedule" ->
                GetCompositeScheduleRequest(
                    duration = r.path("duration").intValue(),
                    chargingRateUnit = text(r, "chargingRateUnit")?.let(ChargingRateUnitEnum::valueOf),
                    evseId = r.path("connectorId").intValue(),
                )
            "GetDiagnostics" ->
                GetLogRequest(
                    log = LogParameters(text(r, "location")!!, text(r, "startTime"), text(r, "stopTime")),
                    logType = LogEnum.DiagnosticsLog,
                    requestId = number(id),
                    retries = int(r, "retries"),
                    retryInterval = int(r, "retryInterval"),
                )
            "GetLocalListVersion" -> GetLocalListVersionRequest()
            "ReserveNow" ->
                ReserveNowRequest(
                    id = r.path("reservationId").intValue(),
                    expiryDateTime = text(r, "expiryDate")!!,
                    idToken = IdToken(idToken = text(r, "idTag")!!, type = IdTokenEnum.Central),
                    evseId = int(r, "connectorId")?.takeIf { it > 0 },
                    groupIdToken = text(r, "parentIdTag")?.let { IdToken(idToken = it, type = IdTokenEnum.Central) },
                )
            "CancelReservation" -> CancelReservationRequest(r.path("reservationId").intValue())
            "SendLocalList" ->
                SendLocalListRequest(
                    localAuthorizationList =
                        r.path("localAuthorizationList").toList().map { entry ->
                            AuthorizationData(
                                idToken = token(text(entry, "idTag")!!),
                                idTokenInfo =
                                    entry.get("idTagInfo")?.let { info ->
                                        IdTokenInfo(
                                            status = AuthorizationStatusEnum.valueOf(text(info, "status")!!),
                                            cacheExpiryDateTime = text(info, "expiryDate"),
                                            groupIdToken = text(info, "parentIdTag")?.let { IdToken(idToken = it, type = IdTokenEnum.Central) },
                                        )
                                    },
                            )
                        }.ifEmpty { null },
                    versionNumber = r.path("listVersion").intValue(),
                    updateType = UpdateEnum.valueOf(text(r, "updateType")!!),
                )
            "SetChargingProfile" ->
                SetChargingProfileRequest(
                    evseId = r.path("connectorId").intValue(),
                    chargingProfile = profile(tenant, station, r.path("csChargingProfiles")),
                )
            "TriggerMessage", "ExtendedTriggerMessage" -> {
                val message =
                    when (val requested = text(r, "requestedMessage")!!) {
                        "DiagnosticsStatusNotification" -> MessageTriggerEnum.LogStatusNotification
                        "SignChargePointCertificate" -> MessageTriggerEnum.SignChargingStationCertificate
                        else -> MessageTriggerEnum.valueOf(requested)
                    }
                TriggerMessageRequest(
                    evse =
                        int(r, "connectorId")?.takeIf { it > 0 }?.let {
                            EVSE(it, if (message == MessageTriggerEnum.StatusNotification) physical(tenant, station, it) else null)
                        },
                    requestedMessage = message,
                )
            }
            "UnlockConnector" ->
                UnlockConnectorRequest(
                    evseId = r.path("connectorId").intValue(),
                    connectorId = physical(tenant, station, r.path("connectorId").intValue()),
                )
            "UpdateFirmware" ->
                UpdateFirmwareRequest(
                    retries = int(r, "retries"),
                    retryInterval = int(r, "retryInterval"),
                    requestId = number(id),
                    firmware = Firmware(location = text(r, "location")!!, retrieveDateTime = text(r, "retrieveDate")!!),
                )
            "SignedUpdateFirmware" -> {
                val firmware = r.path("firmware")
                UpdateFirmwareRequest(
                    retries = int(r, "retries"),
                    retryInterval = int(r, "retryInterval"),
                    requestId = r.path("requestId").intValue(),
                    firmware =
                        Firmware(
                            location = text(firmware, "location")!!,
                            retrieveDateTime = text(firmware, "retrieveDateTime")!!,
                            installDateTime = text(firmware, "installDateTime"),
                            signingCertificate = text(firmware, "signingCertificate"),
                            signature = text(firmware, "signature"),
                        ),
                )
            }
            "CertificateSigned" ->
                CertificateSignedRequest(text(r, "certificateChain")!!, CertificateSigningUseEnum.ChargingStationCertificate)
            "InstallCertificate" ->
                InstallCertificateRequest(installUse(text(r, "certificateType")!!), text(r, "certificate")!!)
            "DeleteCertificate" -> DeleteCertificateRequest(hash(r.path("certificateHashData")))
            "GetInstalledCertificateIds" ->
                GetInstalledCertificateIdsRequest(listOf(GetCertificateIdUseEnum.valueOf(installUse(text(r, "certificateType")!!).name)))
            "GetLog" -> {
                val log = r.path("log")
                GetLogRequest(
                    log = LogParameters(text(log, "remoteLocation")!!, text(log, "oldestTimestamp"), text(log, "latestTimestamp")),
                    logType = LogEnum.valueOf(text(r, "logType")!!),
                    requestId = r.path("requestId").intValue(),
                    retries = int(r, "retries"),
                    retryInterval = int(r, "retryInterval"),
                )
            }
            else -> throw OcppFault("NotSupported", msg("ocpp.translation.unsupported_command", action))
        }

    private fun profile(tenant: UUID, station: String, p: JsonNode): ChargingProfile {
        val schedule = p.path("chargingSchedule")
        val id = p.path("chargingProfileId").intValue()
        return ChargingProfile(
            id = id,
            stackLevel = p.path("stackLevel").intValue(),
            chargingProfilePurpose = purpose(text(p, "chargingProfilePurpose")!!),
            chargingProfileKind = ChargingProfileKindEnum.valueOf(text(p, "chargingProfileKind")!!),
            recurrencyKind = text(p, "recurrencyKind")?.let(RecurrencyKindEnum::valueOf),
            validFrom = text(p, "validFrom"),
            validTo = text(p, "validTo"),
            chargingSchedule =
                listOf(
                    ChargingSchedule(
                        id = id,
                        startSchedule = text(schedule, "startSchedule"),
                        duration = int(schedule, "duration"),
                        chargingRateUnit = ChargingRateUnitEnum.valueOf(text(schedule, "chargingRateUnit")!!),
                        chargingSchedulePeriod =
                            schedule.path("chargingSchedulePeriod").toList().map { period ->
                                ChargingSchedulePeriod(
                                    startPeriod = period.path("startPeriod").intValue(),
                                    limit = period.path("limit").decimalValue(),
                                    numberPhases = int(period, "numberPhases"),
                                )
                            },
                        minChargingRate = schedule.get("minChargingRate")?.decimalValue(),
                    )
                ),
            transactionId = int(p, "transactionId")?.let { reference(tenant, station, it) },
        )
    }

    private fun purpose(value: String) =
        if (value == "ChargePointMaxProfile") ChargingProfilePurposeEnum.ChargingStationMaxProfile
        else ChargingProfilePurposeEnum.valueOf(value)

    private fun installUse(value: String) =
        if (value == "CentralSystemRootCertificate") InstallCertificateUseEnum.CSMSRootCertificate
        else InstallCertificateUseEnum.valueOf(value)

    private fun hash(node: JsonNode) =
        CertificateHashData(
            hashAlgorithm = HashAlgorithmEnum.valueOf(text(node, "hashAlgorithm")!!),
            issuerNameHash = text(node, "issuerNameHash")!!,
            issuerKeyHash = text(node, "issuerKeyHash")!!,
            serialNumber = text(node, "serialNumber")!!,
        )

    private fun token(uid: String) =
        if (VehicleIds.isVid(uid)) IdToken(idToken = uid.substring(VehicleIds.PREFIX.length), type = IdTokenEnum.MacAddress)
        else IdToken(idToken = uid, type = IdTokenEnum.ISO14443)

    private fun variable(key: String): DeviceVariable =
        Ocpp201Variables.parse(key)
            ?: throw OcppFault("NotSupported", msg("ocpp.translation.unknown_parameter", key))

    private fun reference(tenant: UUID, station: String, id: Int): String =
        transactions.findByTenantIdAndStationIdAndId(tenant, station, id)?.ocppTransactionId
            ?: throw OcppFault("NotSupported", msg("ocpp.translation.unknown_session", id))

    private fun physical(tenant: UUID, station: String, evse: Int): Int {
        val states =
            connectors.findById(ConnectorKey(tenant, station, evse)).orElse(null)?.ocppConnectors
                ?.let { runCatching { db.decode(it).properties().associate { (key, value) -> key.toInt() to value.asText() } }.getOrNull() }
                .orEmpty()
        return states.entries.firstOrNull { it.value == "Occupied" }?.key ?: states.keys.minOrNull() ?: 1
    }

    private fun number(id: UUID) = (id.mostSignificantBits xor id.leastSignificantBits).toInt() and Int.MAX_VALUE

    private fun text(node: JsonNode, field: String): String? = node.get(field)?.takeUnless { it.isNull }?.asText()

    private fun int(node: JsonNode, field: String): Int? = node.get(field)?.takeIf { it.isIntegralNumber }?.intValue()
}
