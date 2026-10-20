"use client";

import { Field, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { ExportButton } from "@/features/shared/csv-actions";
import { DataTable, type Column } from "@/features/shared/data-table";
import { PageHeader } from "@/features/shared/page-header";
import { Pager } from "@/features/shared/pager";
import { SearchInput } from "@/features/shared/search-input";
import { EmptyState, ErrorState, ListState } from "@/features/shared/states";
import { usePaged } from "@/features/shared/use-paged";
import { date, display } from "@/lib/format";
import { IconListDetails } from "@tabler/icons-react";
import { useState } from "react";

type LogEntry = {
  id: number | string;
  actor: string;
  action: string;
  target: string;
  created_at: string;
};

const columns: Column<LogEntry>[] = [
  {
    id: "date",
    header: "Date",
    sort: (entry) => entry.created_at,
    cell: (entry) => <span className="whitespace-nowrap">{date(entry.created_at)}</span>,
  },
  {
    id: "action",
    header: "Action",
    sort: (entry) => entry.action,
    cell: (entry) => (
      <span className="flex flex-col">
        <span>{display(entry.action)}</span>
        <code className="text-xs text-muted-foreground">{entry.action}</code>
      </span>
    ),
  },
  {
    id: "actor",
    header: "Auteur",
    sort: (entry) => entry.actor,
    cell: (entry) => <span className="block max-w-56 truncate">{entry.actor}</span>,
  },
  {
    id: "target",
    header: "Cible",
    sort: (entry) => entry.target,
    cell: (entry) => <code className="block max-w-72 truncate text-xs">{entry.target}</code>,
  },
];

function dayBound(value: string, end: boolean) {
  return value ? new Date(`${value}T${end ? "23:59:59.999" : "00:00:00"}`).toISOString() : null;
}

export function LogsScreen() {
  const [search, setSearch] = useState("");
  const [action, setAction] = useState("");
  const [actor, setActor] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const parameters = {
    search,
    action: action.trim(),
    actor: actor.trim(),
    from: dayBound(from, false),
    to: dayBound(to, true),
  };
  const list = usePaged<LogEntry>("logs", parameters);
  return (
    <>
      <PageHeader
        title="Journal d’activité"
        description="Connexions et modifications réalisées dans votre espace."
        actions={<ExportButton path="logs/export" filename="journal" parameters={parameters} />}
      />
      <div className="flex flex-wrap items-end gap-3">
        <SearchInput value={search} onChange={setSearch} label="Rechercher dans le journal" />
        <SearchInput
          value={action}
          onChange={setAction}
          placeholder="Action, ex. RECORD_UPDATE"
          label="Filtrer par action"
        />
        <SearchInput
          value={actor}
          onChange={setActor}
          placeholder="Auteur"
          label="Filtrer par auteur"
        />
        <Field className="w-full sm:w-40">
          <FieldLabel htmlFor="logs-from" className="text-xs text-muted-foreground">
            Du
          </FieldLabel>
          <Input
            id="logs-from"
            type="date"
            value={from}
            max={to || undefined}
            onChange={(event) => setFrom(event.target.value)}
          />
        </Field>
        <Field className="w-full sm:w-40">
          <FieldLabel htmlFor="logs-to" className="text-xs text-muted-foreground">
            Au
          </FieldLabel>
          <Input
            id="logs-to"
            type="date"
            value={to}
            min={from || undefined}
            onChange={(event) => setTo(event.target.value)}
          />
        </Field>
      </div>
      {list.error && list.data && <ErrorState error={list.error} onRetry={list.refresh} />}
      <ListState
        data={list.data}
        items={list.items}
        loading={list.loading}
        error={list.error}
        onRetry={list.refresh}
        empty={
          <EmptyState
            icon={<IconListDetails />}
            title="Aucune entrée"
            description="Modifiez les filtres ou la période."
          />
        }
      >
        <DataTable
          label="Journal"
          rows={list.items}
          columns={columns}
          rowKey={(entry) => String(entry.id)}
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
    </>
  );
}
