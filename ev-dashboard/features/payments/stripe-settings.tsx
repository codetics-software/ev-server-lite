"use client";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { useApi } from "@/hooks/use-api";
import { api } from "@/lib/api";
import { isInvalid, matches, required, rules } from "@/lib/form";
import { revalidateLogic, useForm, useStore } from "@tanstack/react-form";
import { useState } from "react";
import type { PaymentSettings } from "./payment-types";

export function StripeSettings() {
  const [revision, setRevision] = useState(0);
  const { data, error } = useApi<PaymentSettings>("billing/settings", revision);
  const [message, setMessage] = useState("");
  const [failure, setFailure] = useState("");
  const [pending, setPending] = useState(false);
  async function connect() {
    setPending(true);
    setFailure("");
    try {
      const response = await api<{ url: string }>("billing/onboarding", { method: "POST" });
      window.location.assign(response.url);
    } catch (error) {
      setFailure(error instanceof Error ? error.message : "Impossible d’ouvrir Stripe.");
      setPending(false);
    }
  }
  return (
    <section className="operational-panel max-w-2xl">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2>Compte Stripe de l’espace</h2>
        {data && (
          <Badge variant="outline">
            {data.ready
              ? "Prêt à encaisser"
              : data.accountId
                ? "Informations à compléter"
                : "À connecter"}
          </Badge>
        )}
      </div>
      <p className="text-sm text-muted-foreground">
        Les paiements de recharge sont encaissés directement sur le compte Stripe de votre espace
        client.
      </p>
      {(error || failure) && (
        <Alert variant="destructive">
          <AlertDescription>{failure || error?.message}</AlertDescription>
        </Alert>
      )}
      {message && (
        <p role="status" className="text-sm">
          {message}
        </p>
      )}
      {data && (
        <>
          {!data.configured && (
            <Alert>
              <AlertDescription>
                Le paiement Stripe n’est pas encore configuré sur cette installation. Contactez son
                administrateur technique.
              </AlertDescription>
            </Alert>
          )}
          <StripeSettingsForm
            key={`${data.country}:${data.enabled}`}
            settings={data}
            pending={pending}
            onSaved={() => {
              setMessage("Les paramètres de paiement ont été enregistrés.");
              setRevision((value) => value + 1);
            }}
            onReset={() => {
              setFailure("");
              setMessage("");
            }}
            onFailure={setFailure}
            onConnect={connect}
            onRefresh={() => setRevision((value) => value + 1)}
          />
        </>
      )}
    </section>
  );
}

const countryRule = "Le code du pays doit contenir deux lettres majuscules.";

function StripeSettingsForm({
  settings,
  pending,
  onSaved,
  onReset,
  onFailure,
  onConnect,
  onRefresh,
}: {
  settings: PaymentSettings;
  pending: boolean;
  onSaved: () => void;
  onReset: () => void;
  onFailure: (message: string) => void;
  onConnect: () => void;
  onRefresh: () => void;
}) {
  const form = useForm({
    defaultValues: { country: settings.country, enabled: settings.enabled },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      onReset();
      try {
        await api("billing/settings", {
          method: "PUT",
          body: JSON.stringify({ country: value.country, enabled: value.enabled }),
        });
        onSaved();
      } catch (error) {
        onFailure(
          error instanceof Error ? error.message : "Impossible d’enregistrer les paramètres.",
        );
      }
    },
  });
  const submitting = useStore(form.store, (state) => state.isSubmitting);
  const busy = pending || submitting;

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
          name="country"
          validators={{
            onDynamic: rules(required(countryRule), matches(/^[A-Z]{2}$/, countryRule)),
          }}
        >
          {(field) => {
            const invalid = isInvalid(field);
            return (
              <Field data-invalid={invalid}>
                <FieldLabel htmlFor="country">Pays du compte</FieldLabel>
                <Input
                  id="country"
                  name={field.name}
                  value={field.state.value}
                  onBlur={field.handleBlur}
                  onChange={(event) => field.handleChange(event.target.value)}
                  aria-invalid={invalid}
                  readOnly={!!settings.accountId}
                  pattern="[A-Z]{2}"
                  maxLength={2}
                  required
                />
                <FieldDescription>
                  Code pays à deux lettres, par exemple FR. Il ne peut plus changer après la
                  création du compte.
                </FieldDescription>
                {invalid && <FieldError errors={field.state.meta.errors} />}
              </Field>
            );
          }}
        </form.Field>
        <form.Field name="enabled">
          {(field) => (
            <Field orientation="horizontal">
              <Checkbox
                name={field.name}
                id="payments-enabled"
                checked={field.state.value}
                onCheckedChange={(checked) => field.handleChange(checked === true)}
              />
              <FieldLabel htmlFor="payments-enabled">Autoriser les paiements publics</FieldLabel>
            </Field>
          )}
        </form.Field>
        <div className="flex flex-wrap gap-3">
          <Button type="submit" variant="outline" disabled={busy}>
            Enregistrer
          </Button>
          <Button type="button" disabled={busy || !settings.configured} onClick={onConnect}>
            {settings.accountId
              ? "Compléter les informations Stripe"
              : "Connecter mon compte Stripe"}
          </Button>
          <Button type="button" variant="ghost" disabled={busy} onClick={onRefresh}>
            Vérifier l’état
          </Button>
        </div>
      </FieldGroup>
    </form>
  );
}
