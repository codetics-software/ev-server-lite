"use client";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { FieldGroup, FieldLegend, FieldSet } from "@/components/ui/field";
import { FormPicker, FormSelect, FormSwitch, FormText } from "@/features/shared/form-fields";
import { notify } from "@/features/shared/use-action";
import { useComponent } from "@/features/workspace/workspace-context";
import { api, errorMessage } from "@/lib/api";
import { asText, maxLength, optionalNumber, required, rules, toNumber } from "@/lib/form";
import { IconPlus, IconTrash } from "@tabler/icons-react";
import { revalidateLogic, useForm } from "@tanstack/react-form";
import { useState } from "react";
import { connectorTypes, type Station } from "./station-types";

type ConnectorDraft = { connectorId: string; type: string; maximumPowerKw: string; phases: string };

export function StationProperties({
  station,
  canEdit,
  onSaved,
}: {
  station: Station;
  canEdit: boolean;
  onSaved: () => void;
}) {
  const organization = useComponent("organization");
  const smartCharging = useComponent("smart_charging");
  const roaming = useComponent("roaming");
  const [error, setError] = useState("");
  const form = useForm({
    defaultValues: {
      name: station.name,
      siteAreaId: asText(station.site_area_id),
      enabled: station.enabled,
      public: station.public === true,
      latitude: asText(station.latitude),
      longitude: asText(station.longitude),
      maximumPowerKw: asText(station.maximum_power_kw),
      excludeFromSmartCharging: station.exclude_from_smart_charging === true,
      connectors: (station.connectors ?? []).map((connector): ConnectorDraft => ({
        connectorId: String(connector.connector_id),
        type: asText(connector.type),
        maximumPowerKw: asText(connector.maximum_power_kw),
        phases: asText(connector.phases || 3),
      })),
    },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      setError("");
      try {
        await api(`stations/${encodeURIComponent(station.id)}`, {
          method: "PUT",
          body: JSON.stringify({
            name: value.name.trim(),
            siteAreaId: value.siteAreaId || null,
            enabled: value.enabled,
            public: value.public,
            latitude: toNumber(value.latitude) ?? null,
            longitude: toNumber(value.longitude) ?? null,
            maximumPowerKw: toNumber(value.maximumPowerKw) ?? null,
            excludeFromSmartCharging: value.excludeFromSmartCharging,
            connectors: value.connectors.map((connector) => ({
              connectorId: Number(connector.connectorId),
              type: connector.type || null,
              maximumPowerKw: toNumber(connector.maximumPowerKw) ?? null,
              phases: toNumber(connector.phases) ?? null,
            })),
          }),
        });
        notify("Propriétés de la borne enregistrées.");
        onSaved();
      } catch (failure) {
        setError(errorMessage(failure, "Impossible d’enregistrer la borne."));
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
      className="max-w-3xl"
    >
      <fieldset disabled={!canEdit} className="contents">
        <FieldGroup>
          <FieldSet>
            <FieldLegend>Identification</FieldLegend>
            <FieldGroup>
              <form.Field
                name="name"
                validators={{ onDynamic: rules(required("Renseignez un nom."), maxLength(200)) }}
              >
                {(field) => <FormText field={field} label="Nom affiché" maxLength={200} required />}
              </form.Field>
              {organization && (
                <form.Field name="siteAreaId">
                  {(field) => (
                    <FormPicker
                      field={field}
                      source="site-areas"
                      label="Zone de recharge"
                      description="Le site est déduit de la zone choisie."
                    />
                  )}
                </form.Field>
              )}
              <form.Field name="enabled">
                {(field) => (
                  <FormSwitch
                    field={field}
                    label="Borne activée"
                    description="Une borne désactivée refuse les nouvelles connexions OCPP."
                  />
                )}
              </form.Field>
              {roaming && (
                <form.Field name="public">
                  {(field) => (
                    <FormSwitch
                      field={field}
                      label="Borne publique"
                      description="Publiée aux partenaires d’itinérance lorsque son site est public."
                    />
                  )}
                </form.Field>
              )}
            </FieldGroup>
          </FieldSet>
          <FieldSet>
            <FieldLegend>Position et puissance</FieldLegend>
            <div className="grid gap-4 sm:grid-cols-3">
              <form.Field
                name="latitude"
                validators={{
                  onDynamic: rules(optionalNumber(-90, 90, "Latitude entre -90 et 90.")),
                }}
              >
                {(field) => <FormText field={field} label="Latitude" type="number" />}
              </form.Field>
              <form.Field
                name="longitude"
                validators={{
                  onDynamic: rules(optionalNumber(-180, 180, "Longitude entre -180 et 180.")),
                }}
              >
                {(field) => <FormText field={field} label="Longitude" type="number" />}
              </form.Field>
              <form.Field
                name="maximumPowerKw"
                validators={{ onDynamic: rules(optionalNumber(0, 10000, "Puissance invalide.")) }}
              >
                {(field) => (
                  <FormText field={field} label="Puissance maximale (kW)" type="number" min={0} />
                )}
              </form.Field>
            </div>
            {smartCharging && (
              <form.Field name="excludeFromSmartCharging">
                {(field) => (
                  <FormSwitch
                    field={field}
                    label="Exclure de la recharge intelligente"
                    description="La borne n’est pas prise en compte dans la répartition de puissance."
                  />
                )}
              </form.Field>
            )}
          </FieldSet>
          <form.Field name="connectors" mode="array">
            {(connectors) => (
              <FieldSet>
                <FieldLegend>Connecteurs</FieldLegend>
                {connectors.state.value.map((_, index) => (
                  <div
                    key={index}
                    className="grid items-end gap-3 rounded-lg border p-3 sm:grid-cols-[5rem_1fr_8rem_7rem_auto]"
                  >
                    <form.Field name={`connectors[${index}].connectorId`}>
                      {(field) => <FormText field={field} label="N°" type="number" min={1} />}
                    </form.Field>
                    <form.Field name={`connectors[${index}].type`}>
                      {(field) => (
                        <FormSelect field={field} label="Type" options={connectorTypes} />
                      )}
                    </form.Field>
                    <form.Field
                      name={`connectors[${index}].maximumPowerKw`}
                      validators={{
                        onDynamic: rules(optionalNumber(0, 1000, "Puissance invalide.")),
                      }}
                    >
                      {(field) => (
                        <FormText field={field} label="Puissance (kW)" type="number" min={0} />
                      )}
                    </form.Field>
                    <form.Field name={`connectors[${index}].phases`}>
                      {(field) => (
                        <FormSelect
                          field={field}
                          label="Phases"
                          options={[
                            { value: "1", label: "Monophasé" },
                            { value: "3", label: "Triphasé" },
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
          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}
          {canEdit && (
            <form.Subscribe selector={(state) => state.isSubmitting}>
              {(submitting) => (
                <Button type="submit" className="w-fit" disabled={submitting}>
                  {submitting ? "Enregistrement…" : "Enregistrer les propriétés"}
                </Button>
              )}
            </form.Subscribe>
          )}
        </FieldGroup>
      </fieldset>
    </form>
  );
}
