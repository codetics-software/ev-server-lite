"use client";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { FieldGroup } from "@/components/ui/field";
import { ConfirmButton } from "@/features/shared/confirm-button";
import { DataTable, type Column } from "@/features/shared/data-table";
import { useLabels } from "@/features/shared/entity-picker";
import { FormPicker, FormText } from "@/features/shared/form-fields";
import { SecretReveal } from "@/features/shared/secret-reveal";
import { EmptyState, ListState } from "@/features/shared/states";
import { ToneBadge } from "@/features/shared/status-badge";
import { useAction } from "@/features/shared/use-action";
import { isAdmin, useWorkspaceSession } from "@/features/workspace/workspace-context";
import { useApi, useRevision } from "@/hooks/use-api";
import { api, errorMessage } from "@/lib/api";
import { date, fromInputDateTime } from "@/lib/format";
import { maxLength, required, rules } from "@/lib/form";
import { IconKey, IconPlus } from "@tabler/icons-react";
import { revalidateLogic, useForm } from "@tanstack/react-form";
import { useState } from "react";
import type { RegistrationToken } from "./station-types";

function tokenState(token: RegistrationToken) {
  if (token.revoked_at) {
    return <ToneBadge tone="neutral">Révoqué</ToneBadge>;
  }
  if (token.expires_at && new Date(token.expires_at) < new Date()) {
    return <ToneBadge tone="warning">Expiré</ToneBadge>;
  }
  return <ToneBadge tone="success">Actif</ToneBadge>;
}

export function RegistrationTokens() {
  const { user, components } = useWorkspaceSession();
  const [revision, refresh] = useRevision();
  const { data, loading, error } = useApi<RegistrationToken[]>("registration-tokens", revision);
  const areas = useLabels("site-areas");
  const action = useAction();
  const [creating, setCreating] = useState(false);
  const [created, setCreated] = useState<RegistrationToken | null>(null);
  const items = data ?? [];
  const admin = isAdmin(user);
  const columns: Column<RegistrationToken>[] = [
    {
      id: "description",
      header: "Description",
      sort: (token) => token.description,
      cell: (token) => <span className="font-medium">{token.description}</span>,
    },
    { id: "state", header: "État", cell: tokenState },
    ...(components.organization
      ? [
          {
            id: "area",
            header: "Zone",
            cell: (token: RegistrationToken) =>
              token.site_area_id ? (areas.get(token.site_area_id) ?? token.site_area_id) : "—",
          },
        ]
      : []),
    {
      id: "expires",
      header: "Expiration",
      sort: (token) => token.expires_at ?? "",
      cell: (token) => date(token.expires_at),
    },
    {
      id: "url",
      header: "Adresse OCPP",
      cell: (token) => <code className="break-all text-xs">{token.ocpp_url}</code>,
    },
    {
      id: "actions",
      header: "Actions",
      srOnlyHeader: true,
      align: "right",
      cell: (token) =>
        admin && (
          <div className="flex justify-end gap-1">
            {!token.revoked_at && (
              <ConfirmButton
                title="Révoquer ce jeton ?"
                description="Les bornes ne pourront plus s’enregistrer avec ce jeton."
                confirmLabel="Révoquer"
                variant="ghost"
                size="sm"
                onConfirm={() =>
                  action.run(
                    `revoke-${token.id}`,
                    () => api(`registration-tokens/${token.id}/revoke`, { method: "POST" }),
                    { success: "Jeton révoqué.", onDone: refresh },
                  )
                }
              >
                Révoquer
              </ConfirmButton>
            )}
            <ConfirmButton
              title="Supprimer ce jeton ?"
              description="Le jeton est supprimé définitivement."
              confirmLabel="Supprimer"
              variant="ghost"
              size="sm"
              onConfirm={() =>
                action.run(
                  `delete-${token.id}`,
                  () => api(`registration-tokens/${token.id}`, { method: "DELETE" }),
                  { success: "Jeton supprimé.", onDone: refresh },
                )
              }
            >
              Supprimer
            </ConfirmButton>
          </div>
        ),
    },
  ];

  return (
    <section className="flex flex-col gap-4" aria-label="Jetons d’enregistrement">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="max-w-2xl text-sm text-muted-foreground">
          Une borne qui se connecte avec un jeton valide comme mot de passe est créée
          automatiquement dans la zone choisie.
        </p>
        {admin && (
          <Button onClick={() => setCreating(true)}>
            <IconPlus data-icon="inline-start" />
            Nouveau jeton
          </Button>
        )}
      </div>
      <ListState
        data={data}
        items={items}
        loading={loading}
        error={error}
        onRetry={refresh}
        empty={
          <EmptyState
            icon={<IconKey />}
            title="Aucun jeton d’enregistrement"
            description="Créez un jeton pour laisser vos bornes s’enregistrer elles-mêmes."
          />
        }
      >
        <DataTable label="Jetons" rows={items} columns={columns} rowKey={(token) => token.id} />
      </ListState>
      <Dialog
        open={creating}
        onOpenChange={(open) => {
          setCreating(open);
          if (!open) {
            setCreated(null);
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{created ? "Jeton créé" : "Nouveau jeton d’enregistrement"}</DialogTitle>
            <DialogDescription>
              Utilisé comme mot de passe OCPP lors de la première connexion de la borne.
            </DialogDescription>
          </DialogHeader>
          {created?.token ? (
            <>
              <SecretReveal
                title="Jeton d’enregistrement"
                secret={created.token}
                extra={[{ label: "Adresse OCPP", value: created.ocpp_url }]}
              />
              <DialogFooter>
                <Button onClick={() => setCreating(false)}>Terminer</Button>
              </DialogFooter>
            </>
          ) : (
            creating && (
              <TokenForm
                organization={components.organization}
                onCreated={(token) => {
                  setCreated(token);
                  refresh();
                }}
              />
            )
          )}
        </DialogContent>
      </Dialog>
    </section>
  );
}

function TokenForm({
  organization,
  onCreated,
}: {
  organization: boolean;
  onCreated: (token: RegistrationToken) => void;
}) {
  const [error, setError] = useState("");
  const form = useForm({
    defaultValues: { description: "", siteAreaId: "", expiresAt: "" },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      setError("");
      try {
        onCreated(
          await api<RegistrationToken>("registration-tokens", {
            method: "POST",
            body: JSON.stringify({
              description: value.description.trim(),
              siteAreaId: value.siteAreaId || undefined,
              expiresAt: fromInputDateTime(value.expiresAt) ?? undefined,
            }),
          }),
        );
      } catch (failure) {
        setError(errorMessage(failure, "Impossible de créer le jeton."));
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
          name="description"
          validators={{ onDynamic: rules(required("Décrivez l’usage du jeton."), maxLength(200)) }}
        >
          {(field) => <FormText field={field} label="Description" maxLength={200} required />}
        </form.Field>
        {organization && (
          <form.Field name="siteAreaId">
            {(field) => (
              <FormPicker field={field} source="site-areas" label="Zone des nouvelles bornes" />
            )}
          </form.Field>
        )}
        <form.Field name="expiresAt">
          {(field) => (
            <FormText field={field} type="datetime-local" label="Expiration (facultative)" />
          )}
        </form.Field>
        {error && (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => (
            <Button type="submit" disabled={submitting}>
              {submitting ? "Création…" : "Créer le jeton"}
            </Button>
          )}
        </form.Subscribe>
      </FieldGroup>
    </form>
  );
}
