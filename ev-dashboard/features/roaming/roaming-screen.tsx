"use client";

import { Button } from "@/components/ui/button";
import { DataTable, type Column } from "@/features/shared/data-table";
import { FormDialog } from "@/features/shared/form-dialog";
import { PageHeader } from "@/features/shared/page-header";
import { SecretReveal } from "@/features/shared/secret-reveal";
import { EmptyState, ListState } from "@/features/shared/states";
import { StateBadge } from "@/features/shared/status-badge";
import { useApi, useRevision } from "@/hooks/use-api";
import { display, relative } from "@/lib/format";
import { IconPlus, IconWorld } from "@tabler/icons-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { PartnerCreateForm } from "./partner-forms";
import { partnerStatus, type Partner } from "./roaming-types";

export function RoamingScreen() {
  const router = useRouter();
  const [revision, refresh] = useRevision();
  const { data, loading, error } = useApi<Partner[]>("ocpi/partners", revision);
  const [creating, setCreating] = useState(false);
  const [created, setCreated] = useState<{ id: string; token: string; message: string } | null>(
    null,
  );
  const items = data ?? [];
  const columns: Column<Partner>[] = [
    {
      id: "name",
      header: "Partenaire",
      sort: (partner) => partner.name,
      cell: (partner) => (
        <span className="flex flex-col">
          <span className="font-medium">{partner.name}</span>
          <span className="text-xs text-muted-foreground">
            {partner.country_code}*{partner.party_id} · {display(partner.local_role)}
          </span>
        </span>
      ),
    },
    {
      id: "status",
      header: "État",
      sort: (partner) => partnerStatus(partner),
      cell: (partner) => (
        <span className="flex flex-col gap-1">
          <StateBadge state={partnerStatus(partner)} />
          {partner.last_error && (
            <span className="max-w-64 truncate text-xs text-destructive">{partner.last_error}</span>
          )}
        </span>
      ),
    },
    {
      id: "ping",
      header: "Dernier ping",
      sort: (partner) => partner.last_ping_at ?? "",
      cell: (partner) => (partner.last_ping_at ? relative(partner.last_ping_at) : "—"),
    },
    {
      id: "push",
      header: "Dernier envoi",
      sort: (partner) => partner.last_push_at ?? "",
      cell: (partner) => (partner.last_push_at ? relative(partner.last_push_at) : "—"),
    },
    {
      id: "pull",
      header: "Dernière récupération",
      sort: (partner) => partner.last_pull_at ?? "",
      cell: (partner) => (partner.last_pull_at ? relative(partner.last_pull_at) : "—"),
    },
  ];
  return (
    <>
      <PageHeader
        title="Partenaires OCPI"
        description="Itinérance OCPI 2.1.1 : publication de vos sites publics et acceptation des badges partenaires."
        actions={
          <Button onClick={() => setCreating(true)}>
            <IconPlus data-icon="inline-start" />
            Ajouter un partenaire
          </Button>
        }
      />
      <ListState
        data={data}
        items={items}
        loading={loading}
        error={error}
        onRetry={refresh}
        empty={
          <EmptyState
            icon={<IconWorld />}
            title="Aucun partenaire"
            description="Ajoutez un opérateur de mobilité ou un exploitant pour échanger avec lui en OCPI."
          />
        }
      >
        <DataTable
          label="Partenaires"
          rows={items}
          columns={columns}
          rowKey={(partner) => partner.id}
          onRowClick={(partner) => router.push(`/roaming/${partner.id}`)}
        />
      </ListState>
      <FormDialog
        open={creating}
        onOpenChange={(open) => {
          setCreating(open);
          if (!open) {
            setCreated(null);
          }
        }}
        title={created ? "Partenaire créé" : "Ajouter un partenaire"}
        wide
      >
        {created ? (
          <div className="flex flex-col gap-4">
            <SecretReveal
              title="Jeton initial à transmettre"
              message={created.message}
              secret={created.token}
            />
            <Button
              className="w-fit self-end"
              onClick={() => router.push(`/roaming/${created.id}`)}
            >
              Ouvrir le partenaire
            </Button>
          </div>
        ) : (
          <PartnerCreateForm
            onCreated={(result) => {
              setCreated(result);
              refresh();
            }}
          />
        )}
      </FormDialog>
    </>
  );
}
