"use client";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import { Skeleton } from "@/components/ui/skeleton";
import { sections } from "@/features/workspace/navigation";
import type { Row, User } from "@/lib/api-types";
import { IconPlus } from "@tabler/icons-react";
import { useState } from "react";
import { ResourceDetails } from "./components/resource-details";
import { ResourceEditor } from "./components/resource-editor";
import { ResourcePagination } from "./components/resource-pagination";
import { ResourceTable } from "./components/resource-table";
import { ResourceToolbar } from "./components/resource-toolbar";
import { descriptions } from "./config/descriptions";
import { fields } from "./config/form-fields";
import { resourceName } from "./config/resource-names";
import { columns } from "./config/table-columns";
import { exportCsv } from "./export-csv";
import { useResourceList } from "./hooks/use-resource-list";
import { resourceAccess } from "./resource-access";
import type { ResourceColumn } from "./types";

export function ResourcePage({ section, user }: { section: string; user: User }) {
  const list = useResourceList(section);
  const access = resourceAccess(section, user);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<Row | null>(null);
  const [detail, setDetail] = useState<Row | null>(null);
  const title = sections.find((item) => item.id === section)?.label ?? section;
  const name = resourceName(section);
  const tableColumns: readonly ResourceColumn[] = columns[section] ?? [
    ["name", "Nom"],
    ...(fields[section] ?? [])
      .filter((field) => field.key !== "name")
      .slice(0, 2)
      .map((field): ResourceColumn => [`data.${field.key}`, field.label]),
    ["updated_at", "Mise à jour"],
  ];

  function edit(record: Row | null) {
    setEditing(record);
    setEditorOpen(true);
  }

  return (
    <>
      <div className="page-heading">
        <div>
          <h1>{title}</h1>
          <p>{descriptions[section]}</p>
        </div>
        {access.canCreate && fields[section] && (
          <Button onClick={() => edit(null)}>
            <IconPlus data-icon="inline-start" />
            Ajouter {name}
          </Button>
        )}
      </div>
      {list.error && (
        <Alert variant="destructive">
          <AlertTitle>Impossible de charger {title.toLowerCase()}</AlertTitle>
          <AlertDescription>{list.error.message}</AlertDescription>
        </Alert>
      )}
      <section className="flex min-w-0 flex-col gap-5" aria-label={title}>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-sm text-muted-foreground tabular-nums">
            {list.total.toLocaleString("fr-FR")} {title.toLowerCase()}
          </p>
          <ResourceToolbar
            search={list.search}
            onSearch={list.setSearch}
            onRefresh={list.refresh}
            onExport={() => exportCsv(section, tableColumns, list.filtered)}
            loading={list.loading}
            exportDisabled={!list.filtered.length}
          />
        </div>
        {list.loading && !list.data ? (
          <Skeleton className="h-64 w-full" />
        ) : !list.filtered.length ? (
          <Empty className="min-h-48 border">
            <EmptyHeader>
              <EmptyTitle>
                {list.search
                  ? `Aucun résultat pour « ${list.search} »`
                  : "Aucun élément enregistré"}
              </EmptyTitle>
              <EmptyDescription>
                {list.search
                  ? "Essayez une autre recherche dans cette page."
                  : access.canCreate && fields[section]
                    ? `Utilisez le bouton « Ajouter ${name} » pour commencer.`
                    : "Aucun élément à afficher."}
              </EmptyDescription>
            </EmptyHeader>
          </Empty>
        ) : (
          <ResourceTable
            rows={list.filtered}
            columns={tableColumns}
            canEdit={access.canEdit}
            onView={setDetail}
            onEdit={edit}
          />
        )}
        <ResourcePagination
          offset={list.offset}
          count={list.rows.length}
          total={list.total}
          loading={list.loading}
          paginated={list.paginated}
          onChange={list.setOffset}
        />
      </section>
      <ResourceEditor
        open={editorOpen}
        record={editing}
        section={section}
        onClose={() => setEditorOpen(false)}
        onSaved={list.refresh}
      />
      <ResourceDetails
        section={section}
        title={title}
        record={detail}
        user={user}
        canDelete={access.canDelete}
        onClose={() => setDetail(null)}
        onDeleted={list.refresh}
      />
    </>
  );
}
