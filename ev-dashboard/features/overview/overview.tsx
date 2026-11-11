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
import { t } from "@/lib/i18n";

export function Overview() {
  const [revision, setRevision] = useState(0);
  const { data, error, loading } = useApi<NetworkOverview>("overview", revision);
  const offline = data ? data.stations.total - data.stations.online : 0;
  const statistics = useComponent("statistics");

  return (
    <>
      <div className="page-heading">
        <div>
          <h1>{t("Vue d’ensemble du réseau")}</h1>
          <p>
            {data
              ? offline
                ? t("{online} bornes connectées sur {total} · {offline} hors ligne.", {
                    online: data.stations.online,
                    total: data.stations.total,
                    offline,
                  })
                : t("{online} bornes connectées sur {total}.", {
                    online: data.stations.online,
                    total: data.stations.total,
                  })
              : t("Connexion des bornes, état des connecteurs et activité des sessions.")}
          </p>
        </div>
        <Button
          variant="outline"
          onClick={() => setRevision((value) => value + 1)}
          disabled={loading}
        >
          <IconRefresh data-icon="inline-start" />
          {t("Actualiser")}
        </Button>
      </div>
      {error && (
        <Alert variant="destructive">
          <AlertTitle>{t("Impossible d’actualiser la vue du réseau")}</AlertTitle>
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
                <h2 id="operations-heading">{t("Exploitation")}</h2>
                <Link className="operation-link" href="/stations">
                  <div>
                    <strong>{t("Bornes de recharge")}</strong>
                    <span>{t("Consultez les connexions et commandez vos bornes à distance.")}</span>
                  </div>
                  <IconArrowUpRight aria-hidden="true" />
                </Link>
                <Link className="operation-link" href="/transactions">
                  <div>
                    <strong>{t("Sessions de recharge")}</strong>
                    <span>{t("Retrouvez les horaires de recharge et l’énergie mesurée.")}</span>
                  </div>
                  <IconArrowUpRight aria-hidden="true" />
                </Link>
                <Link className="operation-link" href="/commands">
                  <div>
                    <strong>{t("Suivi des commandes")}</strong>
                    <span>{t("Suivez les envois, les réponses et les délais dépassés.")}</span>
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
