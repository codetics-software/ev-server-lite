"use client";

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { ConfirmButton } from "@/features/shared/confirm-button";
import { EmptyState, ErrorState, LoadingState } from "@/features/shared/states";
import { ToneBadge } from "@/features/shared/status-badge";
import { useAction } from "@/features/shared/use-action";
import { useApi, useRevision } from "@/hooks/use-api";
import { api } from "@/lib/api";
import { IconCreditCard, IconPlus } from "@tabler/icons-react";
import dynamic from "next/dynamic";
import { useState } from "react";
import { brandLabels, type PaymentMethod } from "./billing-types";

const CardSetup = dynamic(() => import("./card-setup").then((module) => module.CardSetup), {
  ssr: false,
  loading: () => <LoadingState rows={2} />,
});

function expired(method: PaymentMethod) {
  if (!method.exp_month || !method.exp_year) {
    return false;
  }
  const now = new Date();
  return (
    method.exp_year < now.getFullYear() ||
    (method.exp_year === now.getFullYear() && method.exp_month < now.getMonth() + 1)
  );
}

export function PaymentMethodsList({ path, manage }: { path: string; manage: boolean }) {
  const [revision, refresh] = useRevision();
  const { data, loading, error } = useApi<PaymentMethod[]>(path, revision);
  const [adding, setAdding] = useState(false);
  const action = useAction();
  if (error && !data) {
    return <ErrorState error={error} onRetry={refresh} />;
  }
  if (loading && !data) {
    return <LoadingState rows={2} />;
  }
  const methods = data ?? [];
  return (
    <div className="flex flex-col gap-4">
      {methods.length ? (
        <ul className="grid gap-3 sm:grid-cols-2">
          {methods.map((method) => (
            <li key={method.id}>
              <Card size="sm">
                <CardHeader>
                  <CardTitle className="flex items-center gap-2">
                    <IconCreditCard className="size-4" />
                    {brandLabels[method.brand ?? ""] ?? method.brand ?? "Carte"} ••••{" "}
                    {method.last4 ?? "????"}
                  </CardTitle>
                  <CardDescription className="flex flex-wrap items-center gap-2">
                    Expire fin {String(method.exp_month ?? "").padStart(2, "0")}/
                    {method.exp_year ?? "—"}
                    {method.default && <ToneBadge tone="success">Par défaut</ToneBadge>}
                    {expired(method) && <ToneBadge tone="danger">Expirée</ToneBadge>}
                  </CardDescription>
                </CardHeader>
                {manage && (
                  <CardContent className="flex gap-2">
                    {!method.default && (
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={action.busy}
                        onClick={() =>
                          action.run(
                            "default",
                            () =>
                              api(`account/payment-methods/${method.id}/default`, {
                                method: "POST",
                              }),
                            {
                              success: "Moyen de paiement par défaut modifié.",
                              onDone: refresh,
                            },
                          )
                        }
                      >
                        Utiliser par défaut
                      </Button>
                    )}
                    <ConfirmButton
                      title="Supprimer cette carte ?"
                      description="Elle ne pourra plus être utilisée pour régler vos recharges."
                      confirmLabel="Supprimer"
                      variant="ghost"
                      size="sm"
                      onConfirm={() =>
                        action.run(
                          "delete",
                          () => api(`account/payment-methods/${method.id}`, { method: "DELETE" }),
                          {
                            success: "Carte supprimée.",
                            onDone: refresh,
                          },
                        )
                      }
                    >
                      Supprimer
                    </ConfirmButton>
                  </CardContent>
                )}
              </Card>
            </li>
          ))}
        </ul>
      ) : (
        <EmptyState
          icon={<IconCreditCard />}
          title="Aucun moyen de paiement"
          description={
            manage
              ? "Ajoutez une carte bancaire pour régler vos recharges."
              : "Cet utilisateur n’a enregistré aucune carte."
          }
        />
      )}
      {manage &&
        (adding ? (
          <Card>
            <CardHeader>
              <CardTitle>Ajouter une carte</CardTitle>
              <CardDescription>
                Vos données bancaires sont transmises directement à Stripe.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <CardSetup
                onDone={() => {
                  setAdding(false);
                  refresh();
                }}
                onCancel={() => setAdding(false)}
              />
            </CardContent>
          </Card>
        ) : (
          <Button className="w-fit" onClick={() => setAdding(true)}>
            <IconPlus data-icon="inline-start" />
            Ajouter une carte
          </Button>
        ))}
    </div>
  );
}
