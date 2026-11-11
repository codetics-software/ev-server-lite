import { catalog } from "./catalog";
import { defaultLocale, intlLocale, isLocale, type Locale } from "./locales";

export { intlLocale, localeNames, locales, type Locale } from "./locales";

type Params = Record<string, string | number>;

let active: Locale = defaultLocale;

function documentLocale(): Locale | null {
  if (typeof document === "undefined") {
    return null;
  }
  const lang = document.documentElement.lang;
  return isLocale(lang) ? lang : null;
}

export function setActiveLocale(locale: Locale) {
  active = locale;
}

export function getLocale(): Locale {
  return documentLocale() ?? active;
}

export function getIntlLocale(): string {
  return intlLocale(getLocale());
}

function interpolate(text: string, params?: Params) {
  if (!params) {
    return text;
  }
  return text.replace(/\{(\w+)\}/g, (match, name: string) =>
    name in params ? String(params[name]) : match,
  );
}

export function translate(source: string, locale: Locale, params?: Params): string {
  const text = locale === "fr" ? source : (catalog[source]?.[locale] ?? source);
  return interpolate(text, params);
}

export function t(source: string, params?: Params): string {
  return translate(source, getLocale(), params);
}

export function tp(count: number, one: string, other: string, params?: Params): string {
  const locale = getLocale();
  const category = new Intl.PluralRules(intlLocale(locale)).select(count);
  return translate(category === "one" ? one : other, locale, { count, ...params });
}

export function translated<T extends object>(value: T): T {
  return new Proxy(value, {
    get(target, key, receiver) {
      const item: unknown = Reflect.get(target, key, receiver);
      if (typeof key === "symbol") {
        return item;
      }
      if (typeof item === "string") {
        return t(item);
      }
      if (
        item &&
        typeof item === "object" &&
        (Array.isArray(item) || Object.getPrototypeOf(item) === Object.prototype)
      ) {
        return translated(item);
      }
      return item;
    },
  });
}
