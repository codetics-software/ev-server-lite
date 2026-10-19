"use client";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { api } from "@/lib/api";
import { useState, type FormEvent } from "react";
import type { PublicEvse } from "./portal-types";

export function CheckoutForm({ evse }: { evse: PublicEvse }) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState<{
    requestId: string;
    accessToken: string;
    creditMinor: number;
  } | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setPending(true);
    const creditMinor = Math.round(Number(new FormData(event.currentTarget).get("credit")) * 100);
    const request =
      attempt?.creditMinor === creditMinor
        ? attempt
        : {
            requestId: crypto.randomUUID(),
            creditMinor,
            accessToken: Array.from(crypto.getRandomValues(new Uint8Array(32)), (value) =>
              value.toString(16).padStart(2, "0"),
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
      window.location.assign(result.checkoutUrl);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Le paiement n’a pas pu être ouvert.");
      setPending(false);
    }
  }

  return (
    <form onSubmit={submit}>
      <FieldGroup>
        <Field>
          <FieldLabel htmlFor="credit">Crédit de recharge (€)</FieldLabel>
          <Input
            id="credit"
            name="credit"
            type="number"
            min={evse.minimumCreditMinor / 100}
            max={evse.maximumCreditMinor / 100}
            step="1"
            defaultValue="20"
            required
            disabled={pending}
          />
          <FieldDescription>
            De 5 € à 100 €. Le crédit est débité avant le démarrage. Le montant non utilisé est
            remboursé après la fin de la recharge.
          </FieldDescription>
        </Field>
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
