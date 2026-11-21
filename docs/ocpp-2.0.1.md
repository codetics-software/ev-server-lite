# OCPP 2.0.1 support

EV Supervision Lite implements OCPP 2.0.1 over OCPP-J (JSON over WebSocket, subprotocol `ocpp2.0.1`) alongside OCPP 1.6. This page lists what the server does with each message. [Version française du guide principal](../README.fr.md).

## Connection

- Endpoint: `wss://<server>/ocpp/{workspace}/{station}`, HTTP Basic authentication (security profiles 1 and 2) or client certificate (profile 3).
- Each station is set to **automatic detection**, **OCPP 1.6** or **OCPP 2.0.1**. In automatic mode the server keeps the first version offered in `Sec-WebSocket-Protocol`.
- Authentication is checked first: bad credentials get HTTP 401. When the station is authenticated but offers no version the server accepts, the server completes the handshake without a `Sec-WebSocket-Protocol` header and closes the connection immediately (close code 1002), as OCPP-J requires.
- The server pings each connection every 30 seconds. A connection that has answered pings before and then stays silent for three intervals is closed, so that the station reconnects.

## Framing rules

- Message types 2 (CALL), 3 (CALLRESULT) and 4 (CALLERROR). Other types get `MessageTypeNotSupported`.
- A broken frame (not an array, wrong length, invalid or too-long message id) gets `RpcFrameworkError`. Unreadable JSON gets `RpcFrameworkError` with the message id `-1`.
- OCPP 1.6 stations get the OCPP 1.6 error codes, including the specification's own spelling `OccurenceConstraintViolation` and `FormationViolation`.
- Payload errors use `FormatViolation`, `TypeConstraintViolation`, `PropertyConstraintViolation` or `OccurrenceConstraintViolation`, following the official JSON schemas (required fields, lengths, enumerations, dates, `additionalProperties: false`).
- An unknown action gets `NotImplemented`. An action that only the server may send gets `NotSupported`.
- A malformed CALLRESULT or CALLERROR is logged and ignored: the server never answers a response with an error.
- Only one server CALL is outstanding per station. The response deadline starts when the call is sent, not when it is queued. Incoming calls are deduplicated by message id.
- Outgoing timestamps are UTC with at most three decimal places (`2026-03-01T10:00:00.123Z`).

## Registration (BootNotification)

Each station has a **boot response** setting in the dashboard: **Accepted** (default), **Pending** or **Rejected**. It applies to OCPP 1.6 and 2.0.1 stations.

- **Pending**: the station is answered `Pending`. The server only sends configuration and provisioning commands (`GetVariables`, `SetVariables`, `GetBaseReport`, `GetReport`, `TriggerMessage`, `Reset`, network profile, certificates, monitoring, and their OCPP 1.6 equivalents). Other commands, including remote start and stop, stay queued. Charging messages (`Authorize`, `TransactionEvent`, `MeterValues`, `DataTransfer`, ISO 15118 requests, reservations) get `SecurityError`. Switching the setting to **Accepted** sends `TriggerMessage(BootNotification)`, and the next boot is accepted.
- **Rejected**: the station is answered `Rejected`. The server sends no command, and every message other than `BootNotification` gets `SecurityError`.
- The `interval` of a `Pending` or `Rejected` answer is the delay before the next `BootNotification`.

## Messages sent by the station

| Action | Handling |
| --- | --- |
| `BootNotification` | Stores vendor, model and firmware, answers with the station's boot response (see above). Unless rejected, applies configuration templates; the first boot triggers `GetBaseReport`. |
| `Heartbeat` | Answers the current time and updates the last contact. |
| `StatusNotification` | Combines the status of every connector of an EVSE into the EVSE status shown in the dashboard and the apps. |
| `Authorize` | Checks badges, vehicle identifiers and roaming tokens. With ISO 15118, also checks the contract certificate and OCSP data. |
| `TransactionEvent` | Opens, updates and closes sessions, stores meter values (including signed data), links reservations and the badge. Supports authorize-first (no `evse` yet), plug-first (no badge yet) and free-vend (`NoAuthorization`) flows. Inconsistent events are logged instead of being rejected, so that stations do not drop data. |
| `MeterValues` | Stores meter values outside sessions. |
| `NotifyReport` | Stores the device model as station parameters named `Component/Variable`. |
| `NotifyEvent`, `NotifyMonitoringReport`, `NotifyDisplayMessages`, `NotifyCustomerInformation` | Recorded in the station event log. |
| `NotifyEVChargingNeeds` | Builds a `TxProfile` from the vehicle and station limits and sends it. |
| `NotifyEVChargingSchedule`, `NotifyChargingLimit`, `ClearedChargingLimit`, `ReportChargingProfiles` | Recorded in the event log. |
| `SecurityEventNotification` | Recorded, audited, and critical events notify administrators. |
| `SignCertificate` | Signs station certificates with the configured CA, including V2G certificates for ISO 15118. |
| `Get15118EVCertificate`, `GetCertificateStatus` | Forwarded to the contract certificate service and OCSP responder when ISO 15118 is enabled. |
| `FirmwareStatusNotification`, `PublishFirmwareStatusNotification`, `LogStatusNotification` | Update the firmware and log status of the station. |
| `ReservationStatusUpdate` | Updates the reservation. |
| `DataTransfer` | Accepted for configured vendor ids, `UnknownVendorId` otherwise. |

## Commands sent by the server

All 40 CSMS-to-station actions are available as raw commands to administrators in the dashboard (**Advanced** tab), except certificate and network profile commands, which go through the dedicated security screens.

Commands written in OCPP 1.6 form by the dashboard, the apps and the server are translated for OCPP 2.0.1 stations:

| OCPP 1.6 command | OCPP 2.0.1 message |
| --- | --- |
| `RemoteStartTransaction` / `RemoteStopTransaction` | `RequestStartTransaction` / `RequestStopTransaction` |
| `ChangeConfiguration` / `GetConfiguration` | `SetVariables` / `GetVariables` or `GetBaseReport` |
| `Reset` | `Reset` (`Hard` → `Immediate`, `Soft` → `OnIdle`) |
| `ChangeAvailability`, `UnlockConnector`, `ClearCache`, `DataTransfer` | Same name, 2.0.1 payload |
| `ReserveNow`, `CancelReservation` | Same name, 2.0.1 payload |
| `SetChargingProfile`, `ClearChargingProfile`, `GetCompositeSchedule` | Same name, 2.0.1 profiles |
| `SendLocalList`, `GetLocalListVersion` | Same name, 2.0.1 payload |
| `TriggerMessage`, `ExtendedTriggerMessage` | `TriggerMessage` |
| `GetDiagnostics`, `GetLog` | `GetLog` |
| `UpdateFirmware`, `SignedUpdateFirmware` | `UpdateFirmware` |
| `InstallCertificate`, `DeleteCertificate`, `GetInstalledCertificateIds`, `CertificateSigned` | Same name, 2.0.1 payload |

Responses are translated back, so the dashboard, the apps and the automations work the same way for both versions. Results of native `GetVariables` and `SetVariables` commands also update the station parameters.

## Optional features not implemented

These features are optional in OCPP 2.0.1 and are not part of the OCA certification profiles.

- `TransactionEventResponse` does not send `totalCost`, `chargingPriority` or `updatedPersonalMessage`.
- The dashboard edits display messages, monitoring and customer information only as raw commands.

## Tests

The OCPP 2.0.1 behaviour is covered by `Ocpp201CodecTests`, `Ocpp201MessageTests`, `Ocpp201RobustnessTests`, `Ocpp201WebSocketTests`, `Iso15118Tests` and `Iso15118CertificatesTests` in `ev-server/src/test/kotlin/fr/codetics/ev_server/ocpp/v201`.

---

EV Supervision Lite — built by Kevin Vavelin, Codetics · https://codetics.fr
