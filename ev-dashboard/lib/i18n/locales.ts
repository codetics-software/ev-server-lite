export const locales = ["fr", "en", "es"] as const;

export type Locale = (typeof locales)[number];

export const defaultLocale: Locale = "fr";

export const LOCALE_COOKIE = "codetics-locale";

export const localeNames: Record<Locale, string> = {
  fr: "Français",
  en: "English",
  es: "Español",
};

const intlLocales: Record<Locale, string> = {
  fr: "fr-FR",
  en: "en-GB",
  es: "es-ES",
};

export function isLocale(value: unknown): value is Locale {
  return typeof value === "string" && (locales as readonly string[]).includes(value);
}

export function intlLocale(locale: Locale): string {
  return intlLocales[locale];
}

export function matchLocale(header: string | null | undefined): Locale | null {
  if (!header) {
    return null;
  }
  const ranked = header
    .split(",")
    .map((part, index) => {
      const [tag, ...params] = part.trim().split(";");
      const quality = params.map((param) => param.trim()).find((param) => param.startsWith("q="));
      return { tag: tag.toLowerCase(), q: quality ? Number(quality.slice(2)) || 0 : 1, index };
    })
    .filter((entry) => entry.tag && entry.q > 0)
    .sort((a, b) => b.q - a.q || a.index - b.index);
  for (const entry of ranked) {
    const base = entry.tag.split("-")[0];
    if (isLocale(base)) {
      return base;
    }
  }
  return null;
}

export function resolveLocale(
  cookie: string | null | undefined,
  header: string | null | undefined,
) {
  return isLocale(cookie) ? cookie : (matchLocale(header) ?? defaultLocale);
}
