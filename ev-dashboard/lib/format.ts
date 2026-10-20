import { valueLabel } from "./labels";

const EMPTY = "—";

export function display(value: unknown): string {
  if (value === null || value === undefined || value === "") {
    return EMPTY;
  }
  if (typeof value === "boolean") {
    return value ? "Oui" : "Non";
  }
  if (typeof value === "object") {
    return JSON.stringify(value);
  }
  return valueLabel(String(value));
}

function parseDate(value: unknown) {
  if (!value) {
    return null;
  }
  const parsed = new Date(String(value));
  return Number.isNaN(parsed.valueOf()) ? null : parsed;
}

export function date(value: unknown): string {
  const parsed = parseDate(value);
  if (!parsed) {
    return value ? display(value) : EMPTY;
  }
  return new Intl.DateTimeFormat("fr-FR", { dateStyle: "medium", timeStyle: "short" }).format(
    parsed,
  );
}

export function day(value: unknown): string {
  const parsed = parseDate(value);
  return parsed ? new Intl.DateTimeFormat("fr-FR", { dateStyle: "medium" }).format(parsed) : EMPTY;
}

export function time(value: unknown): string {
  const parsed = parseDate(value);
  return parsed
    ? new Intl.DateTimeFormat("fr-FR", { hour: "2-digit", minute: "2-digit" }).format(parsed)
    : EMPTY;
}

export function relative(value: unknown): string {
  const parsed = parseDate(value);
  if (!parsed) {
    return EMPTY;
  }
  const seconds = Math.round((parsed.valueOf() - Date.now()) / 1000);
  const units: [Intl.RelativeTimeFormatUnit, number][] = [
    ["day", 86400],
    ["hour", 3600],
    ["minute", 60],
  ];
  const format = new Intl.RelativeTimeFormat("fr-FR", { numeric: "auto" });
  for (const [unit, size] of units) {
    if (Math.abs(seconds) >= size) {
      return format.format(Math.round(seconds / size), unit);
    }
  }
  return "à l’instant";
}

function isNumber(value: unknown): value is number | string {
  return (
    (typeof value === "number" && Number.isFinite(value)) ||
    (typeof value === "string" && value.trim() !== "" && Number.isFinite(Number(value)))
  );
}

export function number(value: unknown, digits = 0, minimum = 0): string {
  return isNumber(value)
    ? Number(value).toLocaleString("fr-FR", {
        minimumFractionDigits: minimum,
        maximumFractionDigits: digits,
      })
    : EMPTY;
}

export function kwh(value: unknown, digits = 2): string {
  return isNumber(value) ? `${number(value, digits, digits)} kWh` : EMPTY;
}

export function kw(value: unknown, digits = 1): string {
  return isNumber(value) ? `${number(value, digits)} kW` : EMPTY;
}

export function percent(value: unknown): string {
  return isNumber(value) ? `${number(value, 0)} %` : EMPTY;
}

export function money(value: unknown, currency: unknown = "EUR", digits = 2): string {
  if (!isNumber(value)) {
    return EMPTY;
  }
  return new Intl.NumberFormat("fr-FR", {
    style: "currency",
    currency: typeof currency === "string" && currency ? currency : "EUR",
    maximumFractionDigits: Math.max(2, digits),
  }).format(Number(value));
}

export function moneyMinor(value: unknown, currency: unknown = "EUR"): string {
  return isNumber(value) ? money(Number(value) / 100, currency) : EMPTY;
}

export function duration(seconds: unknown): string {
  if (!isNumber(seconds)) {
    return EMPTY;
  }
  const total = Math.max(0, Math.round(Number(seconds)));
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  if (hours) {
    return `${hours} h ${String(minutes).padStart(2, "0")}`;
  }
  if (minutes) {
    return `${minutes} min`;
  }
  return `${total} s`;
}

export function hours(value: unknown): string {
  return isNumber(value) ? `${number(value, 1)} h` : EMPTY;
}

export function toInputDateTime(value: unknown): string {
  const parsed = parseDate(value);
  if (!parsed) {
    return "";
  }
  const local = new Date(parsed.valueOf() - parsed.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 16);
}

export function fromInputDateTime(value: string): string | null {
  if (!value) {
    return null;
  }
  const parsed = new Date(value);
  return Number.isNaN(parsed.valueOf()) ? null : parsed.toISOString();
}

export function toInputDate(value: unknown): string {
  return toInputDateTime(value).slice(0, 10);
}

export function initials(name: string) {
  return (
    name
      .trim()
      .split(/\s+/)
      .slice(0, 2)
      .map((word) => word[0])
      .join("")
      .toUpperCase() || "C"
  );
}

export const monthLabels = [
  "janv.",
  "févr.",
  "mars",
  "avr.",
  "mai",
  "juin",
  "juil.",
  "août",
  "sept.",
  "oct.",
  "nov.",
  "déc.",
];
