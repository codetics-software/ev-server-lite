"use client";

import { api } from "@/lib/api";
import type { Row } from "@/lib/api-types";
import { useState, type FormEvent } from "react";
import { endpoint } from "../config/endpoint";
import { fields } from "../config/form-fields";

export function useResourceEditor(
  section: string,
  record: Row | null,
  onClose: () => void,
  onSaved: () => void,
) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [receipt, setReceipt] = useState<Row | null>(null);
  const resource = endpoint(section);
  const isRecord = resource.startsWith("records/");
  const existing = record ?? {};

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setError("");

    const form = new FormData(event.currentTarget);
    const values: Row = {};

    for (const field of fields[section] || []) {
      const value = form.get(field.key);

      if (field.type === "checkbox") {
        values[field.key] = value === "on";
      } else if (value) {
        values[field.key] =
          field.type === "datetime-local" ? new Date(String(value)).toISOString() : value;
      }
    }

    const editing = Boolean(record);
    const url = editing && section !== "tokens" ? `${resource}/${existing.id}` : resource;
    const body = isRecord
      ? {
          name: values.name,
          data: {
            ...((existing.data as Row) || {}),
            ...Object.fromEntries(Object.entries(values).filter(([key]) => key !== "name")),
          },
          revision: existing.revision,
        }
      : values;

    try {
      const result = await api<Row>(url, {
        method: editing && section !== "tokens" ? "PUT" : "POST",
        body: JSON.stringify(body),
      });
      onSaved();

      if (result.secret || result.token || result.message) {
        setReceipt(result);
      } else {
        onClose();
      }
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Unable to save");
    } finally {
      setPending(false);
    }
  }

  function close() {
    setReceipt(null);
    setError("");
    onClose();
  }

  return { pending, error, receipt, existing, isRecord, submit, close };
}
