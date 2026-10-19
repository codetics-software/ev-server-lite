export type ChargingSession = {
  id: number;
  station_id: string;
  connector_id: number;
  started_at: string;
  stopped_at: string | null;
  energy_kwh: number | null;
  authorization_status: string;
  stop_reason: string | null;
};

export type SessionPage = { items: ChargingSession[]; total: number };
