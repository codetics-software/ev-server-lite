"use client";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button, buttonVariants } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useApi } from "@/hooks/use-api";
import Link from "next/link";
import { useState } from "react";
import { CheckoutForm } from "./checkout-form";
import { usePreviousSession } from "./portal-access";
import { PortalLayout } from "./portal-layout";
import type { PublicEvse } from "./portal-types";
import { TariffDetails } from "./tariff-details";
import { t } from "@/lib/i18n";

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
    <PortalLayout workspace={workspace}>
      {error ? (
        <Alert variant="destructive">
          <AlertTitle>{t("Point de charge indisponible")}</AlertTitle>
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
                {t("Borne {station} · Connecteur {connector}", {
                  station: evse.stationId,
                  connector: evse.connectorId,
                })}
              </p>
            </div>
          </div>
          <section className="rounded-xl border bg-background p-5 sm:p-7">
            <h2 className="text-lg font-semibold">{t("Votre recharge")}</h2>
            <TariffDetails tariff={evse.tariff} />
            <p className="mb-6 mt-2 text-xs text-muted-foreground">
              {t(
                "Tarifs appliqués au début de la session. Le montant final est calculé à partir des mesures de la borne.",
              )}
            </p>
            {evse.available ? (
              <CheckoutForm evse={evse} />
            ) : (
              <Alert>
                <AlertTitle>
                  {evse.connected
                    ? t("Recharge momentanément indisponible")
                    : t("Borne hors ligne")}
                </AlertTitle>
                <AlertDescription>
                  {t("Ce connecteur ne peut pas recevoir de nouvelle session pour le moment.")}
                </AlertDescription>
              </Alert>
            )}
          </section>
          {previous && (
            <Link
              href={`/charge/${encodeURIComponent(workspace)}/${encodeURIComponent(evseId)}/sessions/${encodeURIComponent(previous)}`}
              data-slot="button"
              className={buttonVariants({ variant: "outline" })}
            >
              {t("Retrouver ma session")}
            </Link>
          )}
        </>
      )}
      <Button variant="ghost" disabled={loading} onClick={() => setRevision((value) => value + 1)}>
        {t("Actualiser la disponibilité")}
      </Button>
    </PortalLayout>
  );
}
