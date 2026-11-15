"use client";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
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
import { isInvalid, rules, type Rule } from "@/lib/form";
import { revalidateLogic, useForm, useStore } from "@tanstack/react-form";
import { useState } from "react";

import { valueLabel } from "@/lib/labels";
import { commandTemplates, type CommandVersion } from "./station-commands";
import { isProtectedKey, protectedKeyMessage } from "./station-security";
import { t } from "@/lib/i18n";

function parsePayload(payload: string): object | null {
  try {
    const parsed: unknown = JSON.parse(payload);
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

const jsonObject: Rule<string> = (value) => {
  try {
    JSON.parse(value);
  } catch {
    return t("Le format JSON des paramètres est invalide.");
  }
  return parsePayload(value)
    ? undefined
    : t("Renseignez un objet JSON pour les paramètres de la commande.");
};

function protectedVariable(entry: unknown) {
  const data = entry as { component?: { name?: unknown }; variable?: { name?: unknown } } | null;
  return isProtectedKey(`${String(data?.component?.name)}/${String(data?.variable?.name)}`);
}

function protectedPayload(action: string): Rule<string> {
  return (value) => {
    const payload = parsePayload(value) as Record<string, unknown> | null;
    const legacy =
      action === "ChangeConfiguration" &&
      typeof payload?.key === "string" &&
      isProtectedKey(payload.key);
    const modern =
      action === "SetVariables" &&
      Array.isArray(payload?.setVariableData) &&
      payload.setVariableData.some(protectedVariable);
    return legacy || modern || action === "SetNetworkProfile" ? protectedKeyMessage() : undefined;
  };
}

export function StationCommandForm({
  station,
  version,
}: {
  station: string;
  version: CommandVersion;
}) {
  const templates = commandTemplates(version);
  const initialAction = Object.keys(templates)[0];
  const [message, setMessage] = useState("");
  const [failure, setFailure] = useState("");
  const form = useForm({
    defaultValues: {
      action: initialAction,
      payload: JSON.stringify(templates[initialAction], null, 2),
    },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      setFailure("");
      setMessage("");
      try {
        const result = await api<Row>(`stations/${encodeURIComponent(station)}/commands`, {
          method: "POST",
          body: JSON.stringify({
            action: value.action,
            payload: parsePayload(value.payload),
            ocppVersion: version === "2.0.1" ? version : undefined,
          }),
        });
        setMessage(
          t("Commande enregistrée : {id}. Consultez sa réponse dans le suivi des commandes.", {
            id: String(result.id),
          }),
        );
      } catch (error) {
        setFailure(error instanceof Error ? error.message : t("Impossible d’envoyer la commande."));
      }
    },
  });
  const pending = useStore(form.store, (state) => state.isSubmitting);

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
          name="action"
          listeners={{
            onChange: ({ value }) =>
              form.setFieldValue("payload", JSON.stringify(templates[value] ?? {}, null, 2)),
          }}
        >
          {(field) => (
            <Field>
              <FieldLabel>
                {version === "2.0.1" ? t("Commande OCPP 2.0.1") : t("Commande OCPP 1.6")}
              </FieldLabel>
              <Select
                name={field.name}
                value={field.state.value}
                onValueChange={(value) => {
                  if (value && Object.hasOwn(templates, value)) {
                    field.handleChange(value);
                  }
                }}
              >
                <SelectTrigger className="w-full">
                  <SelectValue>
                    {(value: string | null) => (value ? valueLabel(value) : null)}
                  </SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {Object.keys(templates).map((name) => (
                      <SelectItem key={name} value={name}>
                        {valueLabel(name)}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
            </Field>
          )}
        </form.Field>
        <form.Field
          name="payload"
          validators={{
            onDynamic: ({ value, fieldApi }) =>
              rules(jsonObject, protectedPayload(fieldApi.form.getFieldValue("action")))({ value }),
          }}
        >
          {(field) => {
            const invalid = isInvalid(field);
            return (
              <Field data-invalid={invalid}>
                <FieldLabel htmlFor="command-payload">{t("Paramètres de la commande")}</FieldLabel>
                <Textarea
                  id="command-payload"
                  name={field.name}
                  className="min-h-32 font-mono"
                  value={field.state.value}
                  onBlur={field.handleBlur}
                  onChange={(event) => field.handleChange(event.target.value)}
                  aria-invalid={invalid}
                  required
                />
                <FieldDescription>
                  {t(
                    "Vérifiez le connecteur et les paramètres avant l’envoi. La réponse confirme la réception de la commande ; le suivi de session confirme le démarrage de la recharge.",
                  )}
                </FieldDescription>
                {invalid && <FieldError errors={field.state.meta.errors} />}
              </Field>
            );
          }}
        </form.Field>
        {message && (
          <Alert>
            <AlertTitle>{t("En attente d’envoi")}</AlertTitle>
            <AlertDescription>{message}</AlertDescription>
          </Alert>
        )}
        {failure && (
          <Alert variant="destructive">
            <AlertDescription>{failure}</AlertDescription>
          </Alert>
        )}
        <Button type="submit" disabled={pending}>
          {pending ? t("Envoi…") : t("Envoyer la commande")}
        </Button>
      </FieldGroup>
    </form>
  );
}
