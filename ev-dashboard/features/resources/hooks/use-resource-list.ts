"use client";

import { useApi } from "@/hooks/use-api";
import type { PageData, Row } from "@/lib/api-types";
import { useState } from "react";
import { endpoint } from "../config/endpoint";

export const PAGE_SIZE = 50;

export function useResourceList(section: string) {
  const [offset, setOffset] = useState(0);
  const [revision, setRevision] = useState(0);
  const [search, setSearch] = useState("");
  const query = useApi<PageData | Row[]>(
    `${endpoint(section)}?offset=${offset}&limit=${PAGE_SIZE}`,
    revision,
  );
  const rows = Array.isArray(query.data) ? query.data : (query.data?.items ?? []);

  return {
    ...query,
    offset,
    setOffset,
    search,
    setSearch,
    rows,
    filtered: rows.filter((row) =>
      JSON.stringify(row).toLowerCase().includes(search.toLowerCase()),
    ),
    total: Array.isArray(query.data) ? query.data.length : (query.data?.total ?? 0),
    paginated: !Array.isArray(query.data),
    refresh: () => setRevision((value) => value + 1),
  };
}
