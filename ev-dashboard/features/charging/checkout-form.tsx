"use client";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { api } from "@/lib/api";
import { integerBetween, isInvalid, rules } from "@/lib/form";
import { revalidateLogic, useForm, useStore } from "@tanstack/react-form";
import { useState } from "react";
import type { PublicEvse } from "./portal-types";

export function CheckoutForm({ evse }: { evse: PublicEvse }) {
  const [redirecting, setRedirecting] = useState(false);
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState<{
    requestId: string;
    accessToken: string;
    creditMinor: number;
  } | null>(null);
  const minimum = evse.minimumCreditMinor / 100;
  const maximum = evse.maximumCreditMinor / 100;
  const form = useForm({
    defaultValues: { credit: "20" },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      setError("");
      const creditMinor = Math.round(Number(value.credit) * 100);
      const request =
        attempt?.creditMinor === creditMinor
          ? attempt
          : {
              requestId: crypto.randomUUID(),
              creditMinor,
              accessToken: Array.from(crypto.getRandomValues(new Uint8Array(32)), (byte) =>
                byte.toString(16).padStart(2, "0"),
              ).join(""),
            };
      setAttempt(request);
      try {
        const result = await api<{ orderId: string; checkoutUrl: string }>(
          `public/evses/${encodeURIComponent(evse.workspaceSlug)}/${evse.id}/checkout`,
          { method: "POST", body: JSON.stringify(request) },
        );
        sessionStorage.setItem(`recharge:${result.orderId}`, request.accessToken);
        sessionStorage.setItem(`recharge-en-cours:${evse.id}`, result.orderId);
        setRedirecting(true);
        window.location.assign(result.checkoutUrl);
      } catch (failure) {
        setError(
          failure instanceof Error ? failure.message : "Le paiement n’a pas pu être ouvert.",
        );
      }
    },
  });
  const submitting = useStore(form.store, (state) => state.isSubmitting);
  const pending = submitting || redirecting;

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
          name="credit"
          validators={{
            onDynamic: rules(
              integerBetween(
                minimum,
                maximum,
                `Le crédit doit être compris entre ${minimum} € et ${maximum} €.`,
              ),
            ),
          }}
        >
          {(field) => {
            const invalid = isInvalid(field);
            return (
              <Field data-invalid={invalid}>
                <FieldLabel htmlFor="credit">Crédit de recharge (€)</FieldLabel>
                <Input
                  id="credit"
                  name={field.name}
                  type="number"
                  min={minimum}
                  max={maximum}
                  step="1"
                  value={field.state.value}
                  onBlur={field.handleBlur}
                  onChange={(event) => field.handleChange(event.target.value)}
                  aria-invalid={invalid}
                  required
                  disabled={pending}
                />
                <FieldDescription>
                  De 5 € à 100 €. Le crédit est débité avant le démarrage. Le montant non utilisé
                  est remboursé après la fin de la recharge.
                </FieldDescription>
                {invalid && <FieldError errors={field.state.meta.errors} />}
              </Field>
            );
          }}
        </form.Field>
        <p className="text-sm text-muted-foreground">
          Branchez votre véhicule avant de payer. La borne recevra la demande de démarrage après
          confirmation du paiement. La facturation est plafonnée au crédit choisi ; l’arrêt dépend
          de la connexion et de la réponse de la borne.
        </p>
        {error && (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}
        <Button type="submit" className="w-full" disabled={pending || !evse.available}>
          {pending ? "Ouverture du paiement…" : "Payer et démarrer"}
        </Button>
      </FieldGroup>
    </form>
  );
}
