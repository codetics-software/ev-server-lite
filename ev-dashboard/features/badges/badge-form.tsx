"use client";

import { FieldGroup } from "@/components/ui/field";
import { FormFooter } from "@/features/shared/form-footer";
import { FormPicker, FormSwitch, FormText } from "@/features/shared/form-fields";
import { notify } from "@/features/shared/use-action";
import { api, errorMessage } from "@/lib/api";
import { fromInputDateTime, toInputDateTime } from "@/lib/format";
import { matches, maxLength, required, rules } from "@/lib/form";
import { revalidateLogic, useForm } from "@tanstack/react-form";
import { useState } from "react";
import type { Badge } from "./badge-types";

export function BadgeForm({
  badge,
  userId,
  onSaved,
}: {
  badge: Badge | null;
  userId?: string;
  onSaved: () => void;
}) {
  const [error, setError] = useState("");
  const form = useForm({
    defaultValues: {
      uid: badge?.uid ?? "",
      visualId: badge?.visual_id ?? "",
      description: badge?.description ?? "",
      userId: badge?.user_id ?? userId ?? "",
      enabled: badge?.enabled ?? true,
      expiresAt: toInputDateTime(badge?.expires_at),
      default: badge?.default ?? false,
    },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      setError("");
      try {
        await api("tokens", {
          method: "POST",
          body: JSON.stringify({
            uid: value.uid.trim(),
            visualId: value.visualId.trim() || undefined,
            description: value.description.trim() || undefined,
            userId: value.userId || undefined,
            enabled: value.enabled,
            expiresAt: fromInputDateTime(value.expiresAt) ?? undefined,
            default: value.default,
          }),
        });
        notify(badge ? "Badge enregistré." : "Badge créé.");
        onSaved();
      } catch (failure) {
        setError(errorMessage(failure, "Impossible d’enregistrer le badge."));
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
        <div className="grid gap-4 sm:grid-cols-2">
          <form.Field
            name="uid"
            validators={{
              onDynamic: rules(
                required("Renseignez l’identifiant RFID."),
                maxLength(20, "L’identifiant OCPP 1.6 contient au plus 20 caractères."),
                matches(/^[A-Za-z0-9_-]+$/, "Lettres, chiffres, tirets et soulignés uniquement."),
              ),
            }}
          >
            {(field) => (
              <FormText
                field={field}
                label="Identifiant RFID"
                description="Identifiant lu par la borne (idTag)."
                maxLength={20}
                readOnly={Boolean(badge)}
                required
              />
            )}
          </form.Field>
          <form.Field name="visualId" validators={{ onDynamic: rules(maxLength(50)) }}>
            {(field) => (
              <FormText
                field={field}
                label="Numéro visible"
                description="Numéro imprimé sur la carte."
                maxLength={50}
              />
            )}
          </form.Field>
        </div>
        <form.Field name="description" validators={{ onDynamic: rules(maxLength(200)) }}>
          {(field) => <FormText field={field} label="Description" maxLength={200} />}
        </form.Field>
        {!userId && (
          <form.Field name="userId">
            {(field) => (
              <FormPicker
                field={field}
                source="users"
                label="Utilisateur (facultatif)"
                fallbackLabel={badge?.user_name ?? undefined}
              />
            )}
          </form.Field>
        )}
        <form.Field name="expiresAt">
          {(field) => (
            <FormText field={field} label="Expiration (facultative)" type="datetime-local" />
          )}
        </form.Field>
        <form.Field name="enabled">
          {(field) => <FormSwitch field={field} label="Autoriser la recharge" />}
        </form.Field>
        <form.Field name="default">
          {(field) => (
            <FormSwitch
              field={field}
              label="Badge par défaut"
              description="Utilisé pour les démarrages à distance de l’utilisateur."
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
