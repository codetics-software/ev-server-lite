import type { Row } from "@/lib/api-types";
import { formatCell } from "./resource-format";
import type { ResourceColumn } from "./types";

function escapeCell(value: string): string {
  const safe = /^[=+\-@\t\r]/.test(value) ? `'${value}` : value;
  return `"${safe.replaceAll('"', '""')}"`;
}

export function exportCsv(filename: string, columns: readonly ResourceColumn[], rows: Row[]) {
  const lines = [
    columns.map(([, label]) => escapeCell(label)).join(","),
    ...rows.map((row) => columns.map(([key]) => escapeCell(formatCell(row, key))).join(",")),
  ];
  const url = URL.createObjectURL(
    new Blob([lines.join("\r\n")], { type: "text/csv;charset=utf-8" }),
  );
  const link = document.createElement("a");
  link.href = url;
  link.download = `${filename}.csv`;
  link.click();
  URL.revokeObjectURL(url);
}
