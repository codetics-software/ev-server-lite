"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";

export function useApi<T>(path: string, revision = 0) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<Error | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;

    async function load() {
      setLoading(true);
      setError(null);

      try {
        const result = await api<T>(path, { signal: controller.signal });

        if (active) {
          setData(result);
        }
      } catch (failure) {
        if (active) {
          setError(
            failure instanceof Error ? failure : new Error("Impossible de charger les données."),
          );
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    load();

    return () => {
      active = false;
      controller.abort();
    };
  }, [path, revision]);

  return { data, error, loading };
}
