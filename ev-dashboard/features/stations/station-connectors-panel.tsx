"use client";

import { Button } from "@/components/ui/button";
import { Card, CardAction, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { EmptyState } from "@/features/shared/states";
import { ConnectorStatusBadge } from "@/features/shared/status-badge";
import { display, duration, kw, kwh, number, relative } from "@/lib/format";
import { IconBoltOff, IconLockOpen, IconPlayerPlay, IconPlug } from "@tabler/icons-react";
import Link from "next/link";
import { inactivitySeconds } from "./connector-summary";
import type { ActionRequest } from "./station-actions";
import type { Station } from "./station-types";

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="text-base font-medium tabular-nums">{value}</dd>
    </div>
  );
}

export function StationConnectorsPanel({
  station,
  canOperate,
  onAction,
}: {
  station: Station;
  canOperate: boolean;
  onAction: (request: ActionRequest) => void;
}) {
  const connectors = station.connectors ?? [];
  if (!connectors.length) {
    return (
      <EmptyState
        icon={<IconPlug />}
        title="Aucun connecteur déclaré"
        description="Les connecteurs apparaissent lorsque la borne envoie leur état."
      />
    );
  }
  return (
    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      {connectors.map((connector) => {
        const charging = connector.current_transaction_id != null;
        const inactive = inactivitySeconds(connector);
        const id = String(connector.connector_id);
        return (
          <Card key={connector.connector_id} size="sm">
            <CardHeader>
              <CardTitle>Connecteur {connector.connector_id}</CardTitle>
              <CardAction>
                <ConnectorStatusBadge status={connector.status} />
              </CardAction>
              <p className="text-xs text-muted-foreground">
                {[
                  connector.type ? display(connector.type) : null,
                  connector.maximum_power_kw != null ? kw(connector.maximum_power_kw) : null,
                  connector.phases
                    ? `${connector.phases} phase${connector.phases > 1 ? "s" : ""}`
                    : null,
                ]
                  .filter(Boolean)
                  .join(" · ") || "Caractéristiques non renseignées"}
              </p>
            </CardHeader>
            <CardContent className="flex flex-col gap-4">
              {charging ? (
                <>
                  <dl className="grid grid-cols-2 gap-3">
                    <Metric label="Puissance" value={kw(connector.current_power_kw)} />
                    <Metric label="Énergie" value={kwh(connector.current_energy_kwh)} />
                    <Metric label="Inactivité" value={inactive ? duration(inactive) : "Aucune"} />
                    <Metric label="Conducteur" value={connector.current_user_name ?? "—"} />
                  </dl>
                  {connector.current_soc != null && (
                    <div className="flex flex-col gap-1.5">
                      <div className="flex justify-between text-xs text-muted-foreground">
                        <span>Batterie</span>
                        <span className="tabular-nums">{number(connector.current_soc)} %</span>
                      </div>
                      <Progress
                        value={connector.current_soc}
                        aria-label="Niveau de batterie du véhicule"
                      />
                    </div>
                  )}
                  <Link
                    href={`/transactions/${connector.current_transaction_id}`}
                    className="text-sm text-primary underline-offset-4 dark:text-sidebar-primary hover:underline"
                  >
                    Session n° {connector.current_transaction_id}
                  </Link>
                </>
              ) : (
                <p className="text-sm text-muted-foreground">
                  {connector.error_code && connector.error_code !== "NoError"
                    ? `Erreur : ${display(connector.error_code)}`
                    : "Aucune session en cours."}
                </p>
              )}
              <p className="text-xs text-muted-foreground">
                État reçu {relative(connector.status_at)}
              </p>
              {canOperate && (
                <div className="flex flex-wrap gap-2">
                  {charging ? (
                    <Button
                      size="sm"
                      variant="destructive"
                      onClick={() =>
                        onAction({
                          id: "stop",
                          initial: { transactionId: String(connector.current_transaction_id) },
                        })
                      }
                    >
                      <IconBoltOff data-icon="inline-start" />
                      Arrêter
                    </Button>
                  ) : (
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => onAction({ id: "start", initial: { connectorId: id } })}
                    >
                      <IconPlayerPlay data-icon="inline-start" />
                      Démarrer
                    </Button>
                  )}
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => onAction({ id: "unlock", initial: { connectorId: id } })}
                  >
                    <IconLockOpen data-icon="inline-start" />
                    Déverrouiller
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() =>
                      onAction({
                        id: "availability",
                        initial: {
                          connectorId: id,
                          type: connector.status === "Unavailable" ? "Operative" : "Inoperative",
                        },
                      })
                    }
                  >
                    {connector.status === "Unavailable"
                      ? "Remettre en service"
                      : "Mettre hors service"}
                  </Button>
                </div>
              )}
            </CardContent>
          </Card>
        );
      })}
    </div>
  );
}
