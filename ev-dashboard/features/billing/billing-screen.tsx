"use client";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button, buttonVariants } from "@/components/ui/button";
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { FieldGroup } from "@/components/ui/field";
import { DataTable, type Column } from "@/features/shared/data-table";
import { FormSelect, FormSwitch } from "@/features/shared/form-fields";
import { FormFooter } from "@/features/shared/form-footer";
import { PageHeader } from "@/features/shared/page-header";
import { EmptyState, ErrorState, LoadingState } from "@/features/shared/states";
import { ToneBadge } from "@/features/shared/status-badge";
import { notify, useAction } from "@/features/shared/use-action";
import { useApi, useRevision } from "@/hooks/use-api";
import { api, errorMessage } from "@/lib/api";
import { number } from "@/lib/format";
import { IconCircleCheck, IconReceiptTax } from "@tabler/icons-react";
import { revalidateLogic, useForm } from "@tanstack/react-form";
import Link from "next/link";
import { useState } from "react";
import type { Tax, UserBilling } from "./billing-types";

type Settings = {
  configured: boolean;
  ready: boolean;
  accountId: string | null;
  user_billing?: UserBilling;
  userBilling?: UserBilling;
};

function UserBillingForm({
  settings,
  taxes,
  onSaved,
}: {
  settings: UserBilling;
  taxes: Tax[];
  onSaved: () => void;
}) {
  const [error, setError] = useState("");
  const form = useForm({
    defaultValues: {
      enabled: settings.enabled,
      mode: settings.mode ?? "PER_SESSION",
      taxRateId: settings.tax_rate_id ?? "none",
    },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      setError("");
      try {
        await api("billing/settings/users", {
          method: "PUT",
          body: JSON.stringify({
            enabled: value.enabled,
            mode: value.mode,
            taxRateId: value.taxRateId === "none" ? undefined : value.taxRateId,
          }),
        });
        notify("Paramètres de facturation enregistrés.");
        onSaved();
      } catch (failure) {
        setError(errorMessage(failure, "Impossible d’enregistrer la facturation."));
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
              label="Facturer les utilisateurs enregistrés"
              description="Les sessions valorisées sont facturées sur la carte enregistrée par chaque conducteur."
            />
          )}
        </form.Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <form.Field name="mode">
            {(field) => (
              <FormSelect
                field={field}
                label="Fréquence"
                options={[
                  { value: "PER_SESSION", label: "Une facture par session" },
                  { value: "MONTHLY", label: "Une facture mensuelle" },
                ]}
              />
            )}
          </form.Field>
          <form.Field name="taxRateId">
            {(field) => (
              <FormSelect
                field={field}
                label="Taxe appliquée"
                placeholder="Aucune taxe"
                options={[
                  { value: "none", label: "Aucune taxe" },
                  ...taxes.map((tax) => ({
                    value: tax.id,
                    label: `${tax.display_name} · ${number(tax.percentage, 2)} %${tax.inclusive ? " incluse" : ""}`,
                  })),
                ]}
              />
            )}
          </form.Field>
        </div>
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => <FormFooter error={error} submitting={submitting} />}
        </form.Subscribe>
      </FieldGroup>
    </form>
  );
}

const taxColumns: Column<Tax>[] = [
  {
    id: "name",
    header: "Taxe",
    sort: (tax) => tax.display_name,
    cell: (tax) => <span className="font-medium">{tax.display_name}</span>,
  },
  {
    id: "rate",
    header: "Taux",
    align: "right",
    sort: (tax) => tax.percentage,
    cell: (tax) => `${number(tax.percentage, 2)} %`,
  },
  {
    id: "inclusive",
    header: "Mode",
    cell: (tax) => (tax.inclusive ? "Incluse dans le prix" : "Ajoutée au prix"),
  },
];

export function BillingScreen() {
  const [revision, refresh] = useRevision();
  const settings = useApi<Settings>("billing/settings", revision);
  const taxes = useApi<Tax[]>("billing/taxes", revision);
  const action = useAction();
  const [check, setCheck] = useState<{ ready: boolean; message: string } | null>(null);
  const userBilling: UserBilling = settings.data?.user_billing ??
    settings.data?.userBilling ?? {
      enabled: false,
      mode: "PER_SESSION" as const,
      tax_rate_id: null,
    };

  return (
    <>
      <PageHeader
        title="Facturation des utilisateurs"
        description="Facturez les recharges des conducteurs enregistrés sur le compte Stripe de votre espace."
        actions={
          <Button
            variant="outline"
            disabled={action.busy}
            onClick={() =>
              action.run(
                "check",
                () => api<{ ready: boolean; message: string }>("billing/check", { method: "POST" }),
                {
                  onDone: (result) => setCheck(result ?? null),
                },
              )
            }
          >
            <IconCircleCheck data-icon="inline-start" />
            Vérifier la configuration
          </Button>
        }
      />
      {check && (
        <Alert variant={check.ready ? "default" : "destructive"}>
          <AlertTitle>
            {check.ready ? "Facturation opérationnelle" : "Configuration incomplète"}
          </AlertTitle>
          <AlertDescription>{check.message}</AlertDescription>
        </Alert>
      )}
      <div className="grid items-start gap-6 xl:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Paramètres</CardTitle>
            <CardDescription>Mode de facturation et taxe appliquée.</CardDescription>
            <CardAction>
              {settings.data && (
                <ToneBadge tone={settings.data.ready ? "success" : "warning"}>
                  {settings.data.ready ? "Compte Stripe prêt" : "Compte Stripe à finaliser"}
                </ToneBadge>
              )}
            </CardAction>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            {settings.error && !settings.data ? (
              <ErrorState error={settings.error} />
            ) : !settings.data ? (
              <LoadingState rows={3} />
            ) : (
              <>
                {!settings.data.ready && (
                  <p className="text-sm text-muted-foreground">
                    Connectez et finalisez le compte Stripe de l’espace depuis la page{" "}
                    <Link
                      className="text-primary underline-offset-4 dark:text-sidebar-primary hover:underline"
                      href="/payments"
                    >
                      Paiements publics
                    </Link>
                    .
                  </p>
                )}
                <UserBillingForm
                  key={`${userBilling.enabled}:${userBilling.mode}:${userBilling.taxRateId ?? userBilling.tax_rate_id}`}
                  settings={userBilling}
                  taxes={taxes.data ?? []}
                  onSaved={refresh}
                />
              </>
            )}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Taxes</CardTitle>
            <CardDescription>
              Taux définis dans le tableau de bord Stripe du compte.
            </CardDescription>
            <CardAction>
              <a
                className={buttonVariants({ variant: "ghost", size: "sm" })}
                href="https://dashboard.stripe.com/tax-rates"
                target="_blank"
                rel="noreferrer"
              >
                Gérer dans Stripe
              </a>
            </CardAction>
          </CardHeader>
          <CardContent>
            {taxes.error && !taxes.data ? (
              <ErrorState error={taxes.error} />
            ) : !taxes.data ? (
              <LoadingState rows={2} />
            ) : taxes.data.length ? (
              <DataTable
                label="Taxes"
                rows={taxes.data}
                columns={taxColumns}
                rowKey={(tax) => tax.id}
              />
            ) : (
              <EmptyState
                icon={<IconReceiptTax />}
                title="Aucune taxe"
                description="Créez un taux de TVA dans Stripe pour l’appliquer aux factures."
              />
            )}
          </CardContent>
        </Card>
      </div>
    </>
  );
}
