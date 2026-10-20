import { api } from "@/lib/api";
import type { Station } from "./station-types";

export async function assignStation(stationId: string, siteAreaId: string | null) {
  const path = `stations/${encodeURIComponent(stationId)}`;
  const station = await api<Station>(path);
  return api(path, {
    method: "PUT",
    body: JSON.stringify({
      name: station.name,
      siteAreaId,
      enabled: station.enabled,
      public: station.public ?? false,
      latitude: station.latitude ?? null,
      longitude: station.longitude ?? null,
      maximumPowerKw: station.maximum_power_kw ?? null,
      excludeFromSmartCharging: station.exclude_from_smart_charging ?? false,
      connectors: station.connectors?.map((connector) => ({
        connectorId: connector.connector_id,
        type: connector.type ?? null,
        maximumPowerKw: connector.maximum_power_kw ?? null,
        phases: connector.phases ?? null,
      })),
    }),
  });
}
