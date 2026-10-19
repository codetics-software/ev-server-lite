package fr.codetics.ev_server.ocpp

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.sha256
import fr.codetics.ev_server.persistence.ChargingTokenKey
import fr.codetics.ev_server.persistence.ChargingTokenRepository
import fr.codetics.ev_server.persistence.ConnectorEntity
import fr.codetics.ev_server.persistence.ConnectorKey
import fr.codetics.ev_server.persistence.ConnectorRepository
import fr.codetics.ev_server.persistence.MeterValueEntity
import fr.codetics.ev_server.persistence.MeterValueRepository
import fr.codetics.ev_server.persistence.OcppCallEntity
import fr.codetics.ev_server.persistence.OcppCallKey
import fr.codetics.ev_server.persistence.OcppCallRepository
import fr.codetics.ev_server.persistence.StationEntity
import fr.codetics.ev_server.persistence.StationKey
import fr.codetics.ev_server.persistence.StationRepository
import fr.codetics.ev_server.persistence.TransactionEntity
import fr.codetics.ev_server.persistence.TransactionRepository
import fr.codetics.ev_server.persistence.UserRepository
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
    private val users: UserRepository
) {
    fun call(tenant: UUID, station: String, id: String, action: String, payload: JsonNode): String {
        OcppValidation.validate(action, payload)

        return db.atomic {
            val device = stations.findForUpdate(StationKey(tenant, station))
                ?: throw OcppFault("SecurityError", "Unknown station")

            if (!device.enabled) {
                throw OcppFault("SecurityError", "Station is disabled")
            }

            val key = OcppCallKey(tenant, station, id)
            val hash = sha256(action + db.encode(payload))
            val previous = calls.findById(key).orElse(null)

            if (previous != null) {
                if (previous.requestHash != hash) {
                    throw OcppFault("ProtocolError", "Message ID was reused with a different request")
                }
                return@atomic previous.response
            }

            device.lastSeen = Instant.now()
            val result = handle(device, action, payload)
            val response = db.encode(listOf(3, id, result))
            calls.save(
                OcppCallEntity(
                    key = key,
                    action = action,
                    requestHash = hash,
                    response = response
                )
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
                    "interval" to 60
                )
            }
            "Heartbeat" -> {
                mapOf("currentTime" to Instant.now().toString())
            }
            "Authorize" -> {
                authorization(tenant, payload.path("idTag").asText())
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
            "DiagnosticsStatusNotification", "FirmwareStatusNotification" -> {
                db.audit(tenant, "station:$identity", action, payload.path("status").asText())
                emptyMap<String, Any>()
            }
            else -> {
                throw OcppFault("NotSupported", "Unsupported action")
            }
        }
    }

    private fun authorization(tenant: UUID, uid: String): Map<String, Any> {
        return mapOf("idTagInfo" to mapOf("status" to authorize(tenant, uid)))
    }

    private fun authorize(tenant: UUID, uid: String): String {
        val token = tokens.findById(ChargingTokenKey(tenant, uid)).orElse(null)
            ?: return "Invalid"
        val userDisabled = token.userId?.let { id ->
            users.findByTenantIdAndId(tenant, id)?.enabled != true
        } ?: false

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

        if (transactions.existsByTenantIdAndStationIdAndConnectorIdAndStoppedAtIsNull(tenant, identity, number)) {
            throw OcppFault("GenericError", "Connector already has an active transaction")
        }

        val uid = payload.path("idTag").asText()
        val status = authorize(tenant, uid)
        val token = tokens.findById(ChargingTokenKey(tenant, uid)).orElse(null)
        val transaction = transactions.saveAndFlush(
            TransactionEntity(
                tenantId = tenant,
                stationId = identity,
                connectorId = number,
                tokenUid = uid,
                userId = token?.userId,
                startedAt = Instant.parse(payload.path("timestamp").asText()),
                meterStartWh = payload.path("meterStart").decimalValue(),
                authorizationStatus = status
            )
        )

        return mapOf(
            "transactionId" to transaction.id,
            "idTagInfo" to mapOf("status" to status)
        )
    }

    private fun stop(station: StationEntity, payload: JsonNode): Map<String, Any> {
        val transaction = verifyTransaction(station, payload.path("transactionId").intValue())
        val stoppedAt = Instant.parse(payload.path("timestamp").asText())
        val meterStop = payload.path("meterStop").decimalValue()

        if (stoppedAt.isBefore(transaction.startedAt)) {
            throw OcppFault("PropertyConstraintViolation", "Stop precedes start")
        }

        if (transaction.stoppedAt == null) {
            transaction.stoppedAt = stoppedAt
            transaction.meterStopWh = meterStop
            transaction.stopReason = payload.get("reason")?.asText() ?: "Local"
            transactions.save(transaction)

            if (payload.has("transactionData")) {
                saveMeters(station, transaction.connectorId, transaction.id, payload.path("transactionData"))
            }
        } else if (transaction.stoppedAt != stoppedAt || transaction.meterStopWh?.compareTo(meterStop) != 0) {
            throw OcppFault("PropertyConstraintViolation", "Final transaction values cannot be overwritten")
        }

        val uid = payload.get("idTag")?.asText() ?: transaction.tokenUid
        return authorization(station.key.tenantId, uid)
    }

    private fun verifyTransaction(station: StationEntity, id: Int, connector: Int? = null): TransactionEntity {
        val transaction = transactions.findByTenantIdAndStationIdAndId(station.key.tenantId, station.key.stationId, id)
            ?: throw OcppFault("PropertyConstraintViolation", "Unknown transaction")

        if (connector != null && transaction.connectorId != connector) {
            throw OcppFault("PropertyConstraintViolation", "Transaction belongs to a different connector")
        }

        return transaction
    }

    private fun saveMeters(station: StationEntity, connector: Int, transaction: Int?, values: JsonNode) {
        val readings = values.toList().map { reading ->
            MeterValueEntity(
                tenantId = station.key.tenantId,
                stationId = station.key.stationId,
                connectorId = connector,
                transactionId = transaction,
                sampledAt = Instant.parse(reading.path("timestamp").asText()),
                samples = db.encode(reading.path("sampledValue"))
            )
        }
        meters.saveAll(readings)
    }
}
