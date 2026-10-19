"use client";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import { Skeleton } from "@/components/ui/skeleton";
import { ResourcePagination } from "@/features/resources/components/resource-pagination";
import { ResourceToolbar } from "@/features/resources/components/resource-toolbar";
import { exportCsv } from "@/features/resources/export-csv";
import { useWorkspace } from "@/features/workspace/workspace-context";
import { useApi } from "@/hooks/use-api";
import { useState } from "react";
import type { SessionPage } from "./session-types";
import { SessionsTable } from "./sessions-table";

export function SessionsScreen() {
  const user = useWorkspace();
  const [offset, setOffset] = useState(0);
  const [revision, setRevision] = useState(0);
  const [search, setSearch] = useState("");
  const { data, error, loading } = useApi<SessionPage>(
    `transactions?offset=${offset}&limit=50`,
    revision,
  );
  const sessions = (data?.items ?? []).filter((session) =>
    `${session.id} ${session.station_id}`.toLowerCase().includes(search.toLowerCase()),
  );

  return (
    <>
      <div className="page-heading">
        <div>
          <h1>Charging sessions</h1>
          <p>
            {user.role === "DRIVER"
              ? "Your session history and measured energy."
              : "Session history, authorization and measured energy across your network."}
          </p>
        </div>
      </div>
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error.message}</AlertDescription>
        </Alert>
      )}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground tabular-nums">{data?.total ?? 0} sessions</p>
        <ResourceToolbar
          search={search}
          onSearch={setSearch}
          loading={loading}
          onRefresh={() => setRevision((value) => value + 1)}
          exportDisabled={!sessions.length}
          onExport={() =>
            exportCsv(
              "sessions",
              [
                ["id", "Session"],
                ["station_id", "Station"],
                ["connector_id", "Connector"],
                ["started_at", "Started"],
                ["stopped_at", "Stopped"],
                ["energy_kwh", "Energy · kWh"],
              ],
              sessions,
            )
          }
        />
      </div>
      {loading && !data ? (
        <Skeleton className="h-64 w-full" />
      ) : !sessions.length ? (
        <Empty className="min-h-48 border">
          <EmptyHeader>
            <EmptyTitle>{search ? "No matching sessions" : "No charging sessions yet"}</EmptyTitle>
            <EmptyDescription>
              {search
                ? "Try a station identity or session number."
                : "Sessions appear when a connected station reports a transaction."}
            </EmptyDescription>
          </EmptyHeader>
        </Empty>
      ) : (
        <SessionsTable sessions={sessions} />
      )}
      <ResourcePagination
        offset={offset}
        count={data?.items.length ?? 0}
        total={data?.total ?? 0}
        loading={loading}
        paginated
        onChange={setOffset}
      />
    </>
  );
}
