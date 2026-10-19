"use client";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { useApi } from "@/hooks/use-api";
import { api } from "@/lib/api";
import { useState, type FormEvent } from "react";
import type { PaymentSettings } from "./payment-types";

export function StripeSettings() {
  const [revision, setRevision] = useState(0);
  const { data, error } = useApi<PaymentSettings>("billing/settings", revision);
  const [message, setMessage] = useState("");
  const [failure, setFailure] = useState("");
  const [pending, setPending] = useState(false);
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setPending(true);
    setFailure("");
    setMessage("");
    try {
      await api("billing/settings", {
        method: "PUT",
        body: JSON.stringify({
          country: form.get("country"),
          enabled: form.get("enabled") === "on",
        }),
      });
      setMessage("Les paramètres de paiement ont été enregistrés.");
      setRevision((value) => value + 1);
    } catch (error) {
      setFailure(
        error instanceof Error ? error.message : "Impossible d’enregistrer les paramètres.",
      );
    } finally {
      setPending(false);
    }
  }
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
          <form key={`${data.country}:${data.enabled}`} onSubmit={save}>
            <FieldGroup>
              <Field>
                <FieldLabel htmlFor="country">Pays du compte</FieldLabel>
                <Input
                  id="country"
                  name="country"
                  defaultValue={data.country}
                  readOnly={!!data.accountId}
                  pattern="[A-Z]{2}"
                  maxLength={2}
                  required
                />
                <FieldDescription>
                  Code pays à deux lettres, par exemple FR. Il ne peut plus changer après la
                  création du compte.
                </FieldDescription>
              </Field>
              <Field orientation="horizontal">
                <Checkbox name="enabled" id="payments-enabled" defaultChecked={data.enabled} />
                <FieldLabel htmlFor="payments-enabled">Autoriser les paiements publics</FieldLabel>
              </Field>
              <div className="flex flex-wrap gap-3">
                <Button type="submit" variant="outline" disabled={pending}>
                  Enregistrer
                </Button>
                <Button type="button" disabled={pending || !data.configured} onClick={connect}>
                  {data.accountId
                    ? "Compléter les informations Stripe"
                    : "Connecter mon compte Stripe"}
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  disabled={pending}
                  onClick={() => setRevision((value) => value + 1)}
                >
                  Vérifier l’état
                </Button>
              </div>
            </FieldGroup>
          </form>
        </>
      )}
    </section>
  );
}
