"use client";

import { Button } from "@/components/ui/button";
import {
  Field,
  FieldContent,
  FieldDescription,
  FieldGroup,
  FieldLabel,
  FieldLegend,
  FieldSet,
} from "@/components/ui/field";
import { Switch } from "@/components/ui/switch";
import { ErrorState, LoadingState } from "@/features/shared/states";
import { useAction } from "@/features/shared/use-action";
import { useApi, useRevision } from "@/hooks/use-api";
import { api } from "@/lib/api";
import { notificationLabels } from "@/lib/labels";
import { useState } from "react";

type Preferences = { email: boolean; types: Record<string, boolean> };

const driverTypes = [
  "SESSION_STARTED",
  "END_OF_CHARGE",
  "END_OF_SESSION",
  "SESSION_NOT_STARTED_AFTER_AUTHORIZE",
  "PREPARING_SESSION_NOT_STARTED",
  "INVOICE_AVAILABLE",
  "BILLING_FAILED",
  "ACCOUNT_STATUS_CHANGED",
  "ACCOUNT_INACTIVITY",
];

export function NotificationPreferences({ path, staff }: { path: string; staff: boolean }) {
  const [revision, refresh] = useRevision();
  const { data, loading, error } = useApi<Preferences>(path, revision);
  const [draft, setDraft] = useState<Preferences | null>(null);
  const action = useAction();
  if (error && !data) {
    return <ErrorState error={error} onRetry={refresh} />;
  }
  if (loading && !data) {
    return <LoadingState rows={4} />;
  }
  if (!data) {
    return null;
  }
  const value = draft ?? data;
  const keys = Object.keys(value.types).length
    ? Object.keys(value.types)
    : Object.keys(notificationLabels);
  const own = keys.filter((key) => driverTypes.includes(key));
  const network = keys.filter((key) => !driverTypes.includes(key));
  const toggle = (key: string, checked: boolean) =>
    setDraft({ ...value, types: { ...value.types, [key]: checked } });

  function group(title: string, description: string, items: string[]) {
    return (
      <FieldSet>
        <FieldLegend>{title}</FieldLegend>
        <FieldDescription>{description}</FieldDescription>
        <div className="grid gap-3 sm:grid-cols-2">
          {items.map((key) => (
            <Field key={key} orientation="horizontal">
              <Switch
                id={`notification-${key}`}
                checked={value.types[key] !== false}
                disabled={!value.email}
                onCheckedChange={(checked) => toggle(key, checked === true)}
              />
              <FieldLabel htmlFor={`notification-${key}`}>
                {notificationLabels[key] ?? key}
              </FieldLabel>
            </Field>
          ))}
        </div>
      </FieldSet>
    );
  }

  return (
    <FieldGroup className="max-w-3xl">
      <Field orientation="horizontal">
        <Switch
          id="notification-email"
          checked={value.email}
          onCheckedChange={(checked) => setDraft({ ...value, email: checked === true })}
        />
        <FieldContent>
          <FieldLabel htmlFor="notification-email">
            Recevoir les notifications par courriel
          </FieldLabel>
          <FieldDescription>
            Désactivez pour ne plus recevoir aucun courriel de notification.
          </FieldDescription>
        </FieldContent>
      </Field>
      {group(
        "Mes recharges",
        "Événements liés à vos sessions, à votre compte et à vos factures.",
        own,
      )}
      {staff &&
        network.length > 0 &&
        group(
          "Exploitation du réseau",
          "Alertes envoyées aux administrateurs du réseau et des sites.",
          network,
        )}
      <div className="flex gap-2">
        <Button
          disabled={!draft || action.busy}
          onClick={() =>
            action.run(
              "save",
              () => api<Preferences>(path, { method: "PUT", body: JSON.stringify(value) }),
              {
                success: "Préférences de notification enregistrées.",
                onDone: () => {
                  setDraft(null);
                  refresh();
                },
              },
            )
          }
        >
          Enregistrer
        </Button>
        {draft && (
          <Button variant="ghost" onClick={() => setDraft(null)}>
            Annuler
          </Button>
        )}
      </div>
    </FieldGroup>
  );
}
