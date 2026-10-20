export type Partner = {
  id: string;
  name: string;
  country_code: string;
  party_id: string;
  local_role: "cpo" | "emsp";
  remote_url: string;
  registered: boolean;
  enabled: boolean;
  status?: "PENDING" | "REGISTERED" | "DISABLED" | "ERROR";
  last_error?: string | null;
  last_ping_at?: string | null;
  last_push_at?: string | null;
  last_pull_at?: string | null;
  modules?: string[];
};

export type JobResult = {
  id?: string;
  module: string;
  direction?: string;
  total: number;
  success: number;
  failure: number;
  started_at: string;
  finished_at: string | null;
  errors: string[];
};

export type OcpiObject = { path: string; data: unknown; last_updated: string | null };

export function pushModules(role: Partner["local_role"]) {
  return role === "cpo" ? ["locations", "statuses", "tariffs", "sessions", "cdrs"] : ["tokens"];
}

export function pullModules(role: Partner["local_role"]) {
  return role === "cpo" ? ["tokens"] : ["locations", "sessions", "cdrs", "tariffs"];
}

export function partnerStatus(partner: Partner) {
  return (
    partner.status ??
    (!partner.enabled ? "DISABLED" : partner.registered ? "REGISTERED" : "PENDING")
  );
}
