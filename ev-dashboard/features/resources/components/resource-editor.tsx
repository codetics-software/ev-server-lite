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
import { fields } from "../config/form-fields";

import { resourceName } from "../config/resource-names";
import { useResourceEditor } from "../hooks/use-resource-editor";
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
  const { pending, error, receipt, existing, isRecord, submit, close } = useResourceEditor(
    section,
    record,
    onClose,
    onSaved,
  );

  return (
    <Dialog open={open} onOpenChange={(open) => !open && close()}>
      <DialogContent className="max-h-[85svh] overflow-auto">
        <DialogHeader>
          <DialogTitle>
            {receipt ? `${name} saved` : record ? `Edit ${name}` : `Add ${name}`}
          </DialogTitle>
          <DialogDescription>
            {sections.find((item) => item.id === section)?.label}
          </DialogDescription>
        </DialogHeader>
        {receipt ? (
          <div className="flex flex-col gap-4">
            <Alert>
              <AlertTitle>Saved successfully</AlertTitle>
              <AlertDescription>{display(receipt.message)}</AlertDescription>
            </Alert>
            {Boolean(receipt.secret || receipt.token) && (
              <Input
                aria-label="One-time credential"
                value={String(receipt.secret || receipt.token)}
                readOnly
              />
            )}
            <Button onClick={close}>Done</Button>
          </div>
        ) : (
          <form onSubmit={submit} key={String(existing.id || existing.uid || "new")}>
            <FieldGroup>
              {(fields[section] || []).map((field) => {
                return (
                  <ResourceField
                    key={field.key}
                    field={field}
                    existing={existing}
                    isRecord={isRecord}
                  />
                );
              })}
              {section === "cars" && (
                <FieldDescription>
                  Vehicles are optional and never required for charging.
                </FieldDescription>
              )}
              {error && (
                <Alert variant="destructive">
                  <AlertDescription>{error}</AlertDescription>
                </Alert>
              )}
              <Button type="submit" disabled={pending}>
                {pending ? "Saving…" : `Save ${name}`}
              </Button>
            </FieldGroup>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
