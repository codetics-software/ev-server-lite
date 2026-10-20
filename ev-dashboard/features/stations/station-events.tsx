"use client";

import { DataTable, type Column } from "@/features/shared/data-table";
import { OptionSelect } from "@/features/shared/form-fields";
import { Pager } from "@/features/shared/pager";
import { EmptyState, ListState } from "@/features/shared/states";
import { ToneBadge, type Tone } from "@/features/shared/status-badge";
import { usePaged } from "@/features/shared/use-paged";
import { date, display } from "@/lib/format";
import { IconTimeline } from "@tabler/icons-react";
import { useState } from "react";
import type { StationEvent } from "./station-types";

const types = [
  "BOOT",
  "STATUS",
  "FIRMWARE",
  "DIAGNOSTICS",
  "CONNECTION",
  "DISCONNECTION",
  "HEARTBEAT_LOST",
  "AUTHORIZE",
  "DATA_TRANSFER",
  "REGISTRATION",
];

function eventTone(event: StationEvent): Tone {
  if (event.type === "DISCONNECTION" || event.type === "HEARTBEAT_LOST") {
    return "danger";
  }
  if (event.error_code && event.error_code !== "NoError") {
    return "danger";
  }
  if (event.type === "AUTHORIZE") {
    return event.status === "Accepted" ? "success" : "danger";
  }
  if (event.type === "CONNECTION" || event.type === "BOOT" || event.type === "REGISTRATION") {
    return "success";
  }
  return "neutral";
}

export function StationEvents({ stationId }: { stationId: string }) {
  const [type, setType] = useState("all");
  const list = usePaged<StationEvent>(`stations/${encodeURIComponent(stationId)}/events`, {
    type: type === "all" ? null : type,
  });
  const columns: Column<StationEvent>[] = [
    {
      id: "date",
      header: "Date",
      sort: (event) => event.created_at,
      cell: (event) => <span className="whitespace-nowrap">{date(event.created_at)}</span>,
    },
    {
      id: "type",
      header: "Événement",
      sort: (event) => event.type,
      cell: (event) => <ToneBadge tone={eventTone(event)}>{display(event.type)}</ToneBadge>,
    },
    {
      id: "connector",
      header: "Connecteur",
      align: "right",
      cell: (event) => (event.connector_id ? event.connector_id : "Borne"),
    },
    {
      id: "status",
      header: "État",
      cell: (event) =>
        [event.status, event.error_code && event.error_code !== "NoError" ? event.error_code : null]
          .filter(Boolean)
          .map((value) => display(value))
          .join(" · ") || "—",
    },
    {
      id: "info",
      header: "Détail",
      cell: (event) => (
        <span className="block max-w-80 break-words text-muted-foreground">
          {event.info ?? "—"}
        </span>
      ),
    },
  ];
  return (
    <section className="flex flex-col gap-4" aria-label="Historique des événements">
      <OptionSelect
        value={type}
        onChange={setType}
        label="Type d’événement"
        className="w-full sm:w-60"
        options={[
          { value: "all", label: "Tous les événements" },
          ...types.map((value) => ({ value, label: display(value) })),
        ]}
      />
      <ListState
        data={list.data}
        items={list.items}
        loading={list.loading}
        error={list.error}
        onRetry={list.refresh}
        empty={
          <EmptyState
            icon={<IconTimeline />}
            title="Aucun événement"
            description="Les démarrages, changements d’état et déconnexions de la borne apparaîtront ici."
          />
        }
      >
        <DataTable
          label="Événements"
          rows={list.items}
          columns={columns}
          rowKey={(event) => String(event.id)}
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
