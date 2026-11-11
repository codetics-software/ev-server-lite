// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

"use client";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { FieldGroup } from "@/components/ui/field";
import { money } from "@/features/payments/payment-types";
import { DataTable, type Column } from "@/features/shared/data-table";
import { FormText } from "@/features/shared/form-fields";
import { FormFooter } from "@/features/shared/form-footer";
import { EmptyState, ErrorState, LoadingState } from "@/features/shared/states";
import { ToneBadge } from "@/features/shared/status-badge";
import { useApi, useRevision } from "@/hooks/use-api";
import { api, errorMessage } from "@/lib/api";
import { date } from "@/lib/format";
import { maxLength, required, rules } from "@/lib/form";
import { revalidateLogic, useForm } from "@tanstack/react-form";
import { useRef, useState } from "react";
import { cardStateLabels, cardStateTones, type CardOffer, type CardOrder } from "./card-types";
import { t, translated } from "@/lib/i18n";

const columns: Column<CardOrder>[] = translated([
  { id: "date", header: "Commande", cell: (order) => date(order.created_at) },
  { id: "amount", header: "Montant", cell: (order) => money(order.amount_minor, order.currency) },
  {
    id: "state",
    header: "État",
    cell: (order) => (
      <ToneBadge tone={cardStateTones[order.state]}>
        {order.refunded ? t("Annulée et remboursée") : cardStateLabels[order.state]}
      </ToneBadge>
    ),
  },
]);

function OrderForm({ offer }: { offer: CardOffer }) {
  const [error, setError] = useState("");
  const requestId = useRef<string | null>(null);
  const form = useForm({
    defaultValues: { recipientName: "", address: "", postalCode: "", city: "" },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      setError("");
      requestId.current ??= crypto.randomUUID();
      try {
        const result = await api<{ order_id: string; checkout_url: string }>(
          "account/card-orders",
          {
            method: "POST",
            body: JSON.stringify({
              requestId: requestId.current,
              ...(offer.shipping
                ? {
                    recipientName: value.recipientName.trim(),
                    address: value.address.trim(),
                    postalCode: value.postalCode.trim(),
                    city: value.city.trim(),
                  }
                : {}),
            }),
          },
        );
        window.location.assign(result.checkout_url);
      } catch (failure) {
        setError(errorMessage(failure, t("La commande n’a pas pu être créée.")));
      }
    },
  });
  const validators = { onDynamic: rules(required(), maxLength(200)) };
  return (
    <form
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        void form.handleSubmit();
      }}
    >
      <FieldGroup>
        {offer.shipping && (
          <>
            <form.Field name="recipientName" validators={validators}>
              {(field) => (
                <FormText field={field} label={t("Destinataire")} autoComplete="name" required />
              )}
            </form.Field>
            <form.Field name="address" validators={validators}>
              {(field) => (
                <FormText
                  field={field}
                  label={t("Adresse")}
                  autoComplete="street-address"
                  required
                />
              )}
            </form.Field>
            <div className="grid gap-4 sm:grid-cols-2">
              <form.Field
                name="postalCode"
                validators={{ onDynamic: rules(required(), maxLength(20)) }}
              >
                {(field) => (
                  <FormText
                    field={field}
                    label={t("Code postal")}
                    autoComplete="postal-code"
                    required
                  />
                )}
              </form.Field>
              <form.Field name="city" validators={validators}>
                {(field) => (
                  <FormText
                    field={field}
                    label={t("Ville")}
                    autoComplete="address-level2"
                    required
                  />
                )}
              </form.Field>
            </div>
          </>
        )}
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => (
            <FormFooter
              error={error}
              submitting={submitting}
              label={`Payer ${money(offer.price_minor, offer.currency)}`}
            />
          )}
        </form.Subscribe>
      </FieldGroup>
    </form>
  );
}

export function DriverCardScreen() {
  const [revision, refresh] = useRevision();
  const offer = useApi<CardOffer>("account/card-offer", revision);
  const orders = useApi<CardOrder[]>("account/card-orders", revision);
  if (offer.error && !offer.data) {
    return <ErrorState error={offer.error} onRetry={refresh} />;
  }
  if (!offer.data || (orders.loading && !orders.data)) {
    return <LoadingState rows={3} />;
  }
  const list = orders.data ?? [];
  const awaitingActivation = list.some((order) => order.state === "SHIPPED");
  return (
    <div className="flex max-w-3xl flex-col gap-6">
      {awaitingActivation && (
        <Alert>
          <AlertTitle>{t("Votre carte est prête")}</AlertTitle>
          <AlertDescription>
            {t(
              "À réception, ouvrez l’application mobile, onglet Badges, puis activez-la en la scannant ou en saisissant le numéro imprimé dessus.",
            )}
          </AlertDescription>
        </Alert>
      )}
      {offer.data.available ? (
        <Card>
          <CardHeader>
            <CardTitle>{t("Commander une carte de recharge")}</CardTitle>
            <CardDescription>
              {offer.data.description ??
                t(
                  "Une carte RFID à présenter aux bornes de ce réseau pour démarrer vos recharges.",
                )}{" "}
              {offer.data.shipping
                ? t("Elle est envoyée à l’adresse de votre choix.")
                : t("Elle vous est remise par l’exploitant.")}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <OrderForm offer={offer.data} />
          </CardContent>
        </Card>
      ) : (
        <EmptyState
          title={t("Vente de cartes indisponible")}
          description={t("Cet espace ne propose pas de carte à la vente pour le moment.")}
        />
      )}
      {list.length > 0 && (
        <DataTable
          label={t("Mes commandes de carte")}
          rows={list}
          columns={columns}
          rowKey={(order) => order.id}
        />
      )}
    </div>
  );
}
