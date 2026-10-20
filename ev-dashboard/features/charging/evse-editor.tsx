"use client";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { FormPicker } from "@/features/shared/form-fields";
import { useApi } from "@/hooks/use-api";
import { api } from "@/lib/api";
import type { PageData, Row } from "@/lib/api-types";
import { integerBetween, isInvalid, maxLength, required, rules } from "@/lib/form";
import { revalidateLogic, useForm, useStore } from "@tanstack/react-form";
import { useState } from "react";
import type { Evse } from "./portal-types";

export function EvseEditor({
  evse,
  onClose,
  onSaved,
}: {
  evse: Evse | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { data: tariffs } = useApi<PageData>("records/pricing-definitions?limit=100");
  const [error, setError] = useState("");
  const items = tariffs?.items ?? [];
  const firstTariff = String(items[0]?.id ?? "");
  const form = useForm({
    defaultValues: {
      name: evse?.name ?? "",
      stationId: evse?.stationId ?? "",
      connectorId: String(evse?.connectorId ?? 1),
      pricingDefinitionId: evse?.pricingDefinitionId ?? "",
      published: evse?.published ?? false,
    },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      setError("");
      try {
        await api(`evses${evse ? `/${evse.id}` : ""}`, {
          method: evse ? "PUT" : "POST",
          body: JSON.stringify({
            name: value.name,
            stationId: value.stationId,
            connectorId: Number(value.connectorId),
            pricingDefinitionId: value.pricingDefinitionId || firstTariff,
            published: value.published,
          }),
        });
        onSaved();
        onClose();
      } catch (failure) {
        setError(
          failure instanceof Error
            ? failure.message
            : "Impossible d’enregistrer le point de charge.",
        );
      }
    },
  });
  const pending = useStore(form.store, (state) => state.isSubmitting);
  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {evse ? "Modifier le point de charge" : "Ajouter un point de charge"}
          </DialogTitle>
          <DialogDescription>
            Associez un connecteur à un tarif pour créer son portail public.
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
              name="name"
              validators={{
                onDynamic: rules(
                  required("Renseignez le nom du point de charge."),
                  maxLength(200, "Le nom contient au plus 200 caractères."),
                ),
              }}
            >
              {(field) => {
                const invalid = isInvalid(field);
                return (
                  <Field data-invalid={invalid}>
                    <FieldLabel htmlFor="evse-name">Nom affiché aux conducteurs</FieldLabel>
                    <Input
                      id="evse-name"
                      name={field.name}
                      value={field.state.value}
                      onBlur={field.handleBlur}
                      onChange={(event) => field.handleChange(event.target.value)}
                      aria-invalid={invalid}
                      maxLength={200}
                      required
                    />
                    {invalid && <FieldError errors={field.state.meta.errors} />}
                  </Field>
                );
              }}
            </form.Field>
            <form.Field
              name="stationId"
              validators={{
                onDynamic: rules(required("Choisissez la borne.")),
              }}
            >
              {(field) => <FormPicker field={field} source="stations" label="Borne" />}
            </form.Field>
            <form.Field
              name="connectorId"
              validators={{
                onDynamic: rules(
                  integerBetween(
                    1,
                    Number.MAX_SAFE_INTEGER,
                    "Sélectionnez un connecteur physique.",
                  ),
                ),
              }}
            >
              {(field) => {
                const invalid = isInvalid(field);
                return (
                  <Field data-invalid={invalid}>
                    <FieldLabel htmlFor="evse-connector">Numéro du connecteur</FieldLabel>
                    <Input
                      id="evse-connector"
                      name={field.name}
                      type="number"
                      min="1"
                      step="1"
                      value={field.state.value}
                      onBlur={field.handleBlur}
                      onChange={(event) => field.handleChange(event.target.value)}
                      aria-invalid={invalid}
                      required
                    />
                    <FieldDescription>
                      Le connecteur doit avoir été déclaré par la borne.
                    </FieldDescription>
                    {invalid && <FieldError errors={field.state.meta.errors} />}
                  </Field>
                );
              }}
            </form.Field>
            <form.Field name="pricingDefinitionId">
              {(field) => (
                <Field>
                  <FieldLabel htmlFor="evse-tariff">Tarif</FieldLabel>
                  <Select
                    name={field.name}
                    value={field.state.value || firstTariff}
                    onValueChange={(value) => field.handleChange(value ?? "")}
                  >
                    <SelectTrigger id="evse-tariff" className="w-full">
                      <SelectValue placeholder="Choisir un tarif">
                        {(value: string | null) => {
                          const tariff = items.find((item) => String(item.id) === value);
                          return tariff ? String(tariff.name) : "Choisir un tarif";
                        }}
                      </SelectValue>
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        {items.map((item: Row) => (
                          <SelectItem key={String(item.id)} value={String(item.id)}>
                            {String(item.name)}
                          </SelectItem>
                        ))}
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                  <FieldDescription>
                    Créez d’abord un tarif en euros dans la page Tarifs.
                  </FieldDescription>
                </Field>
              )}
            </form.Field>
            <form.Field name="published">
              {(field) => (
                <Field orientation="horizontal">
                  <Checkbox
                    id="evse-published"
                    name={field.name}
                    checked={field.state.value}
                    onCheckedChange={(checked) => field.handleChange(checked === true)}
                  />
                  <FieldLabel htmlFor="evse-published">Publier le portail de paiement</FieldLabel>
                </Field>
              )}
            </form.Field>
            {error && (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}
            <Button type="submit" disabled={pending || !items.length}>
              {pending ? "Enregistrement…" : "Enregistrer"}
            </Button>
          </FieldGroup>
        </form>
      </DialogContent>
    </Dialog>
  );
}
