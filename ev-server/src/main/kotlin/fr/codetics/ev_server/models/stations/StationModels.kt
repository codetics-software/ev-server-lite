package fr.codetics.ev_server.models.stations

import fr.codetics.ev_server.core.msg
import fr.codetics.ev_server.models.commands.CommandState
import fr.codetics.ev_server.models.entities.ConnectorEntity
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationEventEntity
import fr.codetics.ev_server.models.entities.StationParameterEntity
import fr.codetics.ev_server.models.entities.TransactionEntity
import fr.codetics.ev_server.ocpp.v201.CertificateSummary
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

data class StationInput(
    val id: String,
    val name: String,
    val siteAreaId: UUID? = null,
    val ocppProtocol: String = "AUTO",
)

data class ConnectorInput(
    val connectorId: Int,
    val type: String? = null,
    val maximumPowerKw: BigDecimal? = null,
    val phases: Int? = null,
)

data class StationUpdate(
    val name: String,
    val siteAreaId: UUID? = null,
    val enabled: Boolean = true,
    val public: Boolean = false,
    val latitude: BigDecimal? = null,
    val longitude: BigDecimal? = null,
    val maximumPowerKw: BigDecimal? = null,
    val excludeFromSmartCharging: Boolean = false,
    val connectors: List<ConnectorInput>? = null,
    val ocppProtocol: String? = null,
    val bootPolicy: String? = null,
)

data class ParameterInput(val key: String, val value: String)

data class AutoChargeSetting(val enabled: Boolean)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class AutoChargeChange(val id: UUID?, val state: CommandState?)

data class PowerLimitInput(val connectorId: Int = 0, val limitKw: BigDecimal)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class StationResponse(
    val id: String,
    val name: String,
    val vendor: String?,
    val model: String?,
    val firmwareVersion: String?,
    val enabled: Boolean,
    val connected: Boolean,
    val lastSeen: Instant?,
    val connectors: List<ConnectorResponse>? = null,
    val lastBoot: Instant? = null,
    val ocppVersion: String = "1.6",
    val ocppProtocol: String = "1.6",
    val bootPolicy: String = "Accepted",
    val registrationStatus: String? = null,
    val siteAreaId: UUID? = null,
    val siteId: UUID? = null,
    val public: Boolean = false,
    val latitude: BigDecimal? = null,
    val longitude: BigDecimal? = null,
    val maximumPowerKw: BigDecimal? = null,
    val templateId: UUID? = null,
    val excludeFromSmartCharging: Boolean = false,
    val firmwareStatus: String? = null,
    val diagnosticsStatus: String? = null,
    val autoCharge: Boolean = false,
    val securityProfile: Int = 1,
    val requestedSecurityProfile: Int? = null,
    val connectionSecurityProfile: Int? = null,
    val pinnedCertificateFingerprint: String? = null,
    val certificateSubject: String? = null,
    val certificateFingerprint: String? = null,
    val certificateExpiresAt: Instant? = null,
    val passwordRotationPending: Boolean = false,
    val heartbeatInterval: Int? = null,
    val dataTransferVendors: List<String> = emptyList(),
    val logStatus: String? = null,
    val v2gCertificateSubject: String? = null,
    val v2gCertificateExpiresAt: Instant? = null,
) {
    companion object {
        fun from(
            station: StationEntity,
            connectors: List<ConnectorResponse>? = null,
            siteId: UUID? = null,
        ) =
            StationResponse(
                id = station.key.stationId,
                name = station.name,
                vendor = station.vendor,
                model = station.model,
                firmwareVersion = station.firmwareVersion,
                enabled = station.enabled,
                connected = station.connected,
                lastSeen = station.lastSeen,
                connectors = connectors,
                lastBoot = station.lastBoot,
                ocppVersion = station.ocppVersion,
                ocppProtocol = station.ocppProtocol,
                bootPolicy = station.bootPolicy,
                registrationStatus = station.registrationStatus,
                siteAreaId = station.siteAreaId,
                siteId = siteId,
                public = station.publicStation,
                latitude = station.latitude,
                longitude = station.longitude,
                maximumPowerKw = station.maximumPowerKw,
                templateId = station.templateId,
                excludeFromSmartCharging = station.excludeFromSmartCharging,
                firmwareStatus = station.firmwareStatus,
                diagnosticsStatus = station.diagnosticsStatus,
                autoCharge = station.autoCharge,
                securityProfile = station.securityProfile,
                requestedSecurityProfile = station.requestedSecurityProfile,
                connectionSecurityProfile = station.connectionSecurityProfile,
                pinnedCertificateFingerprint = station.pinnedCertificateFingerprint,
                certificateSubject = station.certificateSubject,
                certificateFingerprint = station.certificateFingerprint,
                certificateExpiresAt = station.certificateExpiresAt,
                passwordRotationPending = station.pendingSecretHash != null,
                heartbeatInterval = station.heartbeatInterval,
                dataTransferVendors =
                    station.dataTransferVendors.split(',').map(String::trim).filter(String::isNotBlank),
                logStatus = station.logStatus,
                v2gCertificateSubject = station.v2gCertificateSubject,
                v2gCertificateExpiresAt = station.v2gCertificateExpiresAt,
            )
    }
}

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class ConnectorResponse(
    val connectorId: Int,
    val status: String,
    val errorCode: String,
    val statusAt: Instant,
    val type: String? = null,
    val maximumPowerKw: BigDecimal? = null,
    val phases: Int? = null,
    val currentPowerKw: BigDecimal? = null,
    val currentTransactionId: Int? = null,
    val currentEnergyKwh: BigDecimal? = null,
    val currentSoc: Int? = null,
    val currentUserName: String? = null,
    val inactiveSince: Instant? = null,
    val evseId: String? = null,
) {
    companion object {
        fun from(
            connector: ConnectorEntity,
            transaction: TransactionEntity? = null,
            userName: String? = null,
        ) =
            ConnectorResponse(
                connectorId = connector.key.connectorId,
                status = connector.status,
                errorCode = connector.errorCode,
                statusAt = connector.statusAt,
                type = connector.type,
                maximumPowerKw = connector.maximumPowerKw,
                phases = connector.phases,
                currentPowerKw =
                    transaction?.let { connector.currentPowerW?.divide(BigDecimal(1000)) },
                currentTransactionId = transaction?.id,
                currentEnergyKwh =
                    transaction?.let { active ->
                        active.lastEnergyWh
                            ?.subtract(active.meterStartWh)
                            ?.max(BigDecimal.ZERO)
                            ?.divide(BigDecimal(1000), 3, RoundingMode.HALF_UP)
                    },
                currentSoc = transaction?.let { connector.currentSoc },
                currentUserName = userName,
                inactiveSince = transaction?.chargingEndedAt,
            )
    }
}

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class StationInError(val station: StationResponse, val errors: List<String>)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class StationEventResponse(
    val id: Long?,
    val type: String,
    val connectorId: Int?,
    val status: String?,
    val errorCode: String?,
    val info: String?,
    val createdAt: Instant,
) {
    companion object {
        fun from(event: StationEventEntity) =
            StationEventResponse(
                event.id,
                event.type,
                event.connectorId,
                event.status,
                event.errorCode,
                event.info,
                event.createdAt,
            )
    }
}

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class StationParameterResponse(
    val key: String,
    val value: String?,
    val readonly: Boolean,
    val updatedAt: Instant,
) {
    companion object {
        fun from(parameter: StationParameterEntity) =
            StationParameterResponse(
                parameter.key.key,
                parameter.value,
                parameter.readonly,
                parameter.updatedAt,
            )
    }
}

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class TemplateApplication(val templateId: UUID?, val queuedCommands: Int)

data class ProvisionedStation(
    val id: String,
    val secret: String,
    val message: String =
        msg("stations.provisioned.message"),
)

data class SecurityProfileInput(
    val securityProfile: Int,
    val pinnedCertificateFingerprint: String? = null,
    val sendToStation: Boolean = true,
    val configurationSlot: Int? = null,
    val ocppInterface: String? = null,
)

data class OcppSettingsInput(
    val heartbeatInterval: Int? = null,
    val dataTransferVendors: List<String> = emptyList(),
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SecurityChange(
    val station: StationResponse,
    val commandId: UUID? = null,
    val message: String,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class OcppSecurityCapabilities(
    val allowUnauthenticated: Boolean,
    val clientCertificateAuthority: Boolean,
    val clientCertificateHeader: Boolean,
    val trustedProxies: Boolean,
    val certificateSigning: Boolean,
    val defaultHeartbeatInterval: Int,
    val ocppPublicUrl: String?,
    val iso15118: Iso15118Capabilities = Iso15118Capabilities(),
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class Iso15118Capabilities(
    val enabled: Boolean = false,
    val certificatesDirectory: String? = null,
    val v2gRoots: List<CertificateSummary> = emptyList(),
    val cpoChain: List<CertificateSummary> = emptyList(),
    val moRoots: List<CertificateSummary> = emptyList(),
    val contractService: Boolean = false,
)
