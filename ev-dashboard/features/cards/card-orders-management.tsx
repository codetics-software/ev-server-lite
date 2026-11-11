// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

"use client";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { FieldGroup } from "@/components/ui/field";
import { money } from "@/features/payments/payment-types";
import { DataTable, type Column } from "@/features/shared/data-table";
import { FormSwitch, FormText } from "@/features/shared/form-fields";
import { FormFooter } from "@/features/shared/form-footer";
import { EmptyState, ErrorState, LoadingState } from "@/features/shared/states";
import { ToneBadge } from "@/features/shared/status-badge";
import { notify, useAction } from "@/features/shared/use-action";
import { isAdmin, useWorkspace } from "@/features/workspace/workspace-context";
import { useApi, useRevision } from "@/hooks/use-api";
import { api, errorMessage } from "@/lib/api";
import { date } from "@/lib/format";
import { matches, maxLength, numeric, required, rules } from "@/lib/form";
import { revalidateLogic, useForm } from "@tanstack/react-form";
import { useState } from "react";
import { cardStateLabels, cardStateTones, type CardOffer, type CardOrder } from "./card-types";
import { t } from "@/lib/i18n";

function SaleForm({ offer, onSaved }: { offer: CardOffer; onSaved: () => void }) {
  const [error, setError] = useState("");
  const form = useForm({
    defaultValues: {
      enabled: offer.available,
      price: offer.price_minor ? String(offer.price_minor / 100) : "",
      description: offer.description ?? "",
      shipping: offer.shipping,
    },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      setError("");
      try {
        await api("card-orders/settings", {
          method: "PUT",
          body: JSON.stringify({
            enabled: value.enabled,
            priceMinor: Math.round(Number(value.price.replace(",", ".")) * 100),
            description: value.description.trim() || undefined,
            shipping: value.shipping,
          }),
        });
        notify(t("Vente de cartes enregistrée."));
        onSaved();
      } catch (failure) {
        setError(errorMessage(failure, t("Impossible d’enregistrer la vente de cartes.")));
      }
    },
  });
  return (
    <form
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        void form.handleSubmit();
      }}
    >
      <FieldGroup>
        <form.Field name="enabled">
          {(field) => (
            <FormSwitch
              field={field}
              label={t("Vendre des cartes aux conducteurs")}
              description={t("Le paiement est encaissé sur le compte Stripe de l’espace.")}
            />
          )}
        </form.Field>
        <form.Field name="price" validators={{ onDynamic: rules(required(), numeric()) }}>
          {(field) => (
            <FormText field={field} label={t("Prix de la carte (€)")} inputMode="decimal" />
          )}
        </form.Field>
        <form.Field name="description" validators={{ onDynamic: rules(maxLength(500)) }}>
          {(field) => <FormText field={field} label={t("Description (facultatif)")} />}
        </form.Field>
        <form.Field name="shipping">
          {(field) => (
            <FormSwitch
              field={field}
              label={t("Envoyer la carte par courrier")}
              description={t(
                "Demande une adresse de livraison. Sinon, vous remettez la carte en main propre.",
              )}
            />
          )}
        </form.Field>
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => <FormFooter error={error} submitting={submitting} />}
        </form.Subscribe>
      </FieldGroup>
    </form>
  );
}

function AssignDialog({
  order,
  onClose,
  onDone,
}: {
  order: CardOrder;
  onClose: () => void;
  onDone: () => void;
}) {
  const [error, setError] = useState("");
  const form = useForm({
    defaultValues: { uid: order.token_uid ?? "", visualId: order.visual_id ?? "" },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      setError("");
      try {
        await api(`card-orders/${order.id}/assign`, {
          method: "POST",
          body: JSON.stringify({
            uid: value.uid.trim(),
            visualId: value.visualId.trim() || undefined,
          }),
        });
        notify(t("Carte associée. Le conducteur est prévenu."));
        onDone();
      } catch (failure) {
        setError(errorMessage(failure, t("Impossible d’associer cette carte.")));
      }
    },
  });
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("Associer une carte")}</DialogTitle>
          <DialogDescription>
            {order.user_name
              ? t(
                  "La carte reste inactive aux bornes jusqu’à son activation par {name} dans l’application.",
                  { name: order.user_name },
                )
              : t(
                  "La carte reste inactive aux bornes jusqu’à son activation par le conducteur dans l’application.",
                )}
          </DialogDescription>
        </DialogHeader>
        <form
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            void form.handleSubmit();
          }}
        >
          <FieldGroup>
            <form.Field
              name="uid"
              validators={{
                onDynamic: rules(
                  required(),
                  matches(
                    /^[A-Za-z0-9_-]{1,20}$/,
                    t("20 caractères au plus : lettres, chiffres, - et _."),
                  ),
                ),
              }}
            >
              {(field) => (
                <FormText
                  field={field}
                  label={t("Identifiant RFID de la carte")}
                  description={t("Celui transmis à la borne (idTag OCPP).")}
                  required
                />
              )}
            </form.Field>
            <form.Field name="visualId" validators={{ onDynamic: rules(maxLength(100)) }}>
              {(field) => (
                <FormText
                  field={field}
                  label={t("Numéro imprimé sur la carte")}
                  description={t("Le conducteur peut le saisir au lieu de scanner la carte.")}
                />
              )}
            </form.Field>
            <form.Subscribe selector={(state) => state.isSubmitting}>
              {(submitting) => (
                <FormFooter error={error} submitting={submitting} label={t("Associer la carte")} />
              )}
            </form.Subscribe>
          </FieldGroup>
        </form>
      </DialogContent>
    </Dialog>
  );
}

export function CardOrdersManagement() {
  const user = useWorkspace();
  const admin = isAdmin(user);
  const operate = admin || user.role === "OPERATOR";
  const [revision, refresh] = useRevision();
  const offer = useApi<CardOffer>("account/card-offer", revision);
  const orders = useApi<{ items: CardOrder[]; total: number }>("card-orders?limit=200", revision);
  const [assigning, setAssigning] = useState<CardOrder | null>(null);
  const [cancelling, setCancelling] = useState<CardOrder | null>(null);
  const action = useAction();
  if (orders.error && !orders.data) {
    return <ErrorState error={orders.error} onRetry={refresh} />;
  }
  if (!orders.data) {
    return <LoadingState rows={4} />;
  }
  const columns: Column<CardOrder>[] = [
    {
      id: "driver",
      header: t("Conducteur"),
      cell: (order) => (
        <div className="flex flex-col">
          <span>{order.user_name ?? "—"}</span>
          <span className="text-xs text-muted-foreground">{order.user_email}</span>
        </div>
      ),
    },
    {
      id: "delivery",
      header: t("Livraison"),
      cell: (order) =>
        order.address ? (
          <div className="flex flex-col text-xs">
            <span>{order.recipient_name}</span>
            <span>{order.address}</span>
            <span>
              {order.postal_code} {order.city}
            </span>
          </div>
        ) : (
          t("En main propre")
        ),
    },
    {
      id: "date",
      header: t("Date"),
      cell: (order) => date(order.created_at),
      sort: (order) => order.created_at,
    },
    {
      id: "amount",
      header: t("Montant"),
      cell: (order) => money(order.amount_minor, order.currency),
    },
    {
      id: "state",
      header: t("État"),
      cell: (order) => (
        <ToneBadge tone={cardStateTones[order.state]}>
          {order.refunded ? t("Annulée et remboursée") : cardStateLabels[order.state]}
        </ToneBadge>
      ),
    },
    {
      id: "card",
      header: t("Carte"),
      cell: (order) => order.visual_id ?? order.token_uid ?? "—",
    },
    {
      id: "actions",
      header: t("Actions"),
      srOnlyHeader: true,
      align: "right",
      cell: (order) =>
        operate && ["PENDING_PAYMENT", "PAID", "SHIPPED"].includes(order.state) ? (
          <div className="flex justify-end gap-2">
            {order.state !== "PENDING_PAYMENT" && (
              <Button size="sm" onClick={() => setAssigning(order)}>
                {order.state === "PAID" ? t("Associer une carte") : t("Changer de carte")}
              </Button>
            )}
            <Button size="sm" variant="outline" onClick={() => setCancelling(order)}>
              {t("Annuler")}
            </Button>
          </div>
        ) : null,
    },
  ];
  return (
    <div className="flex flex-col gap-6">
      {admin && offer.data && (
        <Card className="max-w-3xl">
          <CardHeader>
            <CardTitle>{t("Vente de cartes")}</CardTitle>
            <CardDescription>
              {t(
                "Les conducteurs commandent depuis leur espace, puis activent la carte reçue dans l’application mobile.",
              )}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <SaleForm key={revision} offer={offer.data} onSaved={refresh} />
          </CardContent>
        </Card>
      )}
      {orders.data.items.length ? (
        <DataTable
          label={t("Commandes de cartes")}
          rows={orders.data.items}
          columns={columns}
          rowKey={(order) => order.id}
        />
      ) : (
        <EmptyState
          title={t("Aucune commande")}
          description={t("Les commandes des conducteurs apparaîtront ici.")}
        />
      )}
      {assigning && (
        <AssignDialog
          order={assigning}
          onClose={() => setAssigning(null)}
          onDone={() => {
            setAssigning(null);
            refresh();
          }}
        />
      )}
      <AlertDialog open={cancelling !== null} onOpenChange={(open) => !open && setCancelling(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("Annuler cette commande ?")}</AlertDialogTitle>
            <AlertDialogDescription>
              {t(
                "Si elle a été payée, le conducteur est intégralement remboursé. La carte associée reste inactive.",
              )}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t("Conserver")}</AlertDialogCancel>
            <AlertDialogAction
              disabled={action.busy}
              onClick={() => {
                const order = cancelling;
                setCancelling(null);
                if (order) {
                  void action.run(
                    order.id,
                    () => api(`card-orders/${order.id}/cancel`, { method: "POST", body: "{}" }),
                    { success: t("Commande annulée."), onDone: refresh },
                  );
                }
              }}
            >
              {t("Annuler la commande")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
