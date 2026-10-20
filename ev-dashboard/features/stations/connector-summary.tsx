import { ConnectorStatusBadge } from "@/features/shared/status-badge";
import { duration, kw, kwh, percent } from "@/lib/format";
import type { StationConnector } from "./station-types";

export function inactivitySeconds(connector: StationConnector) {
  return connector.inactive_since
    ? Math.max(0, (Date.now() - new Date(connector.inactive_since).valueOf()) / 1000)
    : null;
}

export function ConnectorSummary({ connectors }: { connectors: StationConnector[] }) {
  if (!connectors.length) {
    return <span className="text-muted-foreground">Aucun connecteur</span>;
  }
  return (
    <ul className="flex flex-col gap-1.5">
      {connectors.map((connector) => {
        const charging = connector.current_transaction_id != null;
        const inactive = inactivitySeconds(connector);
        return (
          <li key={connector.connector_id} className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <span className="w-5 text-xs tabular-nums text-muted-foreground">
              {connector.connector_id}
            </span>
            <ConnectorStatusBadge status={connector.status} />
            {charging && (
              <span className="text-xs tabular-nums text-muted-foreground">
                {[
                  kw(connector.current_power_kw),
                  kwh(connector.current_energy_kwh),
                  connector.current_soc != null ? percent(connector.current_soc) : null,
                  inactive ? `inactif ${duration(inactive)}` : null,
                ]
                  .filter(Boolean)
                  .join(" · ")}
              </span>
            )}
          </li>
        );
      })}
    </ul>
  );
}
