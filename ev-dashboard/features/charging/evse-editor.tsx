"use client";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useApi } from "@/hooks/use-api";
import { api } from "@/lib/api";
import type { PageData, Row } from "@/lib/api-types";
import { useState, type FormEvent } from "react";
import type { Evse } from "./portal-types";

export function EvseEditor({
  evse,
  onClose,
  onSaved,
}: {
  evse: Evse | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { data: tariffs } = useApi<PageData>("records/pricing-definitions?limit=100");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setError("");
    const form = new FormData(event.currentTarget);
    try {
      await api(`evses${evse ? `/${evse.id}` : ""}`, {
        method: evse ? "PUT" : "POST",
        body: JSON.stringify({
          name: form.get("name"),
          stationId: form.get("stationId"),
          connectorId: Number(form.get("connectorId")),
          pricingDefinitionId: form.get("pricingDefinitionId"),
          published: form.get("published") === "on",
        }),
      });
      onSaved();
      onClose();
    } catch (failure) {
      setError(
        failure instanceof Error ? failure.message : "Impossible d’enregistrer le point de charge.",
      );
    } finally {
      setPending(false);
    }
  }
  const items = tariffs?.items ?? [];
  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {evse ? "Modifier le point de charge" : "Ajouter un point de charge"}
          </DialogTitle>
          <DialogDescription>
            Associez un connecteur à un tarif pour créer son portail public.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={submit}>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="evse-name">Nom affiché aux conducteurs</FieldLabel>
              <Input
                id="evse-name"
                name="name"
                defaultValue={evse?.name}
                maxLength={200}
                required
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="evse-station">Identifiant de la borne</FieldLabel>
              <Input id="evse-station" name="stationId" defaultValue={evse?.stationId} required />
            </Field>
            <Field>
              <FieldLabel htmlFor="evse-connector">Numéro du connecteur</FieldLabel>
              <Input
                id="evse-connector"
                name="connectorId"
                type="number"
                min="1"
                step="1"
                defaultValue={evse?.connectorId ?? 1}
                required
              />
              <FieldDescription>
                Le connecteur doit avoir été déclaré par la borne.
              </FieldDescription>
            </Field>
            <Field>
              <FieldLabel htmlFor="evse-tariff">Tarif</FieldLabel>
              <Select
                name="pricingDefinitionId"
                defaultValue={evse?.pricingDefinitionId ?? String(items[0]?.id ?? "")}
              >
                <SelectTrigger id="evse-tariff" className="w-full">
                  <SelectValue placeholder="Choisir un tarif" />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {items.map((item: Row) => (
                      <SelectItem key={String(item.id)} value={String(item.id)}>
                        {String(item.name)}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
              <FieldDescription>
                Créez d’abord un tarif en euros dans la page Tarifs.
              </FieldDescription>
            </Field>
            <Field orientation="horizontal">
              <Checkbox
                id="evse-published"
                name="published"
                defaultChecked={evse?.published ?? false}
              />
              <FieldLabel htmlFor="evse-published">Publier le portail de paiement</FieldLabel>
            </Field>
            {error && (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}
            <Button type="submit" disabled={pending || !items.length}>
              {pending ? "Enregistrement…" : "Enregistrer"}
            </Button>
          </FieldGroup>
        </form>
      </DialogContent>
    </Dialog>
  );
}
