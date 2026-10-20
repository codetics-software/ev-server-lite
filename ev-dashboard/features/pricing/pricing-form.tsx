"use client";

import { FieldDescription, FieldGroup, FieldLegend, FieldSet } from "@/components/ui/field";
import { Label } from "@/components/ui/label";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { FormFooter } from "@/features/shared/form-footer";
import { FormPicker, FormSelect, FormSwitch, FormText } from "@/features/shared/form-fields";
import { compact, field as dataField, saveRecord } from "@/features/shared/records";
import { notify } from "@/features/shared/use-action";
import { errorMessage } from "@/lib/api";
import type { RecordItem, Row } from "@/lib/api-types";
import { fromInputDateTime, toInputDateTime } from "@/lib/format";
import {
  asText,
  integerBetween,
  matches,
  maxLength,
  optionalNumber,
  required,
  rules,
  toNumber,
} from "@/lib/form";
import { revalidateLogic, useForm } from "@tanstack/react-form";
import { useState } from "react";

const pressed =
  "aria-pressed:border-primary aria-pressed:bg-primary aria-pressed:text-primary-foreground";

export const days = [
  { value: "1", label: "Lun.", name: "lundi" },
  { value: "2", label: "Mar.", name: "mardi" },
  { value: "3", label: "Mer.", name: "mercredi" },
  { value: "4", label: "Jeu.", name: "jeudi" },
  { value: "5", label: "Ven.", name: "vendredi" },
  { value: "6", label: "Sam.", name: "samedi" },
  { value: "7", label: "Dim.", name: "dimanche" },
];

const currencies = ["EUR", "CHF", "GBP", "USD", "XPF"].map((value) => ({ value, label: value }));
const price = rules(optionalNumber(0, 10000, "Prix invalide."));
const clock = rules(matches(/^([01]\d|2[0-3]):[0-5]\d$/, "Heure au format HH:mm."));
const optionalInteger =
  (min: number, max: number, message: string) =>
  ({ value }: { value: string }) =>
    value && integerBetween(min, max, message)(value) ? { message } : undefined;

export function PricingForm({
  record,
  onSaved,
}: {
  record: RecordItem | null;
  onSaved: () => void;
}) {
  const [error, setError] = useState("");
  const scope = (dataField<Row>(record, "scope") ?? {}) as Row;
  const restrictions = (dataField<Row>(record, "restrictions") ?? {}) as Row;
  const form = useForm({
    defaultValues: {
      name: record?.name ?? "",
      active: dataField(record, "active") !== false,
      priority: asText(dataField(record, "priority") ?? 0),
      currency: asText(dataField(record, "currency")) || "EUR",
      flatPrice: asText(dataField(record, "flatPrice")),
      energyPrice: asText(dataField(record, "energyPrice")),
      timePrice: asText(dataField(record, "timePrice")),
      parkingPrice: asText(dataField(record, "parkingPrice")),
      energyStepWh: asText(dataField(record, "energyStepWh")),
      siteId: asText(scope.siteId),
      siteAreaId: asText(scope.siteAreaId),
      stationId: asText(scope.stationId),
      connectorId: asText(scope.connectorId),
      validFrom: toInputDateTime(dataField(record, "validFrom")),
      validTo: toInputDateTime(dataField(record, "validTo")),
      daysOfWeek: ((restrictions.daysOfWeek as number[] | undefined) ?? []).map(String),
      timeFrom: asText(restrictions.timeFrom),
      timeTo: asText(restrictions.timeTo),
      minEnergyKwh: asText(restrictions.minEnergyKwh),
      maxEnergyKwh: asText(restrictions.maxEnergyKwh),
      minDurationMinutes: asText(restrictions.minDurationMinutes),
      maxDurationMinutes: asText(restrictions.maxDurationMinutes),
    },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      setError("");
      const scopeData = compact({
        siteId: value.siteId || undefined,
        siteAreaId: value.siteAreaId || undefined,
        stationId: value.stationId || undefined,
        connectorId: value.stationId ? toNumber(value.connectorId) : undefined,
      });
      const restrictionData = compact({
        daysOfWeek:
          value.daysOfWeek.length && value.daysOfWeek.length < 7
            ? value.daysOfWeek.map(Number).sort()
            : undefined,
        timeFrom: value.timeFrom || undefined,
        timeTo: value.timeTo || undefined,
        minEnergyKwh: toNumber(value.minEnergyKwh),
        maxEnergyKwh: toNumber(value.maxEnergyKwh),
        minDurationMinutes: toNumber(value.minDurationMinutes),
        maxDurationMinutes: toNumber(value.maxDurationMinutes),
      });
      try {
        await saveRecord("pricing-definitions", record, value.name, {
          active: value.active,
          priority: toNumber(value.priority) ?? 0,
          currency: value.currency,
          flatPrice: toNumber(value.flatPrice),
          energyPrice: toNumber(value.energyPrice),
          timePrice: toNumber(value.timePrice),
          parkingPrice: toNumber(value.parkingPrice),
          energyStepWh: toNumber(value.energyStepWh),
          scope: Object.keys(scopeData).length ? scopeData : undefined,
          validFrom: fromInputDateTime(value.validFrom) ?? undefined,
          validTo: fromInputDateTime(value.validTo) ?? undefined,
          restrictions: Object.keys(restrictionData).length ? restrictionData : undefined,
        });
        notify(record ? "Tarif enregistré." : "Tarif créé.");
        onSaved();
      } catch (failure) {
        setError(errorMessage(failure, "Impossible d’enregistrer le tarif."));
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
        <div className="grid gap-4 sm:grid-cols-[1fr_8rem_8rem]">
          <form.Field
            name="name"
            validators={{
              onDynamic: rules(required("Renseignez le nom du tarif."), maxLength(200)),
            }}
          >
            {(field) => <FormText field={field} label="Nom du tarif" maxLength={200} required />}
          </form.Field>
          <form.Field name="currency">
            {(field) => <FormSelect field={field} label="Devise" options={currencies} />}
          </form.Field>
          <form.Field
            name="priority"
            validators={{ onDynamic: optionalInteger(-1000, 1000, "Entre -1 000 et 1 000.") }}
          >
            {(field) => <FormText field={field} label="Priorité" type="number" />}
          </form.Field>
        </div>
        <form.Field name="active">
          {(field) => (
            <FormSwitch
              field={field}
              label="Tarif actif"
              description="Un tarif inactif n’est jamais appliqué."
            />
          )}
        </form.Field>
        <FieldSet>
          <FieldLegend>Prix</FieldLegend>
          <FieldDescription>
            Montants hors taxes de la devise choisie. Laissez vide ce qui n’est pas facturé.
          </FieldDescription>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <form.Field name="flatPrice" validators={{ onDynamic: price }}>
              {(field) => (
                <FormText field={field} label="Frais par session" type="number" min={0} />
              )}
            </form.Field>
            <form.Field name="energyPrice" validators={{ onDynamic: price }}>
              {(field) => <FormText field={field} label="Prix par kWh" type="number" min={0} />}
            </form.Field>
            <form.Field name="timePrice" validators={{ onDynamic: price }}>
              {(field) => (
                <FormText field={field} label="Prix par heure de recharge" type="number" min={0} />
              )}
            </form.Field>
            <form.Field name="parkingPrice" validators={{ onDynamic: price }}>
              {(field) => (
                <FormText
                  field={field}
                  label="Prix par heure d’inactivité"
                  type="number"
                  min={0}
                  description="Après la fin de charge."
                />
              )}
            </form.Field>
          </div>
          <form.Field
            name="energyStepWh"
            validators={{ onDynamic: optionalInteger(1, 100000, "Entre 1 et 100 000 Wh.") }}
          >
            {(field) => (
              <FormText
                field={field}
                label="Pas de facturation de l’énergie (Wh)"
                type="number"
                min={1}
                description="Par exemple 1000 pour facturer par kWh entamé."
                className="sm:max-w-60"
              />
            )}
          </form.Field>
        </FieldSet>
        <FieldSet>
          <FieldLegend>Portée</FieldLegend>
          <FieldDescription>
            Sans portée, le tarif s’applique à tout l’espace. Le tarif le plus précis l’emporte
            (connecteur, borne, zone, site), puis la priorité la plus élevée.
          </FieldDescription>
          <div className="grid gap-4 sm:grid-cols-2">
            <form.Field name="siteId">
              {(field) => (
                <FormPicker
                  field={field}
                  source="sites"
                  label="Site"
                  placeholder="Tous les sites"
                />
              )}
            </form.Field>
            <form.Subscribe selector={(state) => state.values.siteId}>
              {(siteId) => (
                <form.Field name="siteAreaId">
                  {(field) => (
                    <FormPicker
                      field={field}
                      source="site-areas"
                      filters={{ siteId: siteId || undefined }}
                      label="Zone"
                      placeholder="Toutes les zones"
                    />
                  )}
                </form.Field>
              )}
            </form.Subscribe>
            <form.Field name="stationId">
              {(field) => (
                <FormPicker
                  field={field}
                  source="stations"
                  label="Borne"
                  placeholder="Toutes les bornes"
                />
              )}
            </form.Field>
            <form.Subscribe selector={(state) => state.values.stationId}>
              {(stationId) => (
                <form.Field
                  name="connectorId"
                  validators={{ onDynamic: optionalInteger(1, 100, "Connecteur invalide.") }}
                >
                  {(field) => (
                    <FormText
                      field={field}
                      label="Connecteur"
                      type="number"
                      min={1}
                      disabled={!stationId}
                    />
                  )}
                </form.Field>
              )}
            </form.Subscribe>
          </div>
        </FieldSet>
        <FieldSet>
          <FieldLegend>Validité et restrictions</FieldLegend>
          <div className="grid gap-4 sm:grid-cols-2">
            <form.Field name="validFrom">
              {(field) => (
                <FormText field={field} label="Valable à partir du" type="datetime-local" />
              )}
            </form.Field>
            <form.Field
              name="validTo"
              validators={{
                onDynamic: ({ value, fieldApi }) => {
                  const from = fieldApi.form.getFieldValue("validFrom");
                  return value && from && value <= from
                    ? { message: "La fin doit suivre le début." }
                    : undefined;
                },
              }}
            >
              {(field) => <FormText field={field} label="Jusqu’au" type="datetime-local" />}
            </form.Field>
          </div>
          <form.Field name="daysOfWeek">
            {(field) => (
              <div className="flex flex-col gap-2">
                <Label>Jours d’application</Label>
                <ToggleGroup
                  multiple
                  variant="outline"
                  className="flex-wrap"
                  value={field.state.value}
                  onValueChange={(value) => field.handleChange(value as string[])}
                  aria-label="Jours d’application"
                >
                  {days.map((day) => (
                    <ToggleGroupItem
                      key={day.value}
                      value={day.value}
                      aria-label={day.name}
                      className={pressed}
                    >
                      {day.label}
                    </ToggleGroupItem>
                  ))}
                </ToggleGroup>
                <p className="text-xs text-muted-foreground">
                  Aucun jour sélectionné : tous les jours.
                </p>
              </div>
            )}
          </form.Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <form.Field
              name="timeFrom"
              validators={{
                onDynamic: ({ value, fieldApi }) =>
                  (value && clock({ value })) ||
                  (Boolean(value) !== Boolean(fieldApi.form.getFieldValue("timeTo"))
                    ? { message: "Renseignez les deux heures." }
                    : undefined),
              }}
            >
              {(field) => (
                <FormText field={field} label="De (heure de début de session)" type="time" />
              )}
            </form.Field>
            <form.Field name="timeTo" validators={{ onDynamic: clock }}>
              {(field) => <FormText field={field} label="À" type="time" />}
            </form.Field>
            <form.Field name="minEnergyKwh" validators={{ onDynamic: price }}>
              {(field) => (
                <FormText field={field} label="Énergie minimale (kWh)" type="number" min={0} />
              )}
            </form.Field>
            <form.Field name="maxEnergyKwh" validators={{ onDynamic: price }}>
              {(field) => (
                <FormText field={field} label="Énergie maximale (kWh)" type="number" min={0} />
              )}
            </form.Field>
            <form.Field
              name="minDurationMinutes"
              validators={{ onDynamic: optionalInteger(0, 100000, "Durée invalide.") }}
            >
              {(field) => (
                <FormText field={field} label="Durée minimale (min)" type="number" min={0} />
              )}
            </form.Field>
            <form.Field
              name="maxDurationMinutes"
              validators={{ onDynamic: optionalInteger(1, 100000, "Durée invalide.") }}
            >
              {(field) => (
                <FormText field={field} label="Durée maximale (min)" type="number" min={1} />
              )}
            </form.Field>
          </div>
        </FieldSet>
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => <FormFooter error={error} submitting={submitting} />}
        </form.Subscribe>
      </FieldGroup>
    </form>
  );
}
