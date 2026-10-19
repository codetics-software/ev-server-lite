import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import type { NetworkOverview } from "./overview-types";

const statusLabels: Record<string, string> = {
  Available: "Available",
  Preparing: "Preparing",
  Charging: "Charging",
  SuspendedEV: "Paused by vehicle",
  SuspendedEVSE: "Paused by station",
  Finishing: "Finishing",
  Reserved: "Reserved",
  Unavailable: "Unavailable",
  Faulted: "Faulted",
};

export function ConnectorAvailability({
  connectors,
}: {
  connectors: NetworkOverview["connectors"];
}) {
  const total = connectors.reduce((sum, connector) => sum + connector.count, 0);

  return (
    <section className="operational-panel" aria-labelledby="connector-heading">
      <div className="flex items-baseline justify-between gap-3">
        <h2 id="connector-heading">Connector status</h2>
        <span className="text-xs text-muted-foreground tabular-nums">{total} connectors</span>
      </div>
      {!total ? (
        <Empty>
          <EmptyHeader>
            <EmptyTitle>No connector reports yet</EmptyTitle>
            <EmptyDescription>
              Provision a station and connect it to receive its connector states.
            </EmptyDescription>
          </EmptyHeader>
        </Empty>
      ) : (
        <div className="flex flex-col gap-5 py-2">
          {connectors.map(({ status, count }) => (
            <div
              key={status}
              className="grid grid-cols-[minmax(8rem,1fr)_2fr_2rem] items-center gap-4 text-sm"
            >
              <span>{statusLabels[status] ?? status}</span>
              <div className="h-2 overflow-hidden rounded-sm bg-muted" aria-hidden="true">
                <div className="h-full bg-primary" style={{ width: `${(count / total) * 100}%` }} />
              </div>
              <span className="text-right tabular-nums">{count}</span>
            </div>
          ))}
        </div>
      )}
      <p className="text-xs text-muted-foreground">
        Last reported state. A connector state is independent of the station’s connection.
      </p>
    </section>
  );
}
