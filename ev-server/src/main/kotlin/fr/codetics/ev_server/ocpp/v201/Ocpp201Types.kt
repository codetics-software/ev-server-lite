package fr.codetics.ev_server.ocpp.v201

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import java.math.BigDecimal

class CustomData(@field:Size(max = 255) val vendorId: String) {
    private val extra: MutableMap<String, Any?> = linkedMapOf()

    @JsonAnyGetter fun extra(): Map<String, Any?> = extra

    @JsonAnySetter
    fun put(key: String, value: Any?) {
        extra[key] = value
    }
}

data class ACChargingParameters(
    val energyAmount: Int,
    val evMinCurrent: Int,
    val evMaxCurrent: Int,
    val evMaxVoltage: Int,
    val customData: CustomData? = null,
)

data class APN(
    @field:Size(max = 512) val apn: String,
    @field:Size(max = 20) val apnUserName: String? = null,
    @field:Size(max = 20) val apnPassword: String? = null,
    val simPin: Int? = null,
    @field:Size(max = 6) val preferredNetwork: String? = null,
    val useOnlyPreferredNetwork: Boolean? = null,
    val apnAuthentication: APNAuthenticationEnum,
    val customData: CustomData? = null,
)

data class AdditionalInfo(
    @field:Size(max = 36) val additionalIdToken: String,
    @field:Size(max = 50) val type: String,
    val customData: CustomData? = null,
)

data class AuthorizationData(
    @field:Valid val idToken: IdToken,
    @field:Valid val idTokenInfo: IdTokenInfo? = null,
    val customData: CustomData? = null,
)

data class CertificateHashDataChain(
    @field:Valid val certificateHashData: CertificateHashData,
    val certificateType: GetCertificateIdUseEnum,
    @field:Valid @field:Size(min = 1, max = 4) val childCertificateHashData: List<CertificateHashData>? = null,
    val customData: CustomData? = null,
)

data class CertificateHashData(
    val hashAlgorithm: HashAlgorithmEnum,
    @field:Size(max = 128) val issuerNameHash: String,
    @field:Size(max = 128) val issuerKeyHash: String,
    @field:Size(max = 40) val serialNumber: String,
    val customData: CustomData? = null,
)

data class ChargingLimit(
    val chargingLimitSource: ChargingLimitSourceEnum,
    val isGridCritical: Boolean? = null,
    val customData: CustomData? = null,
)

data class ChargingNeeds(
    @field:Valid val acChargingParameters: ACChargingParameters? = null,
    @field:Valid val dcChargingParameters: DCChargingParameters? = null,
    val requestedEnergyTransfer: EnergyTransferModeEnum,
    @field:DateTime val departureTime: String? = null,
    val customData: CustomData? = null,
)

data class ChargingProfileCriterion(
    val chargingProfilePurpose: ChargingProfilePurposeEnum? = null,
    val stackLevel: Int? = null,
    @field:Size(min = 1) val chargingProfileId: List<Int>? = null,
    @field:Size(min = 1, max = 4) val chargingLimitSource: List<ChargingLimitSourceEnum>? = null,
    val customData: CustomData? = null,
)

data class ChargingProfile(
    val id: Int,
    val stackLevel: Int,
    val chargingProfilePurpose: ChargingProfilePurposeEnum,
    val chargingProfileKind: ChargingProfileKindEnum,
    val recurrencyKind: RecurrencyKindEnum? = null,
    @field:DateTime val validFrom: String? = null,
    @field:DateTime val validTo: String? = null,
    @field:Valid @field:Size(min = 1, max = 3) val chargingSchedule: List<ChargingSchedule>,
    @field:Size(max = 36) val transactionId: String? = null,
    val customData: CustomData? = null,
)

data class ChargingSchedulePeriod(
    val startPeriod: Int,
    val limit: BigDecimal,
    val numberPhases: Int? = null,
    val phaseToUse: Int? = null,
    val customData: CustomData? = null,
)

data class ChargingSchedule(
    val id: Int,
    @field:DateTime val startSchedule: String? = null,
    val duration: Int? = null,
    val chargingRateUnit: ChargingRateUnitEnum,
    @field:Valid @field:Size(min = 1, max = 1024) val chargingSchedulePeriod: List<ChargingSchedulePeriod>,
    val minChargingRate: BigDecimal? = null,
    @field:Valid val salesTariff: SalesTariff? = null,
    val customData: CustomData? = null,
)

data class ChargingStation(
    @field:Size(max = 25) val serialNumber: String? = null,
    @field:Size(max = 20) val model: String,
    @field:Valid val modem: Modem? = null,
    @field:Size(max = 50) val vendorName: String,
    @field:Size(max = 50) val firmwareVersion: String? = null,
    val customData: CustomData? = null,
)

data class ClearChargingProfile(
    val evseId: Int? = null,
    val chargingProfilePurpose: ChargingProfilePurposeEnum? = null,
    val stackLevel: Int? = null,
    val customData: CustomData? = null,
)

data class ClearMonitoringResult(
    val status: ClearMonitoringStatusEnum,
    val id: Int,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class Component(
    @field:Valid val evse: EVSE? = null,
    @field:Size(max = 50) val name: String,
    @field:Size(max = 50) val instance: String? = null,
    val customData: CustomData? = null,
)

data class ComponentVariable(
    @field:Valid val component: Component,
    @field:Valid val variable: Variable? = null,
    val customData: CustomData? = null,
)

data class CompositeSchedule(
    @field:Valid @field:Size(min = 1) val chargingSchedulePeriod: List<ChargingSchedulePeriod>,
    val evseId: Int,
    val duration: Int,
    @field:DateTime val scheduleStart: String,
    val chargingRateUnit: ChargingRateUnitEnum,
    val customData: CustomData? = null,
)

data class ConsumptionCost(
    val startValue: BigDecimal,
    @field:Valid @field:Size(min = 1, max = 3) val cost: List<Cost>,
    val customData: CustomData? = null,
)

data class Cost(
    val costKind: CostKindEnum,
    val amount: Int,
    val amountMultiplier: Int? = null,
    val customData: CustomData? = null,
)

data class DCChargingParameters(
    val evMaxCurrent: Int,
    val evMaxVoltage: Int,
    val energyAmount: Int? = null,
    val evMaxPower: Int? = null,
    @field:Min(0) @field:Max(100) val stateOfCharge: Int? = null,
    val evEnergyCapacity: Int? = null,
    @field:Min(0) @field:Max(100) val fullSoC: Int? = null,
    @field:Min(0) @field:Max(100) val bulkSoC: Int? = null,
    val customData: CustomData? = null,
)

data class EVSE(
    val id: Int,
    val connectorId: Int? = null,
    val customData: CustomData? = null,
)

data class EventData(
    val eventId: Int,
    @field:DateTime val timestamp: String,
    val trigger: EventTriggerEnum,
    val cause: Int? = null,
    @field:Size(max = 2500) val actualValue: String,
    @field:Size(max = 50) val techCode: String? = null,
    @field:Size(max = 500) val techInfo: String? = null,
    val cleared: Boolean? = null,
    @field:Size(max = 36) val transactionId: String? = null,
    @field:Valid val component: Component,
    val variableMonitoringId: Int? = null,
    val eventNotificationType: EventNotificationEnum,
    @field:Valid val variable: Variable,
    val customData: CustomData? = null,
)

data class Firmware(
    @field:Size(max = 512) val location: String,
    @field:DateTime val retrieveDateTime: String,
    @field:DateTime val installDateTime: String? = null,
    @field:Size(max = 5500) val signingCertificate: String? = null,
    @field:Size(max = 800) val signature: String? = null,
    val customData: CustomData? = null,
)

data class GetVariableData(
    val attributeType: AttributeEnum? = null,
    @field:Valid val component: Component,
    @field:Valid val variable: Variable,
    val customData: CustomData? = null,
)

data class GetVariableResult(
    @field:Valid val attributeStatusInfo: StatusInfo? = null,
    val attributeStatus: GetVariableStatusEnum,
    val attributeType: AttributeEnum? = null,
    @field:Size(max = 2500) val attributeValue: String? = null,
    @field:Valid val component: Component,
    @field:Valid val variable: Variable,
    val customData: CustomData? = null,
)

data class IdTokenInfo(
    val status: AuthorizationStatusEnum,
    @field:DateTime val cacheExpiryDateTime: String? = null,
    val chargingPriority: Int? = null,
    @field:Size(max = 8) val language1: String? = null,
    @field:Size(min = 1) val evseId: List<Int>? = null,
    @field:Valid val groupIdToken: IdToken? = null,
    @field:Size(max = 8) val language2: String? = null,
    @field:Valid val personalMessage: MessageContent? = null,
    val customData: CustomData? = null,
)

data class IdToken(
    @field:Valid @field:Size(min = 1) val additionalInfo: List<AdditionalInfo>? = null,
    @field:Size(max = 36) val idToken: String,
    val type: IdTokenEnum,
    val customData: CustomData? = null,
)

data class LogParameters(
    @field:Size(max = 512) val remoteLocation: String,
    @field:DateTime val oldestTimestamp: String? = null,
    @field:DateTime val latestTimestamp: String? = null,
    val customData: CustomData? = null,
)

data class MessageContent(
    val format: MessageFormatEnum,
    @field:Size(max = 8) val language: String? = null,
    @field:Size(max = 512) val content: String,
    val customData: CustomData? = null,
)

data class MessageInfo(
    @field:Valid val display: Component? = null,
    val id: Int,
    val priority: MessagePriorityEnum,
    val state: MessageStateEnum? = null,
    @field:DateTime val startDateTime: String? = null,
    @field:DateTime val endDateTime: String? = null,
    @field:Size(max = 36) val transactionId: String? = null,
    @field:Valid val message: MessageContent,
    val customData: CustomData? = null,
)

data class MeterValue(
    @field:Valid @field:Size(min = 1) val sampledValue: List<SampledValue>,
    @field:DateTime val timestamp: String,
    val customData: CustomData? = null,
)

data class Modem(
    @field:Size(max = 20) val iccid: String? = null,
    @field:Size(max = 20) val imsi: String? = null,
    val customData: CustomData? = null,
)

data class MonitoringData(
    @field:Valid val component: Component,
    @field:Valid val variable: Variable,
    @field:Valid @field:Size(min = 1) val variableMonitoring: List<VariableMonitoring>,
    val customData: CustomData? = null,
)

data class NetworkConnectionProfile(
    @field:Valid val apn: APN? = null,
    val ocppVersion: OCPPVersionEnum,
    val ocppTransport: OCPPTransportEnum,
    @field:Size(max = 512) val ocppCsmsUrl: String,
    val messageTimeout: Int,
    val securityProfile: Int,
    val ocppInterface: OCPPInterfaceEnum,
    @field:Valid val vpn: VPN? = null,
    val customData: CustomData? = null,
)

data class OCSPRequestData(
    val hashAlgorithm: HashAlgorithmEnum,
    @field:Size(max = 128) val issuerNameHash: String,
    @field:Size(max = 128) val issuerKeyHash: String,
    @field:Size(max = 40) val serialNumber: String,
    @field:Size(max = 512) val responderURL: String,
    val customData: CustomData? = null,
)

data class RelativeTimeInterval(
    val start: Int,
    val duration: Int? = null,
    val customData: CustomData? = null,
)

data class ReportData(
    @field:Valid val component: Component,
    @field:Valid val variable: Variable,
    @field:Valid @field:Size(min = 1, max = 4) val variableAttribute: List<VariableAttribute>,
    @field:Valid val variableCharacteristics: VariableCharacteristics? = null,
    val customData: CustomData? = null,
)

data class SalesTariffEntry(
    @field:Valid val relativeTimeInterval: RelativeTimeInterval,
    @field:Min(0) val ePriceLevel: Int? = null,
    @field:Valid @field:Size(min = 1, max = 3) val consumptionCost: List<ConsumptionCost>? = null,
    val customData: CustomData? = null,
)

data class SalesTariff(
    val id: Int,
    @field:Size(max = 32) val salesTariffDescription: String? = null,
    val numEPriceLevels: Int? = null,
    @field:Valid @field:Size(min = 1, max = 1024) val salesTariffEntry: List<SalesTariffEntry>,
    val customData: CustomData? = null,
)

data class SampledValue(
    val value: BigDecimal,
    val context: ReadingContextEnum? = null,
    val measurand: MeasurandEnum? = null,
    val phase: PhaseEnum? = null,
    val location: LocationEnum? = null,
    @field:Valid val signedMeterValue: SignedMeterValue? = null,
    @field:Valid val unitOfMeasure: UnitOfMeasure? = null,
    val customData: CustomData? = null,
)

data class SetMonitoringData(
    val id: Int? = null,
    val transaction: Boolean? = null,
    val value: BigDecimal,
    val type: MonitorEnum,
    val severity: Int,
    @field:Valid val component: Component,
    @field:Valid val variable: Variable,
    val customData: CustomData? = null,
)

data class SetMonitoringResult(
    val id: Int? = null,
    @field:Valid val statusInfo: StatusInfo? = null,
    val status: SetMonitoringStatusEnum,
    val type: MonitorEnum,
    @field:Valid val component: Component,
    @field:Valid val variable: Variable,
    val severity: Int,
    val customData: CustomData? = null,
)

data class SetVariableData(
    val attributeType: AttributeEnum? = null,
    @field:Size(max = 1000) val attributeValue: String,
    @field:Valid val component: Component,
    @field:Valid val variable: Variable,
    val customData: CustomData? = null,
)

data class SetVariableResult(
    val attributeType: AttributeEnum? = null,
    val attributeStatus: SetVariableStatusEnum,
    @field:Valid val attributeStatusInfo: StatusInfo? = null,
    @field:Valid val component: Component,
    @field:Valid val variable: Variable,
    val customData: CustomData? = null,
)

data class SignedMeterValue(
    @field:Size(max = 2500) val signedMeterData: String,
    @field:Size(max = 50) val signingMethod: String,
    @field:Size(max = 50) val encodingMethod: String,
    @field:Size(max = 2500) val publicKey: String,
    val customData: CustomData? = null,
)

data class StatusInfo(
    @field:Size(max = 20) val reasonCode: String,
    @field:Size(max = 512) val additionalInfo: String? = null,
    val customData: CustomData? = null,
)

data class Transaction(
    @field:Size(max = 36) val transactionId: String,
    val chargingState: ChargingStateEnum? = null,
    val timeSpentCharging: Int? = null,
    val stoppedReason: ReasonEnum? = null,
    val remoteStartId: Int? = null,
    val customData: CustomData? = null,
)

data class UnitOfMeasure(
    @field:Size(max = 20) val unit: String? = null,
    val multiplier: Int? = null,
    val customData: CustomData? = null,
)

data class VPN(
    @field:Size(max = 512) val server: String,
    @field:Size(max = 20) val user: String,
    @field:Size(max = 20) val group: String? = null,
    @field:Size(max = 20) val password: String,
    @field:Size(max = 255) val key: String,
    val type: VPNEnum,
    val customData: CustomData? = null,
)

data class VariableAttribute(
    val type: AttributeEnum? = null,
    @field:Size(max = 2500) val value: String? = null,
    val mutability: MutabilityEnum? = null,
    val persistent: Boolean? = null,
    val constant: Boolean? = null,
    val customData: CustomData? = null,
)

data class VariableCharacteristics(
    @field:Size(max = 16) val unit: String? = null,
    val dataType: DataEnum,
    val minLimit: BigDecimal? = null,
    val maxLimit: BigDecimal? = null,
    @field:Size(max = 1000) val valuesList: String? = null,
    val supportsMonitoring: Boolean,
    val customData: CustomData? = null,
)

data class VariableMonitoring(
    val id: Int,
    val transaction: Boolean,
    val value: BigDecimal,
    val type: MonitorEnum,
    val severity: Int,
    val customData: CustomData? = null,
)

data class Variable(
    @field:Size(max = 50) val name: String,
    @field:Size(max = 50) val instance: String? = null,
    val customData: CustomData? = null,
)
