"use client";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button, buttonVariants } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  money,
  paymentLabels,
  type Payment,
  type PaymentState,
} from "@/features/payments/payment-types";
import { api } from "@/lib/api";
import Link from "next/link";
import { useEffect, useState } from "react";
import { usePortalAccess } from "./portal-access";
import { PortalLayout } from "./portal-layout";

const finished: ReadonlySet<PaymentState> = new Set([
  "COMPLETED",
  "CANCELLED",
  "FAILED",
  "DISPUTED",
]);

export function SessionScreen({ id, portalPath }: { id: string; portalPath: string }) {
  const { token, ready } = usePortalAccess(id);
  const [payment, setPayment] = useState<Payment | null>(null);
  const [error, setError] = useState("");
  const [stopping, setStopping] = useState(false);

  useEffect(() => {
    if (!token) {
      return;
    }
    const accessToken = token;
    let active = true;
    let timer: ReturnType<typeof setTimeout>;
    const controller = new AbortController();
    async function refresh() {
      try {
        const result = await api<Payment>(`public/sessions/${id}`, {
          headers: { "X-Charge-Token": accessToken },
          signal: controller.signal,
        });
        if (active) {
          setPayment(result);
          setError("");
          if (!finished.has(result.state)) {
            timer = setTimeout(refresh, 5000);
          }
        }
      } catch (failure) {
        if (active) {
          setError(
            failure instanceof Error ? failure.message : "Impossible d’actualiser la session.",
          );
          timer = setTimeout(refresh, 10000);
        }
      }
    }
    refresh();
    return () => {
      active = false;
      clearTimeout(timer);
      controller.abort();
    };
  }, [id, token]);

  async function stop() {
    setStopping(true);
    setError("");
    try {
      setPayment(
        await api<Payment>(`public/sessions/${id}/stop`, {
          method: "POST",
          headers: { "X-Charge-Token": token ?? "" },
        }),
      );
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Impossible de demander l’arrêt.");
    } finally {
      setStopping(false);
    }
  }

  return (
    <PortalLayout workspace={portalPath.split("/")[2]}>
      <div className="page-heading">
        <div>
          <h1>Votre session de recharge</h1>
          <p>Gardez cette page ouverte pour suivre la session et demander l’arrêt.</p>
        </div>
      </div>
      {ready && !token && (
        <Alert variant="destructive">
          <AlertDescription>
            L’accès à cette session n’est plus disponible dans ce navigateur. Contactez l’exploitant
            du point de charge.
          </AlertDescription>
        </Alert>
      )}
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}
      {!payment ? (
        !error && <Skeleton className="h-64" />
      ) : (
        <section className="flex flex-col gap-5 rounded-xl border bg-background p-5 sm:p-7">
          <h2 className="text-xl font-semibold" aria-live="polite">
            {paymentLabels[payment.state]}
          </h2>
          {payment.state === "START_REQUESTED" && (
            <p className="text-sm text-muted-foreground">
              Paiement confirmé. En attente du démarrage annoncé par la borne. Si elle ne démarre
              pas sous cinq minutes, le crédit sera remboursé.
            </p>
          )}
          {payment.state === "STOP_REQUESTED" && (
            <p className="text-sm text-muted-foreground">
              La demande d’arrêt a été transmise. En attente de la confirmation et du relevé final
              de la borne. Si nécessaire, arrêtez la recharge sur la borne ou le véhicule.
            </p>
          )}
          <dl className="divide-y text-sm">
            {(
              [
                ["Crédit payé", payment.prepaidMinor],
                ["Montant de la recharge", payment.billedMinor],
                ["Montant remboursé", payment.refundedMinor],
              ] as const
            ).map(([label, value]) => (
              <div key={label} className="flex justify-between gap-4 py-3">
                <dt>{label}</dt>
                <dd className="tabular-nums">{money(value, payment.currency)}</dd>
              </div>
            ))}
          </dl>
          {payment.message && (
            <Alert>
              <AlertTitle>Information de l’exploitant</AlertTitle>
              <AlertDescription>{payment.message}</AlertDescription>
            </Alert>
          )}
          {payment.state === "CHARGING" && (
            <Button variant="destructive" onClick={stop} disabled={stopping}>
              {stopping ? "Demande en cours…" : "Arrêter la recharge"}
            </Button>
          )}
          {payment.refundedMinor > 0 && (
            <p className="text-sm text-muted-foreground">
              Le remboursement a été transmis à Stripe. Son apparition sur votre compte dépend de
              votre banque.
            </p>
          )}
          <p className="break-all text-xs text-muted-foreground">Référence : {payment.id}</p>
        </section>
      )}
      <Link href={portalPath} data-slot="button" className={buttonVariants({ variant: "outline" })}>
        Retour au point de charge
      </Link>
    </PortalLayout>
  );
}
