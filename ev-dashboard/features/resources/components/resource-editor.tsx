"use client";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { FieldDescription, FieldGroup } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { sections } from "@/features/workspace/navigation";
import { display, type Row } from "@/lib/api";
import { isInvalid } from "@/lib/form";

import { resourceName } from "../config/resource-names";
import {
  useResourceEditor,
  useResourceForm,
  validateResourceField,
} from "../hooks/use-resource-editor";
import { ResourceField } from "./resource-field";

export function ResourceEditor({
  section,
  record,
  open,
  onClose,
  onSaved,
}: {
  section: string;
  record: Row | null;
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
}) {
  const name = resourceName(section);
  const { receipt, setReceipt, close } = useResourceEditor(onClose);

  return (
    <Dialog open={open} onOpenChange={(open) => !open && close()}>
      <DialogContent className="max-h-[85svh] overflow-auto">
        <DialogHeader>
          <DialogTitle>
            {receipt ? "Enregistrement effectué" : record ? `Modifier ${name}` : `Ajouter ${name}`}
          </DialogTitle>
          <DialogDescription>
            {sections.find((item) => item.id === section)?.label}
          </DialogDescription>
        </DialogHeader>
        {receipt ? (
          <div className="flex flex-col gap-4">
            <Alert>
              <AlertTitle>Enregistrement effectué</AlertTitle>
              <AlertDescription>{display(receipt.message)}</AlertDescription>
            </Alert>
            {Boolean(receipt.secret || receipt.token) && (
              <Input
                aria-label="Identifiant affiché une seule fois"
                value={String(receipt.secret || receipt.token)}
                readOnly
              />
            )}
            <Button onClick={close}>Terminer</Button>
          </div>
        ) : (
          <ResourceForm
            key={`${section}:${String(record?.id ?? record?.uid ?? "new")}`}
            section={section}
            record={record}
            onSaved={onSaved}
            onReceipt={setReceipt}
            onClose={close}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

function ResourceForm({
  section,
  record,
  onSaved,
  onReceipt,
  onClose,
}: {
  section: string;
  record: Row | null;
  onSaved: () => void;
  onReceipt: (receipt: Row) => void;
  onClose: () => void;
}) {
  const { form, fields, pending, error } = useResourceForm({
    section,
    record,
    onSaved,
    onReceipt,
    onClose,
  });

  return (
    <form
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        void form.handleSubmit();
      }}
    >
      <FieldGroup>
        {fields.map((config) => (
          <form.Field
            key={config.key}
            name={config.key}
            validators={{ onDynamic: validateResourceField(config) }}
          >
            {(field) => (
              <ResourceField
                field={config}
                value={field.state.value}
                invalid={isInvalid(field)}
                errors={field.state.meta.errors}
                onBlur={field.handleBlur}
                onChange={field.handleChange}
              />
            )}
          </form.Field>
        ))}
        {section === "cars" && (
          <FieldDescription>
            Les véhicules sont facultatifs : leur enregistrement n’est pas nécessaire pour
            recharger.
          </FieldDescription>
        )}
        {error && (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}
        <Button type="submit" disabled={pending}>
          {pending ? "Enregistrement…" : "Enregistrer"}
        </Button>
      </FieldGroup>
    </form>
  );
}
