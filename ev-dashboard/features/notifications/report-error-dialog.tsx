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
import { FieldGroup } from "@/components/ui/field";
import { FormPicker, FormText, FormTextarea } from "@/features/shared/form-fields";
import { useAction } from "@/features/shared/use-action";
import { api } from "@/lib/api";
import type { User } from "@/lib/api-types";
import { integerBetween, maxLength, required, rules } from "@/lib/form";
import { IconMessageReport } from "@tabler/icons-react";
import { revalidateLogic, useForm } from "@tanstack/react-form";
import { useState } from "react";

export function ReportErrorButton({
  user,
  stationId,
  connectorId,
  variant = "ghost",
  label,
}: {
  user: User;
  stationId?: string;
  connectorId?: number;
  variant?: "ghost" | "outline";
  label?: string;
}) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button
        variant={variant}
        size={label ? "default" : "icon"}
        aria-label={label ? undefined : "Signaler un problème"}
        onClick={() => setOpen(true)}
      >
        <IconMessageReport data-icon={label ? "inline-start" : undefined} />
        {label}
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          {open && (
            <ReportForm
              user={user}
              stationId={stationId}
              connectorId={connectorId}
              onDone={() => setOpen(false)}
            />
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}

function ReportForm({
  user,
  stationId,
  connectorId,
  onDone,
}: {
  user: User;
  stationId?: string;
  connectorId?: number;
  onDone: () => void;
}) {
  const action = useAction();
  const form = useForm({
    defaultValues: {
      subject: "",
      description: "",
      stationId: stationId ?? "",
      connectorId: connectorId ? String(connectorId) : "",
      phone: "",
    },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      await action.run(
        "report",
        () =>
          api("notifications/report-error", {
            method: "POST",
            body: JSON.stringify({
              subject: value.subject.trim(),
              description: value.description.trim(),
              stationId: value.stationId || undefined,
              connectorId: value.connectorId ? Number(value.connectorId) : undefined,
              phone: value.phone.trim() || undefined,
            }),
          }),
        {
          success: "Votre signalement a été transmis à l’exploitant.",
          failure: "Le signalement n’a pas pu être envoyé",
          onDone,
        },
      );
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
        <DialogTitle>Signaler un problème</DialogTitle>
        <DialogDescription>
          Décrivez ce qui ne fonctionne pas. L’exploitant du réseau est prévenu par courriel.
        </DialogDescription>
      </DialogHeader>
      <FieldGroup>
        <form.Field
          name="subject"
          validators={{
            onDynamic: rules(required("Indiquez l’objet du signalement."), maxLength(200)),
          }}
        >
          {(field) => <FormText field={field} label="Objet" maxLength={200} required />}
        </form.Field>
        <form.Field
          name="description"
          validators={{
            onDynamic: rules(required("Décrivez le problème rencontré."), maxLength(4000)),
          }}
        >
          {(field) => (
            <FormTextarea
              field={field}
              label="Description"
              className="min-h-28"
              maxLength={4000}
              required
            />
          )}
        </form.Field>
        <div className="grid gap-4 sm:grid-cols-[1fr_8rem]">
          <form.Field name="stationId">
            {(field) =>
              user.role === "DRIVER" ? (
                <FormText field={field} label="Borne concernée (facultatif)" maxLength={100} />
              ) : (
                <FormPicker field={field} source="stations" label="Borne concernée (facultatif)" />
              )
            }
          </form.Field>
          <form.Field
            name="connectorId"
            validators={{
              onDynamic: ({ value }) =>
                value
                  ? integerBetween(1, 99, "Connecteur invalide.")(value)
                    ? { message: "Connecteur invalide." }
                    : undefined
                  : undefined,
            }}
          >
            {(field) => <FormText field={field} label="Connecteur" type="number" min={1} />}
          </form.Field>
        </div>
        <form.Field name="phone" validators={{ onDynamic: rules(maxLength(40)) }}>
          {(field) => (
            <FormText
              field={field}
              label="Téléphone pour vous rappeler (facultatif)"
              type="tel"
              autoComplete="tel"
              maxLength={40}
            />
          )}
        </form.Field>
      </FieldGroup>
      <DialogFooter>
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => (
            <Button type="submit" disabled={submitting}>
              {submitting ? "Envoi…" : "Envoyer le signalement"}
            </Button>
          )}
        </form.Subscribe>
      </DialogFooter>
    </form>
  );
}
