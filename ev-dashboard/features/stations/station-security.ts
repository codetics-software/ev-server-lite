import type { Tone } from "@/features/shared/status-badge";
import { t, tp, translated } from "@/lib/i18n";
import type { Station, StationEvent } from "./station-types";

export type SecurityProfile = 0 | 1 | 2 | 3;

export type SecurityCapabilities = {
  allow_unauthenticated: boolean;
  client_certificate_authority: boolean;
  client_certificate_header: boolean;
  trusted_proxies: boolean;
  certificate_signing: boolean;
  default_heartbeat_interval: number;
  ocpp_public_url?: string | null;
  iso15118?: Iso15118Capabilities;
};

export type CertificateSummary = { subject: string; expires_at: string; fingerprint: string };

export type Iso15118Capabilities = {
  enabled: boolean;
  certificates_directory?: string | null;
  v2g_roots?: CertificateSummary[];
  cpo_chain?: CertificateSummary[];
  mo_roots?: CertificateSummary[];
  contract_service?: boolean;
};

export const ocppInterfaces = [
  "Wired0",
  "Wired1",
  "Wired2",
  "Wired3",
  "Wireless0",
  "Wireless1",
  "Wireless2",
  "Wireless3",
].map((value) => ({ value, label: value }));

export type SecurityChange = { station: Station; command_id: string | null; message: string };

export const securityProfiles: Record<SecurityProfile, string> = translated({
  0: "Profil 0 · sans authentification",
  1: "Profil 1 · mot de passe",
  2: "Profil 2 · TLS et mot de passe",
  3: "Profil 3 · certificat client (TLS mutuel)",
});

export function profileLabel(profile: number | null | undefined) {
  return profile != null && profile in securityProfiles
    ? securityProfiles[profile as SecurityProfile]
    : t("Jamais connectée");
}

export function protectedKeyMessage() {
  return t("Ce paramètre de sécurité se modifie depuis la section Sécurité de la borne.");
}

export function templateKeyMessage() {
  return t("Ce paramètre de sécurité ne peut pas figurer dans un modèle.");
}

export function fingerprintMessage() {
  return t("Empreinte SHA-256 invalide (64 caractères hexadécimaux).");
}

export function isProtectedKey(key: string) {
  const normalized = key.trim().toLowerCase();
  return (
    normalized === "securityprofile" ||
    normalized === "authorizationkey" ||
    normalized.startsWith("securityctrlr/") ||
    normalized.startsWith("ocppcommctrlr/networkconfigurationpriority")
  );
}

export function normalizeFingerprint(value: string) {
  return value.replace(/[\s:]/g, "").toLowerCase();
}

export function isFingerprint(value: string) {
  return /^[0-9a-f]{64}$/.test(normalizeFingerprint(value));
}

export function presentedFingerprint(event: StationEvent) {
  if (event.type !== "CONNECTION_REJECTED" || !event.info) {
    return null;
  }
  const candidate = normalizeFingerprint(event.info.split(" / ").at(-1) ?? "");
  return isFingerprint(candidate) ? candidate : null;
}

export function certificateExpiry(expiresAt: string | null | undefined) {
  if (!expiresAt) {
    return null;
  }
  const time = new Date(expiresAt).valueOf();
  if (Number.isNaN(time)) {
    return null;
  }
  const days = Math.ceil((time - Date.now()) / 86400000);
  if (days <= 0) {
    return { tone: "danger" as Tone, label: t("Expiré") };
  }
  if (days <= 30) {
    return {
      tone: "warning" as Tone,
      label: tp(days, "Expire dans {count} jour", "Expire dans {count} jours"),
    };
  }
  return null;
}

export function ocppUrl(
  workspace: string | null | undefined,
  stationId: string,
  publicUrl?: string | null,
) {
  const path = `${encodeURIComponent(workspace ?? "{espace}")}/${encodeURIComponent(stationId)}`;
  if (publicUrl) {
    return `${publicUrl.replace(/\/+$/, "")}/${path}`;
  }
  if (typeof window === "undefined") {
    return "";
  }
  const scheme = window.location.protocol === "https:" ? "wss" : "ws";
  return `${scheme}://${window.location.host}/ocpp/${path}`;
}

export const criticalSecurityEvents = new Set([
  "TamperDetectionActivated",
  "SecurityLogWasCleared",
  "MemoryExhaustion",
  "InvalidFirmwareSignature",
  "InvalidFirmwareSigningCertificate",
  "InvalidCentralSystemCertificate",
  "InvalidChargePointCertificate",
  "FirmwareUpdated",
]);

const failedFirmware = new Set([
  "DownloadFailed",
  "InstallationFailed",
  "InstallVerificationFailed",
  "InvalidSignature",
  "InvalidCertificate",
  "RevokedCertificate",
]);

const failedLogs = new Set([
  "UploadFailure",
  "BadMessage",
  "PermissionDenied",
  "NotSupportedOperation",
]);

function failedOutcome(status: string) {
  return /(Rejected|NotSupported|TIMED_OUT|FAILED|CANCELLED)$/.test(status);
}

export function securityEventTone(event: StationEvent): Tone | null {
  const status = event.status ?? "";
  switch (event.type) {
    case "CONNECTION_REJECTED":
      return "danger";
    case "SECURITY":
      if (criticalSecurityEvents.has(status)) {
        return "danger";
      }
      if (failedOutcome(status)) {
        return "warning";
      }
      return /(Accepted|Applied|Rotated)$/.test(status) ? "success" : "neutral";
    case "CERTIFICATE":
      if (failedOutcome(status)) {
        return "danger";
      }
      return /(Accepted|Signed)$/.test(status) ? "success" : "neutral";
    case "LOG":
      if (failedLogs.has(status)) {
        return "danger";
      }
      return status === "Uploaded" ? "success" : "neutral";
    case "FIRMWARE":
      return failedFirmware.has(status) ? "danger" : null;
    case "DATA_TRANSFER":
      return status === "UnknownVendorId" ? "warning" : status === "Accepted" ? "success" : null;
    default:
      return null;
  }
}

export function stationFileName(stationId: string) {
  return stationId.replace(/[^A-Za-z0-9._-]/g, "_");
}
