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
import { IconMapPin, IconTrash } from "@tabler/icons-react";
import Link from "next/link";
import { useState } from "react";

type UserSite = { site_id: string; name: string; site_admin: boolean; site_owner: boolean };

export function UserSites({ userId }: { userId: string }) {
  const [revision, refresh] = useRevision();
  const { data, loading, error } = useApi<UserSite[]>(`users/${userId}/sites`, revision);
  const [draft, setDraft] = useState<UserSite[] | null>(null);
  const action = useAction();
  const rows = draft ?? data ?? [];
  const update = (siteId: string, patch: Partial<UserSite>) =>
    setDraft(rows.map((row) => (row.site_id === siteId ? { ...row, ...patch } : row)));
  function add(option: PickerOption | null) {
    if (option && !rows.some((row) => row.site_id === option.value)) {
      setDraft([
        ...rows,
        { site_id: option.value, name: option.label, site_admin: false, site_owner: false },
      ]);
    }
  }
  const columns: Column<UserSite>[] = [
    {
      id: "name",
      header: "Site",
      sort: (row) => row.name,
      cell: (row) => (
        <Link
          href={`/sites/${row.site_id}`}
          className="font-medium hover:text-primary dark:hover:text-sidebar-primary"
        >
          {row.name}
        </Link>
      ),
    },
    {
      id: "admin",
      header: "Administrateur du site",
      cell: (row) => (
        <Switch
          aria-label={`Administrateur de ${row.name}`}
          checked={row.site_admin}
          onCheckedChange={(checked) => update(row.site_id, { site_admin: checked === true })}
        />
      ),
    },
    {
      id: "owner",
      header: "Propriétaire",
      cell: (row) => (
        <Switch
          aria-label={`Propriétaire de ${row.name}`}
          checked={row.site_owner}
          onCheckedChange={(checked) => update(row.site_id, { site_owner: checked === true })}
        />
      ),
    },
    {
      id: "remove",
      header: "Retirer",
      srOnlyHeader: true,
      align: "right",
      cell: (row) => (
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label={`Retirer ${row.name}`}
          onClick={() => setDraft(rows.filter((item) => item.site_id !== row.site_id))}
        >
          <IconTrash />
        </Button>
      ),
    },
  ];
  if (error && !data) {
    return <ErrorState error={error} onRetry={refresh} />;
  }
  if (loading && !data) {
    return <LoadingState rows={3} />;
  }
  return (
    <section className="flex flex-col gap-4" aria-label="Sites de l’utilisateur">
      <div className="flex flex-wrap items-end gap-3">
        <Field className="w-full sm:w-72">
          <FieldLabel htmlFor="user-site-add">Affecter à un site</FieldLabel>
          <EntityPicker
            id="user-site-add"
            source="sites"
            value=""
            onChange={(_, option) => add(option)}
            placeholder="Rechercher un site…"
          />
        </Field>
        {draft && (
          <>
            <Button
              disabled={action.busy}
              onClick={() =>
                action.run(
                  "save",
                  () =>
                    api(`users/${userId}/sites`, {
                      method: "PUT",
                      body: JSON.stringify(
                        rows.map((row) => ({
                          siteId: row.site_id,
                          siteAdmin: row.site_admin,
                          siteOwner: row.site_owner,
                        })),
                      ),
                    }),
                  {
                    success: "Sites enregistrés.",
                    onDone: () => {
                      setDraft(null);
                      refresh();
                    },
                  },
                )
              }
            >
              Enregistrer
            </Button>
            <Button variant="ghost" onClick={() => setDraft(null)}>
              Annuler
            </Button>
          </>
        )}
      </div>
      {rows.length ? (
        <DataTable label="Sites" rows={rows} columns={columns} rowKey={(row) => row.site_id} />
      ) : (
        <EmptyState
          icon={<IconMapPin />}
          title="Aucun site affecté"
          description="L’utilisateur ne peut recharger que sur les sites en accès ouvert."
        />
      )}
    </section>
  );
}
