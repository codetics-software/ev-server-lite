"use client";

import { useEffect, useSyncExternalStore } from "react";

function subscribe(changed: () => void) {
  window.addEventListener("storage", changed);
  window.addEventListener("hashchange", changed);
  return () => {
    window.removeEventListener("storage", changed);
    window.removeEventListener("hashchange", changed);
  };
}

export function usePreviousSession(evseId: string) {
  return useSyncExternalStore(
    subscribe,
    () => sessionStorage.getItem(`recharge-en-cours:${evseId}`),
    () => null,
  );
}

export function usePortalAccess(id: string) {
  const token = useSyncExternalStore(
    subscribe,
    () => {
      const fragment = new URLSearchParams(window.location.hash.slice(1)).get("cle");
      return fragment && /^[a-f0-9]{64}$/.test(fragment)
        ? fragment
        : sessionStorage.getItem(`recharge:${id}`);
    },
    () => null,
  );
  const ready = useSyncExternalStore(
    subscribe,
    () => true,
    () => false,
  );
  useEffect(() => {
    if (token && window.location.hash) {
      sessionStorage.setItem(`recharge:${id}`, token);
      window.history.replaceState(null, "", window.location.pathname);
    }
  }, [id, token]);
  return { token, ready };
}
