"use client";

import { Alert, AlertAction, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { useAction } from "@/features/shared/use-action";
import { useApi } from "@/hooks/use-api";
import { api } from "@/lib/api";
import type { Page } from "@/lib/api-types";
import { IconHistory, IconPlus, IconX } from "@tabler/icons-react";
import { useId, useState } from "react";
import type { SecurityCapabilities, SecurityChange } from "./station-security";
import type { Station, StationEvent } from "./station-types";

const MAX_VENDORS = 20;

function heartbeatError(value: string) {
  if (!value.trim()) {
    return null;
  }
  const number = Number(value);
  return Number.isInteger(number) && number >= 10 && number <= 300
    ? null
    : "Intervalle entre 10 et 300 secondes.";
}

function vendorError(value: string, vendors: string[]) {
  const vendor = value.trim();
  if (!vendor) {
    return null;
  }
  if (vendor.length > 255) {
    return "Un identifiant de fournisseur contient 255 caractères au plus.";
  }
  if (vendor.includes(",")) {
    return "Un identifiant de fournisseur ne peut pas contenir de virgule.";
  }
  if (vendors.length >= MAX_VENDORS) {
    return `${MAX_VENDORS} fournisseurs au plus.`;
  }
  return null;
}

export function StationOcppSettings({
  station,
  capabilities,
  canEdit,
  onChanged,
  onShowCommands,
}: {
  station: Station;
  capabilities: SecurityCapabilities | null;
  canEdit: boolean;
  onChanged: () => void;
  onShowCommands: () => void;
}) {
  const [heartbeat, setHeartbeat] = useState(
    station.heartbeat_interval == null ? "" : String(station.heartbeat_interval),
  );
  const [vendors, setVendors] = useState(station.data_transfer_vendors ?? []);
  const [draft, setDraft] = useState("");
  const [queued, setQueued] = useState<string | null>(null);
  const action = useAction();
  const heartbeatId = useId();
  const vendorId = useId();
  const events = useApi<Page<StationEvent> | StationEvent[]>(
    canEdit
      ? `stations/${encodeURIComponent(station.id)}/events?type=DATA_TRANSFER&limit=50`
      : null,
  );
  const suggestions = [
    ...new Set(
      (Array.isArray(events.data) ? events.data : (events.data?.items ?? []))
        .filter((event) => event.status === "UnknownVendorId" && event.info)
        .map((event) => (event.info ?? "").split(" / ")[0].trim())
        .filter((vendor) => vendor && vendor.length <= 255 && !vendor.includes(",")),
    ),
  ]
    .filter((vendor) => !vendors.includes(vendor))
    .slice(0, 5);
  const fallback = capabilities?.default_heartbeat_interval ?? 60;
  const heartbeatIssue = heartbeatError(heartbeat);
  const draftIssue = vendorError(draft, vendors);

  function add(value: string) {
    const vendor = value.trim();
    if (!vendor || vendorError(vendor, vendors) || vendors.includes(vendor)) {
      return;
    }
    setVendors([...vendors, vendor]);
    setDraft("");
  }

  return (
    <Card className="max-w-3xl">
      <CardHeader>
        <CardTitle>Échanges OCPP</CardTitle>
        <CardDescription>
          Signal de vie et messages DataTransfer propres au fabricant.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            if (heartbeatIssue) {
              return;
            }
            void action.run(
              "ocpp-settings",
              () =>
                api<SecurityChange>(`stations/${encodeURIComponent(station.id)}/ocpp-settings`, {
                  method: "PUT",
                  body: JSON.stringify({
                    heartbeatInterval: heartbeat.trim() ? Number(heartbeat) : null,
                    dataTransferVendors: vendors,
                  }),
                }),
              {
                success: (result) => result.message,
                failure: "Les paramètres OCPP n’ont pas été enregistrés",
                onDone: (result) => {
                  setQueued(
                    result.command_id
                      ? station.connected
                        ? "Le nouvel intervalle du signal de vie est envoyé à la borne."
                        : "Le nouvel intervalle est envoyé à la reconnexion de la borne et appliqué au prochain démarrage de la borne."
                      : null,
                  );
                  onChanged();
                },
              },
            );
          }}
        >
          <fieldset disabled={!canEdit} className="contents">
            <FieldGroup>
              <Field data-invalid={Boolean(heartbeatIssue)}>
                <FieldLabel htmlFor={heartbeatId}>
                  Intervalle du signal de vie (secondes)
                </FieldLabel>
                <Input
                  id={heartbeatId}
                  type="number"
                  min={10}
                  max={300}
                  step={1}
                  value={heartbeat}
                  placeholder={`Valeur du serveur (${fallback} s)`}
                  aria-invalid={Boolean(heartbeatIssue)}
                  onChange={(event) => setHeartbeat(event.target.value)}
                />
                <FieldDescription>
                  Laissez vide pour utiliser la valeur du serveur.
                  {!station.connected && " Appliqué au prochain démarrage de la borne."}
                </FieldDescription>
                {heartbeatIssue && <FieldError>{heartbeatIssue}</FieldError>}
              </Field>
              <Field data-invalid={Boolean(draftIssue)}>
                <FieldLabel htmlFor={vendorId}>Fournisseurs DataTransfer acceptés</FieldLabel>
                {vendors.length > 0 && (
                  <ul className="flex flex-wrap gap-1.5" aria-label="Fournisseurs acceptés">
                    {vendors.map((vendor) => (
                      <li
                        key={vendor}
                        className="flex items-center gap-1 rounded-md border bg-muted py-0.5 pr-0.5 pl-2 font-mono text-xs"
                      >
                        <span className="max-w-60 truncate">{vendor}</span>
                        {canEdit && (
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon-xs"
                            aria-label={`Retirer ${vendor}`}
                            onClick={() => setVendors(vendors.filter((item) => item !== vendor))}
                          >
                            <IconX />
                          </Button>
                        )}
                      </li>
                    ))}
                  </ul>
                )}
                {canEdit && (
                  <div className="flex gap-2">
                    <Input
                      id={vendorId}
                      value={draft}
                      maxLength={255}
                      autoComplete="off"
                      placeholder="com.fabricant"
                      aria-invalid={Boolean(draftIssue)}
                      onChange={(event) => setDraft(event.target.value)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter") {
                          event.preventDefault();
                          add(draft);
                        }
                      }}
                    />
                    <Button
                      type="button"
                      variant="outline"
                      disabled={!draft.trim() || Boolean(draftIssue)}
                      onClick={() => add(draft)}
                    >
                      <IconPlus data-icon="inline-start" />
                      Ajouter
                    </Button>
                  </div>
                )}
                <FieldDescription>
                  Le serveur répond Accepted sans traiter le contenu : n’ajoutez un fournisseur que
                  si la borne l’exige.
                </FieldDescription>
                {draftIssue && <FieldError>{draftIssue}</FieldError>}
                {canEdit && suggestions.length > 0 && (
                  <div className="flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">
                    Vus récemment :
                    {suggestions.map((vendor) => (
                      <Button
                        key={vendor}
                        type="button"
                        size="xs"
                        variant="outline"
                        className="font-mono"
                        onClick={() => add(vendor)}
                      >
                        <IconPlus data-icon="inline-start" />
                        {vendor}
                      </Button>
                    ))}
                  </div>
                )}
              </Field>
              {queued && (
                <Alert>
                  <IconHistory />
                  <AlertTitle>Commande en file d’attente</AlertTitle>
                  <AlertDescription>{queued}</AlertDescription>
                  <AlertAction>
                    <Button type="button" size="sm" variant="outline" onClick={onShowCommands}>
                      Voir les commandes
                    </Button>
                  </AlertAction>
                </Alert>
              )}
              {canEdit && (
                <Button type="submit" className="w-fit" disabled={action.busy}>
                  {action.busy ? "Veuillez patienter…" : "Enregistrer les échanges OCPP"}
                </Button>
              )}
            </FieldGroup>
          </fieldset>
        </form>
      </CardContent>
    </Card>
  );
}
