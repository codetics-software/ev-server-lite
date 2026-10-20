import type { AuthorizationStatus } from "@/lib/api-types";

export type ChargingSession = {
  id: number;
  station_id: string;
  connector_id: number;
  started_at: string;
  stopped_at: string | null;
  energy_kwh: number | null;
  authorization_status: AuthorizationStatus;
  stop_reason: string | null;
  token_uid?: string | null;
  user_id?: string | null;
  user_name?: string | null;
  site_area_id?: string | null;
  site_id?: string | null;
  duration_seconds?: number | null;
  inactivity_seconds?: number | null;
  current_power_kw?: number | null;
  state_of_charge_start?: number | null;
  state_of_charge?: number | null;
  price?: number | null;
  currency?: string | null;
  pricing_definition_id?: string | null;
  billing_status?: "NONE" | "PENDING" | "INVOICED" | "PAID" | "FAILED" | null;
  invoice_id?: string | null;
  roaming_partner?: string | null;
  public_payment?: boolean;
  auto_charge?: boolean;
  vid?: string | null;
  errors?: string[];
};

export type SessionInError = { session: ChargingSession; errors: string[] };

export type Consumption = {
  at: string;
  energy_kwh: number | null;
  power_kw: number | null;
  soc: number | null;
  amperage_a: number | null;
  voltage_v: number | null;
  cumulated_price: number | null;
};

export function sessionDuration(session: ChargingSession) {
  if (session.duration_seconds != null) {
    return session.duration_seconds;
  }
  const end = session.stopped_at ? new Date(session.stopped_at) : new Date();
  return (end.valueOf() - new Date(session.started_at).valueOf()) / 1000;
}
