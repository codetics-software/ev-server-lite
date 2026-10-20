"use client";

import { useApi, useRevision } from "@/hooks/use-api";
import type { RecordItem, Row } from "@/lib/api-types";

export function useRecord<T extends Row = Row>(kind: string, id: string) {
  const [revision, refresh] = useRevision();
  const result = useApi<RecordItem<T>>(`records/${kind}/${id}`, revision);
  return { ...result, refresh };
}
