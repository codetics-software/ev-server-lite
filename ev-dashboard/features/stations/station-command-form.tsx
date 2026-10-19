"use client";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { api, type Row } from "@/lib/api";
import { useState, type FormEvent } from "react";

import { valueLabel } from "@/lib/labels";
import { commands } from "./station-commands";

export function StationCommandForm({ station }: { station: string }) {
  const [action, setAction] = useState("GetConfiguration");
  const [payload, setPayload] = useState("{}");
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState("");
  const [failure, setFailure] = useState("");

  async function submit(event: FormEvent) {
    event.preventDefault();
    setPending(true);
    setFailure("");
    setMessage("");

    try {
      const parsed = JSON.parse(payload);

      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
        throw new Error("Renseignez un objet JSON pour les paramètres de la commande.");
      }

      const result = await api<Row>(`stations/${encodeURIComponent(station)}/commands`, {
        method: "POST",
        body: JSON.stringify({ action, payload: parsed }),
      });
      setMessage(
        `Commande enregistrée : ${result.id}. Consultez sa réponse dans le suivi des commandes.`,
      );
    } catch (error) {
      setFailure(
        error instanceof SyntaxError
          ? "Le format JSON des paramètres est invalide."
          : error instanceof Error
            ? error.message
            : "Impossible d’envoyer la commande.",
      );
    } finally {
      setPending(false);
    }
  }

  return (
    <form onSubmit={submit}>
      <FieldGroup>
        <Field>
          <FieldLabel>Commande OCPP 1.6</FieldLabel>
          <Select
            value={action}
            onValueChange={(value) => {
              if (value) {
                setAction(value);
                setPayload(JSON.stringify(commands[value], null, 2));
              }
            }}
          >
            <SelectTrigger className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                {Object.keys(commands).map((name) => (
                  <SelectItem key={valueLabel(name)} value={valueLabel(name)}>
                    {valueLabel(name)}
                  </SelectItem>
                ))}
              </SelectGroup>
            </SelectContent>
          </Select>
        </Field>
        <Field>
          <FieldLabel htmlFor="command-payload">Paramètres de la commande</FieldLabel>
          <Textarea
            id="command-payload"
            className="min-h-32 font-mono"
            value={payload}
            onChange={(event) => setPayload(event.target.value)}
            required
          />
          <FieldDescription>
            Vérifiez le connecteur et les paramètres avant l’envoi. La réponse confirme la réception
            de la commande ; le suivi de session confirme le démarrage de la recharge.
          </FieldDescription>
        </Field>
        {message && (
          <Alert>
            <AlertTitle>En attente d’envoi</AlertTitle>
            <AlertDescription>{message}</AlertDescription>
          </Alert>
        )}
        {failure && (
          <Alert variant="destructive">
            <AlertDescription>{failure}</AlertDescription>
          </Alert>
        )}
        <Button type="submit" disabled={pending}>
          {pending ? "Envoi…" : "Envoyer la commande"}
        </Button>
      </FieldGroup>
    </form>
  );
}
