"use client";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { FieldGroup } from "@/components/ui/field";
import { FormPicker, FormText } from "@/features/shared/form-fields";
import { api, errorMessage } from "@/lib/api";
import type { RecordItem } from "@/lib/api-types";
import { fromInputDateTime, money, number } from "@/lib/format";
import { integerBetween, optionalNumber, required, rules } from "@/lib/form";
import { IconCalculator } from "@tabler/icons-react";
import { revalidateLogic, useForm } from "@tanstack/react-form";
import { useState } from "react";
import { t } from "@/lib/i18n";

type Resolution = {
  definition: RecordItem | null;
  preview: { energy_kwh: number; duration_minutes: number; price: number; currency: string } | null;
};

export function PricingResolve({ onOpen }: { onOpen: (record: RecordItem) => void }) {
  const [result, setResult] = useState<Resolution | null>(null);
  const [error, setError] = useState("");
  const form = useForm({
    defaultValues: {
      stationId: "",
      connectorId: "1",
      at: "",
      energyKwh: "10",
      durationMinutes: "60",
    },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      setError("");
      setResult(null);
      try {
        setResult(
          await api<Resolution>("pricing/resolve", {
            method: "POST",
            body: JSON.stringify({
              stationId: value.stationId,
              connectorId: Number(value.connectorId),
              at: fromInputDateTime(value.at) ?? undefined,
              energyKwh: Number(value.energyKwh),
              durationMinutes: Number(value.durationMinutes),
            }),
          }),
        );
      } catch (failure) {
        setError(errorMessage(failure, t("Impossible de calculer le tarif.")));
      }
    },
  });
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("Simuler un tarif")}</CardTitle>
        <CardDescription>
          {t("Retrouvez le tarif appliqué à un connecteur et estimez le prix d’une session.")}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <form
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            void form.handleSubmit();
          }}
        >
          <FieldGroup>
            <div className="grid gap-4 sm:grid-cols-[1fr_7rem]">
              <form.Field
                name="stationId"
                validators={{ onDynamic: rules(required(t("Choisissez une borne."))) }}
              >
                {(field) => <FormPicker field={field} source="stations" label={t("Borne")} />}
              </form.Field>
              <form.Field
                name="connectorId"
                validators={{ onDynamic: rules(integerBetween(1, 100, t("Connecteur invalide."))) }}
              >
                {(field) => (
                  <FormText field={field} label={t("Connecteur")} type="number" min={1} />
                )}
              </form.Field>
            </div>
            <div className="grid gap-4 sm:grid-cols-3">
              <form.Field name="at">
                {(field) => (
                  <FormText
                    field={field}
                    label={t("Début de la session")}
                    type="datetime-local"
                    description={t("Maintenant si vide.")}
                  />
                )}
              </form.Field>
              <form.Field
                name="energyKwh"
                validators={{
                  onDynamic: rules(
                    required(t("Requis.")),
                    optionalNumber(0, 10000, t("Énergie invalide.")),
                  ),
                }}
              >
                {(field) => (
                  <FormText field={field} label={t("Énergie (kWh)")} type="number" min={0} />
                )}
              </form.Field>
              <form.Field
                name="durationMinutes"
                validators={{ onDynamic: rules(integerBetween(0, 100000, t("Durée invalide."))) }}
              >
                {(field) => (
                  <FormText field={field} label={t("Durée (min)")} type="number" min={0} />
                )}
              </form.Field>
            </div>
            <form.Subscribe selector={(state) => state.isSubmitting}>
              {(submitting) => (
                <Button type="submit" variant="outline" className="w-fit" disabled={submitting}>
                  <IconCalculator data-icon="inline-start" />
                  {submitting ? t("Calcul…") : t("Calculer")}
                </Button>
              )}
            </form.Subscribe>
          </FieldGroup>
        </form>
        {error && (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}
        {result &&
          (result.definition && result.preview ? (
            <Alert>
              <AlertTitle>
                {t("{price} pour {energy} kWh en {minutes} min", {
                  price: money(result.preview.price, result.preview.currency),
                  energy: number(result.preview.energy_kwh, 2),
                  minutes: result.preview.duration_minutes,
                })}
              </AlertTitle>
              <AlertDescription>
                {t("Tarif appliqué :")}{" "}
                <button
                  type="button"
                  className="font-medium text-primary underline-offset-4 dark:text-sidebar-primary hover:underline"
                  onClick={() => result.definition && onOpen(result.definition)}
                >
                  {result.definition.name}
                </button>
              </AlertDescription>
            </Alert>
          ) : (
            <Alert>
              <AlertTitle>{t("Aucun tarif applicable")}</AlertTitle>
              <AlertDescription>
                {t("La session serait gratuite. Créez un tarif couvrant ce connecteur.")}
              </AlertDescription>
            </Alert>
          ))}
      </CardContent>
    </Card>
  );
}
