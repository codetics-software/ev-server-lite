"use client";

import { Button } from "@/components/ui/button";
import { DataTable, type Column } from "@/features/shared/data-table";
import { EmptyState, ListState } from "@/features/shared/states";
import { StateBadge } from "@/features/shared/status-badge";
import { useApi, useRevision } from "@/hooks/use-api";
import type { Row } from "@/lib/api-types";
import { date, display } from "@/lib/format";
import { IconHistory, IconRefresh } from "@tabler/icons-react";

export function StationCommandsLog({
  stationId,
  revision: external,
}: {
  stationId: string;
  revision: number;
}) {
  const [revision, refresh] = useRevision();
  const { data, loading, error } = useApi<Row[] | { items: Row[] }>(
    `commands?stationId=${encodeURIComponent(stationId)}`,
    `${revision}:${external}`,
  );
  const items = (Array.isArray(data) ? data : (data?.items ?? [])).filter(
    (command) => command.station_id === stationId,
  );
  const columns: Column<Row>[] = [
    {
      id: "action",
      header: "Commande",
      sort: (command) => String(command.action),
      cell: (command) => display(command.action),
    },
    { id: "state", header: "État", cell: (command) => <StateBadge state={command.state} /> },
    {
      id: "created",
      header: "Demandée le",
      sort: (command) => String(command.created_at),
      cell: (command) => date(command.created_at),
    },
    {
      id: "completed",
      header: "Réponse",
      cell: (command) => (
        <span className="flex flex-col">
          <span>{date(command.completed_at)}</span>
          {command.result != null && (
            <code className="max-w-80 truncate text-xs text-muted-foreground">
              {JSON.stringify(command.result)}
            </code>
          )}
        </span>
      ),
    },
  ];
  return (
    <section className="flex flex-col gap-4" aria-label="Commandes de la borne">
      <Button variant="outline" className="w-fit" onClick={refresh}>
        <IconRefresh data-icon="inline-start" />
        Actualiser
      </Button>
      <ListState
        data={data}
        items={items}
        loading={loading}
        error={error}
        onRetry={refresh}
        empty={
          <EmptyState
            icon={<IconHistory />}
            title="Aucune commande"
            description="Les commandes envoyées à cette borne et leurs réponses apparaîtront ici."
          />
        }
      >
        <DataTable
          label="Commandes"
          rows={items}
          columns={columns}
          rowKey={(command) => String(command.id)}
        />
      </ListState>
    </section>
  );
}
