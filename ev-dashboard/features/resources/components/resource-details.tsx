"use client";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { StationDetail } from "@/features/stations/station-detail";
import { api } from "@/lib/api";
import type { Row, User } from "@/lib/api-types";
import { display } from "@/lib/format";
import { useState } from "react";
import { endpoint } from "../config/endpoint";
import { fields } from "../config/form-fields";
import { resourceName } from "../config/resource-names";

type Props = {
  section: string;
  title: string;
  record: Row | null;
  user: User;
  canDelete: boolean;
  onClose: () => void;
  onDeleted: () => void;
};

export function ResourceDetails({
  section,
  title,
  record,
  user,
  canDelete,
  onClose,
  onDeleted,
}: Props) {
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState("");
  const name = resourceName(section);
  const entries = record ? { ...record, ...((record.data as Row) ?? {}) } : {};

  async function remove() {
    if (!record || !canDelete) {
      return;
    }
    setDeleting(true);
    setError("");
    try {
      await api(`${endpoint(section)}/${record.id}`, { method: "DELETE" });
      onDeleted();
      onClose();
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : `Unable to delete ${name}`);
    } finally {
      setDeleting(false);
    }
  }

  return (
    <Dialog
      open={record !== null}
      onOpenChange={(open) => {
        if (!open) {
          setError("");
          onClose();
        }
      }}
    >
      <DialogContent className="max-h-[85svh] overflow-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>
            {String(record?.name ?? record?.uid ?? record?.action ?? `${title} details`)}
          </DialogTitle>
          <DialogDescription>{title}</DialogDescription>
        </DialogHeader>
        {record && section === "stations" ? (
          <StationDetail station={String(record.id)} user={user} />
        ) : (
          <dl className="flex flex-col gap-3">
            {Object.entries(entries)
              .filter(([key]) => key !== "data")
              .map(([key, value]) => (
                <div key={key} className="grid gap-1 border-b pb-3 text-sm sm:grid-cols-3">
                  <dt className="text-muted-foreground">
                    {fields[section]?.find((field) => field.key === key)?.label ??
                      key.replaceAll("_", " ").replace(/([a-z])([A-Z])/g, "$1 $2")}
                  </dt>
                  <dd className="min-w-0 break-words sm:col-span-2">{display(value)}</dd>
                </div>
              ))}
          </dl>
        )}
        {canDelete && (
          <div className="flex flex-col gap-3 border-t pt-4">
            <p className="text-sm text-muted-foreground">
              Permanently delete {String(record?.name ?? `this ${name}`)}.
            </p>
            {error && (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}
            <Button variant="destructive" disabled={deleting} onClick={remove}>
              {deleting ? "Deleting…" : `Delete ${name}`}
            </Button>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
