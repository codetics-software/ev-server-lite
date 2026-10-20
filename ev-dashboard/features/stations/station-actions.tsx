"use client";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { FieldGroup } from "@/components/ui/field";
import { FormPicker, FormSelect, FormText, type Option } from "@/features/shared/form-fields";
import { notify, notifyError } from "@/features/shared/use-action";
import { api, errorMessage } from "@/lib/api";
import type { CommandReceipt } from "@/lib/api-types";
import { fromInputDateTime, toInputDateTime } from "@/lib/format";
import { required, rules } from "@/lib/form";
import {
  IconBolt,
  IconBoltOff,
  IconCalendarEvent,
  IconCalendarOff,
  IconChevronDown,
  IconClearAll,
  IconCloudDownload,
  IconFileAnalytics,
  IconLockOpen,
  IconPlayerPlay,
  IconPower,
  IconRefresh,
  IconToggleRight,
  IconSend,
} from "@tabler/icons-react";
import { revalidateLogic, useForm } from "@tanstack/react-form";
import Link from "next/link";
import type { Station } from "./station-types";

type FieldKind =
  | "connector"
  | "connectorOrStation"
  | "session"
  | "badge"
  | "text"
  | "datetime"
  | "number"
  | "select";

type FieldSpec = {
  key: string;
  label: string;
  kind: FieldKind;
  options?: Option[];
  required?: boolean;
  initial?: (station: Station) => string;
  description?: string;
};

type ActionSpec = {
  id: string;
  label: string;
  description: string;
  icon: typeof IconPower;
  group: "Recharge" | "Connecteurs" | "Maintenance";
  danger?: boolean;
  confirmLabel: string;
  fields: FieldSpec[];
  submit: (station: Station, values: Record<string, string>) => Promise<unknown>;
};

export function sendCommand(stationId: string, action: string, payload: Record<string, unknown>) {
  return api<CommandReceipt>(`stations/${encodeURIComponent(stationId)}/commands`, {
    method: "POST",
    body: JSON.stringify({ action, payload }),
  });
}

const triggerMessages: Option[] = [
  "StatusNotification",
  "Heartbeat",
  "MeterValues",
  "BootNotification",
  "FirmwareStatusNotification",
  "DiagnosticsStatusNotification",
].map((value) => ({ value, label: value }));

const inOneHour = () => toInputDateTime(new Date(Date.now() + 3600000).toISOString());
const now = () => toInputDateTime(new Date().toISOString());
const firstConnector = (station: Station) => String(station.connectors?.[0]?.connector_id ?? 1);
const optionalNumber = (value: string) => (value ? Number(value) : undefined);

export const stationActions: ActionSpec[] = [
  {
    id: "start",
    label: "Démarrer une recharge",
    description: "Demande à la borne de démarrer une session pour un badge sur un connecteur.",
    icon: IconPlayerPlay,
    group: "Recharge",
    confirmLabel: "Démarrer",
    fields: [
      { key: "connectorId", label: "Connecteur", kind: "connector", initial: firstConnector },
      { key: "tokenUid", label: "Badge", kind: "badge", required: true },
    ],
    submit: (station, values) =>
      api<CommandReceipt>("transactions/start", {
        method: "POST",
        body: JSON.stringify({
          stationId: station.id,
          connectorId: Number(values.connectorId),
          tokenUid: values.tokenUid,
        }),
      }),
  },
  {
    id: "stop",
    label: "Arrêter une recharge",
    description: "Demande à la borne d’arrêter la session en cours sur le connecteur.",
    icon: IconBoltOff,
    group: "Recharge",
    danger: true,
    confirmLabel: "Arrêter",
    fields: [{ key: "transactionId", label: "Session en cours", kind: "session", required: true }],
    submit: (_station, values) =>
      api<CommandReceipt>(`transactions/${values.transactionId}/stop`, { method: "POST" }),
  },
  {
    id: "reserve",
    label: "Réserver un connecteur",
    description: "Réserve le connecteur pour un badge jusqu’à l’échéance choisie.",
    icon: IconCalendarEvent,
    group: "Recharge",
    confirmLabel: "Réserver",
    fields: [
      { key: "connectorId", label: "Connecteur", kind: "connector", initial: firstConnector },
      { key: "idTag", label: "Badge", kind: "badge", required: true },
      { key: "expiryDate", label: "Fin de la réservation", kind: "datetime", initial: inOneHour },
      {
        key: "reservationId",
        label: "Numéro de réservation",
        kind: "number",
        initial: () => String(Math.floor(Date.now() / 1000) % 1000000),
      },
    ],
    submit: (station, values) =>
      sendCommand(station.id, "ReserveNow", {
        connectorId: Number(values.connectorId),
        idTag: values.idTag,
        expiryDate: fromInputDateTime(values.expiryDate),
        reservationId: Number(values.reservationId),
      }),
  },
  {
    id: "cancel-reservation",
    label: "Annuler une réservation",
    description: "Annule une réservation en cours à partir de son numéro.",
    icon: IconCalendarOff,
    group: "Recharge",
    confirmLabel: "Annuler la réservation",
    fields: [
      { key: "reservationId", label: "Numéro de réservation", kind: "number", required: true },
    ],
    submit: (station, values) =>
      sendCommand(station.id, "CancelReservation", { reservationId: Number(values.reservationId) }),
  },
  {
    id: "unlock",
    label: "Déverrouiller un connecteur",
    description: "Libère le câble bloqué dans le connecteur.",
    icon: IconLockOpen,
    group: "Connecteurs",
    confirmLabel: "Déverrouiller",
    fields: [
      { key: "connectorId", label: "Connecteur", kind: "connector", initial: firstConnector },
    ],
    submit: (station, values) =>
      sendCommand(station.id, "UnlockConnector", { connectorId: Number(values.connectorId) }),
  },
  {
    id: "availability",
    label: "Modifier la disponibilité",
    description: "Met la borne ou un connecteur en service ou hors service.",
    icon: IconToggleRight,
    group: "Connecteurs",
    confirmLabel: "Appliquer",
    fields: [
      { key: "connectorId", label: "Cible", kind: "connectorOrStation", initial: () => "0" },
      {
        key: "type",
        label: "Disponibilité",
        kind: "select",
        options: [
          { value: "Operative", label: "En service" },
          { value: "Inoperative", label: "Hors service" },
        ],
        initial: () => "Operative",
      },
    ],
    submit: (station, values) =>
      sendCommand(station.id, "ChangeAvailability", {
        connectorId: Number(values.connectorId),
        type: values.type,
      }),
  },
  {
    id: "trigger",
    label: "Demander un message",
    description: "Demande à la borne d’envoyer immédiatement un message OCPP.",
    icon: IconSend,
    group: "Connecteurs",
    confirmLabel: "Demander",
    fields: [
      {
        key: "requestedMessage",
        label: "Message",
        kind: "select",
        options: triggerMessages,
        initial: () => "StatusNotification",
      },
      { key: "connectorId", label: "Cible", kind: "connectorOrStation", initial: () => "0" },
    ],
    submit: (station, values) =>
      sendCommand(station.id, "TriggerMessage", {
        requestedMessage: values.requestedMessage,
        connectorId: Number(values.connectorId) || undefined,
      }),
  },
  {
    id: "reset-soft",
    label: "Redémarrage logiciel",
    description:
      "Redémarre le logiciel de la borne. Les sessions en cours sont arrêtées proprement.",
    icon: IconRefresh,
    group: "Maintenance",
    confirmLabel: "Redémarrer",
    fields: [],
    submit: (station) => sendCommand(station.id, "Reset", { type: "Soft" }),
  },
  {
    id: "reset-hard",
    label: "Redémarrage matériel",
    description:
      "Coupe et relance la borne. Les sessions en cours sont interrompues immédiatement.",
    icon: IconPower,
    group: "Maintenance",
    danger: true,
    confirmLabel: "Redémarrer la borne",
    fields: [],
    submit: (station) => sendCommand(station.id, "Reset", { type: "Hard" }),
  },
  {
    id: "clear-cache",
    label: "Vider le cache des autorisations",
    description: "Efface les badges mémorisés localement par la borne.",
    icon: IconClearAll,
    group: "Maintenance",
    confirmLabel: "Vider le cache",
    fields: [],
    submit: (station) => sendCommand(station.id, "ClearCache", {}),
  },
  {
    id: "firmware",
    label: "Mettre à jour le micrologiciel",
    description: "La borne télécharge puis installe le micrologiciel à la date indiquée.",
    icon: IconCloudDownload,
    group: "Maintenance",
    confirmLabel: "Programmer la mise à jour",
    fields: [
      {
        key: "location",
        label: "Adresse du fichier",
        kind: "text",
        required: true,
        description: "Adresse HTTPS ou FTP accessible par la borne.",
      },
      { key: "retrieveDate", label: "Date de téléchargement", kind: "datetime", initial: now },
      { key: "retries", label: "Nombre de tentatives", kind: "number", initial: () => "3" },
    ],
    submit: (station, values) =>
      sendCommand(station.id, "UpdateFirmware", {
        location: values.location,
        retrieveDate: fromInputDateTime(values.retrieveDate),
        retries: optionalNumber(values.retries),
      }),
  },
  {
    id: "diagnostics",
    label: "Demander un diagnostic",
    description: "La borne envoie ses journaux de diagnostic à l’adresse indiquée.",
    icon: IconFileAnalytics,
    group: "Maintenance",
    confirmLabel: "Demander le diagnostic",
    fields: [
      { key: "location", label: "Adresse de dépôt", kind: "text", required: true },
      { key: "startTime", label: "Depuis (facultatif)", kind: "datetime" },
      { key: "stopTime", label: "Jusqu’à (facultatif)", kind: "datetime" },
    ],
    submit: (station, values) =>
      sendCommand(station.id, "GetDiagnostics", {
        location: values.location,
        startTime: fromInputDateTime(values.startTime) ?? undefined,
        stopTime: fromInputDateTime(values.stopTime) ?? undefined,
      }),
  },
];

export type ActionRequest = { id: string; initial?: Record<string, string> };

export function StationActionMenu({
  onSelect,
  disabled,
}: {
  onSelect: (request: ActionRequest) => void;
  disabled?: boolean;
}) {
  const groupNames = ["Recharge", "Connecteurs", "Maintenance"] as const;
  return (
    <DropdownMenu>
      <DropdownMenuTrigger render={<Button disabled={disabled} />}>
        <IconBolt data-icon="inline-start" />
        Actions
        <IconChevronDown data-icon="inline-end" />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-64">
        {groupNames.map((group, index) => (
          <DropdownMenuGroup key={group}>
            {index > 0 && <DropdownMenuSeparator />}
            <DropdownMenuLabel>{group}</DropdownMenuLabel>
            {stationActions
              .filter((action) => action.group === group)
              .map((action) => (
                <DropdownMenuItem
                  key={action.id}
                  variant={action.danger ? "destructive" : "default"}
                  onClick={() => onSelect({ id: action.id })}
                >
                  <action.icon />
                  {action.label}
                </DropdownMenuItem>
              ))}
          </DropdownMenuGroup>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

export function StationActionDialog({
  station,
  request,
  onClose,
  onSent,
}: {
  station: Station;
  request: ActionRequest | null;
  onClose: () => void;
  onSent: () => void;
}) {
  const action = stationActions.find((item) => item.id === request?.id);
  return (
    <Dialog open={Boolean(action)} onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        {action && request && (
          <ActionForm
            key={`${action.id}:${JSON.stringify(request.initial ?? {})}`}
            action={action}
            station={station}
            initial={request.initial ?? {}}
            onDone={() => {
              onSent();
              onClose();
            }}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

function ActionForm({
  action,
  station,
  initial,
  onDone,
}: {
  action: ActionSpec;
  station: Station;
  initial: Record<string, string>;
  onDone: () => void;
}) {
  const connectors = station.connectors ?? [];
  const connectorOptions: Option[] = connectors.map((connector) => ({
    value: String(connector.connector_id),
    label: `Connecteur ${connector.connector_id}`,
  }));
  const sessions: Option[] = connectors
    .filter((connector) => connector.current_transaction_id != null)
    .map((connector) => ({
      value: String(connector.current_transaction_id),
      label: `Connecteur ${connector.connector_id} · session ${connector.current_transaction_id}${connector.current_user_name ? ` · ${connector.current_user_name}` : ""}`,
    }));
  const defaults: Record<string, string> = Object.fromEntries(
    action.fields.map((field) => [
      field.key,
      initial[field.key] ??
        field.initial?.(station) ??
        (field.kind === "session" ? (sessions[0]?.value ?? "") : ""),
    ]),
  );
  const form = useForm({
    defaultValues: defaults,
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      try {
        const receipt = await action.submit(station, value);
        const id = (receipt as CommandReceipt | undefined)?.id;
        notify(
          `${action.label} : demande envoyée`,
          id ? `Suivez la réponse de la borne dans le suivi des commandes.` : undefined,
        );
        onDone();
      } catch (error) {
        notifyError(`${action.label} : échec`, errorMessage(error, "La borne n’a pas été jointe."));
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
      className="flex flex-col gap-5"
    >
      <DialogHeader>
        <DialogTitle>{action.label}</DialogTitle>
        <DialogDescription>
          {station.name} · {action.description}
        </DialogDescription>
      </DialogHeader>
      {!station.connected && (
        <p className="rounded-lg border border-dashed p-3 text-sm text-muted-foreground">
          La borne est hors ligne : la demande sera envoyée dès sa reconnexion ou expirera.
        </p>
      )}
      {action.fields.length > 0 && (
        <FieldGroup>
          {action.fields.map((spec) => (
            <form.Field
              key={spec.key}
              name={spec.key}
              validators={
                spec.required
                  ? { onDynamic: rules(required("Ce champ est obligatoire.")) }
                  : undefined
              }
            >
              {(field) => {
                switch (spec.kind) {
                  case "badge":
                    return <FormPicker field={field} source="tokens" label={spec.label} />;
                  case "connector":
                    return (
                      <FormSelect field={field} label={spec.label} options={connectorOptions} />
                    );
                  case "connectorOrStation":
                    return (
                      <FormSelect
                        field={field}
                        label={spec.label}
                        options={[{ value: "0", label: "Toute la borne" }, ...connectorOptions]}
                      />
                    );
                  case "session":
                    return sessions.length ? (
                      <FormSelect field={field} label={spec.label} options={sessions} />
                    ) : (
                      <p className="text-sm text-muted-foreground">
                        Aucune session en cours sur cette borne.
                      </p>
                    );
                  case "select":
                    return (
                      <FormSelect field={field} label={spec.label} options={spec.options ?? []} />
                    );
                  case "datetime":
                    return (
                      <FormText
                        field={field}
                        label={spec.label}
                        type="datetime-local"
                        description={spec.description}
                      />
                    );
                  case "number":
                    return (
                      <FormText
                        field={field}
                        label={spec.label}
                        type="number"
                        min={0}
                        description={spec.description}
                      />
                    );
                  default:
                    return (
                      <FormText
                        field={field}
                        label={spec.label}
                        description={spec.description}
                        required={spec.required}
                      />
                    );
                }
              }}
            </form.Field>
          ))}
        </FieldGroup>
      )}
      <DialogFooter className="items-center sm:justify-between">
        <Link href="/commands" className="text-xs text-muted-foreground hover:text-foreground">
          Suivi des commandes
        </Link>
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => (
            <Button
              type="submit"
              variant={action.danger ? "destructive" : "default"}
              disabled={submitting || (action.id === "stop" && !sessions.length)}
            >
              {submitting ? "Envoi…" : action.confirmLabel}
            </Button>
          )}
        </form.Subscribe>
      </DialogFooter>
    </form>
  );
}
