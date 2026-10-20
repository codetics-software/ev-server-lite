"use client";

import { useSyncExternalStore } from "react";

export type ThemePreference = "light" | "dark" | "system";

const STORAGE_KEY = "codetics-theme";
const EVENT = "codetics-theme-change";
const DARK_QUERY = "(prefers-color-scheme: dark)";

function readPreference(): ThemePreference {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored === "light" || stored === "dark" ? stored : "system";
  } catch {
    return "system";
  }
}

function apply(preference: ThemePreference) {
  const dark = preference === "dark" || (preference === "system" && matchMedia(DARK_QUERY).matches);
  document.documentElement.classList.toggle("dark", dark);
}

function subscribe(onChange: () => void) {
  const media = matchMedia(DARK_QUERY);
  const update = () => {
    apply(readPreference());
    onChange();
  };
  media.addEventListener("change", update);
  window.addEventListener(EVENT, update);
  window.addEventListener("storage", update);
  return () => {
    media.removeEventListener("change", update);
    window.removeEventListener(EVENT, update);
    window.removeEventListener("storage", update);
  };
}

export function useTheme() {
  const preference = useSyncExternalStore(subscribe, readPreference, () => "system" as const);
  const dark = useSyncExternalStore(
    subscribe,
    () => document.documentElement.classList.contains("dark"),
    () => false,
  );

  function setPreference(next: ThemePreference) {
    try {
      if (next === "system") {
        localStorage.removeItem(STORAGE_KEY);
      } else {
        localStorage.setItem(STORAGE_KEY, next);
      }
    } catch (error) {
      void error;
    }
    apply(next);
    window.dispatchEvent(new Event(EVENT));
  }

  return { preference, dark, setPreference };
}

export const themeScript = `(function(){try{var t=localStorage.getItem("${STORAGE_KEY}");var d=t==="dark"||(t!=="light"&&matchMedia("${DARK_QUERY}").matches);document.documentElement.classList.toggle("dark",d)}catch(e){}})();`;
