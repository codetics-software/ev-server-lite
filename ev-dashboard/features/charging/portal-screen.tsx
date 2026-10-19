"use client";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useApi } from "@/hooks/use-api";
import Link from "next/link";
import { useState } from "react";
import { CheckoutForm } from "./checkout-form";
import { usePreviousSession } from "./portal-access";
import { PortalLayout } from "./portal-layout";
import type { PublicEvse } from "./portal-types";
import { TariffDetails } from "./tariff-details";

export function PortalScreen({ workspace, evseId }: { workspace: string; evseId: string }) {
  const [revision, setRevision] = useState(0);
  const previous = usePreviousSession(evseId);
  const {
    data: evse,
    error,
    loading,
  } = useApi<PublicEvse>(
    `public/evses/${encodeURIComponent(workspace)}/${encodeURIComponent(evseId)}`,
    revision,
  );
  return (
    <PortalLayout>
      {error ? (
        <Alert variant="destructive">
          <AlertTitle>Point de charge indisponible</AlertTitle>
          <AlertDescription>{error.message}</AlertDescription>
        </Alert>
      ) : !evse ? (
        <Skeleton className="h-96" />
      ) : (
        <>
          <div className="page-heading">
            <div>
              <p>{evse.workspace}</p>
              <h1>{evse.name}</h1>
              <p>
                Borne {evse.stationId} · Connecteur {evse.connectorId}
              </p>
            </div>
          </div>
          <section className="rounded-xl border bg-background p-5 sm:p-7">
            <h2 className="text-lg font-semibold">Votre recharge</h2>
            <TariffDetails tariff={evse.tariff} />
            <p className="mb-6 mt-2 text-xs text-muted-foreground">
              Tarifs appliqués au début de la session. Le montant final est calculé à partir des
              mesures de la borne.
            </p>
            {evse.available ? (
              <CheckoutForm evse={evse} />
            ) : (
              <Alert>
                <AlertTitle>
                  {evse.connected ? "Recharge momentanément indisponible" : "Borne hors ligne"}
                </AlertTitle>
                <AlertDescription>
                  Ce connecteur ne peut pas recevoir de nouvelle session pour le moment.
                </AlertDescription>
              </Alert>
            )}
          </section>
          {previous && (
            <Button
              nativeButton={false}
              variant="outline"
              render={<Link href={`/charge/${workspace}/${evseId}/sessions/${previous}`} />}
            >
              Retrouver ma session
            </Button>
          )}
        </>
      )}
      <Button variant="ghost" disabled={loading} onClick={() => setRevision((value) => value + 1)}>
        Actualiser la disponibilité
      </Button>
    </PortalLayout>
  );
}
