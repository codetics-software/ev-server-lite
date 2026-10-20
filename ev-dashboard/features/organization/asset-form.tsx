"use client";

import { FieldGroup, FieldLegend, FieldSet } from "@/components/ui/field";
import { FormFooter } from "@/features/shared/form-footer";
import { FormPicker, FormSelect, FormSwitch, FormText } from "@/features/shared/form-fields";
import { field as dataField, saveRecord } from "@/features/shared/records";
import { notify } from "@/features/shared/use-action";
import { errorMessage } from "@/lib/api";
import type { Row } from "@/lib/api-types";
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
import type { FormProps } from "./organization-forms";

export const assetTypes = [
  { value: "CONSUMPTION", label: "Consommation" },
  { value: "PRODUCTION", label: "Production" },
  { value: "CONSUMPTION_AND_PRODUCTION", label: "Consommation et production" },
];

export const assetKinds = [
  { value: "METER", label: "Compteur" },
  { value: "SOLAR", label: "Production solaire" },
  { value: "BATTERY", label: "Batterie" },
  { value: "BUILDING", label: "Bâtiment" },
];

export const connectionTypes = [
  { value: "NONE", label: "Aucune (puissance fixe)" },
  { value: "PUSH", label: "Envoi par l’équipement" },
  { value: "SCHNEIDER", label: "Schneider" },
  { value: "GREENCOM", label: "GreenCom" },
  { value: "IOTHINK", label: "ioThink" },
  { value: "LACROIX", label: "Lacroix" },
  { value: "WIT", label: "WIT" },
];

export function AssetForm({ record, onSaved, defaults }: FormProps) {
  const [error, setError] = useState("");
  const connection = (dataField<Row>(record, "connection") ?? {}) as Row;
  const form = useForm({
    defaultValues: {
      name: record?.name ?? "",
      siteAreaId: asText(dataField(record, "siteAreaId")) || defaults?.siteAreaId || "",
      type: asText(dataField(record, "type")) || "CONSUMPTION",
      kind: asText(dataField(record, "kind")) || "METER",
      maximumPowerKw: asText(dataField(record, "maximumPowerKw")),
      staticPowerKw: asText(dataField(record, "staticPowerKw")),
      excludeFromSmartCharging: dataField(record, "excludeFromSmartCharging") === true,
      connectionType: asText(connection.type) || "NONE",
      url: asText(connection.url),
      authenticationUrl: asText(connection.authenticationUrl),
      clientId: asText(connection.clientId),
      meterId: asText(connection.meterId),
      user: asText(connection.user),
      refreshSeconds: asText(connection.refreshSeconds ?? 300),
    },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      setError("");
      try {
        const saved = await saveRecord("assets", record, value.name, {
          siteAreaId: value.siteAreaId || undefined,
          type: value.type,
          kind: value.kind || undefined,
          maximumPowerKw: toNumber(value.maximumPowerKw),
          staticPowerKw: toNumber(value.staticPowerKw),
          excludeFromSmartCharging: value.excludeFromSmartCharging,
          connection:
            value.connectionType === "NONE"
              ? undefined
              : {
                  type: value.connectionType,
                  url: value.url || undefined,
                  authenticationUrl: value.authenticationUrl || undefined,
                  clientId: value.clientId || undefined,
                  meterId: value.meterId || undefined,
                  user: value.user || undefined,
                  refreshSeconds: toNumber(value.refreshSeconds),
                },
        });
        notify(record ? "Équipement enregistré." : "Équipement créé.");
        onSaved(saved?.id ?? record?.id ?? "");
      } catch (failure) {
        setError(errorMessage(failure, "Impossible d’enregistrer l’équipement."));
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
        <form.Field
          name="name"
          validators={{
            onDynamic: rules(required("Renseignez le nom de l’équipement."), maxLength(200)),
          }}
        >
          {(field) => (
            <FormText field={field} label="Nom de l’équipement" maxLength={200} required />
          )}
        </form.Field>
        <form.Field name="siteAreaId">
          {(field) => (
            <FormPicker
              field={field}
              source="site-areas"
              label="Zone de recharge"
              description="Sa puissance est prise en compte dans le calcul de la zone."
            />
          )}
        </form.Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <form.Field name="type">
            {(field) => <FormSelect field={field} label="Sens" options={assetTypes} />}
          </form.Field>
          <form.Field name="kind">
            {(field) => <FormSelect field={field} label="Catégorie" options={assetKinds} />}
          </form.Field>
          <form.Field
            name="maximumPowerKw"
            validators={{ onDynamic: rules(optionalNumber(0, 100000, "Puissance invalide.")) }}
          >
            {(field) => (
              <FormText field={field} label="Puissance maximale (kW)" type="number" min={0} />
            )}
          </form.Field>
          <form.Field
            name="staticPowerKw"
            validators={{ onDynamic: rules(optionalNumber(0, 100000, "Puissance invalide.")) }}
          >
            {(field) => (
              <FormText
                field={field}
                label="Puissance fixe (kW)"
                type="number"
                min={0}
                description="Utilisée sans mesure en direct."
              />
            )}
          </form.Field>
        </div>
        <form.Field name="excludeFromSmartCharging">
          {(field) => <FormSwitch field={field} label="Exclure de la recharge intelligente" />}
        </form.Field>
        <FieldSet>
          <FieldLegend variant="label">Connexion aux mesures</FieldLegend>
          <form.Field name="connectionType">
            {(field) => <FormSelect field={field} label="Fournisseur" options={connectionTypes} />}
          </form.Field>
          <form.Subscribe selector={(state) => state.values.connectionType}>
            {(type) =>
              type !== "NONE" &&
              type !== "PUSH" && (
                <div className="grid gap-4 sm:grid-cols-2">
                  <form.Field
                    name="url"
                    validators={{
                      onDynamic: rules(matches(/^https:\/\//, "Adresse HTTPS requise.")),
                    }}
                  >
                    {(field) => <FormText field={field} label="Adresse du service" type="url" />}
                  </form.Field>
                  <form.Field
                    name="authenticationUrl"
                    validators={{
                      onDynamic: rules(matches(/^https:\/\//, "Adresse HTTPS requise.")),
                    }}
                  >
                    {(field) => (
                      <FormText field={field} label="Adresse d’authentification" type="url" />
                    )}
                  </form.Field>
                  <form.Field name="clientId" validators={{ onDynamic: rules(maxLength(200)) }}>
                    {(field) => (
                      <FormText field={field} label="Identifiant client" autoComplete="off" />
                    )}
                  </form.Field>
                  <form.Field name="meterId" validators={{ onDynamic: rules(maxLength(100)) }}>
                    {(field) => <FormText field={field} label="Identifiant du compteur" />}
                  </form.Field>
                  <form.Field name="user" validators={{ onDynamic: rules(maxLength(200)) }}>
                    {(field) => <FormText field={field} label="Utilisateur" autoComplete="off" />}
                  </form.Field>
                  <form.Field
                    name="refreshSeconds"
                    validators={{
                      onDynamic: rules(integerBetween(60, 86400, "Entre 60 secondes et un jour.")),
                    }}
                  >
                    {(field) => (
                      <FormText field={field} label="Fréquence de relevé (s)" type="number" />
                    )}
                  </form.Field>
                </div>
              )
            }
          </form.Subscribe>
        </FieldSet>
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => <FormFooter error={error} submitting={submitting} />}
        </form.Subscribe>
      </FieldGroup>
    </form>
  );
}
