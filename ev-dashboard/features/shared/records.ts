import { api } from "@/lib/api";
import type { RecordItem, Row } from "@/lib/api-types";

export function compact<T extends Row>(data: T): T {
  return Object.fromEntries(
    Object.entries(data).filter(
      ([, value]) => value !== undefined && value !== "" && value !== null,
    ),
  ) as T;
}

export function saveRecord<T extends Row>(
  kind: string,
  record: RecordItem | null,
  name: string,
  data: T,
) {
  return api<{ id: string; revision: number }>(
    record ? `records/${kind}/${record.id}` : `records/${kind}`,
    {
      method: record ? "PUT" : "POST",
      body: JSON.stringify({ name: name.trim(), data: compact(data), revision: record?.revision }),
    },
  );
}

export function deleteRecord(kind: string, id: string) {
  return api(`records/${kind}/${id}`, { method: "DELETE" });
}

export function field<T = unknown>(
  record: RecordItem | null | undefined,
  key: string,
): T | undefined {
  return (record?.data as Row | undefined)?.[key] as T | undefined;
}
