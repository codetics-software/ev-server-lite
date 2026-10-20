import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import type { NetworkOverview } from "./overview-types";

const statusLabels: Record<string, string> = {
  Available: "Disponible",
  Preparing: "Préparation",
  Charging: "Recharge en cours",
  SuspendedEV: "Suspendu par le véhicule",
  SuspendedEVSE: "Suspendu par la borne",
  Finishing: "Fin de recharge",
  Reserved: "Réservé",
  Unavailable: "Indisponible",
  Faulted: "En défaut",
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
        <h2 id="connector-heading">État des connecteurs</h2>
        <span className="text-xs text-muted-foreground tabular-nums">{total} connecteurs</span>
      </div>
      {!total ? (
        <Empty>
          <EmptyHeader>
            <EmptyTitle>Aucun connecteur déclaré</EmptyTitle>
            <EmptyDescription>
              Ajoutez une borne et connectez-la pour recevoir l’état de ses connecteurs.
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
                <div
                  className={
                    status === "Faulted"
                      ? "h-full bg-destructive"
                      : status === "Charging"
                        ? "h-full bg-chart-3"
                        : status === "Available"
                          ? "h-full bg-primary"
                          : "h-full bg-muted-foreground/40"
                  }
                  style={{ width: `${(count / total) * 100}%` }}
                />
              </div>
              <span className="text-right tabular-nums">{count}</span>
            </div>
          ))}
        </div>
      )}
      <p className="text-xs text-muted-foreground">
        Dernier état reçu. La connexion de la borne est indiquée séparément.
      </p>
    </section>
  );
}
