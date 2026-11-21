import { afterEach, describe, expect, it } from "vitest";
import {
  getIntlLocale,
  getLocale,
  setActiveLocale,
  t,
  tp,
  translate,
  translated,
} from "@/lib/i18n";
import { isLocale, matchLocale, resolveLocale } from "@/lib/i18n/locales";
import { catalog } from "@/lib/i18n/catalog";

describe("i18n", () => {
  afterEach(() => setActiveLocale("fr"));

  it("uses French sources as the default language", () => {
    expect(getLocale()).toBe("fr");
    expect(getIntlLocale()).toBe("fr-FR");
    expect(t("Oui")).toBe("Oui");
  });

  it("translates and interpolates", () => {
    expect(translate("Oui", "en")).toBe("Yes");
    expect(translate("Oui", "es")).toBe("Sí");
    expect(translate("Texte inconnu {x}", "en", { x: 3 })).toBe("Texte inconnu 3");
    expect(translate("Texte {absent}", "fr", { x: 1 })).toBe("Texte {absent}");
  });

  it("chooses plural forms", () => {
    setActiveLocale("en");
    expect(tp(1, "{count} élément", "{count} éléments")).toContain("1");
    expect(tp(2, "{count} élément", "{count} éléments")).toContain("2");
  });

  it("translates nested objects lazily", () => {
    const labels = translated({ yes: "Oui", nested: { no: "Non" }, count: 2 });
    setActiveLocale("en");
    expect(labels.yes).toBe("Yes");
    expect(labels.nested.no).toBe("No");
    expect(labels.count).toBe(2);
  });

  it("negotiates the locale", () => {
    expect(isLocale("en")).toBe(true);
    expect(isLocale("de")).toBe(false);
    expect(matchLocale("de-DE,es;q=0.8,en;q=0.9")).toBe("en");
    expect(matchLocale("de")).toBeNull();
    expect(matchLocale(null)).toBeNull();
    expect(matchLocale("en;q=0,es")).toBe("es");
    expect(resolveLocale("es", "en")).toBe("es");
    expect(resolveLocale("xx", "en-GB")).toBe("en");
    expect(resolveLocale(null, null)).toBe("fr");
  });

  it("has an English and Spanish translation for every catalog entry", () => {
    const missing = Object.entries(catalog).filter(([, value]) => !value.en || !value.es);
    expect(missing.map(([key]) => key)).toEqual([]);
  });
});
