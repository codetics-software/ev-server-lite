"use client";

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { cn } from "@/lib/utils";
import { IconArrowDown, IconArrowUp, IconSelector } from "@tabler/icons-react";
import {
  createSortedRowModel,
  rowSortingFeature,
  tableFeatures,
  useTable,
  type ColumnDef,
  type RowData,
  type SortingState,
} from "@tanstack/react-table";
import { useMemo, useState, type KeyboardEvent, type ReactNode } from "react";

export type Column<T extends RowData> = {
  id: string;
  header: string;
  cell: (row: T) => ReactNode;
  sort?: (row: T) => string | number | boolean | null | undefined;
  align?: "right";
  className?: string;
  srOnlyHeader?: boolean;
};

const features = tableFeatures({ rowSortingFeature, sortedRowModel: createSortedRowModel() });

function compare(left: unknown, right: unknown) {
  if (left === right) {
    return 0;
  }
  if (left === null || left === undefined || left === "") {
    return 1;
  }
  if (right === null || right === undefined || right === "") {
    return -1;
  }
  if (typeof left === "number" && typeof right === "number") {
    return left - right;
  }
  return String(left).localeCompare(String(right), "fr", { numeric: true, sensitivity: "base" });
}

export function DataTable<T extends RowData>({
  rows,
  columns,
  rowKey,
  label,
  onRowClick,
  rowClassName,
}: {
  rows: T[];
  columns: Column<T>[];
  rowKey: (row: T, index: number) => string;
  label: string;
  onRowClick?: (row: T) => void;
  rowClassName?: (row: T) => string | undefined;
}) {
  const [sorting, setSorting] = useState<SortingState>([]);
  const definitions = useMemo(
    () =>
      columns.map((column): ColumnDef<typeof features, T, unknown> => ({
        id: column.id,
        accessorFn: (row: T) => (column.sort ? column.sort(row) : null),
        enableSorting: Boolean(column.sort),
        sortFn: (left, right, id) => compare(left.getValue(id), right.getValue(id)),
        header: column.header,
      })),
    [columns],
  );
  const table = useTable({
    features,
    columns: definitions,
    data: rows,
    state: { sorting },
    onSortingChange: setSorting,
    getRowId: (row, index) => rowKey(row, index),
  });
  const byId = new Map(columns.map((column) => [column.id, column]));

  function activate(event: KeyboardEvent<HTMLTableRowElement>, row: T) {
    if (event.target !== event.currentTarget) {
      return;
    }
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      onRowClick?.(row);
    }
  }

  return (
    <Table aria-label={label}>
      <TableHeader>
        {table.getHeaderGroups().map((group) => (
          <TableRow key={group.id}>
            {group.headers.map((header) => {
              const column = byId.get(header.column.id);
              const sorted = header.column.getIsSorted();
              return (
                <TableHead
                  key={header.id}
                  aria-sort={
                    sorted === "asc" ? "ascending" : sorted === "desc" ? "descending" : undefined
                  }
                  className={cn(column?.align === "right" && "text-right", column?.className)}
                >
                  {column?.srOnlyHeader ? (
                    <span className="sr-only">{column.header}</span>
                  ) : header.column.getCanSort() ? (
                    <button
                      type="button"
                      onClick={header.column.getToggleSortingHandler()}
                      className={cn(
                        "-mx-1 inline-flex items-center gap-1 rounded-md px-1 py-0.5 hover:text-foreground focus-visible:outline-2 focus-visible:outline-ring",
                        column?.align === "right" && "flex-row-reverse",
                      )}
                    >
                      {column?.header}
                      {sorted === "asc" ? (
                        <IconArrowUp className="size-3.5" aria-hidden="true" />
                      ) : sorted === "desc" ? (
                        <IconArrowDown className="size-3.5" aria-hidden="true" />
                      ) : (
                        <IconSelector className="size-3.5 opacity-50" aria-hidden="true" />
                      )}
                    </button>
                  ) : (
                    column?.header
                  )}
                </TableHead>
              );
            })}
          </TableRow>
        ))}
      </TableHeader>
      <TableBody>
        {table.getRowModel().rows.map((row) => (
          <TableRow
            key={row.id}
            tabIndex={onRowClick ? 0 : undefined}
            onClick={onRowClick ? () => onRowClick(row.original) : undefined}
            onKeyDown={onRowClick ? (event) => activate(event, row.original) : undefined}
            className={cn(
              onRowClick &&
                "cursor-pointer focus-visible:bg-muted/60 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-ring",
              rowClassName?.(row.original),
            )}
          >
            {columns.map((column) => (
              <TableCell
                key={column.id}
                className={cn(
                  column.align === "right" && "text-right tabular-nums",
                  column.className,
                )}
              >
                {column.cell(row.original)}
              </TableCell>
            ))}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
