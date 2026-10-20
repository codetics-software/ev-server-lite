"use client";

import { DataTable, type Column } from "@/features/shared/data-table";
import { EmptyState, ListState } from "@/features/shared/states";
import { ErrorBadges } from "@/features/shared/status-badge";
import { useApi } from "@/hooks/use-api";
import { IconCircleCheck } from "@tabler/icons-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { StationConnection } from "./stations-list";
import type { StationInError } from "./station-types";

export function StationsInError({ revision }: { revision: number }) {
  const router = useRouter();
  const { data, loading, error } = useApi<StationInError[]>("stations/in-error", revision);
  const items = data ?? [];
  const columns: Column<StationInError>[] = [
    {
      id: "name",
      header: "Borne",
      sort: (item) => item.station.name,
      cell: ({ station }) => (
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
      cell: ({ station }) => <StationConnection station={station} />,
    },
    {
      id: "errors",
      header: "Anomalies",
      sort: (item) => item.errors.length,
      cell: (item) => <ErrorBadges errors={item.errors} />,
    },
  ];
  return (
    <ListState
      data={data}
      items={items}
      loading={loading}
      error={error}
      empty={
        <EmptyState
          icon={<IconCircleCheck />}
          title="Aucune borne en erreur"
          description="Toutes les bornes sont connectées, rattachées à une zone et sans défaut signalé."
        />
      }
    >
      <DataTable
        label="Bornes en erreur"
        rows={items}
        columns={columns}
        rowKey={(item) => item.station.id}
        onRowClick={(item) => router.push(`/stations/${encodeURIComponent(item.station.id)}`)}
      />
    </ListState>
  );
}
