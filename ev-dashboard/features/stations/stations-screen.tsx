"use client";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import { Skeleton } from "@/components/ui/skeleton";
import { ResourceEditor } from "@/features/resources/components/resource-editor";
import { ResourcePagination } from "@/features/resources/components/resource-pagination";
import { ResourceToolbar } from "@/features/resources/components/resource-toolbar";
import { exportCsv } from "@/features/resources/export-csv";
import { useWorkspace } from "@/features/workspace/workspace-context";
import { useApi } from "@/hooks/use-api";
import { IconPlus } from "@tabler/icons-react";
import { useState } from "react";
import { StationDetail } from "./station-detail";
import type { StationPage, StationSummary } from "./station-types";
import { StationsTable } from "./stations-table";

export function StationsScreen() {
  const user = useWorkspace();
  const [offset, setOffset] = useState(0);
  const [revision, setRevision] = useState(0);
  const [search, setSearch] = useState("");
  const [provisioning, setProvisioning] = useState(false);
  const [selected, setSelected] = useState<StationSummary | null>(null);
  const { data, error, loading } = useApi<StationPage>(
    `stations?offset=${offset}&limit=50`,
    revision,
  );
  const stations = (data?.items ?? []).filter((station) =>
    [station.name, station.id, station.vendor, station.model]
      .join(" ")
      .toLowerCase()
      .includes(search.toLowerCase()),
  );
  const refresh = () => setRevision((value) => value + 1);

  return (
    <>
      <div className="page-heading">
        <div>
          <h1>Bornes de recharge</h1>
          <p>Connexion des bornes, état des connecteurs et commandes à distance.</p>
        </div>
        {user.role === "ADMIN" && (
          <Button onClick={() => setProvisioning(true)}>
            <IconPlus data-icon="inline-start" />
            Ajouter une borne
          </Button>
        )}
      </div>
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error.message}</AlertDescription>
        </Alert>
      )}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground tabular-nums">{data?.total ?? 0} stations</p>
        <ResourceToolbar
          search={search}
          onSearch={setSearch}
          onRefresh={refresh}
          loading={loading}
          exportDisabled={!stations.length}
          onExport={() =>
            exportCsv(
              "stations",
              [
                ["name", "Borne"],
                ["id", "Identifiant"],
                ["connected", "Connectée"],
                ["last_seen", "Dernier contact"],
              ],
              stations,
            )
          }
        />
      </div>
      {loading && !data ? (
        <Skeleton className="h-64 w-full" />
      ) : !stations.length ? (
        <Empty className="min-h-48 border">
          <EmptyHeader>
            <EmptyTitle>
              {search ? "Aucune borne correspondante" : "Aucune borne enregistrée"}
            </EmptyTitle>
            <EmptyDescription>
              {search
                ? "Essayez le nom ou l’identifiant d’une borne."
                : "Ajoutez une borne pour obtenir ses identifiants de connexion."}
            </EmptyDescription>
          </EmptyHeader>
        </Empty>
      ) : (
        <StationsTable stations={stations} onSelect={setSelected} />
      )}
      <ResourcePagination
        offset={offset}
        count={data?.items.length ?? 0}
        total={data?.total ?? 0}
        loading={loading}
        paginated
        onChange={setOffset}
      />
      <ResourceEditor
        section="stations"
        open={provisioning}
        record={null}
        onClose={() => setProvisioning(false)}
        onSaved={refresh}
      />
      <Dialog open={selected !== null} onOpenChange={(open) => !open && setSelected(null)}>
        <DialogContent className="max-h-[85svh] overflow-auto sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>{selected?.name}</DialogTitle>
            <DialogDescription>{selected?.id} · Gestion de la borne</DialogDescription>
          </DialogHeader>
          {selected && <StationDetail station={selected.id} user={user} />}
        </DialogContent>
      </Dialog>
    </>
  );
}
