package fr.codetics.ev_server.ocpp

import java.time.Instant
import tools.jackson.databind.JsonNode

class OcppFault(
    val code: String,
    override val message: String
) : RuntimeException(message)

object OcppValidation {
    val inbound = setOf(
        "BootNotification", "Heartbeat", "Authorize", "StatusNotification", "MeterValues",
        "StartTransaction", "StopTransaction", "DataTransfer", "DiagnosticsStatusNotification",
        "FirmwareStatusNotification"
    )
    val outbound = setOf(
        "CancelReservation", "ChangeAvailability", "ChangeConfiguration", "ClearCache",
        "ClearChargingProfile", "DataTransfer", "GetCompositeSchedule", "GetConfiguration",
        "GetDiagnostics", "GetLocalListVersion", "RemoteStartTransaction", "RemoteStopTransaction",
        "ReserveNow", "Reset", "SendLocalList", "SetChargingProfile", "TriggerMessage",
        "UnlockConnector", "UpdateFirmware"
    )
    private val required = mapOf(
        "BootNotification" to listOf("chargePointVendor", "chargePointModel"),
        "Authorize" to listOf("idTag"),
        "StatusNotification" to listOf("connectorId", "status", "errorCode"),
        "MeterValues" to listOf("connectorId", "meterValue"),
        "StartTransaction" to listOf("connectorId", "idTag", "meterStart", "timestamp"),
        "StopTransaction" to listOf("transactionId", "meterStop", "timestamp"),
        "DataTransfer" to listOf("vendorId"),
        "DiagnosticsStatusNotification" to listOf("status"),
        "FirmwareStatusNotification" to listOf("status"),
        "CancelReservation" to listOf("reservationId"),
        "ChangeAvailability" to listOf("connectorId", "type"),
        "ChangeConfiguration" to listOf("key", "value"),
        "GetCompositeSchedule" to listOf("connectorId", "duration"),
        "GetDiagnostics" to listOf("location"),
        "RemoteStartTransaction" to listOf("idTag"),
        "RemoteStopTransaction" to listOf("transactionId"),
        "ReserveNow" to listOf("connectorId", "expiryDate", "idTag", "reservationId"),
        "Reset" to listOf("type"),
        "SendLocalList" to listOf("listVersion", "updateType"),
        "SetChargingProfile" to listOf("connectorId", "csChargingProfiles"),
        "TriggerMessage" to listOf("requestedMessage"),
        "UnlockConnector" to listOf("connectorId"),
        "UpdateFirmware" to listOf("location", "retrieveDate")
    )
    private val integers = setOf(
        "connectorId", "transactionId", "meterStart", "meterStop", "reservationId",
        "duration", "listVersion", "retries", "retryInterval"
    )
    private val dates = setOf("timestamp", "expiryDate", "retrieveDate", "startTime", "stopTime")
    private val statuses = setOf(
        "Available", "Preparing", "Charging", "SuspendedEV", "SuspendedEVSE",
        "Finishing", "Reserved", "Unavailable", "Faulted"
    )
    private val errors = setOf(
        "ConnectorLockFailure", "EVCommunicationError", "GroundFailure", "HighTemperature",
        "InternalError", "LocalListConflict", "NoError", "OtherError", "OverCurrentFailure",
        "OverVoltage", "PowerMeterFailure", "PowerSwitchFailure", "ReaderFailure", "ResetFailure",
        "UnderVoltage", "WeakSignal"
    )
    private val textLimits = mapOf(
        "idTag" to 20,
        "parentIdTag" to 20,
        "chargePointVendor" to 20,
        "chargePointModel" to 20,
        "vendorId" to 255,
        "key" to 50,
        "value" to 500
    )

    fun validate(action: String, payload: JsonNode, serverInitiated: Boolean = false) {
        val supported = if (serverInitiated) outbound else inbound

        if (action !in supported) {
            throw OcppFault("NotSupported", "Unsupported action")
        }
        if (!payload.isObject) {
            throw OcppFault("TypeConstraintViolation", "Payload must be an object")
        }

        required[action].orEmpty().forEach { key ->
            if (!payload.hasNonNull(key)) {
                throw OcppFault("OccurrenceConstraintViolation", "Missing $key")
            }
        }
        validatePrimitives(payload)
        validateAction(action, payload)
    }

    private fun validatePrimitives(payload: JsonNode) {
        payload.properties().forEach { (key, value) ->
            if (key in integers && (!value.isIntegralNumber || !value.canConvertToInt() || value.intValue() < 0)) {
                throw OcppFault("TypeConstraintViolation", "$key must be a nonnegative integer")
            }
            if (key in dates) {
                timestamp(value, key)
            }
        }

        textLimits.forEach { (key, maximum) ->
            payload.get(key)?.let { value ->
                val minimum = if (key == "value") 0 else 1

                if (!value.isString || value.asText().length !in minimum..maximum) {
                    throw OcppFault("PropertyConstraintViolation", "Invalid $key")
                }
            }
        }
    }

    private fun validateAction(action: String, payload: JsonNode) {
        when (action) {
            "StartTransaction" -> {
                if (payload.path("connectorId").intValue() == 0) {
                    throw OcppFault("PropertyConstraintViolation", "Transaction requires a physical connector")
                }
            }
            "StatusNotification" -> {
                enumeration(payload, "status", statuses)
                enumeration(payload, "errorCode", errors)
            }
            "Reset" -> {
                enumeration(payload, "type", setOf("Hard", "Soft"))
            }
            "ChangeAvailability" -> {
                enumeration(payload, "type", setOf("Operative", "Inoperative"))
            }
            "MeterValues" -> {
                meterValues(payload.path("meterValue"))
            }
            "StopTransaction" -> {
                if (payload.has("transactionData")) {
                    meterValues(payload.path("transactionData"))
                }
            }
            "DiagnosticsStatusNotification" -> {
                enumeration(payload, "status", setOf("Idle", "Uploaded", "UploadFailed", "Uploading"))
            }
            "FirmwareStatusNotification" -> {
                enumeration(
                    payload,
                    "status",
                    setOf("Downloaded", "DownloadFailed", "Downloading", "Idle", "InstallationFailed", "Installing", "Installed")
                )
            }
        }
    }

    private fun timestamp(value: JsonNode, field: String) {
        try {
            Instant.parse(value.asText())
        } catch (_: Exception) {
            throw OcppFault("PropertyConstraintViolation", "Invalid $field")
        }
    }

    private fun enumeration(payload: JsonNode, field: String, values: Set<String>) {
        if (payload.path(field).asText() !in values) {
            throw OcppFault("PropertyConstraintViolation", "Invalid $field")
        }
    }

    private fun meterValues(values: JsonNode) {
        if (!values.isArray || values.size() !in 1..1000) {
            throw OcppFault("PropertyConstraintViolation", "Invalid meter values")
        }

        values.forEach { meter ->
            timestamp(meter.path("timestamp"), "meter timestamp")
            val samples = meter.path("sampledValue")

            if (!samples.isArray || samples.isEmpty) {
                throw OcppFault("OccurrenceConstraintViolation", "Missing sampledValue")
            }

            samples.forEach { sample ->
                if (!sample.path("value").isString) {
                    throw OcppFault("TypeConstraintViolation", "Sample value must be a string")
                }
            }
        }
    }
}
