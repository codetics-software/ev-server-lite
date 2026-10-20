export type FieldIssue = { message: string };
export type Rule<T> = (value: T) => string | undefined;

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function rules<T>(...checks: Rule<T>[]) {
  return ({ value }: { value: T }): FieldIssue | undefined => {
    for (const check of checks) {
      const message = check(value);
      if (message) {
        return { message };
      }
    }
    return undefined;
  };
}

export function required(message = "Ce champ est obligatoire."): Rule<string> {
  return (value) => (value.trim() ? undefined : message);
}

export function maxLength(max: number, message?: string): Rule<string> {
  return (value) =>
    value.length <= max ? undefined : (message ?? `Saisissez au plus ${max} caractères.`);
}

export function lengthBetween(min: number, max: number, message: string): Rule<string> {
  return (value) => (value.length >= min && value.length <= max ? undefined : message);
}

export function matches(pattern: RegExp, message: string): Rule<string> {
  return (value) => (!value || pattern.test(value) ? undefined : message);
}

export function email(message = "Renseignez une adresse électronique valide."): Rule<string> {
  return (value) => (!value || (value.length <= 320 && EMAIL.test(value)) ? undefined : message);
}

export function numeric(message = "Saisissez un nombre valide."): Rule<string> {
  return (value) => (!value.trim() || Number.isFinite(Number(value)) ? undefined : message);
}

export function integerBetween(min: number, max: number, message: string): Rule<string> {
  return (value) => {
    const number = Number(value);
    return value.trim() && Number.isInteger(number) && number >= min && number <= max
      ? undefined
      : message;
  };
}

export function isInvalid(field: { state: { meta: { isTouched: boolean; isValid: boolean } } }) {
  return field.state.meta.isTouched && !field.state.meta.isValid;
}

export function optionalNumber(
  min = -Infinity,
  max = Infinity,
  message = "Saisissez un nombre valide.",
): Rule<string> {
  return (value) => {
    if (!value.trim()) {
      return undefined;
    }
    const number = Number(value);
    return Number.isFinite(number) && number >= min && number <= max ? undefined : message;
  };
}

export function toNumber(value: string | number | null | undefined): number | undefined {
  if (value === null || value === undefined || String(value).trim() === "") {
    return undefined;
  }
  const number = Number(value);
  return Number.isFinite(number) ? number : undefined;
}

export function asText(value: unknown): string {
  return value === null || value === undefined ? "" : String(value);
}
