"use client";

import { DataTable, type Column } from "@/features/shared/data-table";
import { Pager } from "@/features/shared/pager";
import { SearchInput } from "@/features/shared/search-input";
import { EmptyState, ErrorState, ListState } from "@/features/shared/states";
import { usePaged } from "@/features/shared/use-paged";
import type { RecordItem, Row } from "@/lib/api-types";
import { useState, type ReactNode } from "react";

export function RecordList<T extends Row>({
  kind,
  columns,
  filters = {},
  onSelect,
  emptyTitle,
  emptyDescription,
  emptyIcon,
  toolbar,
  revision = 0,
  label,
  filterRows,
}: {
  kind: string;
  columns: Column<RecordItem<T>>[];
  filters?: Record<string, string | undefined>;
  onSelect?: (record: RecordItem<T>) => void;
  emptyTitle: string;
  emptyDescription: string;
  emptyIcon?: ReactNode;
  toolbar?: ReactNode;
  revision?: number;
  label: string;
  filterRows?: (record: RecordItem<T>) => boolean;
}) {
  const [search, setSearch] = useState("");
  const list = usePaged<RecordItem<T>>(`records/${kind}`, { search, ...filters }, { revision });
  const rows = filterRows ? list.items.filter(filterRows) : list.items;
  return (
    <section className="flex min-w-0 flex-col gap-4" aria-label={label}>
      <div className="flex flex-wrap items-end gap-3">
        <SearchInput value={search} onChange={setSearch} label={`Rechercher · ${label}`} />
        {toolbar}
      </div>
      {list.error && list.data && <ErrorState error={list.error} onRetry={list.refresh} />}
      <ListState
        data={list.data}
        items={rows}
        loading={list.loading}
        error={list.error}
        onRetry={list.refresh}
        empty={
          <EmptyState
            icon={emptyIcon}
            title={search ? `Aucun résultat pour « ${search} »` : emptyTitle}
            description={search ? "Essayez une autre recherche." : emptyDescription}
          />
        }
      >
        <DataTable
          label={label}
          rows={rows}
          columns={columns}
          rowKey={(record) => record.id}
          onRowClick={onSelect}
        />
      </ListState>
      <Pager
        offset={list.offset}
        count={list.items.length}
        total={list.total}
        pageSize={list.pageSize}
        loading={list.loading}
        onChange={list.setOffset}
      />
    </section>
  );
}
