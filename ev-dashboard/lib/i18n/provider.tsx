"use client";

import { createContext, useContext, type ReactNode } from "react";
import { setActiveLocale } from ".";
import { LOCALE_COOKIE, type Locale } from "./locales";

const LocaleContext = createContext<Locale>("fr");

export function I18nProvider({ locale, children }: { locale: Locale; children: ReactNode }) {
  setActiveLocale(locale);
  return <LocaleContext.Provider value={locale}>{children}</LocaleContext.Provider>;
}

export function useLocale() {
  return useContext(LocaleContext);
}

export function changeLocale(next: Locale) {
  document.cookie = `${LOCALE_COOKIE}=${next}; path=/; max-age=31536000; samesite=lax`;
  window.location.reload();
}
