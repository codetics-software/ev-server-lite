import { t } from "@/lib/i18n";

export type StationConnector = {
  connector_id: number;
  status: string;
  error_code: string | null;
  status_at: string | null;
  type?: string | null;
  maximum_power_kw?: number | null;
  phases?: number | null;
  current_power_kw?: number | null;
  current_transaction_id?: number | null;
  current_energy_kwh?: number | null;
  current_soc?: number | null;
  current_user_name?: string | null;
  inactive_since?: string | null;
};

export type Station = {
  id: string;
  name: string;
  vendor: string | null;
  model: string | null;
  firmware_version: string | null;
  enabled: boolean;
  connected: boolean;
  last_seen: string | null;
  last_boot?: string | null;
  ocpp_version?: string | null;
  site_area_id?: string | null;
  site_id?: string | null;
  public?: boolean;
  latitude?: number | null;
  longitude?: number | null;
  maximum_power_kw?: number | null;
  template_id?: string | null;
  exclude_from_smart_charging?: boolean;
  auto_charge?: boolean;
  firmware_status?: string | null;
  diagnostics_status?: string | null;
  connectors?: StationConnector[];
  security_profile?: number;
  requested_security_profile?: number | null;
  connection_security_profile?: number | null;
  pinned_certificate_fingerprint?: string | null;
  certificate_subject?: string | null;
  certificate_fingerprint?: string | null;
  certificate_expires_at?: string | null;
  password_rotation_pending?: boolean;
  heartbeat_interval?: number | null;
  data_transfer_vendors?: string[];
  log_status?: string | null;
};

export type StationInError = { station: Station; errors: string[] };

export type StationEvent = {
  id: string;
  type: string;
  connector_id: number | null;
  status: string | null;
  error_code: string | null;
  info: string | null;
  created_at: string;
};

export type StationParameter = {
  key: string;
  value: string | null;
  readonly: boolean;
  updated_at: string | null;
};

export type ChargingProfile = {
  id: string;
  station_id: string;
  connector_id: number;
  profile_id: number;
  purpose: string;
  stack_level: number;
  profile: OcppChargingProfile;
  created_at: string;
  command_state: string | null;
};

export type OcppChargingProfile = {
  chargingProfileId?: number;
  stackLevel?: number;
  chargingProfilePurpose?: string;
  chargingProfileKind?: string;
  recurrencyKind?: string;
  validFrom?: string;
  validTo?: string;
  chargingSchedule?: {
    duration?: number;
    startSchedule?: string;
    chargingRateUnit?: "W" | "A";
    chargingSchedulePeriod?: { startPeriod: number; limit: number; numberPhases?: number }[];
    minChargingRate?: number;
  };
};

export type RegistrationToken = {
  id: string;
  description: string;
  site_area_id: string | null;
  expires_at: string | null;
  revoked_at: string | null;
  created_at: string;
  ocpp_url: string;
  token?: string;
};

export type ProvisionedStation = { id: string; secret: string; message: string };

export function connectorTypes() {
  return [
    { value: "Type2", label: t("Type 2") },
    { value: "CCS", label: t("Combo CCS") },
    { value: "CHAdeMO", label: "CHAdeMO" },
    { value: "Type1", label: t("Type 1") },
    { value: "Type3", label: t("Type 3") },
    { value: "Domestic", label: t("Prise domestique") },
    { value: "Tesla", label: t("Tesla") },
    { value: "Cable", label: t("Câble attaché") },
    { value: "Other", label: t("Autre") },
  ];
}
