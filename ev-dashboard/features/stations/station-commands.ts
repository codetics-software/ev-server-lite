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
} satisfies Record<string, object>;

export type CommandAction = keyof typeof commands;

export function isCommandAction(value: string): value is CommandAction {
  return Object.hasOwn(commands, value);
}
