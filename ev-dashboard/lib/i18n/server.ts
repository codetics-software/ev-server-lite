import { cookies, headers } from "next/headers";
import { translate } from ".";
import { LOCALE_COOKIE, resolveLocale } from "./locales";

export async function getRequestLocale() {
  const [cookieStore, headerList] = await Promise.all([cookies(), headers()]);
  return resolveLocale(cookieStore.get(LOCALE_COOKIE)?.value, headerList.get("accept-language"));
}

export async function getServerTranslator() {
  const locale = await getRequestLocale();
  return (source: string, params?: Record<string, string | number>) =>
    translate(source, locale, params);
}
