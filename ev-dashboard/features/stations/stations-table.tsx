"use client";

import { Button } from "@/components/ui/button";
import { DataTable, type Column } from "@/features/shared/data-table";
import { Pager } from "@/features/shared/pager";
import { EmptyState, ListState } from "@/features/shared/states";
import { usePaged } from "@/features/shared/use-paged";
import { kw } from "@/lib/format";
import { IconChargingPile } from "@tabler/icons-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ConnectorSummary } from "./connector-summary";
import type { Station } from "./station-types";
import { StationConnection } from "./stations-list";

export function StationsTable({
  filters,
  revision = 0,
  onRemove,
  emptyDescription = "Aucune borne n’est rattachée ici.",
}: {
  filters: Record<string, string | undefined>;
  revision?: number;
  onRemove?: (station: Station) => void;
  emptyDescription?: string;
}) {
  const router = useRouter();
  const list = usePaged<Station>("stations", filters, { revision });
  const columns: Column<Station>[] = [
    {
      id: "name",
      header: "Borne",
      sort: (station) => station.name,
      cell: (station) => (
        <Link
          href={`/stations/${encodeURIComponent(station.id)}`}
          className="flex flex-col hover:text-primary dark:hover:text-sidebar-primary"
          onClick={(event) => event.stopPropagation()}
        >
          <span className="font-medium">{station.name}</span>
          <span className="text-xs text-muted-foreground">{station.id}</span>
        </Link>
      ),
    },
    {
      id: "state",
      header: "Connexion",
      cell: (station) => <StationConnection station={station} />,
    },
    {
      id: "connectors",
      header: "Connecteurs",
      cell: (station) => <ConnectorSummary connectors={station.connectors ?? []} />,
    },
    {
      id: "power",
      header: "Puissance",
      align: "right",
      sort: (station) => station.maximum_power_kw ?? null,
      cell: (station) => kw(station.maximum_power_kw),
    },
    ...(onRemove
      ? [
          {
            id: "remove",
            header: "Actions",
            srOnlyHeader: true,
            align: "right" as const,
            cell: (station: Station) => (
              <Button
                variant="ghost"
                size="sm"
                onClick={(event) => {
                  event.stopPropagation();
                  onRemove(station);
                }}
              >
                Retirer
              </Button>
            ),
          },
        ]
      : []),
  ];
  return (
    <div className="flex flex-col gap-4">
      <ListState
        data={list.data}
        items={list.items}
        loading={list.loading}
        error={list.error}
        onRetry={list.refresh}
        empty={
          <EmptyState
            icon={<IconChargingPile />}
            title="Aucune borne"
            description={emptyDescription}
          />
        }
      >
        <DataTable
          label="Bornes"
          rows={list.items}
          columns={columns}
          rowKey={(station) => station.id}
          onRowClick={(station) => router.push(`/stations/${encodeURIComponent(station.id)}`)}
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
    </div>
  );
}
