"use client";

import { api } from "@/lib/api";
import type { Row } from "@/lib/api-types";
import { email, numeric, type FieldIssue, type Rule } from "@/lib/form";
import { revalidateLogic, useForm, useStore } from "@tanstack/react-form";
import { useState } from "react";
import { endpoint } from "../config/endpoint";
import { fields } from "../config/form-fields";
import { asRow, initialValue } from "../resource-format";
import type { FormField } from "../types";

export type ResourceValue = string | boolean;

export function useResourceEditor(onClose: () => void) {
  const [receipt, setReceipt] = useState<Row | null>(null);

  function close() {
    setReceipt(null);
    onClose();
  }

  return { receipt, setReceipt, close };
}

function textRules(field: FormField): Rule<string>[] {
  const checks: Rule<string>[] = [
    (value) => (field.required && !value.trim() ? "Ce champ est obligatoire." : undefined),
    (value) =>
      value.length > (field.maxLength ?? 320)
        ? `Saisissez au plus ${field.maxLength ?? 320} caractères.`
        : undefined,
  ];

  if (field.type === "email") {
    checks.push(email());
  }
  if (field.type === "number") {
    checks.push(numeric());
  }
  if (field.type === "datetime-local") {
    checks.push((value) =>
      value && Number.isNaN(new Date(value).valueOf()) ? "Saisissez une date valide." : undefined,
    );
  }
  if (field.pattern) {
    const { regex, message } = field.pattern;
    checks.push((value) => (value && !regex.test(value) ? message : undefined));
  }
  return checks;
}

export function validateResourceField(field: FormField) {
  const checks = textRules(field);

  return ({ value }: { value: ResourceValue }): FieldIssue | undefined => {
    if (typeof value === "boolean") {
      return undefined;
    }
    for (const check of checks) {
      const message = check(value);
      if (message) {
        return { message };
      }
    }
    return undefined;
  };
}

export function useResourceForm({
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
  const [error, setError] = useState("");
  const resource = endpoint(section);
  const isRecord = resource.startsWith("records/");
  const existing = record ?? {};
  const sectionFields = fields[section] || [];
  const defaultValues: Record<string, ResourceValue> = Object.fromEntries(
    sectionFields.map((field) => [field.key, initialValue(field, existing, isRecord)]),
  );

  const form = useForm({
    defaultValues,
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value: submitted }) => {
      setError("");

      const values: Row = {};

      for (const field of sectionFields) {
        const value = submitted[field.key];

        if (field.type === "checkbox") {
          values[field.key] = value === true;
        } else if (typeof value === "string" && value) {
          values[field.key] =
            field.type === "datetime-local" ? new Date(value).toISOString() : value;
        }
      }

      const editing = Boolean(record);
      const url = editing && section !== "tokens" ? `${resource}/${existing.id}` : resource;
      const body = isRecord
        ? {
            name: values.name,
            data: {
              ...asRow(existing.data),
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

        if (result && (result.secret || result.token || result.message)) {
          onReceipt(result);
        } else {
          onClose();
        }
      } catch (failure) {
        setError(failure instanceof Error ? failure.message : "Impossible d’enregistrer.");
      }
    },
  });
  const pending = useStore(form.store, (state) => state.isSubmitting);

  return { form, fields: sectionFields, pending, error };
}
