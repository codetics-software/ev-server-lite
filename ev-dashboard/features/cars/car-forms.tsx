"use client";

import { FieldGroup } from "@/components/ui/field";
import { FormFooter } from "@/features/shared/form-footer";
import { FormPicker, FormSwitch, FormText } from "@/features/shared/form-fields";
import { compact, field as dataField, saveRecord } from "@/features/shared/records";
import { notify } from "@/features/shared/use-action";
import { api, errorMessage } from "@/lib/api";
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
import { revalidateLogic, useForm } from "@tanstack/react-form";
import { useState } from "react";

const power = rules(optionalNumber(0, 1000, "Valeur invalide."));

export function CarForm({
  record,
  mine,
  userId,
  onSaved,
}: {
  record: RecordItem | null;
  mine: boolean;
  userId?: string;
  onSaved: () => void;
}) {
  const [error, setError] = useState("");
  const form = useForm({
    defaultValues: {
      name: record?.name ?? "",
      userId: asText(dataField(record, "userId")) || userId || "",
      carCatalogId: asText(dataField(record, "carCatalogId")),
      licensePlate: asText(dataField(record, "licensePlate")),
      vin: asText(dataField(record, "vin")),
      make: asText(dataField(record, "make")),
      model: asText(dataField(record, "model")),
      batteryKwh: asText(dataField(record, "batteryKwh")),
      maxAcPowerKw: asText(dataField(record, "maxAcPowerKw")),
      maxDcPowerKw: asText(dataField(record, "maxDcPowerKw")),
      default: dataField(record, "default") === true,
    },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      setError("");
      const data = {
        userId: mine ? undefined : value.userId || undefined,
        carCatalogId: value.carCatalogId || undefined,
        licensePlate: value.licensePlate.trim().toUpperCase() || undefined,
        vin: value.vin.trim().toUpperCase() || undefined,
        make: value.make.trim() || undefined,
        model: value.model.trim() || undefined,
        batteryKwh: toNumber(value.batteryKwh),
        maxAcPowerKw: toNumber(value.maxAcPowerKw),
        maxDcPowerKw: toNumber(value.maxDcPowerKw),
        default: value.default,
      };
      const name =
        value.name.trim() ||
        [value.make, value.model].filter(Boolean).join(" ") ||
        value.licensePlate ||
        "Véhicule";
      try {
        if (mine) {
          await api(record ? `account/cars/${record.id}` : "account/cars", {
            method: record ? "PUT" : "POST",
            body: JSON.stringify({ name, data: compact(data), revision: record?.revision }),
          });
        } else {
          await saveRecord("cars", record, name, data);
        }
        notify(record ? "Véhicule enregistré." : "Véhicule ajouté.");
        onSaved();
      } catch (failure) {
        setError(errorMessage(failure, "Impossible d’enregistrer le véhicule."));
      }
    },
  });

  async function applyCatalog(id: string) {
    if (!id) {
      return;
    }
    try {
      const entry = await api<RecordItem<Row>>(`records/car-catalog/${id}`);
      const pick = (key: string) => asText((entry.data as Row)[key]);
      form.setFieldValue("make", pick("make"));
      form.setFieldValue("model", [pick("model"), pick("version")].filter(Boolean).join(" "));
      form.setFieldValue("batteryKwh", pick("batteryKwh"));
      form.setFieldValue("maxAcPowerKw", pick("maxAcPowerKw"));
      form.setFieldValue("maxDcPowerKw", pick("maxDcPowerKw"));
    } catch {
      return;
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
        <form.Field name="name" validators={{ onDynamic: rules(maxLength(200)) }}>
          {(field) => (
            <FormText
              field={field}
              label="Nom du véhicule (facultatif)"
              placeholder="Voiture de service"
            />
          )}
        </form.Field>
        {!mine && (
          <form.Field name="userId">
            {(field) => <FormPicker field={field} source="users" label="Conducteur" />}
          </form.Field>
        )}
        <form.Field
          name="carCatalogId"
          listeners={{ onChange: ({ value }) => void applyCatalog(value) }}
        >
          {(field) => (
            <FormPicker
              field={field}
              source="car-catalog"
              label="Modèle du catalogue"
              description="Remplit automatiquement les caractéristiques."
            />
          )}
        </form.Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <form.Field name="make" validators={{ onDynamic: rules(maxLength(100)) }}>
            {(field) => <FormText field={field} label="Fabricant" />}
          </form.Field>
          <form.Field name="model" validators={{ onDynamic: rules(maxLength(100)) }}>
            {(field) => <FormText field={field} label="Modèle" />}
          </form.Field>
          <form.Field name="licensePlate" validators={{ onDynamic: rules(maxLength(20)) }}>
            {(field) => <FormText field={field} label="Immatriculation" />}
          </form.Field>
          <form.Field
            name="vin"
            validators={{
              onDynamic: rules(
                matches(
                  /^[A-HJ-NPR-Za-hj-npr-z0-9]{17}$/,
                  "Le VIN contient 17 caractères (sans I, O ni Q).",
                ),
              ),
            }}
          >
            {(field) => <FormText field={field} label="Numéro VIN" maxLength={17} />}
          </form.Field>
        </div>
        <div className="grid gap-4 sm:grid-cols-3">
          <form.Field name="batteryKwh" validators={{ onDynamic: power }}>
            {(field) => <FormText field={field} label="Batterie (kWh)" type="number" min={0} />}
          </form.Field>
          <form.Field name="maxAcPowerKw" validators={{ onDynamic: power }}>
            {(field) => <FormText field={field} label="Puissance AC (kW)" type="number" min={0} />}
          </form.Field>
          <form.Field name="maxDcPowerKw" validators={{ onDynamic: power }}>
            {(field) => <FormText field={field} label="Puissance DC (kW)" type="number" min={0} />}
          </form.Field>
        </div>
        <form.Field name="default">
          {(field) => <FormSwitch field={field} label="Véhicule par défaut" />}
        </form.Field>
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => <FormFooter error={error} submitting={submitting} />}
        </form.Subscribe>
      </FieldGroup>
    </form>
  );
}

export function CatalogForm({
  record,
  onSaved,
}: {
  record: RecordItem | null;
  onSaved: () => void;
}) {
  const [error, setError] = useState("");
  const form = useForm({
    defaultValues: {
      make: asText(dataField(record, "make")),
      model: asText(dataField(record, "model")),
      version: asText(dataField(record, "version")),
      batteryKwh: asText(dataField(record, "batteryKwh")),
      maxAcPowerKw: asText(dataField(record, "maxAcPowerKw")),
      maxDcPowerKw: asText(dataField(record, "maxDcPowerKw")),
      rangeKm: asText(dataField(record, "rangeKm")),
      imageUrl: asText(dataField(record, "imageUrl")),
    },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      setError("");
      try {
        await saveRecord(
          "car-catalog",
          record,
          [value.make, value.model, value.version].filter(Boolean).join(" "),
          {
            ...asRow(record?.data),
            make: value.make.trim(),
            model: value.model.trim(),
            version: value.version.trim() || undefined,
            batteryKwh: toNumber(value.batteryKwh),
            maxAcPowerKw: toNumber(value.maxAcPowerKw),
            maxDcPowerKw: toNumber(value.maxDcPowerKw),
            rangeKm: toNumber(value.rangeKm),
            imageUrl: value.imageUrl.trim() || undefined,
          },
        );
        notify("Modèle enregistré.");
        onSaved();
      } catch (failure) {
        setError(errorMessage(failure, "Impossible d’enregistrer le modèle."));
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
        <div className="grid gap-4 sm:grid-cols-3">
          <form.Field
            name="make"
            validators={{ onDynamic: rules(required("Fabricant requis."), maxLength(100)) }}
          >
            {(field) => <FormText field={field} label="Fabricant" required />}
          </form.Field>
          <form.Field
            name="model"
            validators={{ onDynamic: rules(required("Modèle requis."), maxLength(100)) }}
          >
            {(field) => <FormText field={field} label="Modèle" required />}
          </form.Field>
          <form.Field name="version" validators={{ onDynamic: rules(maxLength(100)) }}>
            {(field) => <FormText field={field} label="Version" />}
          </form.Field>
          <form.Field name="batteryKwh" validators={{ onDynamic: power }}>
            {(field) => <FormText field={field} label="Batterie (kWh)" type="number" />}
          </form.Field>
          <form.Field name="maxAcPowerKw" validators={{ onDynamic: power }}>
            {(field) => <FormText field={field} label="Puissance AC (kW)" type="number" />}
          </form.Field>
          <form.Field name="maxDcPowerKw" validators={{ onDynamic: power }}>
            {(field) => <FormText field={field} label="Puissance DC (kW)" type="number" />}
          </form.Field>
          <form.Field
            name="rangeKm"
            validators={{
              onDynamic: ({ value }) =>
                value && integerBetween(0, 5000, "Autonomie invalide.")(value)
                  ? { message: "Autonomie invalide." }
                  : undefined,
            }}
          >
            {(field) => <FormText field={field} label="Autonomie (km)" type="number" />}
          </form.Field>
        </div>
        <form.Field
          name="imageUrl"
          validators={{ onDynamic: rules(matches(/^https:\/\//, "Adresse HTTPS requise.")) }}
        >
          {(field) => <FormText field={field} label="Image (adresse HTTPS)" type="url" />}
        </form.Field>
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => <FormFooter error={error} submitting={submitting} />}
        </form.Subscribe>
      </FieldGroup>
    </form>
  );
}

function asRow(value: unknown): Row {
  if (!value || typeof value !== "object") {
    return {};
  }
  const { externalId } = value as Row;
  return externalId ? { externalId } : {};
}
