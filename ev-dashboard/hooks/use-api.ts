"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";

export function useApi<T>(path: string | null, revision: number | string = 0) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<Error | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!path) {
      return;
    }
    const controller = new AbortController();
    let active = true;

    async function load(target: string) {
      setLoading(true);
      setError(null);

      try {
        const result = await api<T>(target, { signal: controller.signal });

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

    load(path);

    return () => {
      active = false;
      controller.abort();
    };
  }, [path, revision]);

  return { data: path ? data : null, error: path ? error : null, loading: path ? loading : false };
}

export function useRevision() {
  const [revision, setRevision] = useState(0);
  return [revision, () => setRevision((value) => value + 1)] as const;
}
