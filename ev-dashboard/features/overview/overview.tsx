"use client";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useApi } from "@/hooks/use-api";
import { IconArrowUpRight, IconRefresh } from "@tabler/icons-react";
import Link from "next/link";
import { useState } from "react";
import { ConnectorAvailability } from "./connector-availability";
import { OverviewMetrics } from "./overview-metrics";
import type { NetworkOverview } from "./overview-types";

export function Overview() {
  const [revision, setRevision] = useState(0);
  const { data, error, loading } = useApi<NetworkOverview>("overview", revision);
  const offline = data ? data.stations.total - data.stations.online : 0;

  return (
    <>
      <div className="page-heading">
        <div>
          <h1>Network overview</h1>
          <p>
            {data
              ? `${data.stations.online} of ${data.stations.total} stations connected${offline ? ` · ${offline} offline` : ""}.`
              : "Station connections, connector states and session activity."}
          </p>
        </div>
        <Button
          variant="outline"
          onClick={() => setRevision((value) => value + 1)}
          disabled={loading}
        >
          <IconRefresh data-icon="inline-start" />
          Refresh
        </Button>
      </div>
      {error && (
        <Alert variant="destructive">
          <AlertTitle>Unable to refresh the network overview</AlertTitle>
          <AlertDescription>{error.message}</AlertDescription>
        </Alert>
      )}
      {!data ? (
        <Skeleton className="h-40 w-full" />
      ) : (
        <>
          <OverviewMetrics data={data} />
          <div className="grid items-start gap-6 xl:grid-cols-[1.6fr_1fr]">
            <ConnectorAvailability connectors={data.connectors} />
            <section className="operational-panel" aria-labelledby="operations-heading">
              <h2 id="operations-heading">Operations</h2>
              <Link className="operation-link" href="/stations">
                <div>
                  <strong>Charging stations</strong>
                  <span>Inspect connectivity and send station commands.</span>
                </div>
                <IconArrowUpRight aria-hidden="true" />
              </Link>
              <Link className="operation-link" href="/transactions">
                <div>
                  <strong>Charging sessions</strong>
                  <span>Review start and stop times and measured energy.</span>
                </div>
                <IconArrowUpRight aria-hidden="true" />
              </Link>
              <Link className="operation-link" href="/commands">
                <div>
                  <strong>Command activity</strong>
                  <span>Check delivery, responses and timeouts.</span>
                </div>
                <IconArrowUpRight aria-hidden="true" />
              </Link>
            </section>
          </div>
        </>
      )}
    </>
  );
}
