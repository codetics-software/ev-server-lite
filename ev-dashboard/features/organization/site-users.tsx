"use client";

import { Button } from "@/components/ui/button";
import { Field, FieldLabel } from "@/components/ui/field";
import { Switch } from "@/components/ui/switch";
import { DataTable, type Column } from "@/features/shared/data-table";
import { EntityPicker, type PickerOption } from "@/features/shared/entity-picker";
import { EmptyState, ErrorState, LoadingState } from "@/features/shared/states";
import { useAction } from "@/features/shared/use-action";
import { useApi, useRevision } from "@/hooks/use-api";
import { api } from "@/lib/api";
import { IconTrash, IconUsers } from "@tabler/icons-react";
import Link from "next/link";
import { useState } from "react";

type SiteUser = {
  user_id: string;
  name: string;
  email: string;
  site_admin: boolean;
  site_owner: boolean;
};

export function SiteUsers({ siteId, canEdit }: { siteId: string; canEdit: boolean }) {
  const [revision, refresh] = useRevision();
  const { data, loading, error } = useApi<SiteUser[]>(`sites/${siteId}/users`, revision);
  const [draft, setDraft] = useState<SiteUser[] | null>(null);
  const [picker, setPicker] = useState("");
  const action = useAction();
  const rows = draft ?? data ?? [];
  const dirty = draft !== null;

  function update(userId: string, patch: Partial<SiteUser>) {
    setDraft(rows.map((row) => (row.user_id === userId ? { ...row, ...patch } : row)));
  }

  function add(option: PickerOption | null) {
    if (!option || rows.some((row) => row.user_id === option.value)) {
      return;
    }
    setDraft([
      ...rows,
      {
        user_id: option.value,
        name: option.label,
        email: option.description ?? "",
        site_admin: false,
        site_owner: false,
      },
    ]);
    setPicker("");
  }

  const columns: Column<SiteUser>[] = [
    {
      id: "name",
      header: "Utilisateur",
      sort: (row) => row.name,
      cell: (row) => (
        <Link
          href={`/users/${row.user_id}`}
          className="flex flex-col hover:text-primary dark:hover:text-sidebar-primary"
        >
          <span className="font-medium">{row.name}</span>
          <span className="text-xs text-muted-foreground">{row.email}</span>
        </Link>
      ),
    },
    {
      id: "admin",
      header: "Administrateur du site",
      cell: (row) => (
        <Switch
          aria-label={`Administrateur du site : ${row.name}`}
          checked={row.site_admin}
          disabled={!canEdit}
          onCheckedChange={(checked) => update(row.user_id, { site_admin: checked === true })}
        />
      ),
    },
    {
      id: "owner",
      header: "Propriétaire",
      cell: (row) => (
        <Switch
          aria-label={`Propriétaire du site : ${row.name}`}
          checked={row.site_owner}
          disabled={!canEdit}
          onCheckedChange={(checked) => update(row.user_id, { site_owner: checked === true })}
        />
      ),
    },
    ...(canEdit
      ? [
          {
            id: "remove",
            header: "Retirer",
            srOnlyHeader: true,
            align: "right" as const,
            cell: (row: SiteUser) => (
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label={`Retirer ${row.name}`}
                onClick={() => setDraft(rows.filter((item) => item.user_id !== row.user_id))}
              >
                <IconTrash />
              </Button>
            ),
          },
        ]
      : []),
  ];

  if (error && !data) {
    return <ErrorState error={error} onRetry={refresh} />;
  }
  if (loading && !data) {
    return <LoadingState rows={3} />;
  }
  return (
    <section className="flex flex-col gap-4" aria-label="Utilisateurs du site">
      {canEdit && (
        <div className="flex flex-wrap items-end gap-3">
          <Field className="w-full sm:w-72">
            <FieldLabel htmlFor="site-user-add">Affecter un utilisateur</FieldLabel>
            <EntityPicker
              id="site-user-add"
              source="users"
              value={picker}
              onChange={(_, option) => add(option)}
              placeholder="Rechercher un utilisateur…"
            />
          </Field>
          {dirty && (
            <>
              <Button
                disabled={action.busy}
                onClick={() =>
                  action.run(
                    "save",
                    () =>
                      api(`sites/${siteId}/users`, {
                        method: "PUT",
                        body: JSON.stringify(
                          rows.map((row) => ({
                            userId: row.user_id,
                            siteAdmin: row.site_admin,
                            siteOwner: row.site_owner,
                          })),
                        ),
                      }),
                    {
                      success: "Affectations enregistrées.",
                      onDone: () => {
                        setDraft(null);
                        refresh();
                      },
                    },
                  )
                }
              >
                Enregistrer les affectations
              </Button>
              <Button variant="ghost" onClick={() => setDraft(null)}>
                Annuler
              </Button>
            </>
          )}
        </div>
      )}
      {rows.length ? (
        <DataTable
          label="Utilisateurs du site"
          rows={rows}
          columns={columns}
          rowKey={(row) => row.user_id}
        />
      ) : (
        <EmptyState
          icon={<IconUsers />}
          title="Aucun utilisateur affecté"
          description="Affectez des utilisateurs pour leur donner accès au site lorsque le contrôle d’accès est actif."
        />
      )}
    </section>
  );
}
