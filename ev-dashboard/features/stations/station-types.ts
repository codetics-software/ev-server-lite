export type StationSummary = {
  id: string;
  name: string;
  vendor: string | null;
  model: string | null;
  firmware_version: string | null;
  enabled: boolean;
  connected: boolean;
  last_seen: string | null;
};

export type StationConnector = {
  connector_id: number;
  status: string;
  error_code: string;
  status_at: string;
};

export type StationDetails = StationSummary & { connectors: StationConnector[] };
export type StationPage = { items: StationSummary[]; total: number };
