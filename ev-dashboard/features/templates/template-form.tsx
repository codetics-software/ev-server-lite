"use client";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FieldDescription, FieldGroup, FieldLegend, FieldSet } from "@/components/ui/field";
import { connectorTypes } from "@/features/stations/station-types";
import { FormFooter } from "@/features/shared/form-footer";
import { FormSelect, FormText } from "@/features/shared/form-fields";
import { field as dataField, saveRecord } from "@/features/shared/records";
import { notify } from "@/features/shared/use-action";
import { errorMessage } from "@/lib/api";
import type { RecordItem, Row } from "@/lib/api-types";
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
import { IconPlus, IconTrash } from "@tabler/icons-react";
import { revalidateLogic, useForm } from "@tanstack/react-form";
import { useState } from "react";

type ConnectorDraft = { connectorId: string; type: string; maximumPowerKw: string; phases: string };
type ParameterDraft = { key: string; value: string };

const pattern = ({ value }: { value: string }) => {
  if (!value) {
    return undefined;
  }
  try {
    new RegExp(value);
    return undefined;
  } catch {
    return { message: "Expression régulière invalide." };
  }
};

export function TemplateForm({
  record,
  onSaved,
}: {
  record: RecordItem | null;
  onSaved: () => void;
}) {
  const [error, setError] = useState("");
  const [sample, setSample] = useState({ vendor: "", model: "" });
  const form = useForm({
    defaultValues: {
      name: record?.name ?? "",
      vendor: asText(dataField(record, "vendor")),
      model: asText(dataField(record, "model")),
      firmwareVersion: asText(dataField(record, "firmwareVersion")),
      maximumPowerKw: asText(dataField(record, "maximumPowerKw")),
      connectors: ((dataField<Row[]>(record, "connectors") ?? []) as Row[]).map(
        (connector): ConnectorDraft => ({
          connectorId: asText(connector.connectorId),
          type: asText(connector.type),
          maximumPowerKw: asText(connector.maximumPowerKw),
          phases: asText(connector.phases ?? 3),
        }),
      ),
      parameters: ((dataField<Row[]>(record, "parameters") ?? []) as Row[]).map(
        (parameter): ParameterDraft => ({
          key: asText(parameter.key),
          value: asText(parameter.value),
        }),
      ),
    },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      setError("");
      try {
        await saveRecord("charging-station-templates", record, value.name, {
          vendor: value.vendor,
          model: value.model,
          firmwareVersion: value.firmwareVersion || undefined,
          maximumPowerKw: toNumber(value.maximumPowerKw),
          connectors: value.connectors.map((connector) => ({
            connectorId: Number(connector.connectorId),
            type: connector.type || undefined,
            maximumPowerKw: toNumber(connector.maximumPowerKw),
            phases: toNumber(connector.phases),
          })),
          parameters: value.parameters
            .filter((parameter) => parameter.key.trim())
            .map((parameter) => ({ key: parameter.key.trim(), value: parameter.value })),
        });
        notify(record ? "Modèle enregistré." : "Modèle créé.");
        onSaved();
      } catch (failure) {
        setError(errorMessage(failure, "Impossible d’enregistrer le modèle."));
      }
    },
  });

  function test(source: string, value: string) {
    if (!source) {
      return null;
    }
    try {
      return new RegExp(`^(?:${source})$`, "i").test(value);
    } catch {
      return null;
    }
  }

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
          validators={{ onDynamic: rules(required("Renseignez un nom."), maxLength(200)) }}
        >
          {(field) => <FormText field={field} label="Nom du modèle de configuration" required />}
        </form.Field>
        <FieldSet>
          <FieldLegend>Bornes concernées</FieldLegend>
          <FieldDescription>
            Expressions régulières comparées au fabricant, au modèle et à la version annoncés au
            démarrage de la borne.
          </FieldDescription>
          <div className="grid gap-4 sm:grid-cols-3">
            <form.Field
              name="vendor"
              validators={{
                onDynamic: rules(required("Requis."), maxLength(200)),
                onBlur: pattern,
              }}
            >
              {(field) => (
                <FormText
                  field={field}
                  label="Fabricant"
                  placeholder="ABB|Schneider.*"
                  className="font-mono"
                  required
                />
              )}
            </form.Field>
            <form.Field
              name="model"
              validators={{
                onDynamic: rules(required("Requis."), maxLength(200)),
                onBlur: pattern,
              }}
            >
              {(field) => (
                <FormText
                  field={field}
                  label="Modèle"
                  placeholder="Terra AC.*"
                  className="font-mono"
                  required
                />
              )}
            </form.Field>
            <form.Field name="firmwareVersion" validators={{ onBlur: pattern }}>
              {(field) => (
                <FormText
                  field={field}
                  label="Version (facultative)"
                  placeholder="1\.2\..*"
                  className="font-mono"
                />
              )}
            </form.Field>
          </div>
          <form.Subscribe selector={(state) => [state.values.vendor, state.values.model] as const}>
            {([vendor, model]) => {
              const vendorMatch = sample.vendor ? test(vendor, sample.vendor) : null;
              const modelMatch = sample.model ? test(model, sample.model) : null;
              return (
                <div className="grid items-end gap-3 rounded-lg border border-dashed p-3 sm:grid-cols-[1fr_1fr_auto]">
                  <label className="flex flex-col gap-1 text-xs text-muted-foreground">
                    Fabricant à tester
                    <Input
                      value={sample.vendor}
                      onChange={(event) => setSample({ ...sample, vendor: event.target.value })}
                    />
                  </label>
                  <label className="flex flex-col gap-1 text-xs text-muted-foreground">
                    Modèle à tester
                    <Input
                      value={sample.model}
                      onChange={(event) => setSample({ ...sample, model: event.target.value })}
                    />
                  </label>
                  <span className="text-sm" aria-live="polite">
                    {vendorMatch === null && modelMatch === null
                      ? "Saisissez une borne pour tester"
                      : vendorMatch !== false && modelMatch !== false
                        ? "Correspond"
                        : "Ne correspond pas"}
                  </span>
                </div>
              );
            }}
          </form.Subscribe>
          <form.Field
            name="maximumPowerKw"
            validators={{ onDynamic: rules(optionalNumber(0, 10000, "Puissance invalide.")) }}
          >
            {(field) => (
              <FormText
                field={field}
                label="Puissance maximale (kW)"
                type="number"
                className="sm:max-w-48"
              />
            )}
          </form.Field>
        </FieldSet>
        <form.Field name="connectors" mode="array">
          {(connectors) => (
            <FieldSet>
              <FieldLegend>Connecteurs</FieldLegend>
              {connectors.state.value.map((_, index) => (
                <div
                  key={index}
                  className="grid items-end gap-3 sm:grid-cols-[5rem_1fr_8rem_8rem_auto]"
                >
                  <form.Field
                    name={`connectors[${index}].connectorId`}
                    validators={{ onDynamic: rules(integerBetween(1, 100, "1 à 100.")) }}
                  >
                    {(field) => <FormText field={field} label="N°" type="number" min={1} />}
                  </form.Field>
                  <form.Field name={`connectors[${index}].type`}>
                    {(field) => <FormSelect field={field} label="Type" options={connectorTypes} />}
                  </form.Field>
                  <form.Field
                    name={`connectors[${index}].maximumPowerKw`}
                    validators={{ onDynamic: rules(optionalNumber(0, 1000, "Invalide.")) }}
                  >
                    {(field) => <FormText field={field} label="Puissance (kW)" type="number" />}
                  </form.Field>
                  <form.Field name={`connectors[${index}].phases`}>
                    {(field) => (
                      <FormSelect
                        field={field}
                        label="Phases"
                        options={[
                          { value: "3", label: "Triphasé" },
                          { value: "1", label: "Monophasé" },
                        ]}
                      />
                    )}
                  </form.Field>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    aria-label={`Retirer le connecteur ${index + 1}`}
                    onClick={() => connectors.removeValue(index)}
                  >
                    <IconTrash />
                  </Button>
                </div>
              ))}
              <Button
                type="button"
                variant="outline"
                className="w-fit"
                onClick={() =>
                  connectors.pushValue({
                    connectorId: String(connectors.state.value.length + 1),
                    type: "Type2",
                    maximumPowerKw: "22",
                    phases: "3",
                  })
                }
              >
                <IconPlus data-icon="inline-start" />
                Ajouter un connecteur
              </Button>
            </FieldSet>
          )}
        </form.Field>
        <form.Field name="parameters" mode="array">
          {(parameters) => (
            <FieldSet>
              <FieldLegend>Paramètres OCPP appliqués</FieldLegend>
              <FieldDescription>
                Envoyés par ChangeConfiguration lorsque le modèle est appliqué à une borne.
              </FieldDescription>
              {parameters.state.value.map((_, index) => (
                <div key={index} className="grid items-end gap-3 sm:grid-cols-[1fr_1fr_auto]">
                  <form.Field
                    name={`parameters[${index}].key`}
                    validators={{
                      onDynamic: rules(
                        matches(/^[A-Za-z0-9_.:-]{1,100}$/, "Nom de paramètre invalide."),
                      ),
                    }}
                  >
                    {(field) => (
                      <FormText
                        field={field}
                        label="Paramètre"
                        className="font-mono"
                        placeholder="HeartbeatInterval"
                      />
                    )}
                  </form.Field>
                  <form.Field
                    name={`parameters[${index}].value`}
                    validators={{ onDynamic: rules(maxLength(500)) }}
                  >
                    {(field) => <FormText field={field} label="Valeur" className="font-mono" />}
                  </form.Field>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    aria-label={`Retirer le paramètre ${index + 1}`}
                    onClick={() => parameters.removeValue(index)}
                  >
                    <IconTrash />
                  </Button>
                </div>
              ))}
              <Button
                type="button"
                variant="outline"
                className="w-fit"
                onClick={() => parameters.pushValue({ key: "", value: "" })}
              >
                <IconPlus data-icon="inline-start" />
                Ajouter un paramètre
              </Button>
            </FieldSet>
          )}
        </form.Field>
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => <FormFooter error={error} submitting={submitting} />}
        </form.Subscribe>
      </FieldGroup>
    </form>
  );
}
