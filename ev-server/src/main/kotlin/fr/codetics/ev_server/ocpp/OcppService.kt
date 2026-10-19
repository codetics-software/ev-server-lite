package fr.codetics.ev_server.ocpp

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.sha256
import fr.codetics.ev_server.models.entities.ChargingTokenKey
import fr.codetics.ev_server.models.entities.ConnectorEntity
import fr.codetics.ev_server.models.entities.ConnectorKey
import fr.codetics.ev_server.models.entities.MeterValueEntity
import fr.codetics.ev_server.models.entities.OcppCallEntity
import fr.codetics.ev_server.models.entities.OcppCallKey
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.entities.TransactionEntity
import fr.codetics.ev_server.repositories.ChargingTokenRepository
import fr.codetics.ev_server.repositories.ConnectorRepository
import fr.codetics.ev_server.repositories.MeterValueRepository
import fr.codetics.ev_server.repositories.OcppCallRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TransactionRepository
import fr.codetics.ev_server.repositories.UserRepository
import fr.codetics.ev_server.services.billing.ChargingPaymentLifecycle
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode

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
) {
    fun call(tenant: UUID, station: String, id: String, action: String, payload: JsonNode): String {
        OcppValidation.validate(action, payload)

        return db.atomic {
            val device =
                stations.findForUpdate(StationKey(tenant, station))
                    ?: throw OcppFault("SecurityError", "Borne inconnue.")

            if (!device.enabled) {
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
                return@atomic previous.response
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
                station.vendor = payload.path("chargePointVendor").asText()
                station.model = payload.path("chargePointModel").asText()
                station.firmwareVersion = payload.get("firmwareVersion")?.asText()
                station.metadata = db.encode(payload)

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
                authorization(tenant, identity, payload.path("idTag").asText())
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
                mapOf("status" to "UnknownVendorId")
            }
            "DiagnosticsStatusNotification",
            "FirmwareStatusNotification" -> {
                db.audit(tenant, "station:$identity", action, payload.path("status").asText())
                emptyMap<String, Any>()
            }
            else -> {
                throw OcppFault("NotSupported", "Action non prise en charge.")
            }
        }
    }

    private fun authorization(tenant: UUID, station: String, uid: String): Map<String, Any> {
        return mapOf("idTagInfo" to mapOf("status" to authorize(tenant, station, uid)))
    }

    private fun authorize(
        tenant: UUID,
        station: String,
        uid: String,
        connector: Int? = null,
    ): String {
        payments.authorization(tenant, station, connector, uid)?.let {
            return it
        }
        val token = tokens.findById(ChargingTokenKey(tenant, uid)).orElse(null) ?: return "Invalid"
        val userDisabled =
            token.userId?.let { id -> users.findByTenantIdAndId(tenant, id)?.enabled != true }
                ?: false

        if (!token.enabled || userDisabled) {
            return "Blocked"
        }
        if (token.expiresAt?.isBefore(Instant.now()) == true) {
            return "Expired"
        }

        return "Accepted"
    }

    private fun connector(station: StationEntity, number: Int): ConnectorEntity {
        val key = ConnectorKey(station.key.tenantId, station.key.stationId, number)

        return connectors.findById(key).orElse(null)
            ?: connectors.saveAndFlush(ConnectorEntity(key = key))
    }

    private fun updateStatus(station: StationEntity, payload: JsonNode) {
        val connector = connector(station, payload.path("connectorId").intValue())
        val timestamp = payload.get("timestamp")?.asText()?.let(Instant::parse) ?: Instant.now()

        if (timestamp.isBefore(connector.statusAt)) {
            return
        }

        connector.status = payload.path("status").asText()
        connector.errorCode = payload.path("errorCode").asText()
        connector.statusAt = timestamp
        connectors.save(connector)
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

        val uid = payload.path("idTag").asText()
        val status = authorize(tenant, identity, uid, number)
        val token = tokens.findById(ChargingTokenKey(tenant, uid)).orElse(null)
        val transaction =
            transactions.saveAndFlush(
                TransactionEntity(
                    tenantId = tenant,
                    stationId = identity,
                    connectorId = number,
                    tokenUid = uid,
                    userId = token?.userId,
                    startedAt = Instant.parse(payload.path("timestamp").asText()),
                    meterStartWh = payload.path("meterStart").decimalValue(),
                    authorizationStatus = status,
                )
            )

        payments.started(transaction)
        return mapOf("transactionId" to transaction.id, "idTagInfo" to mapOf("status" to status))
    }

    private fun stop(station: StationEntity, payload: JsonNode): Map<String, Any> {
        val transaction = verifyTransaction(station, payload.path("transactionId").intValue())
        val stoppedAt = Instant.parse(payload.path("timestamp").asText())
        val meterStop = payload.path("meterStop").decimalValue()

        if (stoppedAt.isBefore(transaction.startedAt)) {
            throw OcppFault("PropertyConstraintViolation", "La fin de session précède son début.")
        }

        if (transaction.stoppedAt == null) {
            transaction.stoppedAt = stoppedAt
            transaction.meterStopWh = meterStop
            transaction.stopReason = payload.get("reason")?.asText() ?: "Local"
            transactions.save(transaction)

            if (payload.has("transactionData")) {
                saveMeters(
                    station,
                    transaction.connectorId,
                    transaction.id,
                    payload.path("transactionData"),
                )
            }
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
        return authorization(station.key.tenantId, station.key.stationId, uid)
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
        if (transaction != null) {
            val active = verifyTransaction(station, transaction, connector)
            values.forEach { reading ->
                reading.path("sampledValue").forEach { sample ->
                    if (
                        sample.path("measurand").asText("Energy.Active.Import.Register") ==
                            "Energy.Active.Import.Register" &&
                            sample.path("format").asText("Raw") == "Raw" &&
                            !sample.has("phase") &&
                            sample.path("location").asText("Outlet") == "Outlet"
                    ) {
                        val value =
                            sample
                                .path("value")
                                .asText()
                                .takeIf { it.length <= 30 }
                                ?.toBigDecimalOrNull()
                        val unit = sample.path("unit").asText("Wh")
                        if (value != null && value.signum() >= 0 && unit in setOf("Wh", "kWh")) {
                            payments.observed(
                                active,
                                if (unit == "kWh") value * java.math.BigDecimal(1000) else value,
                                Instant.parse(reading.path("timestamp").asText()),
                            )
                        }
                    }
                }
            }
        }
    }
}
