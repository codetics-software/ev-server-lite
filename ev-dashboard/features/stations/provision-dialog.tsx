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
import { FormPicker, FormText } from "@/features/shared/form-fields";
import { SecretReveal } from "@/features/shared/secret-reveal";
import { useComponent, useWorkspaceSession } from "@/features/workspace/workspace-context";
import { api, errorMessage } from "@/lib/api";
import { matches, maxLength, required, rules } from "@/lib/form";
import { revalidateLogic, useForm } from "@tanstack/react-form";
import { useState } from "react";
import { useApi } from "@/hooks/use-api";
import { ocppUrl, profileLabel, type SecurityCapabilities } from "./station-security";
import type { ProvisionedStation } from "./station-types";
import { t } from "@/lib/i18n";

export function ProvisionDialog({
  open,
  onOpenChange,
  onCreated,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCreated: () => void;
}) {
  const [receipt, setReceipt] = useState<ProvisionedStation | null>(null);
  const { workspace } = useWorkspaceSession();
  const capabilities = useApi<SecurityCapabilities>(
    receipt ? "stations/security/capabilities" : null,
  );
  const publicUrl = capabilities.data?.ocpp_public_url;
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        onOpenChange(next);
        if (!next) {
          setReceipt(null);
        }
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{receipt ? t("Borne ajoutée") : t("Ajouter une borne")}</DialogTitle>
          <DialogDescription>
            {receipt
              ? t(
                  "Fiche de mise en service de la borne {id} : configurez-la avec ces informations.",
                  { id: receipt.id },
                )
              : t(
                  "La borne se connecte ensuite en OCPP 1.6 avec son identifiant et son mot de passe.",
                )}
          </DialogDescription>
        </DialogHeader>
        {receipt ? (
          <>
            <SecretReveal
              title={t("Mot de passe de la borne")}
              message={receipt.message}
              secret={receipt.secret}
              extra={[
                {
                  label: t("Adresse OCPP"),
                  value: ocppUrl(workspace?.slug, receipt.id, publicUrl),
                },
                { label: t("Identifiant de la borne"), value: receipt.id },
              ]}
            />
            <p className="text-sm text-muted-foreground">
              {t(
                "{profile} : OCPP 1.6 JSON, sous-protocole ocpp1.6, authentification Basic avec l’identifiant et ce mot de passe.",
                { profile: profileLabel(1) },
              )}
              {!publicUrl &&
                ` ${t("Adaptez le nom d’hôte si le serveur OCPP est publié à une autre adresse que ce tableau de bord.")}`}
            </p>
            <DialogFooter>
              <Button onClick={() => onOpenChange(false)}>{t("Terminer")}</Button>
            </DialogFooter>
          </>
        ) : (
          open && (
            <ProvisionForm
              onCreated={(result) => {
                setReceipt(result);
                onCreated();
              }}
            />
          )
        )}
      </DialogContent>
    </Dialog>
  );
}

function ProvisionForm({ onCreated }: { onCreated: (result: ProvisionedStation) => void }) {
  const organization = useComponent("organization");
  const [error, setError] = useState("");
  const form = useForm({
    defaultValues: { id: "", name: "", siteAreaId: "" },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      setError("");
      try {
        onCreated(
          await api<ProvisionedStation>("stations", {
            method: "POST",
            body: JSON.stringify({
              id: value.id.trim(),
              name: value.name.trim(),
              siteAreaId: value.siteAreaId || undefined,
            }),
          }),
        );
      } catch (failure) {
        setError(errorMessage(failure, t("Impossible d’ajouter la borne.")));
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
          name="id"
          validators={{
            onDynamic: rules(
              required(t("Renseignez l’identifiant OCPP de la borne.")),
              maxLength(100),
              matches(/^[A-Za-z0-9._:*-]{1,100}$/, t("Lettres, chiffres et . _ : * - uniquement.")),
              (value: string) =>
                value.trim() === "." || value.trim() === ".."
                  ? t("Les identifiants « . » et « .. » sont réservés.")
                  : undefined,
            ),
          }}
        >
          {(field) => (
            <FormText
              field={field}
              label={t("Identifiant de la borne")}
              description={t(
                "Identifiant annoncé par la borne dans l’adresse OCPP. Lettres, chiffres et . _ : * -",
              )}
              maxLength={100}
              autoComplete="off"
              required
            />
          )}
        </form.Field>
        <form.Field
          name="name"
          validators={{ onDynamic: rules(required(t("Renseignez un nom.")), maxLength(200)) }}
        >
          {(field) => <FormText field={field} label={t("Nom")} maxLength={200} required />}
        </form.Field>
        {organization && (
          <form.Field name="siteAreaId">
            {(field) => (
              <FormPicker
                field={field}
                source="site-areas"
                label={t("Zone de recharge (facultatif)")}
              />
            )}
          </form.Field>
        )}
        {error && (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => (
            <Button type="submit" disabled={submitting}>
              {submitting ? t("Ajout…") : t("Ajouter la borne")}
            </Button>
          )}
        </form.Subscribe>
      </FieldGroup>
    </form>
  );
}
