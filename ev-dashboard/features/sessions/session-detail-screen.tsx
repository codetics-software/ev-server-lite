"use client";

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { InvoiceDownload } from "@/features/billing/invoice-download";
import { ConfirmButton } from "@/features/shared/confirm-button";
import { PageHeader } from "@/features/shared/page-header";
import { EmptyState, ErrorState, LoadingState } from "@/features/shared/states";
import { ErrorBadges, StateBadge } from "@/features/shared/status-badge";
import { useAction } from "@/features/shared/use-action";
import { canWrite, isAdmin, isStaff, useWorkspace } from "@/features/workspace/workspace-context";
import { useApi, useRevision } from "@/hooks/use-api";
import { api } from "@/lib/api";
import { date, display, duration, kw, kwh, money, percent } from "@/lib/format";
import { IconChartLine, IconPlayerStop, IconRefresh } from "@tabler/icons-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { ConsumptionChart } from "./consumption-chart";
import { SessionState } from "./session-columns";
import { sessionDuration, type ChargingSession, type Consumption } from "./session-types";

function Detail({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid gap-1 border-b py-2.5 text-sm last:border-0 sm:grid-cols-[12rem_1fr]">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="min-w-0 break-words">{children}</dd>
    </div>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="mt-1 text-2xl font-medium tabular-nums">{value}</dd>
    </div>
  );
}

export function SessionDetailScreen({ id }: { id: string }) {
  const user = useWorkspace();
  const router = useRouter();
  const [revision, refresh] = useRevision();
  const action = useAction();
  const { data: session, error, loading } = useApi<ChargingSession>(`transactions/${id}`, revision);
  const consumptions = useApi<Consumption[]>(`transactions/${id}/consumptions`, revision);
  const active = session ? !session.stopped_at : false;

  useEffect(() => {
    if (!active) {
      return;
    }
    const timer = setInterval(refresh, 15000);
    return () => clearInterval(timer);
  }, [active, refresh]);

  if (error && !session) {
    return (
      <>
        <PageHeader
          title={`Session n° ${id}`}
          back={{ href: "/transactions", label: "Sessions" }}
        />
        <ErrorState error={error} onRetry={refresh} />
      </>
    );
  }
  if (loading && !session) {
    return <LoadingState />;
  }
  if (!session) {
    return null;
  }
  const staff = isStaff(user);
  const deletable = isAdmin(user) && !active && !session.invoice_id && !session.public_payment;

  return (
    <>
      <PageHeader
        back={{ href: "/transactions", label: "Sessions" }}
        title={`Session n° ${session.id}`}
        description={`${session.station_id} · connecteur ${session.connector_id} · ${date(session.started_at)}`}
        meta={
          <>
            <SessionState session={session} />
            {session.billing_status && session.billing_status !== "NONE" && (
              <StateBadge state={session.billing_status} />
            )}
          </>
        }
        actions={
          <>
            <Button variant="outline" size="icon" aria-label="Actualiser" onClick={refresh}>
              <IconRefresh />
            </Button>
            {active && (
              <ConfirmButton
                title="Arrêter la recharge ?"
                description="La borne reçoit une demande d’arrêt de la session."
                confirmLabel="Arrêter"
                onConfirm={() =>
                  action.run(
                    "stop",
                    () => api(`transactions/${session.id}/stop`, { method: "POST" }),
                    {
                      success: "Demande d’arrêt envoyée à la borne.",
                      onDone: () => setTimeout(refresh, 3000),
                    },
                  )
                }
              >
                <IconPlayerStop data-icon="inline-start" />
                Arrêter
              </ConfirmButton>
            )}
            {active && canWrite(user) && (
              <ConfirmButton
                title="Clôturer la session localement ?"
                description="La session est terminée dans la supervision à partir du dernier relevé, sans attendre la borne. À utiliser si la borne ne répond plus."
                confirmLabel="Clôturer"
                variant="outline"
                onConfirm={() =>
                  action.run(
                    "soft-stop",
                    () => api(`transactions/${session.id}/soft-stop`, { method: "POST" }),
                    { success: "Session clôturée.", onDone: refresh },
                  )
                }
              >
                Clôturer localement
              </ConfirmButton>
            )}
            {deletable && (
              <ConfirmButton
                title="Supprimer cette session ?"
                description="La session et ses mesures sont supprimées définitivement."
                confirmLabel="Supprimer"
                onConfirm={() =>
                  action.run(
                    "delete",
                    () => api(`transactions/${session.id}`, { method: "DELETE" }),
                    {
                      success: "Session supprimée.",
                      onDone: () => router.replace("/transactions"),
                    },
                  )
                }
              >
                Supprimer
              </ConfirmButton>
            )}
          </>
        }
      />
      <dl className="grid grid-cols-2 gap-6 border-y py-5 md:grid-cols-4">
        <Metric label="Énergie" value={kwh(session.energy_kwh)} />
        <Metric label="Durée" value={duration(sessionDuration(session))} />
        <Metric
          label={active ? "Puissance actuelle" : "Inactivité"}
          value={active ? kw(session.current_power_kw) : duration(session.inactivity_seconds)}
        />
        <Metric label="Prix" value={money(session.price, session.currency)} />
      </dl>
      {session.errors && session.errors.length > 0 && (
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <span className="text-muted-foreground">Anomalies :</span>
          <ErrorBadges errors={session.errors} />
        </div>
      )}
      <div className="grid items-start gap-6 xl:grid-cols-[1.6fr_1fr]">
        <Card>
          <CardHeader>
            <CardTitle>Consommation</CardTitle>
            <CardDescription>
              Puissance, énergie cumulée et niveau de batterie relevés.
            </CardDescription>
          </CardHeader>
          <CardContent>
            {consumptions.error && !consumptions.data ? (
              <ErrorState error={consumptions.error} />
            ) : consumptions.loading && !consumptions.data ? (
              <LoadingState rows={3} />
            ) : consumptions.data?.length ? (
              <ConsumptionChart points={consumptions.data} />
            ) : (
              <EmptyState
                icon={<IconChartLine />}
                title="Aucune mesure"
                description="La borne n’a pas encore envoyé de relevé pour cette session."
              />
            )}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Détails</CardTitle>
          </CardHeader>
          <CardContent>
            <dl>
              <Detail label="Borne">
                {staff ? (
                  <Link
                    className="text-primary underline-offset-4 dark:text-sidebar-primary hover:underline"
                    href={`/stations/${encodeURIComponent(session.station_id)}`}
                  >
                    {session.station_id}
                  </Link>
                ) : (
                  session.station_id
                )}{" "}
                · connecteur {session.connector_id}
              </Detail>
              {staff && (
                <Detail label="Utilisateur">
                  {session.user_id && isAdmin(user) ? (
                    <Link
                      className="text-primary underline-offset-4 dark:text-sidebar-primary hover:underline"
                      href={`/users/${session.user_id}`}
                    >
                      {session.user_name ?? session.user_id}
                    </Link>
                  ) : (
                    display(session.user_name)
                  )}
                </Detail>
              )}
              <Detail label="Badge">{display(session.token_uid)}</Detail>
              <Detail label="Début">{date(session.started_at)}</Detail>
              <Detail label="Fin">{active ? "En cours" : date(session.stopped_at)}</Detail>
              <Detail label="Batterie">
                {session.state_of_charge != null
                  ? `${percent(session.state_of_charge_start)} → ${percent(session.state_of_charge)}`
                  : "—"}
              </Detail>
              <Detail label="Autorisation">{display(session.authorization_status)}</Detail>
              <Detail label="Motif d’arrêt">{display(session.stop_reason)}</Detail>
              {session.roaming_partner && (
                <Detail label="Partenaire d’itinérance">{session.roaming_partner}</Detail>
              )}
              {session.public_payment && (
                <Detail label="Paiement">Paiement public par QR code</Detail>
              )}
              {session.invoice_id && (
                <Detail label="Facture">
                  <InvoiceDownload
                    id={session.invoice_id}
                    label="Télécharger la facture"
                    variant="link"
                  />
                </Detail>
              )}
            </dl>
          </CardContent>
        </Card>
      </div>
    </>
  );
}
