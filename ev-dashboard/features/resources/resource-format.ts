import { date, display, type Row } from "@/lib/api";
import type { FormField } from "./types";

export function asRow(value: unknown): Row {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? Object.fromEntries(Object.entries(value))
    : {};
}

export function valueAt(row: Row, key: string): unknown {
  if (key.startsWith("data.")) {
    return (row.data as Row | undefined)?.[key.slice(5)];
  }

  return row[key];
}

export function formatCell(row: Row, key: string): string {
  const value = valueAt(row, key);

  if (key.endsWith("_at") || key === "last_seen") {
    return date(value);
  }

  if (key === "energy_kwh" && value !== null) {
    return Number(value).toLocaleString("fr-FR", { maximumFractionDigits: 3 });
  }

  return display(value);
}

export function inputValue(value: unknown, type?: string): string {
  if (value === null || value === undefined) {
    return "";
  }

  if (type === "datetime-local") {
    const date = new Date(String(value));

    if (Number.isNaN(date.valueOf())) {
      return "";
    }

    date.setMinutes(date.getMinutes() - date.getTimezoneOffset());
    return date.toISOString().slice(0, -1);
  }

  return String(value);
}

const aliases: Record<string, string> = {
  userId: "user_id",
  expiresAt: "expires_at",
};

export function initialValue(field: FormField, existing: Row, isRecord: boolean): string | boolean {
  const alias = aliases[field.key];
  const fallback =
    field.key === "name"
      ? existing.name || existing.display_name
      : (existing[field.key] ?? (alias ? existing[alias] : undefined));
  const value = (isRecord ? asRow(existing.data)[field.key] : undefined) ?? fallback;

  if (field.type === "checkbox") {
    return value !== false;
  }
  if (field.options) {
    return String(value || field.options[0]);
  }
  return inputValue(value, field.type);
}
