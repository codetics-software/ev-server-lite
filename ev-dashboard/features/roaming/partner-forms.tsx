"use client";

import { FieldGroup } from "@/components/ui/field";
import { FormFooter } from "@/features/shared/form-footer";
import { FormSelect, FormSwitch, FormText } from "@/features/shared/form-fields";
import { notify } from "@/features/shared/use-action";
import { api, errorMessage } from "@/lib/api";
import { matches, maxLength, required, rules } from "@/lib/form";
import { revalidateLogic, useForm } from "@tanstack/react-form";
import { useState } from "react";
import { t } from "@/lib/i18n";
import type { Partner } from "./roaming-types";

const https = (value: string) => matches(/^https:\/\//, t("Adresse HTTPS requise."))(value);

export function PartnerCreateForm({
  onCreated,
}: {
  onCreated: (result: { id: string; token: string; message: string }) => void;
}) {
  const [error, setError] = useState("");
  const form = useForm({
    defaultValues: { name: "", countryCode: "FR", partyId: "", localRole: "cpo", remoteUrl: "" },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      setError("");
      try {
        onCreated(
          await api<{ id: string; token: string; message: string }>("ocpi/partners", {
            method: "POST",
            body: JSON.stringify({
              ...value,
              name: value.name.trim(),
              remoteUrl: value.remoteUrl.trim(),
            }),
          }),
        );
      } catch (failure) {
        setError(errorMessage(failure, t("Impossible de créer le partenaire.")));
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
          validators={{ onDynamic: rules(required(t("Renseignez le nom.")), maxLength(200)) }}
        >
          {(field) => <FormText field={field} label={t("Nom du partenaire")} required />}
        </form.Field>
        <div className="grid gap-4 sm:grid-cols-3">
          <form.Field
            name="countryCode"
            validators={{ onDynamic: rules(matches(/^[A-Z]{2}$/, t("Deux lettres majuscules."))) }}
          >
            {(field) => <FormText field={field} label={t("Code pays")} maxLength={2} />}
          </form.Field>
          <form.Field
            name="partyId"
            validators={{
              onDynamic: rules(
                required(t("Requis.")),
                matches(/^[A-Z0-9]{3}$/, t("Trois lettres majuscules ou chiffres.")),
              ),
            }}
          >
            {(field) => (
              <FormText field={field} label={t("Identifiant de l’opérateur")} maxLength={3} />
            )}
          </form.Field>
          <form.Field name="localRole">
            {(field) => (
              <FormSelect
                field={field}
                label={t("Notre rôle")}
                options={[
                  { value: "cpo", label: t("Exploitant de bornes") },
                  { value: "emsp", label: t("Opérateur de mobilité") },
                ]}
              />
            )}
          </form.Field>
        </div>
        <form.Field
          name="remoteUrl"
          validators={{ onDynamic: rules(required(t("Renseignez l’adresse.")), https) }}
        >
          {(field) => (
            <FormText
              field={field}
              label={t("Adresse de découverte du partenaire (versions)")}
              type="url"
              placeholder="https://partenaire.exemple/ocpi/versions"
            />
          )}
        </form.Field>
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => (
            <FormFooter error={error} submitting={submitting} label={t("Créer le partenaire")} />
          )}
        </form.Subscribe>
      </FieldGroup>
    </form>
  );
}

export function PartnerEditForm({ partner, onSaved }: { partner: Partner; onSaved: () => void }) {
  const [error, setError] = useState("");
  const form = useForm({
    defaultValues: { name: partner.name, remoteUrl: partner.remote_url, enabled: partner.enabled },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      setError("");
      try {
        await api(`ocpi/partners/${partner.id}`, { method: "PUT", body: JSON.stringify(value) });
        notify(t("Partenaire enregistré."));
        onSaved();
      } catch (failure) {
        setError(errorMessage(failure, t("Impossible d’enregistrer le partenaire.")));
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
          validators={{ onDynamic: rules(required(t("Renseignez le nom.")), maxLength(200)) }}
        >
          {(field) => <FormText field={field} label={t("Nom du partenaire")} required />}
        </form.Field>
        <form.Field
          name="remoteUrl"
          validators={{ onDynamic: rules(required(t("Renseignez l’adresse.")), https) }}
        >
          {(field) => <FormText field={field} label={t("Adresse de découverte")} type="url" />}
        </form.Field>
        <form.Field name="enabled">
          {(field) => <FormSwitch field={field} label={t("Échanges activés")} />}
        </form.Field>
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => <FormFooter error={error} submitting={submitting} />}
        </form.Subscribe>
      </FieldGroup>
    </form>
  );
}

export function RegisterForm({ partner, onDone }: { partner: Partner; onDone: () => void }) {
  const [error, setError] = useState("");
  const form = useForm({
    defaultValues: { token: "" },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      setError("");
      try {
        await api(`ocpi/partners/${partner.id}/register`, {
          method: "POST",
          body: JSON.stringify({ token: value.token.trim() }),
        });
        notify(t("Échange des identifiants réussi."));
        onDone();
      } catch (failure) {
        setError(errorMessage(failure, t("L’enregistrement auprès du partenaire a échoué.")));
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
          name="token"
          validators={{
            onDynamic: rules(required(t("Collez le jeton transmis par le partenaire."))),
          }}
        >
          {(field) => (
            <FormText
              field={field}
              label={t("Jeton du partenaire")}
              description={t(
                "Jeton initial (token A) fourni par le partenaire pour l’échange des identifiants.",
              )}
              autoComplete="off"
            />
          )}
        </form.Field>
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => (
            <FormFooter
              error={error}
              submitting={submitting}
              label={t("S’enregistrer auprès du partenaire")}
            />
          )}
        </form.Subscribe>
      </FieldGroup>
    </form>
  );
}
