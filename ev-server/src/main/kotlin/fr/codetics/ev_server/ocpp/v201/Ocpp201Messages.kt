package fr.codetics.ev_server.ocpp.v201

import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import kotlin.reflect.KClass
import tools.jackson.databind.JsonNode

data class AuthorizeRequest(
    @field:Valid val idToken: IdToken,
    @field:Size(max = 5500) val certificate: String? = null,
    @field:Valid @field:Size(min = 1, max = 4) val iso15118CertificateHashData: List<OCSPRequestData>? = null,
    val customData: CustomData? = null,
)

data class AuthorizeResponse(
    @field:Valid val idTokenInfo: IdTokenInfo,
    val certificateStatus: AuthorizeCertificateStatusEnum? = null,
    val customData: CustomData? = null,
)

data class BootNotificationRequest(
    @field:Valid val chargingStation: ChargingStation,
    val reason: BootReasonEnum,
    val customData: CustomData? = null,
)

data class BootNotificationResponse(
    @field:DateTime val currentTime: String,
    val interval: Int,
    val status: RegistrationStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class CancelReservationRequest(val reservationId: Int, val customData: CustomData? = null)

data class CancelReservationResponse(
    val status: CancelReservationStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class CertificateSignedRequest(
    @field:Size(max = 10000) val certificateChain: String,
    val certificateType: CertificateSigningUseEnum? = null,
    val customData: CustomData? = null,
)

data class CertificateSignedResponse(
    val status: CertificateSignedStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class ChangeAvailabilityRequest(
    @field:Valid val evse: EVSE? = null,
    val operationalStatus: OperationalStatusEnum,
    val customData: CustomData? = null,
)

data class ChangeAvailabilityResponse(
    val status: ChangeAvailabilityStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class ClearCacheRequest(val customData: CustomData? = null)

data class ClearCacheResponse(
    val status: ClearCacheStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class ClearChargingProfileRequest(
    val chargingProfileId: Int? = null,
    @field:Valid val chargingProfileCriteria: ClearChargingProfile? = null,
    val customData: CustomData? = null,
)

data class ClearChargingProfileResponse(
    val status: ClearChargingProfileStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class ClearDisplayMessageRequest(val id: Int, val customData: CustomData? = null)

data class ClearDisplayMessageResponse(
    val status: ClearMessageStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class ClearVariableMonitoringRequest(
    @field:Size(min = 1) val id: List<Int>,
    val customData: CustomData? = null,
)

data class ClearVariableMonitoringResponse(
    @field:Valid @field:Size(min = 1) val clearMonitoringResult: List<ClearMonitoringResult>,
    val customData: CustomData? = null,
)

data class ClearedChargingLimitRequest(
    val chargingLimitSource: ChargingLimitSourceEnum,
    val evseId: Int? = null,
    val customData: CustomData? = null,
)

data class ClearedChargingLimitResponse(val customData: CustomData? = null)

data class CostUpdatedRequest(
    val totalCost: BigDecimal,
    @field:Size(max = 36) val transactionId: String,
    val customData: CustomData? = null,
)

data class CostUpdatedResponse(val customData: CustomData? = null)

data class CustomerInformationRequest(
    @field:Valid val customerCertificate: CertificateHashData? = null,
    @field:Valid val idToken: IdToken? = null,
    val requestId: Int,
    val report: Boolean,
    val clear: Boolean,
    @field:Size(max = 64) val customerIdentifier: String? = null,
    val customData: CustomData? = null,
)

data class CustomerInformationResponse(
    val status: CustomerInformationStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class DataTransferRequest(
    @field:Size(max = 50) val messageId: String? = null,
    val data: JsonNode? = null,
    @field:Size(max = 255) val vendorId: String,
    val customData: CustomData? = null,
)

data class DataTransferResponse(
    val status: DataTransferStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val data: JsonNode? = null,
    val customData: CustomData? = null,
)

data class DeleteCertificateRequest(
    @field:Valid val certificateHashData: CertificateHashData,
    val customData: CustomData? = null,
)

data class DeleteCertificateResponse(
    val status: DeleteCertificateStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class FirmwareStatusNotificationRequest(
    val status: FirmwareStatusEnum,
    val requestId: Int? = null,
    val customData: CustomData? = null,
)

data class FirmwareStatusNotificationResponse(val customData: CustomData? = null)

data class Get15118EVCertificateRequest(
    @field:Size(max = 50) val iso15118SchemaVersion: String,
    val action: CertificateActionEnum,
    @field:Size(max = 5600) val exiRequest: String,
    val customData: CustomData? = null,
)

data class Get15118EVCertificateResponse(
    val status: Iso15118EVCertificateStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    @field:Size(max = 5600) val exiResponse: String,
    val customData: CustomData? = null,
)

data class GetBaseReportRequest(
    val requestId: Int,
    val reportBase: ReportBaseEnum,
    val customData: CustomData? = null,
)

data class GetBaseReportResponse(
    val status: GenericDeviceModelStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class GetCertificateStatusRequest(
    @field:Valid val ocspRequestData: OCSPRequestData,
    val customData: CustomData? = null,
)

data class GetCertificateStatusResponse(
    val status: GetCertificateStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    @field:Size(max = 5500) val ocspResult: String? = null,
    val customData: CustomData? = null,
)

data class GetChargingProfilesRequest(
    val requestId: Int,
    val evseId: Int? = null,
    @field:Valid val chargingProfile: ChargingProfileCriterion,
    val customData: CustomData? = null,
)

data class GetChargingProfilesResponse(
    val status: GetChargingProfileStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class GetCompositeScheduleRequest(
    val duration: Int,
    val chargingRateUnit: ChargingRateUnitEnum? = null,
    val evseId: Int,
    val customData: CustomData? = null,
)

data class GetCompositeScheduleResponse(
    val status: GenericStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    @field:Valid val schedule: CompositeSchedule? = null,
    val customData: CustomData? = null,
)

data class GetDisplayMessagesRequest(
    @field:Size(min = 1) val id: List<Int>? = null,
    val requestId: Int,
    val priority: MessagePriorityEnum? = null,
    val state: MessageStateEnum? = null,
    val customData: CustomData? = null,
)

data class GetDisplayMessagesResponse(
    val status: GetDisplayMessagesStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class GetInstalledCertificateIdsRequest(
    @field:Size(min = 1) val certificateType: List<GetCertificateIdUseEnum>? = null,
    val customData: CustomData? = null,
)

data class GetInstalledCertificateIdsResponse(
    val status: GetInstalledCertificateStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    @field:Valid @field:Size(min = 1) val certificateHashDataChain: List<CertificateHashDataChain>? = null,
    val customData: CustomData? = null,
)

data class GetLocalListVersionRequest(val customData: CustomData? = null)

data class GetLocalListVersionResponse(val versionNumber: Int, val customData: CustomData? = null)

data class GetLogRequest(
    @field:Valid val log: LogParameters,
    val logType: LogEnum,
    val requestId: Int,
    val retries: Int? = null,
    val retryInterval: Int? = null,
    val customData: CustomData? = null,
)

data class GetLogResponse(
    val status: LogStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    @field:Size(max = 255) val filename: String? = null,
    val customData: CustomData? = null,
)

data class GetMonitoringReportRequest(
    @field:Valid @field:Size(min = 1) val componentVariable: List<ComponentVariable>? = null,
    val requestId: Int,
    @field:Size(min = 1, max = 3) val monitoringCriteria: List<MonitoringCriterionEnum>? = null,
    val customData: CustomData? = null,
)

data class GetMonitoringReportResponse(
    val status: GenericDeviceModelStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class GetReportRequest(
    @field:Valid @field:Size(min = 1) val componentVariable: List<ComponentVariable>? = null,
    val requestId: Int,
    @field:Size(min = 1, max = 4) val componentCriteria: List<ComponentCriterionEnum>? = null,
    val customData: CustomData? = null,
)

data class GetReportResponse(
    val status: GenericDeviceModelStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class GetTransactionStatusRequest(
    @field:Size(max = 36) val transactionId: String? = null,
    val customData: CustomData? = null,
)

data class GetTransactionStatusResponse(
    val ongoingIndicator: Boolean? = null,
    val messagesInQueue: Boolean,
    val customData: CustomData? = null,
)

data class GetVariablesRequest(
    @field:Valid @field:Size(min = 1) val getVariableData: List<GetVariableData>,
    val customData: CustomData? = null,
)

data class GetVariablesResponse(
    @field:Valid @field:Size(min = 1) val getVariableResult: List<GetVariableResult>,
    val customData: CustomData? = null,
)

data class HeartbeatRequest(val customData: CustomData? = null)

data class HeartbeatResponse(@field:DateTime val currentTime: String, val customData: CustomData? = null)

data class InstallCertificateRequest(
    val certificateType: InstallCertificateUseEnum,
    @field:Size(max = 5500) val certificate: String,
    val customData: CustomData? = null,
)

data class InstallCertificateResponse(
    val status: InstallCertificateStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class LogStatusNotificationRequest(
    val status: UploadLogStatusEnum,
    val requestId: Int? = null,
    val customData: CustomData? = null,
)

data class LogStatusNotificationResponse(val customData: CustomData? = null)

data class MeterValuesRequest(
    val evseId: Int,
    @field:Valid @field:Size(min = 1) val meterValue: List<MeterValue>,
    val customData: CustomData? = null,
)

data class MeterValuesResponse(val customData: CustomData? = null)

data class NotifyChargingLimitRequest(
    @field:Valid @field:Size(min = 1) val chargingSchedule: List<ChargingSchedule>? = null,
    val evseId: Int? = null,
    @field:Valid val chargingLimit: ChargingLimit,
    val customData: CustomData? = null,
)

data class NotifyChargingLimitResponse(val customData: CustomData? = null)

data class NotifyCustomerInformationRequest(
    @field:Size(max = 512) val data: String,
    val tbc: Boolean? = null,
    val seqNo: Int,
    @field:DateTime val generatedAt: String,
    val requestId: Int,
    val customData: CustomData? = null,
)

data class NotifyCustomerInformationResponse(val customData: CustomData? = null)

data class NotifyDisplayMessagesRequest(
    @field:Valid @field:Size(min = 1) val messageInfo: List<MessageInfo>? = null,
    val requestId: Int,
    val tbc: Boolean? = null,
    val customData: CustomData? = null,
)

data class NotifyDisplayMessagesResponse(val customData: CustomData? = null)

data class NotifyEVChargingNeedsRequest(
    val maxScheduleTuples: Int? = null,
    @field:Valid val chargingNeeds: ChargingNeeds,
    val evseId: Int,
    val customData: CustomData? = null,
)

data class NotifyEVChargingNeedsResponse(
    val status: NotifyEVChargingNeedsStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class NotifyEVChargingScheduleRequest(
    @field:DateTime val timeBase: String,
    @field:Valid val chargingSchedule: ChargingSchedule,
    val evseId: Int,
    val customData: CustomData? = null,
)

data class NotifyEVChargingScheduleResponse(
    val status: GenericStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class NotifyEventRequest(
    @field:DateTime val generatedAt: String,
    val tbc: Boolean? = null,
    val seqNo: Int,
    @field:Valid @field:Size(min = 1) val eventData: List<EventData>,
    val customData: CustomData? = null,
)

data class NotifyEventResponse(val customData: CustomData? = null)

data class NotifyMonitoringReportRequest(
    @field:Valid @field:Size(min = 1) val monitor: List<MonitoringData>? = null,
    val requestId: Int,
    val tbc: Boolean? = null,
    val seqNo: Int,
    @field:DateTime val generatedAt: String,
    val customData: CustomData? = null,
)

data class NotifyMonitoringReportResponse(val customData: CustomData? = null)

data class NotifyReportRequest(
    val requestId: Int,
    @field:DateTime val generatedAt: String,
    @field:Valid @field:Size(min = 1) val reportData: List<ReportData>? = null,
    val tbc: Boolean? = null,
    val seqNo: Int,
    val customData: CustomData? = null,
)

data class NotifyReportResponse(val customData: CustomData? = null)

data class PublishFirmwareRequest(
    @field:Size(max = 512) val location: String,
    val retries: Int? = null,
    @field:Size(max = 32) val checksum: String,
    val requestId: Int,
    val retryInterval: Int? = null,
    val customData: CustomData? = null,
)

data class PublishFirmwareResponse(
    val status: GenericStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class PublishFirmwareStatusNotificationRequest(
    val status: PublishFirmwareStatusEnum,
    @field:Size(min = 1) @field:EachMaxLength(512) val location: List<String>? = null,
    val requestId: Int? = null,
    val customData: CustomData? = null,
)

data class PublishFirmwareStatusNotificationResponse(val customData: CustomData? = null)

data class ReportChargingProfilesRequest(
    val requestId: Int,
    val chargingLimitSource: ChargingLimitSourceEnum,
    @field:Valid @field:Size(min = 1) val chargingProfile: List<ChargingProfile>,
    val tbc: Boolean? = null,
    val evseId: Int,
    val customData: CustomData? = null,
)

data class ReportChargingProfilesResponse(val customData: CustomData? = null)

data class RequestStartTransactionRequest(
    val evseId: Int? = null,
    @field:Valid val groupIdToken: IdToken? = null,
    @field:Valid val idToken: IdToken,
    val remoteStartId: Int,
    @field:Valid val chargingProfile: ChargingProfile? = null,
    val customData: CustomData? = null,
)

data class RequestStartTransactionResponse(
    val status: RequestStartStopStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    @field:Size(max = 36) val transactionId: String? = null,
    val customData: CustomData? = null,
)

data class RequestStopTransactionRequest(
    @field:Size(max = 36) val transactionId: String,
    val customData: CustomData? = null,
)

data class RequestStopTransactionResponse(
    val status: RequestStartStopStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class ReservationStatusUpdateRequest(
    val reservationId: Int,
    val reservationUpdateStatus: ReservationUpdateStatusEnum,
    val customData: CustomData? = null,
)

data class ReservationStatusUpdateResponse(val customData: CustomData? = null)

data class ReserveNowRequest(
    val id: Int,
    @field:DateTime val expiryDateTime: String,
    val connectorType: ConnectorEnum? = null,
    @field:Valid val idToken: IdToken,
    val evseId: Int? = null,
    @field:Valid val groupIdToken: IdToken? = null,
    val customData: CustomData? = null,
)

data class ReserveNowResponse(
    val status: ReserveNowStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class ResetRequest(val type: ResetEnum, val evseId: Int? = null, val customData: CustomData? = null)

data class ResetResponse(
    val status: ResetStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class SecurityEventNotificationRequest(
    @field:Size(max = 50) val type: String,
    @field:DateTime val timestamp: String,
    @field:Size(max = 255) val techInfo: String? = null,
    val customData: CustomData? = null,
)

data class SecurityEventNotificationResponse(val customData: CustomData? = null)

data class SendLocalListRequest(
    @field:Valid @field:Size(min = 1) val localAuthorizationList: List<AuthorizationData>? = null,
    val versionNumber: Int,
    val updateType: UpdateEnum,
    val customData: CustomData? = null,
)

data class SendLocalListResponse(
    val status: SendLocalListStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class SetChargingProfileRequest(
    val evseId: Int,
    @field:Valid val chargingProfile: ChargingProfile,
    val customData: CustomData? = null,
)

data class SetChargingProfileResponse(
    val status: ChargingProfileStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class SetDisplayMessageRequest(@field:Valid val message: MessageInfo, val customData: CustomData? = null)

data class SetDisplayMessageResponse(
    val status: DisplayMessageStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class SetMonitoringBaseRequest(val monitoringBase: MonitoringBaseEnum, val customData: CustomData? = null)

data class SetMonitoringBaseResponse(
    val status: GenericDeviceModelStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class SetMonitoringLevelRequest(val severity: Int, val customData: CustomData? = null)

data class SetMonitoringLevelResponse(
    val status: GenericStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class SetNetworkProfileRequest(
    val configurationSlot: Int,
    @field:Valid val connectionData: NetworkConnectionProfile,
    val customData: CustomData? = null,
)

data class SetNetworkProfileResponse(
    val status: SetNetworkProfileStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class SetVariableMonitoringRequest(
    @field:Valid @field:Size(min = 1) val setMonitoringData: List<SetMonitoringData>,
    val customData: CustomData? = null,
)

data class SetVariableMonitoringResponse(
    @field:Valid @field:Size(min = 1) val setMonitoringResult: List<SetMonitoringResult>,
    val customData: CustomData? = null,
)

data class SetVariablesRequest(
    @field:Valid @field:Size(min = 1) val setVariableData: List<SetVariableData>,
    val customData: CustomData? = null,
)

data class SetVariablesResponse(
    @field:Valid @field:Size(min = 1) val setVariableResult: List<SetVariableResult>,
    val customData: CustomData? = null,
)

data class SignCertificateRequest(
    @field:Size(max = 5500) val csr: String,
    val certificateType: CertificateSigningUseEnum? = null,
    val customData: CustomData? = null,
)

data class SignCertificateResponse(
    val status: GenericStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class StatusNotificationRequest(
    @field:DateTime val timestamp: String,
    val connectorStatus: ConnectorStatusEnum,
    val evseId: Int,
    val connectorId: Int,
    val customData: CustomData? = null,
)

data class StatusNotificationResponse(val customData: CustomData? = null)

data class TransactionEventRequest(
    val eventType: TransactionEventEnum,
    @field:Valid @field:Size(min = 1) val meterValue: List<MeterValue>? = null,
    @field:DateTime val timestamp: String,
    val triggerReason: TriggerReasonEnum,
    val seqNo: Int,
    val offline: Boolean? = null,
    val numberOfPhasesUsed: Int? = null,
    val cableMaxCurrent: Int? = null,
    val reservationId: Int? = null,
    @field:Valid val transactionInfo: Transaction,
    @field:Valid val evse: EVSE? = null,
    @field:Valid val idToken: IdToken? = null,
    val customData: CustomData? = null,
)

data class TransactionEventResponse(
    val totalCost: BigDecimal? = null,
    val chargingPriority: Int? = null,
    @field:Valid val idTokenInfo: IdTokenInfo? = null,
    @field:Valid val updatedPersonalMessage: MessageContent? = null,
    val customData: CustomData? = null,
)

data class TriggerMessageRequest(
    @field:Valid val evse: EVSE? = null,
    val requestedMessage: MessageTriggerEnum,
    val customData: CustomData? = null,
)

data class TriggerMessageResponse(
    val status: TriggerMessageStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class UnlockConnectorRequest(val evseId: Int, val connectorId: Int, val customData: CustomData? = null)

data class UnlockConnectorResponse(
    val status: UnlockStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class UnpublishFirmwareRequest(@field:Size(max = 32) val checksum: String, val customData: CustomData? = null)

data class UnpublishFirmwareResponse(val status: UnpublishFirmwareStatusEnum, val customData: CustomData? = null)

data class UpdateFirmwareRequest(
    val retries: Int? = null,
    val retryInterval: Int? = null,
    val requestId: Int,
    @field:Valid val firmware: Firmware,
    val customData: CustomData? = null,
)

data class UpdateFirmwareResponse(
    val status: UpdateFirmwareStatusEnum,
    @field:Valid val statusInfo: StatusInfo? = null,
    val customData: CustomData? = null,
)

data class Ocpp201Message(val request: KClass<*>, val response: KClass<*>)

object Ocpp201Messages {
    val fromStation: Map<String, Ocpp201Message> =
        mapOf(
            "Authorize" to Ocpp201Message(AuthorizeRequest::class, AuthorizeResponse::class),
            "BootNotification" to Ocpp201Message(BootNotificationRequest::class, BootNotificationResponse::class),
            "ClearedChargingLimit" to
                Ocpp201Message(ClearedChargingLimitRequest::class, ClearedChargingLimitResponse::class),
            "DataTransfer" to Ocpp201Message(DataTransferRequest::class, DataTransferResponse::class),
            "FirmwareStatusNotification" to
                Ocpp201Message(FirmwareStatusNotificationRequest::class, FirmwareStatusNotificationResponse::class),
            "Get15118EVCertificate" to
                Ocpp201Message(Get15118EVCertificateRequest::class, Get15118EVCertificateResponse::class),
            "GetCertificateStatus" to
                Ocpp201Message(GetCertificateStatusRequest::class, GetCertificateStatusResponse::class),
            "Heartbeat" to Ocpp201Message(HeartbeatRequest::class, HeartbeatResponse::class),
            "LogStatusNotification" to
                Ocpp201Message(LogStatusNotificationRequest::class, LogStatusNotificationResponse::class),
            "MeterValues" to Ocpp201Message(MeterValuesRequest::class, MeterValuesResponse::class),
            "NotifyChargingLimit" to
                Ocpp201Message(NotifyChargingLimitRequest::class, NotifyChargingLimitResponse::class),
            "NotifyCustomerInformation" to
                Ocpp201Message(NotifyCustomerInformationRequest::class, NotifyCustomerInformationResponse::class),
            "NotifyDisplayMessages" to
                Ocpp201Message(NotifyDisplayMessagesRequest::class, NotifyDisplayMessagesResponse::class),
            "NotifyEVChargingNeeds" to
                Ocpp201Message(NotifyEVChargingNeedsRequest::class, NotifyEVChargingNeedsResponse::class),
            "NotifyEVChargingSchedule" to
                Ocpp201Message(NotifyEVChargingScheduleRequest::class, NotifyEVChargingScheduleResponse::class),
            "NotifyEvent" to Ocpp201Message(NotifyEventRequest::class, NotifyEventResponse::class),
            "NotifyMonitoringReport" to
                Ocpp201Message(NotifyMonitoringReportRequest::class, NotifyMonitoringReportResponse::class),
            "NotifyReport" to Ocpp201Message(NotifyReportRequest::class, NotifyReportResponse::class),
            "PublishFirmwareStatusNotification" to
                Ocpp201Message(
                    PublishFirmwareStatusNotificationRequest::class,
                    PublishFirmwareStatusNotificationResponse::class,
                ),
            "ReportChargingProfiles" to
                Ocpp201Message(ReportChargingProfilesRequest::class, ReportChargingProfilesResponse::class),
            "ReservationStatusUpdate" to
                Ocpp201Message(ReservationStatusUpdateRequest::class, ReservationStatusUpdateResponse::class),
            "SecurityEventNotification" to
                Ocpp201Message(SecurityEventNotificationRequest::class, SecurityEventNotificationResponse::class),
            "SignCertificate" to Ocpp201Message(SignCertificateRequest::class, SignCertificateResponse::class),
            "StatusNotification" to
                Ocpp201Message(StatusNotificationRequest::class, StatusNotificationResponse::class),
            "TransactionEvent" to Ocpp201Message(TransactionEventRequest::class, TransactionEventResponse::class),
        )

    val toStation: Map<String, Ocpp201Message> =
        mapOf(
            "CancelReservation" to Ocpp201Message(CancelReservationRequest::class, CancelReservationResponse::class),
            "CertificateSigned" to Ocpp201Message(CertificateSignedRequest::class, CertificateSignedResponse::class),
            "ChangeAvailability" to
                Ocpp201Message(ChangeAvailabilityRequest::class, ChangeAvailabilityResponse::class),
            "ClearCache" to Ocpp201Message(ClearCacheRequest::class, ClearCacheResponse::class),
            "ClearChargingProfile" to
                Ocpp201Message(ClearChargingProfileRequest::class, ClearChargingProfileResponse::class),
            "ClearDisplayMessage" to
                Ocpp201Message(ClearDisplayMessageRequest::class, ClearDisplayMessageResponse::class),
            "ClearVariableMonitoring" to
                Ocpp201Message(ClearVariableMonitoringRequest::class, ClearVariableMonitoringResponse::class),
            "CostUpdated" to Ocpp201Message(CostUpdatedRequest::class, CostUpdatedResponse::class),
            "CustomerInformation" to
                Ocpp201Message(CustomerInformationRequest::class, CustomerInformationResponse::class),
            "DataTransfer" to Ocpp201Message(DataTransferRequest::class, DataTransferResponse::class),
            "DeleteCertificate" to Ocpp201Message(DeleteCertificateRequest::class, DeleteCertificateResponse::class),
            "GetBaseReport" to Ocpp201Message(GetBaseReportRequest::class, GetBaseReportResponse::class),
            "GetChargingProfiles" to
                Ocpp201Message(GetChargingProfilesRequest::class, GetChargingProfilesResponse::class),
            "GetCompositeSchedule" to
                Ocpp201Message(GetCompositeScheduleRequest::class, GetCompositeScheduleResponse::class),
            "GetDisplayMessages" to
                Ocpp201Message(GetDisplayMessagesRequest::class, GetDisplayMessagesResponse::class),
            "GetInstalledCertificateIds" to
                Ocpp201Message(GetInstalledCertificateIdsRequest::class, GetInstalledCertificateIdsResponse::class),
            "GetLocalListVersion" to
                Ocpp201Message(GetLocalListVersionRequest::class, GetLocalListVersionResponse::class),
            "GetLog" to Ocpp201Message(GetLogRequest::class, GetLogResponse::class),
            "GetMonitoringReport" to
                Ocpp201Message(GetMonitoringReportRequest::class, GetMonitoringReportResponse::class),
            "GetReport" to Ocpp201Message(GetReportRequest::class, GetReportResponse::class),
            "GetTransactionStatus" to
                Ocpp201Message(GetTransactionStatusRequest::class, GetTransactionStatusResponse::class),
            "GetVariables" to Ocpp201Message(GetVariablesRequest::class, GetVariablesResponse::class),
            "InstallCertificate" to
                Ocpp201Message(InstallCertificateRequest::class, InstallCertificateResponse::class),
            "PublishFirmware" to Ocpp201Message(PublishFirmwareRequest::class, PublishFirmwareResponse::class),
            "RequestStartTransaction" to
                Ocpp201Message(RequestStartTransactionRequest::class, RequestStartTransactionResponse::class),
            "RequestStopTransaction" to
                Ocpp201Message(RequestStopTransactionRequest::class, RequestStopTransactionResponse::class),
            "ReserveNow" to Ocpp201Message(ReserveNowRequest::class, ReserveNowResponse::class),
            "Reset" to Ocpp201Message(ResetRequest::class, ResetResponse::class),
            "SendLocalList" to Ocpp201Message(SendLocalListRequest::class, SendLocalListResponse::class),
            "SetChargingProfile" to
                Ocpp201Message(SetChargingProfileRequest::class, SetChargingProfileResponse::class),
            "SetDisplayMessage" to Ocpp201Message(SetDisplayMessageRequest::class, SetDisplayMessageResponse::class),
            "SetMonitoringBase" to Ocpp201Message(SetMonitoringBaseRequest::class, SetMonitoringBaseResponse::class),
            "SetMonitoringLevel" to
                Ocpp201Message(SetMonitoringLevelRequest::class, SetMonitoringLevelResponse::class),
            "SetNetworkProfile" to Ocpp201Message(SetNetworkProfileRequest::class, SetNetworkProfileResponse::class),
            "SetVariableMonitoring" to
                Ocpp201Message(SetVariableMonitoringRequest::class, SetVariableMonitoringResponse::class),
            "SetVariables" to Ocpp201Message(SetVariablesRequest::class, SetVariablesResponse::class),
            "TriggerMessage" to Ocpp201Message(TriggerMessageRequest::class, TriggerMessageResponse::class),
            "UnlockConnector" to Ocpp201Message(UnlockConnectorRequest::class, UnlockConnectorResponse::class),
            "UnpublishFirmware" to Ocpp201Message(UnpublishFirmwareRequest::class, UnpublishFirmwareResponse::class),
            "UpdateFirmware" to Ocpp201Message(UpdateFirmwareRequest::class, UpdateFirmwareResponse::class),
        )
}
