package fr.codetics.ev_server.ocpp

import tools.jackson.databind.JsonNode

class OcppFault(val code: String, override val message: String) : RuntimeException(message)

object OcppValidation {
    val inbound =
        setOf(
            "BootNotification",
            "Heartbeat",
            "Authorize",
            "StatusNotification",
            "MeterValues",
            "StartTransaction",
            "StopTransaction",
            "DataTransfer",
            "DiagnosticsStatusNotification",
            "FirmwareStatusNotification",
            "SecurityEventNotification",
            "SignCertificate",
            "LogStatusNotification",
            "SignedFirmwareStatusNotification",
        )
    val outbound =
        setOf(
            "CancelReservation",
            "ChangeAvailability",
            "ChangeConfiguration",
            "ClearCache",
            "ClearChargingProfile",
            "DataTransfer",
            "GetCompositeSchedule",
            "GetConfiguration",
            "GetDiagnostics",
            "GetLocalListVersion",
            "RemoteStartTransaction",
            "RemoteStopTransaction",
            "ReserveNow",
            "Reset",
            "SendLocalList",
            "SetChargingProfile",
            "TriggerMessage",
            "UnlockConnector",
            "UpdateFirmware",
            "ExtendedTriggerMessage",
            "CertificateSigned",
            "InstallCertificate",
            "DeleteCertificate",
            "GetInstalledCertificateIds",
            "GetLog",
            "SignedUpdateFirmware",
        )
    private val required =
        mapOf(
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
            "UpdateFirmware" to listOf("location", "retrieveDate"),
            "SecurityEventNotification" to listOf("type", "timestamp"),
            "SignCertificate" to listOf("csr"),
            "LogStatusNotification" to listOf("status"),
            "SignedFirmwareStatusNotification" to listOf("status"),
            "ExtendedTriggerMessage" to listOf("requestedMessage"),
            "CertificateSigned" to listOf("certificateChain"),
            "InstallCertificate" to listOf("certificateType", "certificate"),
            "DeleteCertificate" to listOf("certificateHashData"),
            "GetInstalledCertificateIds" to listOf("certificateType"),
            "GetLog" to listOf("logType", "requestId", "log"),
            "SignedUpdateFirmware" to listOf("requestId", "firmware"),
        )
    private val integers =
        setOf(
            "connectorId",
            "transactionId",
            "reservationId",
            "requestId",
            "duration",
            "listVersion",
            "retries",
            "retryInterval",
        )
    private val decimals = setOf("meterStart", "meterStop")
    private val dates = setOf("timestamp", "expiryDate", "retrieveDate", "startTime", "stopTime")
    private val statuses =
        setOf(
            "Available",
            "Preparing",
            "Charging",
            "SuspendedEV",
            "SuspendedEVSE",
            "Finishing",
            "Reserved",
            "Unavailable",
            "Faulted",
        )
    val errors =
        setOf(
            "ConnectorLockFailure",
            "EVCommunicationError",
            "GroundFailure",
            "HighTemperature",
            "InternalError",
            "LocalListConflict",
            "NoError",
            "OtherError",
            "OverCurrentFailure",
            "OverVoltage",
            "PowerMeterFailure",
            "PowerSwitchFailure",
            "ReaderFailure",
            "ResetFailure",
            "UnderVoltage",
            "WeakSignal",
        )
    private val logStatuses =
        setOf(
            "BadMessage",
            "Idle",
            "NotSupportedOperation",
            "PermissionDenied",
            "Uploaded",
            "UploadFailure",
            "Uploading",
        )
    private val signedFirmwareStatuses =
        setOf(
            "Downloaded",
            "DownloadFailed",
            "Downloading",
            "DownloadScheduled",
            "DownloadPaused",
            "Idle",
            "InstallationFailed",
            "Installing",
            "Installed",
            "InstallRebooting",
            "InstallScheduled",
            "InstallVerificationFailed",
            "InvalidSignature",
            "SignatureVerified",
        )
    private val extendedTriggers =
        setOf(
            "BootNotification",
            "LogStatusNotification",
            "FirmwareStatusNotification",
            "Heartbeat",
            "MeterValues",
            "SignChargePointCertificate",
            "StatusNotification",
        )
    private val rootCertificates = setOf("CentralSystemRootCertificate", "ManufacturerRootCertificate")
    private val textLimits =
        mapOf(
            "idTag" to 20,
            "parentIdTag" to 20,
            "chargePointVendor" to 50,
            "chargePointModel" to 50,
            "vendorId" to 255,
            "key" to 50,
            "value" to 500,
            "errorCode" to 50,
            "csr" to 10000,
            "certificateChain" to 20000,
            "certificate" to 10000,
            "techInfo" to 1000,
        )

    fun validate(action: String, payload: JsonNode, serverInitiated: Boolean = false) {
        val supported = if (serverInitiated) outbound else inbound

        if (action !in supported) {
            throw OcppFault("NotSupported", "Action non prise en charge.")
        }
        if (!payload.isObject) {
            throw OcppFault("TypeConstraintViolation", "Les paramètres doivent former un objet.")
        }

        required[action].orEmpty().forEach { key ->
            if (!payload.hasNonNull(key)) {
                throw OcppFault("OccurrenceConstraintViolation", "Paramètre manquant : $key")
            }
        }
        validatePrimitives(payload)
        validateAction(action, payload)
    }

    private fun validatePrimitives(payload: JsonNode) {
        payload.properties().forEach { (key, value) ->
            if (
                key in integers &&
                    (!value.isIntegralNumber || !value.canConvertToInt() || value.intValue() < 0)
            ) {
                throw OcppFault(
                    "TypeConstraintViolation",
                    "Le paramètre $key doit être un entier positif ou nul.",
                )
            }
            if (key in decimals && (!value.isNumber || value.decimalValue().signum() < 0)) {
                throw OcppFault(
                    "TypeConstraintViolation",
                    "Le paramètre $key doit être un nombre positif ou nul.",
                )
            }
            if (key in dates) {
                timestamp(value, key)
            }
        }

        textLimits.forEach { (key, maximum) ->
            payload.get(key)?.let { value ->
                val minimum = if (key == "value") 0 else 1

                if (!value.isString || value.asText().length !in minimum..maximum) {
                    throw OcppFault("PropertyConstraintViolation", "Paramètre invalide : $key")
                }
            }
        }
    }

    private fun validateAction(action: String, payload: JsonNode) {
        when (action) {
            "StartTransaction" -> {
                if (payload.path("connectorId").intValue() == 0) {
                    throw OcppFault(
                        "PropertyConstraintViolation",
                        "La session nécessite un connecteur physique.",
                    )
                }
            }
            "StatusNotification" -> {
                enumeration(payload, "status", statuses)
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
                enumeration(
                    payload,
                    "status",
                    setOf("Idle", "Uploaded", "UploadFailed", "Uploading"),
                )
            }
            "LogStatusNotification" -> {
                enumeration(payload, "status", logStatuses)
            }
            "SignedFirmwareStatusNotification" -> {
                enumeration(payload, "status", signedFirmwareStatuses)
            }
            "SecurityEventNotification" -> {
                if (!payload.path("type").isString || payload.path("type").asText().length !in 1..50) {
                    throw OcppFault("PropertyConstraintViolation", "Valeur invalide : type")
                }
            }
            "ExtendedTriggerMessage" -> {
                enumeration(payload, "requestedMessage", extendedTriggers)
            }
            "InstallCertificate" -> {
                enumeration(payload, "certificateType", rootCertificates)
            }
            "GetInstalledCertificateIds" -> {
                enumeration(payload, "certificateType", rootCertificates)
            }
            "GetLog" -> {
                enumeration(payload, "logType", setOf("DiagnosticsLog", "SecurityLog"))
                if (!payload.path("log").path("remoteLocation").isString) {
                    throw OcppFault("OccurrenceConstraintViolation", "Paramètre manquant : log.remoteLocation")
                }
            }
            "SignedUpdateFirmware" -> {
                val firmware = payload.path("firmware")
                listOf("location", "retrieveDateTime", "signingCertificate", "signature").forEach { key ->
                    if (!firmware.path(key).isString) {
                        throw OcppFault("OccurrenceConstraintViolation", "Paramètre manquant : firmware.$key")
                    }
                }
                timestamp(firmware.path("retrieveDateTime"), "firmware.retrieveDateTime")
            }
            "DeleteCertificate" -> {
                val hash = payload.path("certificateHashData")
                listOf("hashAlgorithm", "issuerNameHash", "issuerKeyHash", "serialNumber").forEach { key ->
                    if (!hash.path(key).isString) {
                        throw OcppFault("OccurrenceConstraintViolation", "Paramètre manquant : certificateHashData.$key")
                    }
                }
            }
            "FirmwareStatusNotification" -> {
                enumeration(
                    payload,
                    "status",
                    setOf(
                        "Downloaded",
                        "DownloadFailed",
                        "Downloading",
                        "Idle",
                        "InstallationFailed",
                        "Installing",
                        "Installed",
                    ),
                )
            }
        }
    }

    private fun timestamp(value: JsonNode, field: String) {
        try {
            require(value.isString)
            OcppTime.parse(value.asText())
        } catch (_: Exception) {
            throw OcppFault("PropertyConstraintViolation", "Valeur invalide : $field")
        }
    }

    private fun enumeration(payload: JsonNode, field: String, values: Set<String>) {
        if (payload.path(field).asText() !in values) {
            throw OcppFault("PropertyConstraintViolation", "Valeur invalide : $field")
        }
    }

    private fun meterValues(values: JsonNode) {
        if (!values.isArray || values.size() !in 1..1000) {
            throw OcppFault("PropertyConstraintViolation", "Mesures du compteur invalides.")
        }

        values.forEach { meter ->
            timestamp(meter.path("timestamp"), "date de la mesure")
            val samples = meter.path("sampledValue")

            if (!samples.isArray || samples.isEmpty) {
                throw OcppFault(
                    "OccurrenceConstraintViolation",
                    "Les valeurs de mesure sont manquantes.",
                )
            }

            samples.forEach { sample ->
                if (!sample.path("value").isString && !sample.path("value").isNumber) {
                    throw OcppFault(
                        "TypeConstraintViolation",
                        "La valeur de mesure doit être une chaîne de caractères.",
                    )
                }
            }
        }
    }
}
