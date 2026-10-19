import { date, display, type Row } from "@/lib/api";

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
