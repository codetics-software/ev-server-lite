export const commands = {
  GetConfiguration: {},
  RemoteStartTransaction: { idTag: "", connectorId: 1 },
  RemoteStopTransaction: { transactionId: 1 },
  Reset: { type: "Soft" },
  ChangeAvailability: { connectorId: 0, type: "Operative" },
  ChangeConfiguration: { key: "", value: "" },
  ClearCache: {},
  UnlockConnector: { connectorId: 1 },
  GetDiagnostics: { location: "https://" },
  UpdateFirmware: {
    location: "https://",
    retrieveDate: new Date().toISOString(),
  },
  ReserveNow: {
    connectorId: 1,
    expiryDate: new Date(Date.now() + 3600000).toISOString(),
    idTag: "",
    reservationId: 1,
  },
  CancelReservation: { reservationId: 1 },
  GetCompositeSchedule: { connectorId: 1, duration: 3600 },
  SetChargingProfile: { connectorId: 0, csChargingProfiles: {} },
  ClearChargingProfile: {},
  GetLocalListVersion: {},
  SendLocalList: {
    listVersion: 1,
    updateType: "Full",
    localAuthorizationList: [],
  },
  TriggerMessage: { requestedMessage: "StatusNotification" },
  DataTransfer: { vendorId: "" },
  ExtendedTriggerMessage: { requestedMessage: "StatusNotification" },
  GetInstalledCertificateIds: { certificateType: "CentralSystemRootCertificate" },
  GetLog: {
    logType: "DiagnosticsLog",
    requestId: 1,
    log: { remoteLocation: "https://" },
    retries: 3,
  },
  SignedUpdateFirmware: {
    requestId: 1,
    firmware: {
      location: "https://",
      retrieveDateTime: new Date().toISOString(),
      signingCertificate: "-----BEGIN CERTIFICATE-----",
      signature: "",
    },
  },
} satisfies Record<string, object>;

export const commands201 = {
  GetBaseReport: { requestId: 1, reportBase: "FullInventory" },
  GetReport: { requestId: 1, componentCriteria: ["Problem"] },
  GetVariables: {
    getVariableData: [
      { component: { name: "OCPPCommCtrlr" }, variable: { name: "HeartbeatInterval" } },
    ],
  },
  SetVariables: {
    setVariableData: [
      {
        component: { name: "OCPPCommCtrlr" },
        variable: { name: "HeartbeatInterval" },
        attributeValue: "300",
      },
    ],
  },
  RequestStartTransaction: {
    evseId: 1,
    remoteStartId: 1,
    idToken: { idToken: "", type: "Central" },
  },
  RequestStopTransaction: { transactionId: "" },
  GetTransactionStatus: {},
  Reset: { type: "OnIdle" },
  ChangeAvailability: { evse: { id: 1 }, operationalStatus: "Operative" },
  UnlockConnector: { evseId: 1, connectorId: 1 },
  TriggerMessage: { requestedMessage: "StatusNotification" },
  ClearCache: {},
  GetLocalListVersion: {},
  SendLocalList: { versionNumber: 1, updateType: "Full" },
  ReserveNow: {
    id: 1,
    expiryDateTime: new Date(Date.now() + 3600000).toISOString(),
    idToken: { idToken: "", type: "Central" },
    evseId: 1,
  },
  CancelReservation: { reservationId: 1 },
  SetChargingProfile: { evseId: 0, chargingProfile: {} },
  GetChargingProfiles: {
    requestId: 1,
    chargingProfile: { chargingProfilePurpose: "TxDefaultProfile" },
  },
  ClearChargingProfile: {},
  GetCompositeSchedule: { evseId: 1, duration: 3600 },
  GetLog: { logType: "DiagnosticsLog", requestId: 1, log: { remoteLocation: "https://" } },
  UpdateFirmware: {
    requestId: 1,
    firmware: { location: "https://", retrieveDateTime: new Date().toISOString() },
  },
  PublishFirmware: { location: "https://", checksum: "", requestId: 1 },
  UnpublishFirmware: { checksum: "" },
  GetInstalledCertificateIds: {},
  SetDisplayMessage: {
    message: { id: 1, priority: "NormalCycle", message: { format: "UTF8", content: "" } },
  },
  GetDisplayMessages: { requestId: 1 },
  ClearDisplayMessage: { id: 1 },
  SetMonitoringBase: { monitoringBase: "All" },
  SetMonitoringLevel: { severity: 5 },
  GetMonitoringReport: { requestId: 1 },
  SetVariableMonitoring: {
    setMonitoringData: [
      {
        value: 1,
        type: "Delta",
        severity: 5,
        component: { name: "EVSE", evse: { id: 1 } },
        variable: { name: "Power" },
      },
    ],
  },
  ClearVariableMonitoring: { id: [1] },
  CustomerInformation: { requestId: 1, report: true, clear: false, customerIdentifier: "" },
  CostUpdated: { totalCost: 0, transactionId: "" },
  DataTransfer: { vendorId: "" },
} satisfies Record<string, object>;

export type CommandVersion = "1.6" | "2.0.1";

export function commandTemplates(version: CommandVersion): Record<string, object> {
  return version === "2.0.1" ? commands201 : commands;
}
