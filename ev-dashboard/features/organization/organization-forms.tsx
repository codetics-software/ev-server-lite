"use client";

import { Button } from "@/components/ui/button";
import { FieldGroup, FieldLegend, FieldSet } from "@/components/ui/field";
import { FormFooter } from "@/features/shared/form-footer";
import { FormPicker, FormSelect, FormSwitch, FormText } from "@/features/shared/form-fields";
import { field as dataField, saveRecord } from "@/features/shared/records";
import { notify } from "@/features/shared/use-action";
import { errorMessage } from "@/lib/api";
import type { RecordItem } from "@/lib/api-types";
import {
  asText,
  email,
  matches,
  maxLength,
  optionalNumber,
  required,
  rules,
  toNumber,
} from "@/lib/form";
import { t } from "@/lib/i18n";
import { IconCurrentLocation } from "@tabler/icons-react";
import { revalidateLogic, useForm } from "@tanstack/react-form";
import { useState } from "react";

const countryRule = (value: string) =>
  matches(/^[A-Z]{2}$/, t("Code pays ISO à deux lettres majuscules, par exemple FR."))(value);

export type FormProps = {
  record: RecordItem | null;
  onSaved: (id: string) => void;
  defaults?: Record<string, string>;
};

function useSubmit(kind: string, record: RecordItem | null, onSaved: (id: string) => void) {
  const [error, setError] = useState("");
  async function submit(name: string, data: Record<string, unknown>) {
    setError("");
    try {
      const saved = await saveRecord(kind, record, name, data);
      notify(record ? t("Modifications enregistrées.") : t("Élément créé."));
      onSaved(saved?.id ?? record?.id ?? "");
    } catch (failure) {
      setError(errorMessage(failure, t("Impossible d’enregistrer.")));
    }
  }
  return { error, submit };
}

export function CompanyForm({ record, onSaved }: FormProps) {
  const { error, submit } = useSubmit("companies", record, onSaved);
  const form = useForm({
    defaultValues: {
      name: record?.name ?? "",
      address: asText(dataField(record, "address")),
      postalCode: asText(dataField(record, "postalCode")),
      city: asText(dataField(record, "city")),
      country: asText(dataField(record, "country")) || "FR",
      vatNumber: asText(dataField(record, "vatNumber")),
      email: asText(dataField(record, "email")),
      phone: asText(dataField(record, "phone")),
    },
    validationLogic: revalidateLogic(),
    onSubmit: ({ value }) => {
      const { name, ...data } = value;
      return submit(name, data);
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
            onDynamic: rules(required(t("Renseignez le nom de l’entreprise.")), maxLength(200)),
          }}
        >
          {(field) => (
            <FormText field={field} label={t("Nom de l’entreprise")} maxLength={200} required />
          )}
        </form.Field>
        <form.Field name="address" validators={{ onDynamic: rules(maxLength(200)) }}>
          {(field) => <FormText field={field} label={t("Adresse")} autoComplete="street-address" />}
        </form.Field>
        <div className="grid gap-4 sm:grid-cols-[8rem_1fr_6rem]">
          <form.Field name="postalCode" validators={{ onDynamic: rules(maxLength(20)) }}>
            {(field) => (
              <FormText field={field} label={t("Code postal")} autoComplete="postal-code" />
            )}
          </form.Field>
          <form.Field name="city" validators={{ onDynamic: rules(maxLength(100)) }}>
            {(field) => <FormText field={field} label={t("Ville")} autoComplete="address-level2" />}
          </form.Field>
          <form.Field name="country" validators={{ onDynamic: rules(countryRule) }}>
            {(field) => <FormText field={field} label={t("Pays")} maxLength={2} />}
          </form.Field>
        </div>
        <div className="grid gap-4 sm:grid-cols-3">
          <form.Field name="vatNumber" validators={{ onDynamic: rules(maxLength(40)) }}>
            {(field) => <FormText field={field} label={t("Numéro de TVA")} />}
          </form.Field>
          <form.Field name="email" validators={{ onDynamic: rules(email()) }}>
            {(field) => <FormText field={field} label={t("Courriel")} type="email" />}
          </form.Field>
          <form.Field name="phone" validators={{ onDynamic: rules(maxLength(40)) }}>
            {(field) => <FormText field={field} label={t("Téléphone")} type="tel" />}
          </form.Field>
        </div>
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => <FormFooter error={error} submitting={submitting} />}
        </form.Subscribe>
      </FieldGroup>
    </form>
  );
}

export const timezones = [
  "Europe/Paris",
  "Europe/Brussels",
  "Europe/Luxembourg",
  "Europe/Zurich",
  "Europe/Berlin",
  "Europe/Madrid",
  "Europe/Rome",
  "Europe/London",
  "Indian/Reunion",
  "America/Martinique",
  "America/Guadeloupe",
  "America/Cayenne",
  "Pacific/Noumea",
  "Pacific/Tahiti",
].map((value) => ({ value, label: value }));

export function SiteForm({ record, onSaved, defaults }: FormProps) {
  const { error, submit } = useSubmit("sites", record, onSaved);
  const [locating, setLocating] = useState(false);
  const form = useForm({
    defaultValues: {
      name: record?.name ?? "",
      companyId: asText(dataField(record, "companyId")) || defaults?.companyId || "",
      address: asText(dataField(record, "address")),
      postalCode: asText(dataField(record, "postalCode")),
      city: asText(dataField(record, "city")),
      country: asText(dataField(record, "country")) || "FR",
      latitude: asText(dataField(record, "latitude")),
      longitude: asText(dataField(record, "longitude")),
      timezone: asText(dataField(record, "timezone")) || "Europe/Paris",
      accessControl: dataField(record, "accessControl") === true,
      autoUserAssignment: dataField(record, "autoUserAssignment") === true,
      public: dataField(record, "public") === true,
    },
    validationLogic: revalidateLogic(),
    onSubmit: ({ value }) => {
      const { name, latitude, longitude, ...data } = value;
      return submit(name, {
        ...data,
        latitude: toNumber(latitude),
        longitude: toNumber(longitude),
      });
    },
  });

  function locate() {
    if (!navigator.geolocation) {
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        form.setFieldValue("latitude", position.coords.latitude.toFixed(6));
        form.setFieldValue("longitude", position.coords.longitude.toFixed(6));
        setLocating(false);
      },
      () => setLocating(false),
    );
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
          validators={{
            onDynamic: rules(required(t("Renseignez le nom du site.")), maxLength(200)),
          }}
        >
          {(field) => <FormText field={field} label={t("Nom du site")} maxLength={200} required />}
        </form.Field>
        <form.Field
          name="companyId"
          validators={{ onDynamic: rules(required(t("Choisissez l’entreprise du site."))) }}
        >
          {(field) => <FormPicker field={field} source="companies" label={t("Entreprise")} />}
        </form.Field>
        <FieldSet>
          <FieldLegend variant="label">{t("Adresse")}</FieldLegend>
          <form.Field name="address" validators={{ onDynamic: rules(maxLength(200)) }}>
            {(field) => <FormText field={field} label={t("Rue")} autoComplete="street-address" />}
          </form.Field>
          <div className="grid gap-4 sm:grid-cols-[8rem_1fr_6rem]">
            <form.Field name="postalCode" validators={{ onDynamic: rules(maxLength(20)) }}>
              {(field) => <FormText field={field} label={t("Code postal")} />}
            </form.Field>
            <form.Field name="city" validators={{ onDynamic: rules(maxLength(100)) }}>
              {(field) => <FormText field={field} label={t("Ville")} />}
            </form.Field>
            <form.Field name="country" validators={{ onDynamic: rules(countryRule) }}>
              {(field) => <FormText field={field} label={t("Pays")} maxLength={2} />}
            </form.Field>
          </div>
          <div className="grid items-end gap-4 sm:grid-cols-[1fr_1fr_auto]">
            <form.Field
              name="latitude"
              validators={{
                onDynamic: rules(optionalNumber(-90, 90, t("Latitude entre -90 et 90."))),
              }}
            >
              {(field) => <FormText field={field} label={t("Latitude")} type="number" />}
            </form.Field>
            <form.Field
              name="longitude"
              validators={{
                onDynamic: rules(optionalNumber(-180, 180, t("Longitude entre -180 et 180."))),
              }}
            >
              {(field) => <FormText field={field} label={t("Longitude")} type="number" />}
            </form.Field>
            <Button type="button" variant="outline" onClick={locate} disabled={locating}>
              <IconCurrentLocation data-icon="inline-start" />
              {locating ? t("Localisation…") : t("Ma position")}
            </Button>
          </div>
          <form.Field name="timezone">
            {(field) => (
              <FormSelect field={field} label={t("Fuseau horaire")} options={timezones} />
            )}
          </form.Field>
        </FieldSet>
        <FieldSet>
          <FieldLegend variant="label">{t("Accès")}</FieldLegend>
          <form.Field name="accessControl">
            {(field) => (
              <FormSwitch
                field={field}
                label={t("Contrôle d’accès")}
                description={t(
                  "Seuls les utilisateurs affectés au site peuvent recharger. Sinon, tout badge valide est accepté.",
                )}
              />
            )}
          </form.Field>
          <form.Field name="autoUserAssignment">
            {(field) => (
              <FormSwitch
                field={field}
                label={t("Affectation automatique")}
                description={t("Les nouveaux utilisateurs sont affectés à ce site.")}
              />
            )}
          </form.Field>
          <form.Field name="public">
            {(field) => (
              <FormSwitch
                field={field}
                label={t("Site public")}
                description={t("Publié aux partenaires d’itinérance avec ses bornes publiques.")}
              />
            )}
          </form.Field>
        </FieldSet>
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => <FormFooter error={error} submitting={submitting} />}
        </form.Subscribe>
      </FieldGroup>
    </form>
  );
}

export function SiteAreaForm({ record, onSaved, defaults }: FormProps) {
  const { error, submit } = useSubmit("site-areas", record, onSaved);
  const form = useForm({
    defaultValues: {
      name: record?.name ?? "",
      siteId: asText(dataField(record, "siteId")) || defaults?.siteId || "",
      parentSiteAreaId:
        asText(dataField(record, "parentSiteAreaId")) || defaults?.parentSiteAreaId || "",
      maximumPowerKw: asText(dataField(record, "maximumPowerKw")),
      numberOfPhases: asText(dataField(record, "numberOfPhases") ?? 3),
      voltage: asText(dataField(record, "voltage") ?? 230),
      smartCharging: dataField(record, "smartCharging") === true,
    },
    validationLogic: revalidateLogic(),
    onSubmit: ({ value }) =>
      submit(value.name, {
        siteId: value.siteId,
        parentSiteAreaId: value.parentSiteAreaId || undefined,
        maximumPowerKw: toNumber(value.maximumPowerKw),
        numberOfPhases: toNumber(value.numberOfPhases),
        voltage: toNumber(value.voltage),
        smartCharging: value.smartCharging,
      }),
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
            onDynamic: rules(required(t("Renseignez le nom de la zone.")), maxLength(200)),
          }}
        >
          {(field) => (
            <FormText field={field} label={t("Nom de la zone")} maxLength={200} required />
          )}
        </form.Field>
        <form.Field
          name="siteId"
          validators={{ onDynamic: rules(required(t("Choisissez le site."))) }}
        >
          {(field) => <FormPicker field={field} source="sites" label={t("Site")} />}
        </form.Field>
        <form.Subscribe selector={(state) => state.values.siteId}>
          {(siteId) => (
            <form.Field name="parentSiteAreaId">
              {(field) => (
                <FormPicker
                  field={field}
                  source="site-areas"
                  filters={{ siteId: siteId || undefined }}
                  label={t("Zone parente (facultative)")}
                  description={t(
                    "La puissance de la zone parente plafonne celle de ses sous-zones.",
                  )}
                />
              )}
            </form.Field>
          )}
        </form.Subscribe>
        <div className="grid gap-4 sm:grid-cols-3">
          <form.Field
            name="maximumPowerKw"
            validators={{ onDynamic: rules(optionalNumber(0, 100000, t("Puissance invalide."))) }}
          >
            {(field) => (
              <FormText
                field={field}
                label={t("Puissance disponible (kW)")}
                type="number"
                min={0}
              />
            )}
          </form.Field>
          <form.Field name="numberOfPhases">
            {(field) => (
              <FormSelect
                field={field}
                label={t("Phases")}
                options={[
                  { value: "3", label: t("Triphasé") },
                  { value: "1", label: t("Monophasé") },
                ]}
              />
            )}
          </form.Field>
          <form.Field
            name="voltage"
            validators={{
              onDynamic: rules(optionalNumber(100, 1000, t("Tension entre 100 et 1 000 V."))),
            }}
          >
            {(field) => <FormText field={field} label={t("Tension (V)")} type="number" />}
          </form.Field>
        </div>
        <form.Field
          name="smartCharging"
          validators={{
            onDynamic: ({ value, fieldApi }) =>
              value && !fieldApi.form.getFieldValue("maximumPowerKw")
                ? {
                    message: t(
                      "Renseignez la puissance disponible pour activer la recharge intelligente.",
                    ),
                  }
                : undefined,
          }}
        >
          {(field) => (
            <FormSwitch
              field={field}
              label={t("Recharge intelligente")}
              description={t(
                "Répartit automatiquement la puissance disponible entre les bornes de la zone.",
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
