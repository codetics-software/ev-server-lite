export type VehicleId = {
  uid: string;
  vehicle_id: string;
  user_id: string | null;
  enabled: boolean;
  last_used_at: string | null;
};

export type AutoChargeCandidate = {
  transaction_id: number;
  station_id: string;
  connector_id: number;
  started_at: string;
  vid: string;
  vehicle_id: string | null;
};

export const VID_PATTERN = /^VID:([0-9A-Fa-f]{2}[:-]?){5}[0-9A-Fa-f]{2}$/i;

export function autoChargePath(carId: string, mine: boolean) {
  return `${mine ? "account/" : ""}cars/${carId}/autocharge`;
}
