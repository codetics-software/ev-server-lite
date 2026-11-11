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
import {
  FormPicker,
  FormSelect,
  FormText,
  FormTextarea,
  type Option,
} from "@/features/shared/form-fields";
import { notify, notifyError } from "@/features/shared/use-action";
import { api, errorMessage } from "@/lib/api";
import type { CommandReceipt } from "@/lib/api-types";
import { fromInputDateTime, toInputDateTime } from "@/lib/format";
import { required, rules } from "@/lib/form";
import { t } from "@/lib/i18n";
import {
  IconBolt,
  IconBoltOff,
  IconCalendarEvent,
  IconCalendarOff,
  IconChevronDown,
  IconClearAll,
  IconCloudDownload,
  IconFileAnalytics,
  IconFileCertificate,
  IconFileText,
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
  | "select"
  | "textarea";

type FieldSpec = {
  key: string;
  label: string;
  kind: FieldKind;
  options?: Option[];
  required?: boolean;
  initial?: (station: Station) => string;
  description?: string;
};

type ActionGroup = "charging" | "connectors" | "maintenance";

type ActionSpec = {
  id: string;
  label: string;
  description: string;
  icon: typeof IconPower;
  group: ActionGroup;
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

const extendedTriggerMessages: Option[] = [
  "StatusNotification",
  "Heartbeat",
  "MeterValues",
  "BootNotification",
  "FirmwareStatusNotification",
  "LogStatusNotification",
].map((value) => ({ value, label: value }));

const requestId = () => String(Math.floor(Date.now() / 1000) % 1000000);

const inOneHour = () => toInputDateTime(new Date(Date.now() + 3600000).toISOString());
const now = () => toInputDateTime(new Date().toISOString());
const firstConnector = (station: Station) => String(station.connectors?.[0]?.connector_id ?? 1);
const optionalNumber = (value: string) => (value ? Number(value) : undefined);

export function stationActions(): ActionSpec[] {
  return [
    {
      id: "start",
      label: t("Démarrer une recharge"),
      description: t("Demande à la borne de démarrer une session pour un badge sur un connecteur."),
      icon: IconPlayerPlay,
      group: "charging",
      confirmLabel: t("Démarrer"),
      fields: [
        { key: "connectorId", label: t("Connecteur"), kind: "connector", initial: firstConnector },
        { key: "tokenUid", label: t("Badge"), kind: "badge", required: true },
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
      label: t("Arrêter une recharge"),
      description: t("Demande à la borne d’arrêter la session en cours sur le connecteur."),
      icon: IconBoltOff,
      group: "charging",
      danger: true,
      confirmLabel: t("Arrêter"),
      fields: [
        { key: "transactionId", label: t("Session en cours"), kind: "session", required: true },
      ],
      submit: (_station, values) =>
        api<CommandReceipt>(`transactions/${values.transactionId}/stop`, { method: "POST" }),
    },
    {
      id: "reserve",
      label: t("Réserver un connecteur"),
      description: t("Réserve le connecteur pour un badge jusqu’à l’échéance choisie."),
      icon: IconCalendarEvent,
      group: "charging",
      confirmLabel: t("Réserver"),
      fields: [
        { key: "connectorId", label: t("Connecteur"), kind: "connector", initial: firstConnector },
        { key: "idTag", label: t("Badge"), kind: "badge", required: true },
        {
          key: "expiryDate",
          label: t("Fin de la réservation"),
          kind: "datetime",
          initial: inOneHour,
        },
        {
          key: "reservationId",
          label: t("Numéro de réservation"),
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
      label: t("Annuler une réservation"),
      description: t("Annule une réservation en cours à partir de son numéro."),
      icon: IconCalendarOff,
      group: "charging",
      confirmLabel: t("Annuler la réservation"),
      fields: [
        { key: "reservationId", label: t("Numéro de réservation"), kind: "number", required: true },
      ],
      submit: (station, values) =>
        sendCommand(station.id, "CancelReservation", {
          reservationId: Number(values.reservationId),
        }),
    },
    {
      id: "unlock",
      label: t("Déverrouiller un connecteur"),
      description: t("Libère le câble bloqué dans le connecteur."),
      icon: IconLockOpen,
      group: "connectors",
      confirmLabel: t("Déverrouiller"),
      fields: [
        { key: "connectorId", label: t("Connecteur"), kind: "connector", initial: firstConnector },
      ],
      submit: (station, values) =>
        sendCommand(station.id, "UnlockConnector", { connectorId: Number(values.connectorId) }),
    },
    {
      id: "availability",
      label: t("Modifier la disponibilité"),
      description: t("Met la borne ou un connecteur en service ou hors service."),
      icon: IconToggleRight,
      group: "connectors",
      confirmLabel: t("Appliquer"),
      fields: [
        { key: "connectorId", label: t("Cible"), kind: "connectorOrStation", initial: () => "0" },
        {
          key: "type",
          label: t("Disponibilité"),
          kind: "select",
          options: [
            { value: "Operative", label: t("En service") },
            { value: "Inoperative", label: t("Hors service") },
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
      label: t("Demander un message"),
      description: t("Demande à la borne d’envoyer immédiatement un message OCPP."),
      icon: IconSend,
      group: "connectors",
      confirmLabel: t("Demander"),
      fields: [
        {
          key: "requestedMessage",
          label: t("Message"),
          kind: "select",
          options: triggerMessages,
          initial: () => "StatusNotification",
        },
        { key: "connectorId", label: t("Cible"), kind: "connectorOrStation", initial: () => "0" },
      ],
      submit: (station, values) =>
        sendCommand(station.id, "TriggerMessage", {
          requestedMessage: values.requestedMessage,
          connectorId: Number(values.connectorId) || undefined,
        }),
    },
    {
      id: "extended-trigger",
      label: t("Demander un message (étendu)"),
      description: t(
        "Demande à la borne d’envoyer immédiatement un message OCPP (ExtendedTriggerMessage, profil de sécurité).",
      ),
      icon: IconSend,
      group: "connectors",
      confirmLabel: t("Demander"),
      fields: [
        {
          key: "requestedMessage",
          label: t("Message"),
          kind: "select",
          options: extendedTriggerMessages,
          initial: () => "StatusNotification",
        },
        { key: "connectorId", label: t("Cible"), kind: "connectorOrStation", initial: () => "0" },
      ],
      submit: (station, values) =>
        sendCommand(station.id, "ExtendedTriggerMessage", {
          requestedMessage: values.requestedMessage,
          connectorId: Number(values.connectorId) || undefined,
        }),
    },
    {
      id: "reset-soft",
      label: t("Redémarrage logiciel"),
      description: t(
        "Redémarre le logiciel de la borne. Les sessions en cours sont arrêtées proprement.",
      ),
      icon: IconRefresh,
      group: "maintenance",
      confirmLabel: t("Redémarrer"),
      fields: [],
      submit: (station) => sendCommand(station.id, "Reset", { type: "Soft" }),
    },
    {
      id: "reset-hard",
      label: t("Redémarrage matériel"),
      description: t(
        "Coupe et relance la borne. Les sessions en cours sont interrompues immédiatement.",
      ),
      icon: IconPower,
      group: "maintenance",
      danger: true,
      confirmLabel: t("Redémarrer la borne"),
      fields: [],
      submit: (station) => sendCommand(station.id, "Reset", { type: "Hard" }),
    },
    {
      id: "clear-cache",
      label: t("Vider le cache des autorisations"),
      description: t("Efface les badges mémorisés localement par la borne."),
      icon: IconClearAll,
      group: "maintenance",
      confirmLabel: t("Vider le cache"),
      fields: [],
      submit: (station) => sendCommand(station.id, "ClearCache", {}),
    },
    {
      id: "firmware",
      label: t("Mettre à jour le micrologiciel"),
      description: t("La borne télécharge puis installe le micrologiciel à la date indiquée."),
      icon: IconCloudDownload,
      group: "maintenance",
      confirmLabel: t("Programmer la mise à jour"),
      fields: [
        {
          key: "location",
          label: t("Adresse du fichier"),
          kind: "text",
          required: true,
          description: t("Adresse HTTPS ou FTP accessible par la borne."),
        },
        { key: "retrieveDate", label: t("Date de téléchargement"), kind: "datetime", initial: now },
        { key: "retries", label: t("Nombre de tentatives"), kind: "number", initial: () => "3" },
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
      label: t("Demander un diagnostic"),
      description: t("La borne envoie ses journaux de diagnostic à l’adresse indiquée."),
      icon: IconFileAnalytics,
      group: "maintenance",
      confirmLabel: t("Demander le diagnostic"),
      fields: [
        { key: "location", label: t("Adresse de dépôt"), kind: "text", required: true },
        { key: "startTime", label: t("Depuis (facultatif)"), kind: "datetime" },
        { key: "stopTime", label: t("Jusqu’à (facultatif)"), kind: "datetime" },
      ],
      submit: (station, values) =>
        sendCommand(station.id, "GetDiagnostics", {
          location: values.location,
          startTime: fromInputDateTime(values.startTime) ?? undefined,
          stopTime: fromInputDateTime(values.stopTime) ?? undefined,
        }),
    },
    {
      id: "logs",
      label: t("Demander les journaux"),
      description: t(
        "La borne envoie son journal de diagnostic ou de sécurité à l’adresse indiquée.",
      ),
      icon: IconFileText,
      group: "maintenance",
      confirmLabel: t("Demander les journaux"),
      fields: [
        {
          key: "logType",
          label: t("Journal"),
          kind: "select",
          options: [
            { value: "DiagnosticsLog", label: t("Diagnostic") },
            { value: "SecurityLog", label: t("Sécurité") },
          ],
          initial: () => "DiagnosticsLog",
        },
        { key: "remoteLocation", label: t("Adresse de dépôt"), kind: "text", required: true },
        { key: "oldestTimestamp", label: t("Depuis (facultatif)"), kind: "datetime" },
        { key: "latestTimestamp", label: t("Jusqu’à (facultatif)"), kind: "datetime" },
        { key: "retries", label: t("Nombre de tentatives"), kind: "number", initial: () => "3" },
      ],
      submit: (station, values) =>
        sendCommand(station.id, "GetLog", {
          logType: values.logType,
          requestId: Number(requestId()),
          log: {
            remoteLocation: values.remoteLocation,
            oldestTimestamp: fromInputDateTime(values.oldestTimestamp) ?? undefined,
            latestTimestamp: fromInputDateTime(values.latestTimestamp) ?? undefined,
          },
          retries: optionalNumber(values.retries),
        }),
    },
    {
      id: "signed-firmware",
      label: t("Mettre à jour le micrologiciel signé"),
      description: t(
        "La borne télécharge le micrologiciel, vérifie sa signature puis l’installe (SignedUpdateFirmware).",
      ),
      icon: IconFileCertificate,
      group: "maintenance",
      confirmLabel: t("Programmer la mise à jour"),
      fields: [
        {
          key: "location",
          label: t("Adresse du fichier"),
          kind: "text",
          required: true,
          description: t("Adresse HTTPS ou FTP accessible par la borne."),
        },
        {
          key: "retrieveDateTime",
          label: t("Date de téléchargement"),
          kind: "datetime",
          initial: now,
        },
        {
          key: "signingCertificate",
          label: t("Certificat de signature (PEM)"),
          kind: "textarea",
          required: true,
        },
        { key: "signature", label: t("Signature (base64)"), kind: "textarea", required: true },
        { key: "retries", label: t("Nombre de tentatives"), kind: "number", initial: () => "3" },
      ],
      submit: (station, values) =>
        sendCommand(station.id, "SignedUpdateFirmware", {
          requestId: Number(requestId()),
          firmware: {
            location: values.location,
            retrieveDateTime: fromInputDateTime(values.retrieveDateTime),
            signingCertificate: values.signingCertificate.trim(),
            signature: values.signature.trim(),
          },
          retries: optionalNumber(values.retries),
        }),
    },
  ];
}

export type ActionRequest = { id: string; initial?: Record<string, string> };

export function StationActionMenu({
  onSelect,
  disabled,
}: {
  onSelect: (request: ActionRequest) => void;
  disabled?: boolean;
}) {
  const actions = stationActions();
  const groups: { id: ActionGroup; label: string }[] = [
    { id: "charging", label: t("Recharge") },
    { id: "connectors", label: t("Connecteurs") },
    { id: "maintenance", label: t("Maintenance") },
  ];
  return (
    <DropdownMenu>
      <DropdownMenuTrigger render={<Button disabled={disabled} />}>
        <IconBolt data-icon="inline-start" />
        {t("Actions")}
        <IconChevronDown data-icon="inline-end" />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-64">
        {groups.map((group, index) => (
          <DropdownMenuGroup key={group.id}>
            {index > 0 && <DropdownMenuSeparator />}
            <DropdownMenuLabel>{group.label}</DropdownMenuLabel>
            {actions
              .filter((action) => action.group === group.id)
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
  const action = stationActions().find((item) => item.id === request?.id);
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
    label: t("Connecteur {id}", { id: connector.connector_id }),
  }));
  const sessions: Option[] = connectors
    .filter((connector) => connector.current_transaction_id != null)
    .map((connector) => ({
      value: String(connector.current_transaction_id),
      label: `${t("Connecteur {id} · session {session}", {
        id: connector.connector_id,
        session: connector.current_transaction_id ?? "",
      })}${connector.current_user_name ? ` · ${connector.current_user_name}` : ""}`,
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
          t("{action} : demande envoyée", { action: action.label }),
          id ? t("Suivez la réponse de la borne dans le suivi des commandes.") : undefined,
        );
        onDone();
      } catch (error) {
        notifyError(
          t("{action} : échec", { action: action.label }),
          errorMessage(error, t("La borne n’a pas été jointe.")),
        );
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
          {t("La borne est hors ligne : la demande sera envoyée dès sa reconnexion ou expirera.")}
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
                  ? { onDynamic: rules(required(t("Ce champ est obligatoire."))) }
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
                        options={[{ value: "0", label: t("Toute la borne") }, ...connectorOptions]}
                      />
                    );
                  case "session":
                    return sessions.length ? (
                      <FormSelect field={field} label={spec.label} options={sessions} />
                    ) : (
                      <p className="text-sm text-muted-foreground">
                        {t("Aucune session en cours sur cette borne.")}
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
                  case "textarea":
                    return (
                      <FormTextarea
                        field={field}
                        label={spec.label}
                        description={spec.description}
                        className="min-h-24 font-mono text-xs"
                        required={spec.required}
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
          {t("Suivi des commandes")}
        </Link>
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => (
            <Button
              type="submit"
              variant={action.danger ? "destructive" : "default"}
              disabled={submitting || (action.id === "stop" && !sessions.length)}
            >
              {submitting ? t("Envoi…") : action.confirmLabel}
            </Button>
          )}
        </form.Subscribe>
      </DialogFooter>
    </form>
  );
}
