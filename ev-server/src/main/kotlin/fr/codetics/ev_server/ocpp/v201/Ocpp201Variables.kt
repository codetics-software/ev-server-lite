package fr.codetics.ev_server.ocpp.v201

data class DeviceVariable(val component: Component, val variable: Variable)

object Ocpp201Variables {
    const val BASIC_AUTH_PASSWORD = "SecurityCtrlr/BasicAuthPassword"
    const val SECURITY_PROFILE = "SecurityCtrlr/SecurityProfile"

    val aliases: Map<String, String> =
        mapOf(
            "HeartbeatInterval" to "OCPPCommCtrlr/HeartbeatInterval",
            "AuthorizationKey" to BASIC_AUTH_PASSWORD,
            "WebSocketPingInterval" to "OCPPCommCtrlr/WebSocketPingInterval",
            "ResetRetries" to "OCPPCommCtrlr/ResetRetries",
            "TransactionMessageAttempts" to "OCPPCommCtrlr/MessageAttempts:TransactionEvent",
            "TransactionMessageRetryInterval" to "OCPPCommCtrlr/MessageAttemptInterval:TransactionEvent",
            "UnlockConnectorOnEVSideDisconnect" to "OCPPCommCtrlr/UnlockOnEVSideDisconnect",
            "ConnectionTimeOut" to "TxCtrlr/EVConnectionTimeOut",
            "StopTransactionOnInvalidId" to "TxCtrlr/StopTxOnInvalidId",
            "StopTransactionOnEVSideDisconnect" to "TxCtrlr/StopTxOnEVSideDisconnect",
            "MeterValueSampleInterval" to "SampledDataCtrlr/TxUpdatedInterval",
            "MeterValuesSampledData" to "SampledDataCtrlr/TxUpdatedMeasurands",
            "StopTxnSampledData" to "SampledDataCtrlr/TxEndedMeasurands",
            "ClockAlignedDataInterval" to "AlignedDataCtrlr/Interval",
            "MeterValuesAlignedData" to "AlignedDataCtrlr/Measurands",
            "StopTxnAlignedData" to "AlignedDataCtrlr/TxEndedMeasurands",
            "LocalAuthorizeOffline" to "AuthCtrlr/LocalAuthorizeOffline",
            "LocalPreAuthorize" to "AuthCtrlr/LocalPreAuthorize",
            "AuthorizeRemoteTxRequests" to "AuthCtrlr/AuthorizeRemoteStart",
            "AllowOfflineTxForUnknownId" to "AuthCtrlr/OfflineTxForUnknownIdEnabled",
            "AuthorizationCacheEnabled" to "AuthCacheCtrlr/Enabled",
            "LocalAuthListEnabled" to "LocalAuthListCtrlr/Enabled",
        )

    private val pattern =
        Regex("([A-Za-z0-9_.*-]{1,50})(?::([^@/]{1,50}))?(?:@([0-9]{1,5})(?:\\.([0-9]{1,5}))?)?/([A-Za-z0-9_.*-]{1,50})(?::(.{1,50}))?")

    fun canonical(key: String): String = aliases[key] ?: key

    fun parse(key: String): DeviceVariable? {
        val match = pattern.matchEntire(canonical(key)) ?: return null
        val (component, componentInstance, evse, connector, variable, variableInstance) = match.destructured
        return DeviceVariable(
            Component(
                evse = evse.takeIf(String::isNotEmpty)?.let { EVSE(it.toInt(), connector.takeIf(String::isNotEmpty)?.toInt()) },
                name = component,
                instance = componentInstance.ifEmpty { null },
            ),
            Variable(variable, variableInstance.ifEmpty { null }),
        )
    }

    fun key(component: Component, variable: Variable): String =
        buildString {
            append(component.name)
            component.instance?.let { append(':').append(it) }
            component.evse?.let { evse ->
                append('@').append(evse.id)
                evse.connectorId?.let { append('.').append(it) }
            }
            append('/').append(variable.name)
            variable.instance?.let { append(':').append(it) }
        }

    fun protected(key: String): Boolean {
        val canonical = canonical(key)
        return canonical.equals(BASIC_AUTH_PASSWORD, true) ||
            canonical.equals(SECURITY_PROFILE, true) ||
            canonical.startsWith("SecurityCtrlr/", true) ||
            canonical.startsWith("OCPPCommCtrlr/NetworkConfigurationPriority", true)
    }
}
