"use client";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useComponent } from "@/features/workspace/workspace-context";
import { useApi } from "@/hooks/use-api";
import { IconArrowUpRight, IconRefresh } from "@tabler/icons-react";
import Link from "next/link";
import { useState } from "react";
import { ConnectorAvailability } from "./connector-availability";
import { Anomalies, YearConsumption } from "./overview-insights";
import { OverviewMetrics } from "./overview-metrics";
import type { NetworkOverview } from "./overview-types";

export function Overview() {
  const [revision, setRevision] = useState(0);
  const { data, error, loading } = useApi<NetworkOverview>("overview", revision);
  const offline = data ? data.stations.total - data.stations.online : 0;
  const statistics = useComponent("statistics");

  return (
    <>
      <div className="page-heading">
        <div>
          <h1>Vue d’ensemble du réseau</h1>
          <p>
            {data
              ? `${data.stations.online} bornes connectées sur ${data.stations.total}${offline ? ` · ${offline} hors ligne` : ""}.`
              : "Connexion des bornes, état des connecteurs et activité des sessions."}
          </p>
        </div>
        <Button
          variant="outline"
          onClick={() => setRevision((value) => value + 1)}
          disabled={loading}
        >
          <IconRefresh data-icon="inline-start" />
          Actualiser
        </Button>
      </div>
      {error && (
        <Alert variant="destructive">
          <AlertTitle>Impossible d’actualiser la vue du réseau</AlertTitle>
          <AlertDescription>{error.message}</AlertDescription>
        </Alert>
      )}
      {!data ? (
        <Skeleton className="h-40 w-full" />
      ) : (
        <>
          <OverviewMetrics data={data} />
          <div className="grid items-start gap-6 xl:grid-cols-[1.6fr_1fr]">
            <div className="flex min-w-0 flex-col gap-8">
              <ConnectorAvailability connectors={data.connectors} />
              {statistics && <YearConsumption revision={revision} />}
            </div>
            <div className="flex min-w-0 flex-col gap-8">
              <section className="operational-panel" aria-labelledby="operations-heading">
                <h2 id="operations-heading">Exploitation</h2>
                <Link className="operation-link" href="/stations">
                  <div>
                    <strong>Bornes de recharge</strong>
                    <span>Consultez les connexions et commandez vos bornes à distance.</span>
                  </div>
                  <IconArrowUpRight aria-hidden="true" />
                </Link>
                <Link className="operation-link" href="/transactions">
                  <div>
                    <strong>Sessions de recharge</strong>
                    <span>Retrouvez les horaires de recharge et l’énergie mesurée.</span>
                  </div>
                  <IconArrowUpRight aria-hidden="true" />
                </Link>
                <Link className="operation-link" href="/commands">
                  <div>
                    <strong>Suivi des commandes</strong>
                    <span>Suivez les envois, les réponses et les délais dépassés.</span>
                  </div>
                  <IconArrowUpRight aria-hidden="true" />
                </Link>
              </section>
              <Anomalies revision={revision} />
            </div>
          </div>
        </>
      )}
    </>
  );
}
