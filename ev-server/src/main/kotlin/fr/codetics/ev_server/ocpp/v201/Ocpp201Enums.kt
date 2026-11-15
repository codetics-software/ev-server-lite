package fr.codetics.ev_server.ocpp.v201

import com.fasterxml.jackson.annotation.JsonValue

enum class APNAuthenticationEnum { CHAP, NONE, PAP, AUTO }

enum class AttributeEnum { Actual, Target, MinSet, MaxSet }

enum class AuthorizationStatusEnum {
    Accepted,
    Blocked,
    ConcurrentTx,
    Expired,
    Invalid,
    NoCredit,
    NotAllowedTypeEVSE,
    NotAtThisLocation,
    NotAtThisTime,
    Unknown,
}

enum class AuthorizeCertificateStatusEnum {
    Accepted,
    SignatureError,
    CertificateExpired,
    CertificateRevoked,
    NoCertificateAvailable,
    CertChainError,
    ContractCancelled,
}

enum class BootReasonEnum {
    ApplicationReset,
    FirmwareUpdate,
    LocalReset,
    PowerUp,
    RemoteReset,
    ScheduledReset,
    Triggered,
    Unknown,
    Watchdog,
}

enum class CancelReservationStatusEnum { Accepted, Rejected }

enum class CertificateActionEnum { Install, Update }

enum class CertificateSignedStatusEnum { Accepted, Rejected }

enum class CertificateSigningUseEnum { ChargingStationCertificate, V2GCertificate }

enum class ChangeAvailabilityStatusEnum { Accepted, Rejected, Scheduled }

enum class ChargingLimitSourceEnum { EMS, Other, SO, CSO }

enum class ChargingProfileKindEnum { Absolute, Recurring, Relative }

enum class ChargingProfilePurposeEnum {
    ChargingStationExternalConstraints,
    ChargingStationMaxProfile,
    TxDefaultProfile,
    TxProfile,
}

enum class ChargingProfileStatusEnum { Accepted, Rejected }

enum class ChargingRateUnitEnum { W, A }

enum class ChargingStateEnum { Charging, EVConnected, SuspendedEV, SuspendedEVSE, Idle }

enum class ClearCacheStatusEnum { Accepted, Rejected }

enum class ClearChargingProfileStatusEnum { Accepted, Unknown }

enum class ClearMessageStatusEnum { Accepted, Unknown }

enum class ClearMonitoringStatusEnum { Accepted, Rejected, NotFound }

enum class ComponentCriterionEnum { Active, Available, Enabled, Problem }

enum class ConnectorEnum(@get:JsonValue val value: String) {
    CCCS1("cCCS1"),
    CCCS2("cCCS2"),
    CG105("cG105"),
    CTesla("cTesla"),
    CType1("cType1"),
    CType2("cType2"),
    S309_1P_16A("s309-1P-16A"),
    S309_1P_32A("s309-1P-32A"),
    S309_3P_16A("s309-3P-16A"),
    S309_3P_32A("s309-3P-32A"),
    SBS1361("sBS1361"),
    SCEE_7_7("sCEE-7-7"),
    SType2("sType2"),
    SType3("sType3"),
    Other1PhMax16A("Other1PhMax16A"),
    Other1PhOver16A("Other1PhOver16A"),
    Other3Ph("Other3Ph"),
    Pan("Pan"),
    WInductive("wInductive"),
    WResonant("wResonant"),
    Undetermined("Undetermined"),
    Unknown("Unknown"),
}

enum class ConnectorStatusEnum { Available, Occupied, Reserved, Unavailable, Faulted }

enum class CostKindEnum { CarbonDioxideEmission, RelativePricePercentage, RenewableGenerationPercentage }

enum class CustomerInformationStatusEnum { Accepted, Rejected, Invalid }

enum class DataEnum(@get:JsonValue val value: String) {
    STRING("string"),
    DECIMAL("decimal"),
    INTEGER("integer"),
    DATE_TIME("dateTime"),
    BOOLEAN("boolean"),
    OPTION_LIST("OptionList"),
    SEQUENCE_LIST("SequenceList"),
    MEMBER_LIST("MemberList"),
}

enum class DataTransferStatusEnum { Accepted, Rejected, UnknownMessageId, UnknownVendorId }

enum class DeleteCertificateStatusEnum { Accepted, Failed, NotFound }

enum class DisplayMessageStatusEnum {
    Accepted,
    NotSupportedMessageFormat,
    Rejected,
    NotSupportedPriority,
    NotSupportedState,
    UnknownTransaction,
}

enum class EnergyTransferModeEnum { DC, AC_single_phase, AC_two_phase, AC_three_phase }

enum class EventNotificationEnum { HardWiredNotification, HardWiredMonitor, PreconfiguredMonitor, CustomMonitor }

enum class EventTriggerEnum { Alerting, Delta, Periodic }

enum class FirmwareStatusEnum {
    Downloaded,
    DownloadFailed,
    Downloading,
    DownloadScheduled,
    DownloadPaused,
    Idle,
    InstallationFailed,
    Installing,
    Installed,
    InstallRebooting,
    InstallScheduled,
    InstallVerificationFailed,
    InvalidSignature,
    SignatureVerified,
}

enum class GenericDeviceModelStatusEnum { Accepted, Rejected, NotSupported, EmptyResultSet }

enum class GenericStatusEnum { Accepted, Rejected }

enum class GetCertificateIdUseEnum {
    V2GRootCertificate,
    MORootCertificate,
    CSMSRootCertificate,
    V2GCertificateChain,
    ManufacturerRootCertificate,
}

enum class GetCertificateStatusEnum { Accepted, Failed }

enum class GetChargingProfileStatusEnum { Accepted, NoProfiles }

enum class GetDisplayMessagesStatusEnum { Accepted, Unknown }

enum class GetInstalledCertificateStatusEnum { Accepted, NotFound }

enum class GetVariableStatusEnum { Accepted, Rejected, UnknownComponent, UnknownVariable, NotSupportedAttributeType }

enum class HashAlgorithmEnum { SHA256, SHA384, SHA512 }

enum class IdTokenEnum { Central, eMAID, ISO14443, ISO15693, KeyCode, Local, MacAddress, NoAuthorization }

enum class InstallCertificateStatusEnum { Accepted, Rejected, Failed }

enum class InstallCertificateUseEnum {
    V2GRootCertificate,
    MORootCertificate,
    CSMSRootCertificate,
    ManufacturerRootCertificate,
}

enum class Iso15118EVCertificateStatusEnum { Accepted, Failed }

enum class LocationEnum { Body, Cable, EV, Inlet, Outlet }

enum class LogEnum { DiagnosticsLog, SecurityLog }

enum class LogStatusEnum { Accepted, Rejected, AcceptedCanceled }

enum class MeasurandEnum(@get:JsonValue val value: String) {
    CURRENT_EXPORT("Current.Export"),
    CURRENT_IMPORT("Current.Import"),
    CURRENT_OFFERED("Current.Offered"),
    ENERGY_ACTIVE_EXPORT_REGISTER("Energy.Active.Export.Register"),
    ENERGY_ACTIVE_IMPORT_REGISTER("Energy.Active.Import.Register"),
    ENERGY_REACTIVE_EXPORT_REGISTER("Energy.Reactive.Export.Register"),
    ENERGY_REACTIVE_IMPORT_REGISTER("Energy.Reactive.Import.Register"),
    ENERGY_ACTIVE_EXPORT_INTERVAL("Energy.Active.Export.Interval"),
    ENERGY_ACTIVE_IMPORT_INTERVAL("Energy.Active.Import.Interval"),
    ENERGY_ACTIVE_NET("Energy.Active.Net"),
    ENERGY_REACTIVE_EXPORT_INTERVAL("Energy.Reactive.Export.Interval"),
    ENERGY_REACTIVE_IMPORT_INTERVAL("Energy.Reactive.Import.Interval"),
    ENERGY_REACTIVE_NET("Energy.Reactive.Net"),
    ENERGY_APPARENT_NET("Energy.Apparent.Net"),
    ENERGY_APPARENT_IMPORT("Energy.Apparent.Import"),
    ENERGY_APPARENT_EXPORT("Energy.Apparent.Export"),
    FREQUENCY("Frequency"),
    POWER_ACTIVE_EXPORT("Power.Active.Export"),
    POWER_ACTIVE_IMPORT("Power.Active.Import"),
    POWER_FACTOR("Power.Factor"),
    POWER_OFFERED("Power.Offered"),
    POWER_REACTIVE_EXPORT("Power.Reactive.Export"),
    POWER_REACTIVE_IMPORT("Power.Reactive.Import"),
    SOC("SoC"),
    VOLTAGE("Voltage"),
}

enum class MessageFormatEnum { ASCII, HTML, URI, UTF8 }

enum class MessagePriorityEnum { AlwaysFront, InFront, NormalCycle }

enum class MessageStateEnum { Charging, Faulted, Idle, Unavailable }

enum class MessageTriggerEnum {
    BootNotification,
    LogStatusNotification,
    FirmwareStatusNotification,
    Heartbeat,
    MeterValues,
    SignChargingStationCertificate,
    SignV2GCertificate,
    StatusNotification,
    TransactionEvent,
    SignCombinedCertificate,
    PublishFirmwareStatusNotification,
}

enum class MonitorEnum { UpperThreshold, LowerThreshold, Delta, Periodic, PeriodicClockAligned }

enum class MonitoringBaseEnum { All, FactoryDefault, HardWiredOnly }

enum class MonitoringCriterionEnum { ThresholdMonitoring, DeltaMonitoring, PeriodicMonitoring }

enum class MutabilityEnum { ReadOnly, WriteOnly, ReadWrite }

enum class NotifyEVChargingNeedsStatusEnum { Accepted, Rejected, Processing }

enum class OCPPInterfaceEnum { Wired0, Wired1, Wired2, Wired3, Wireless0, Wireless1, Wireless2, Wireless3 }

enum class OCPPTransportEnum { JSON, SOAP }

enum class OCPPVersionEnum { OCPP12, OCPP15, OCPP16, OCPP20 }

enum class OperationalStatusEnum { Inoperative, Operative }

enum class PhaseEnum(@get:JsonValue val value: String) {
    L1("L1"),
    L2("L2"),
    L3("L3"),
    N("N"),
    L1_N("L1-N"),
    L2_N("L2-N"),
    L3_N("L3-N"),
    L1_L2("L1-L2"),
    L2_L3("L2-L3"),
    L3_L1("L3-L1"),
}

enum class PublishFirmwareStatusEnum {
    Idle,
    DownloadScheduled,
    Downloading,
    Downloaded,
    Published,
    DownloadFailed,
    DownloadPaused,
    InvalidChecksum,
    ChecksumVerified,
    PublishFailed,
}

enum class ReadingContextEnum(@get:JsonValue val value: String) {
    INTERRUPTION_BEGIN("Interruption.Begin"),
    INTERRUPTION_END("Interruption.End"),
    OTHER("Other"),
    SAMPLE_CLOCK("Sample.Clock"),
    SAMPLE_PERIODIC("Sample.Periodic"),
    TRANSACTION_BEGIN("Transaction.Begin"),
    TRANSACTION_END("Transaction.End"),
    TRIGGER("Trigger"),
}

enum class ReasonEnum {
    DeAuthorized,
    EmergencyStop,
    EnergyLimitReached,
    EVDisconnected,
    GroundFault,
    ImmediateReset,
    Local,
    LocalOutOfCredit,
    MasterPass,
    Other,
    OvercurrentFault,
    PowerLoss,
    PowerQuality,
    Reboot,
    Remote,
    SOCLimitReached,
    StoppedByEV,
    TimeLimitReached,
    Timeout,
}

enum class RecurrencyKindEnum { Daily, Weekly }

enum class RegistrationStatusEnum { Accepted, Pending, Rejected }

enum class ReportBaseEnum { ConfigurationInventory, FullInventory, SummaryInventory }

enum class RequestStartStopStatusEnum { Accepted, Rejected }

enum class ReservationUpdateStatusEnum { Expired, Removed }

enum class ReserveNowStatusEnum { Accepted, Faulted, Occupied, Rejected, Unavailable }

enum class ResetEnum { Immediate, OnIdle }

enum class ResetStatusEnum { Accepted, Rejected, Scheduled }

enum class SendLocalListStatusEnum { Accepted, Failed, VersionMismatch }

enum class SetMonitoringStatusEnum { Accepted, UnknownComponent, UnknownVariable, UnsupportedMonitorType, Rejected, Duplicate }

enum class SetNetworkProfileStatusEnum { Accepted, Rejected, Failed }

enum class SetVariableStatusEnum {
    Accepted,
    Rejected,
    UnknownComponent,
    UnknownVariable,
    NotSupportedAttributeType,
    RebootRequired,
}

enum class TransactionEventEnum { Ended, Started, Updated }

enum class TriggerMessageStatusEnum { Accepted, Rejected, NotImplemented }

enum class TriggerReasonEnum {
    Authorized,
    CablePluggedIn,
    ChargingRateChanged,
    ChargingStateChanged,
    Deauthorized,
    EnergyLimitReached,
    EVCommunicationLost,
    EVConnectTimeout,
    MeterValueClock,
    MeterValuePeriodic,
    TimeLimitReached,
    Trigger,
    UnlockCommand,
    StopAuthorized,
    EVDeparted,
    EVDetected,
    RemoteStop,
    RemoteStart,
    AbnormalCondition,
    SignedDataReceived,
    ResetCommand,
}

enum class UnlockStatusEnum { Unlocked, UnlockFailed, OngoingAuthorizedTransaction, UnknownConnector }

enum class UnpublishFirmwareStatusEnum { DownloadOngoing, NoFirmware, Unpublished }

enum class UpdateEnum { Differential, Full }

enum class UpdateFirmwareStatusEnum { Accepted, Rejected, AcceptedCanceled, InvalidCertificate, RevokedCertificate }

enum class UploadLogStatusEnum {
    BadMessage,
    Idle,
    NotSupportedOperation,
    PermissionDenied,
    Uploaded,
    UploadFailure,
    Uploading,
    AcceptedCanceled,
}

enum class VPNEnum { IKEv2, IPSec, L2TP, PPTP }
