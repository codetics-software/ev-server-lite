"use client";

import { useApi } from "@/hooks/use-api";
import { query } from "@/lib/api";
import { useState } from "react";

export const PAGE_SIZE = 50;

type Parameters = Record<string, string | number | boolean | null | undefined>;

export function usePaged<T>(
  path: string | null,
  parameters: Parameters = {},
  { pageSize = PAGE_SIZE, revision: external = 0 }: { pageSize?: number; revision?: number } = {},
) {
  const key = `${path}${query(parameters)}`;
  const [state, setState] = useState({ key, offset: 0 });
  const [revision, setRevision] = useState(0);
  const offset = state.key === key ? state.offset : 0;
  const result = useApi<{ items: T[]; total: number } | T[]>(
    path ? `${path}${query({ ...parameters, offset, limit: pageSize })}` : null,
    `${revision}:${external}`,
  );
  const items = Array.isArray(result.data) ? result.data : (result.data?.items ?? []);
  const total = Array.isArray(result.data) ? result.data.length : (result.data?.total ?? 0);

  return {
    ...result,
    items,
    total,
    offset,
    pageSize,
    setOffset: (next: number) => setState({ key, offset: next }),
    refresh: () => setRevision((value) => value + 1),
  };
}
