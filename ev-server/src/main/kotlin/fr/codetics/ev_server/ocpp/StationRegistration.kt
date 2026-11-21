package fr.codetics.ev_server.ocpp

object StationRegistration {
    const val ACCEPTED = "Accepted"
    const val PENDING = "Pending"
    const val REJECTED = "Rejected"

    val POLICIES = setOf(ACCEPTED, PENDING, REJECTED)

    private val PENDING_COMMANDS =
        setOf(
            "GetVariables",
            "SetVariables",
            "GetBaseReport",
            "GetReport",
            "GetConfiguration",
            "ChangeConfiguration",
            "TriggerMessage",
            "ExtendedTriggerMessage",
            "Reset",
            "SetNetworkProfile",
            "InstallCertificate",
            "GetInstalledCertificateIds",
            "DeleteCertificate",
            "SetMonitoringBase",
            "SetMonitoringLevel",
            "SetVariableMonitoring",
            "ClearVariableMonitoring",
            "GetMonitoringReport",
        )

    private val PENDING_REFUSED_CALLS =
        setOf(
            "Authorize",
            "TransactionEvent",
            "StartTransaction",
            "StopTransaction",
            "MeterValues",
            "DataTransfer",
            "NotifyEVChargingNeeds",
            "NotifyEVChargingSchedule",
            "Get15118EVCertificate",
            "GetCertificateStatus",
            "ReservationStatusUpdate",
        )

    fun policy(value: String?): String = value?.takeIf { it in POLICIES } ?: ACCEPTED

    fun acceptsCall(status: String?, action: String): Boolean =
        when (status) {
            REJECTED -> action == "BootNotification"
            PENDING -> action !in PENDING_REFUSED_CALLS
            else -> true
        }

    fun allowsCommand(status: String?, action: String): Boolean =
        when (status) {
            REJECTED -> false
            PENDING -> action in PENDING_COMMANDS
            else -> true
        }
}
