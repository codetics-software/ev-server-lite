"use client";

import { DataTable } from "@/features/shared/data-table";
import { EmptyState, ListState } from "@/features/shared/states";
import { ErrorBadges } from "@/features/shared/status-badge";
import { Pager } from "@/features/shared/pager";
import { usePaged } from "@/features/shared/use-paged";
import { IconCircleCheck } from "@tabler/icons-react";
import { useRouter } from "next/navigation";
import { sessionColumns } from "./session-columns";
import type { ChargingSession, SessionInError } from "./session-types";

export function SessionsInError({ revision }: { revision: number }) {
  const router = useRouter();
  const list = usePaged<SessionInError>("transactions/in-error", {}, { revision });
  const { data, loading, error } = list;
  const rows: ChargingSession[] = list.items.map((item) => ({
    ...item.session,
    errors: item.errors,
  }));
  const columns = [
    ...sessionColumns({ showUser: true, active: false }).filter((column) =>
      ["id", "station", "user", "start", "energy", "price"].includes(column.id),
    ),
    {
      id: "errors",
      header: "Anomalies",
      cell: (session: ChargingSession) => <ErrorBadges errors={session.errors ?? []} />,
    },
  ];
  return (
    <div className="flex flex-col gap-4">
      <ListState
        data={data}
        items={rows}
        loading={loading}
        error={error}
        empty={
          <EmptyState
            icon={<IconCircleCheck />}
            title="Aucune session en erreur"
            description="Les sessions sans consommation, sans prix ou avec un badge invalide apparaîtront ici."
          />
        }
      >
        <DataTable
          label="Sessions en erreur"
          rows={rows}
          columns={columns}
          rowKey={(session) => String(session.id)}
          onRowClick={(session) => router.push(`/transactions/${session.id}`)}
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
