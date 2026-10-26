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
          <DialogTitle>{receipt ? "Borne ajoutée" : "Ajouter une borne"}</DialogTitle>
          <DialogDescription>
            {receipt
              ? `Fiche de mise en service de la borne ${receipt.id} : configurez-la avec ces informations.`
              : "La borne se connecte ensuite en OCPP 1.6 avec son identifiant et son mot de passe."}
          </DialogDescription>
        </DialogHeader>
        {receipt ? (
          <>
            <SecretReveal
              title="Mot de passe de la borne"
              message={receipt.message}
              secret={receipt.secret}
              extra={[
                { label: "Adresse OCPP", value: ocppUrl(workspace?.slug, receipt.id, publicUrl) },
                { label: "Identifiant de la borne", value: receipt.id },
              ]}
            />
            <p className="text-sm text-muted-foreground">
              {profileLabel(1)} : OCPP 1.6 JSON, sous-protocole ocpp1.6, authentification Basic avec
              l’identifiant et ce mot de passe.
              {!publicUrl &&
                " Adaptez le nom d’hôte si le serveur OCPP est publié à une autre adresse que ce tableau de bord."}
            </p>
            <DialogFooter>
              <Button onClick={() => onOpenChange(false)}>Terminer</Button>
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
        setError(errorMessage(failure, "Impossible d’ajouter la borne."));
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
              required("Renseignez l’identifiant OCPP de la borne."),
              maxLength(100),
              matches(/^[A-Za-z0-9._:*-]{1,100}$/, "Lettres, chiffres et . _ : * - uniquement."),
              (value: string) =>
                value.trim() === "." || value.trim() === ".."
                  ? "Les identifiants « . » et « .. » sont réservés."
                  : undefined,
            ),
          }}
        >
          {(field) => (
            <FormText
              field={field}
              label="Identifiant de la borne"
              description="Identifiant annoncé par la borne dans l’adresse OCPP. Lettres, chiffres et . _ : * -"
              maxLength={100}
              autoComplete="off"
              required
            />
          )}
        </form.Field>
        <form.Field
          name="name"
          validators={{ onDynamic: rules(required("Renseignez un nom."), maxLength(200)) }}
        >
          {(field) => <FormText field={field} label="Nom" maxLength={200} required />}
        </form.Field>
        {organization && (
          <form.Field name="siteAreaId">
            {(field) => (
              <FormPicker field={field} source="site-areas" label="Zone de recharge (facultatif)" />
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
              {submitting ? "Ajout…" : "Ajouter la borne"}
            </Button>
          )}
        </form.Subscribe>
      </FieldGroup>
    </form>
  );
}
