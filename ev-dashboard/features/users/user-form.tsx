"use client";

import { FieldGroup } from "@/components/ui/field";
import { FormFooter } from "@/features/shared/form-footer";
import { FormSelect, FormSwitch, FormText } from "@/features/shared/form-fields";
import { notify } from "@/features/shared/use-action";
import { api, errorMessage } from "@/lib/api";
import { userRoles } from "@/lib/api-types";
import { email, maxLength, required, rules } from "@/lib/form";
import { roleLabels } from "@/lib/labels";
import { revalidateLogic, useForm } from "@tanstack/react-form";
import { useState } from "react";
import type { ManagedUser } from "./user-types";

export const roleOptions = [...userRoles]
  .reverse()
  .map((role) => ({ value: role, label: roleLabels[role] }));
export const localeOptions = [
  { value: "fr", label: "Français" },
  { value: "en", label: "Anglais" },
  { value: "de", label: "Allemand" },
  { value: "es", label: "Espagnol" },
  { value: "it", label: "Italien" },
];

export function UserForm({
  user,
  onSaved,
}: {
  user: ManagedUser | null;
  onSaved: (result: { id: string; message?: string }) => void;
}) {
  const [error, setError] = useState("");
  const form = useForm({
    defaultValues: {
      email: user?.email ?? "",
      name: user?.display_name ?? "",
      role: user?.role ?? "DRIVER",
      enabled: user?.enabled ?? true,
      phone: user?.phone ?? "",
      locale: user?.locale ?? "fr",
    },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      setError("");
      try {
        const result = await api<{ id: string; message?: string } | ManagedUser>(
          user ? `users/${user.id}` : "users",
          {
            method: user ? "PUT" : "POST",
            body: JSON.stringify({
              email: value.email.trim(),
              name: value.name.trim(),
              role: value.role,
              enabled: value.enabled,
              phone: value.phone.trim() || undefined,
              locale: value.locale || undefined,
            }),
          },
        );
        const message = result && "message" in result ? result.message : undefined;
        notify(user ? "Utilisateur enregistré." : "Utilisateur créé.", message);
        onSaved({ id: result?.id ?? user?.id ?? "", message });
      } catch (failure) {
        setError(errorMessage(failure, "Impossible d’enregistrer l’utilisateur."));
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
          validators={{ onDynamic: rules(required("Renseignez le nom."), maxLength(200)) }}
        >
          {(field) => (
            <FormText
              field={field}
              label="Nom complet"
              autoComplete="name"
              maxLength={200}
              required
            />
          )}
        </form.Field>
        <form.Field
          name="email"
          validators={{ onDynamic: rules(required("Renseignez l’adresse électronique."), email()) }}
        >
          {(field) => (
            <FormText
              field={field}
              label="Adresse électronique"
              type="email"
              autoComplete="email"
              required
            />
          )}
        </form.Field>
        <div className="grid gap-4 sm:grid-cols-3">
          <form.Field name="role">
            {(field) => <FormSelect field={field} label="Rôle" options={roleOptions} />}
          </form.Field>
          <form.Field name="phone" validators={{ onDynamic: rules(maxLength(40)) }}>
            {(field) => <FormText field={field} label="Téléphone" type="tel" autoComplete="tel" />}
          </form.Field>
          <form.Field name="locale">
            {(field) => <FormSelect field={field} label="Langue" options={localeOptions} />}
          </form.Field>
        </div>
        <form.Field name="enabled">
          {(field) => (
            <FormSwitch
              field={field}
              label="Compte activé"
              description={
                user
                  ? "Un compte désactivé ne peut plus se connecter ni recharger."
                  : "L’utilisateur reçoit un courriel pour définir son mot de passe."
              }
            />
          )}
        </form.Field>
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => (
            <FormFooter
              error={error}
              submitting={submitting}
              label={user ? "Enregistrer" : "Créer l’utilisateur"}
            />
          )}
        </form.Subscribe>
      </FieldGroup>
    </form>
  );
}
