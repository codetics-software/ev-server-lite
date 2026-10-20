"use client";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Field, FieldLabel } from "@/components/ui/field";
import { Skeleton } from "@/components/ui/skeleton";
import { notify } from "@/features/shared/use-action";
import { useTheme } from "@/hooks/use-theme";
import { api, errorMessage } from "@/lib/api";
import { loadStripe, type Stripe, type StripeCardElement } from "@stripe/stripe-js";
import { useEffect, useRef, useState } from "react";

type Setup = { client_secret: string; account_id: string; publishable_key: string };

function tokenColor(name: string) {
  const probe = document.createElement("span");
  probe.style.color = `var(${name})`;
  probe.style.display = "none";
  document.body.append(probe);
  const computed = getComputedStyle(probe).color;
  probe.remove();
  const canvas = document.createElement("canvas");
  canvas.width = 1;
  canvas.height = 1;
  const context = canvas.getContext("2d");
  if (!context) {
    return computed;
  }
  context.fillStyle = computed;
  context.fillRect(0, 0, 1, 1);
  const [red, green, blue] = context.getImageData(0, 0, 1, 1).data;
  return `rgb(${red}, ${green}, ${blue})`;
}

export function CardSetup({ onDone, onCancel }: { onDone: () => void; onCancel: () => void }) {
  const mount = useRef<HTMLDivElement>(null);
  const { dark } = useTheme();
  const [stripe, setStripe] = useState<Stripe | null>(null);
  const [card, setCard] = useState<StripeCardElement | null>(null);
  const [secret, setSecret] = useState("");
  const [error, setError] = useState("");
  const [ready, setReady] = useState(false);
  const [pending, setPending] = useState(false);

  useEffect(() => {
    let active = true;
    let element: StripeCardElement | null = null;
    async function start() {
      try {
        const setup = await api<Setup>("account/payment-methods/setup", { method: "POST" });
        const instance = await loadStripe(setup.publishable_key, {
          stripeAccount: setup.account_id,
          locale: "fr",
        });
        if (!active || !instance || !mount.current) {
          return;
        }
        element = instance.elements({ locale: "fr" }).create("card", {
          hidePostalCode: true,
          style: {
            base: {
              color: tokenColor("--foreground"),
              iconColor: tokenColor("--primary"),
              fontFamily: '"Source Sans 3", system-ui, sans-serif',
              fontSize: "15px",
              "::placeholder": { color: tokenColor("--muted-foreground") },
            },
            invalid: { color: tokenColor("--destructive"), iconColor: tokenColor("--destructive") },
          },
        });
        element.mount(mount.current);
        element.on("ready", () => active && setReady(true));
        element.on("change", (event) => active && setError(event.error?.message ?? ""));
        setStripe(instance);
        setCard(element);
        setSecret(setup.client_secret);
      } catch (failure) {
        if (active) {
          setError(errorMessage(failure, "Le formulaire de paiement n’a pas pu être chargé."));
        }
      }
    }
    void start();
    return () => {
      active = false;
      element?.destroy();
    };
  }, [dark]);

  async function submit() {
    if (!stripe || !card) {
      return;
    }
    setPending(true);
    setError("");
    const result = await stripe.confirmCardSetup(secret, { payment_method: { card } });
    setPending(false);
    if (result.error) {
      setError(result.error.message ?? "La carte a été refusée.");
      return;
    }
    notify("Carte enregistrée.");
    onDone();
  }

  return (
    <div className="flex flex-col gap-4">
      <Field>
        <FieldLabel>Carte bancaire</FieldLabel>
        <div className="relative min-h-9 rounded-lg border border-input px-3 py-2.5 dark:bg-input/30">
          {!ready && !error && <Skeleton className="absolute inset-2" />}
          <div ref={mount} />
        </div>
      </Field>
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}
      <div className="flex gap-2">
        <Button onClick={submit} disabled={!ready || pending}>
          {pending ? "Vérification…" : "Enregistrer la carte"}
        </Button>
        <Button variant="ghost" onClick={onCancel}>
          Annuler
        </Button>
      </div>
    </div>
  );
}
