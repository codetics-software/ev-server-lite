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
import { date, kw } from "@/lib/format";
import { IconFileSettings, IconPlus } from "@tabler/icons-react";
import { useState } from "react";
import { TemplateForm } from "./template-form";

const columns: Column<RecordItem>[] = [
  {
    id: "name",
    header: "Modèle",
    sort: (record) => record.name,
    cell: (record) => <span className="font-medium">{record.name}</span>,
  },
  {
    id: "match",
    header: "Bornes concernées",
    cell: (record) => (
      <code className="text-xs">
        {String(field(record, "vendor") ?? "*")} / {String(field(record, "model") ?? "*")}
        {field(record, "firmwareVersion") ? ` / ${String(field(record, "firmwareVersion"))}` : ""}
      </code>
    ),
  },
  {
    id: "power",
    header: "Puissance",
    align: "right",
    cell: (record) => kw(field(record, "maximumPowerKw")),
  },
  {
    id: "connectors",
    header: "Connecteurs",
    align: "right",
    cell: (record) => (field<unknown[]>(record, "connectors") ?? []).length,
  },
  {
    id: "parameters",
    header: "Paramètres",
    align: "right",
    cell: (record) => (field<unknown[]>(record, "parameters") ?? []).length,
  },
  {
    id: "updated",
    header: "Mise à jour",
    sort: (record) => record.updated_at,
    cell: (record) => <span className="text-muted-foreground">{date(record.updated_at)}</span>,
  },
];

export function TemplatesScreen() {
  const user = useWorkspace();
  const admin = isAdmin(user);
  const [revision, refresh] = useRevision();
  const [editing, setEditing] = useState<RecordItem | null | undefined>();
  const action = useAction();
  return (
    <>
      <PageHeader
        title="Modèles de bornes"
        description="Caractéristiques et paramètres OCPP appliqués automatiquement aux bornes correspondantes."
        actions={
          admin && (
            <Button onClick={() => setEditing(null)}>
              <IconPlus data-icon="inline-start" />
              Ajouter un modèle
            </Button>
          )
        }
      />
      <RecordList
        kind="charging-station-templates"
        label="Modèles de bornes"
        revision={revision}
        columns={columns}
        onSelect={admin ? setEditing : undefined}
        emptyIcon={<IconFileSettings />}
        emptyTitle="Aucun modèle"
        emptyDescription="Décrivez un fabricant et un modèle pour configurer automatiquement leurs connecteurs."
      />
      <FormDialog
        open={editing !== undefined}
        onOpenChange={(open) => !open && setEditing(undefined)}
        title={editing ? `Modifier ${editing.name}` : "Ajouter un modèle"}
        wide
      >
        <TemplateForm
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
              description="Les bornes déjà configurées conservent leurs paramètres."
              confirmLabel="Supprimer"
              onConfirm={() =>
                action.run("delete", () => deleteRecord("charging-station-templates", editing.id), {
                  success: "Modèle supprimé.",
                  onDone: () => {
                    setEditing(undefined);
                    refresh();
                  },
                })
              }
            >
              Supprimer le modèle
            </ConfirmButton>
          </div>
        )}
      </FormDialog>
    </>
  );
}
