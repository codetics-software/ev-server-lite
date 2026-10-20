"use client";

import { Button } from "@/components/ui/button";
import { ConfirmButton } from "@/features/shared/confirm-button";
import type { Column } from "@/features/shared/data-table";
import { FormDialog } from "@/features/shared/form-dialog";
import { PageHeader } from "@/features/shared/page-header";
import { RecordList } from "@/features/shared/record-list";
import { deleteRecord, field } from "@/features/shared/records";
import { useAction } from "@/features/shared/use-action";
import { isAdmin, useWorkspace } from "@/features/workspace/workspace-context";
import { useRevision } from "@/hooks/use-api";
import type { RecordItem } from "@/lib/api-types";
import { date, display } from "@/lib/format";
import { IconBuildingFactory2, IconPlus } from "@tabler/icons-react";
import Link from "next/link";
import { useState } from "react";
import { CompanyForm } from "./organization-forms";

export function CompaniesScreen() {
  const user = useWorkspace();
  const admin = isAdmin(user);
  const [revision, refresh] = useRevision();
  const [editing, setEditing] = useState<RecordItem | null | undefined>();
  const action = useAction();
  const columns: Column<RecordItem>[] = [
    {
      id: "name",
      header: "Entreprise",
      sort: (record) => record.name,
      cell: (record) => <span className="font-medium">{record.name}</span>,
    },
    {
      id: "city",
      header: "Ville",
      sort: (record) => String(field(record, "city") ?? ""),
      cell: (record) =>
        [field(record, "postalCode"), field(record, "city"), field(record, "country")]
          .filter(Boolean)
          .join(" ") || "—",
    },
    { id: "vat", header: "TVA", cell: (record) => display(field(record, "vatNumber")) },
    {
      id: "sites",
      header: "Sites",
      cell: (record) => (
        <Link
          href={`/sites?companyId=${record.id}`}
          className="text-primary underline-offset-4 dark:text-sidebar-primary hover:underline"
          onClick={(event) => event.stopPropagation()}
        >
          Voir les sites
        </Link>
      ),
    },
    {
      id: "updated",
      header: "Mise à jour",
      sort: (record) => record.updated_at,
      cell: (record) => <span className="text-muted-foreground">{date(record.updated_at)}</span>,
    },
  ];
  return (
    <>
      <PageHeader
        title="Entreprises"
        description="Les sociétés propriétaires des sites de recharge."
        actions={
          admin && (
            <Button onClick={() => setEditing(null)}>
              <IconPlus data-icon="inline-start" />
              Ajouter une entreprise
            </Button>
          )
        }
      />
      <RecordList
        kind="companies"
        label="Entreprises"
        columns={columns}
        revision={revision}
        onSelect={admin ? setEditing : undefined}
        emptyIcon={<IconBuildingFactory2 />}
        emptyTitle="Aucune entreprise"
        emptyDescription="Créez une entreprise avant d’y rattacher des sites."
      />
      <FormDialog
        open={editing !== undefined}
        onOpenChange={(open) => !open && setEditing(undefined)}
        title={editing ? `Modifier ${editing.name}` : "Ajouter une entreprise"}
        wide
      >
        <CompanyForm
          record={editing ?? null}
          onSaved={() => {
            setEditing(undefined);
            refresh();
          }}
        />
        {editing && (
          <div className="border-t pt-4">
            <ConfirmButton
              title={`Supprimer ${editing.name} ?`}
              description="Impossible tant que des sites sont rattachés à cette entreprise."
              confirmLabel="Supprimer"
              onConfirm={() =>
                action.run("delete", () => deleteRecord("companies", editing.id), {
                  success: "Entreprise supprimée.",
                  onDone: () => {
                    setEditing(undefined);
                    refresh();
                  },
                })
              }
            >
              Supprimer l’entreprise
            </ConfirmButton>
          </div>
        )}
      </FormDialog>
    </>
  );
}
