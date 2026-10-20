"use client";

import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ExportButton, ImportButton } from "@/features/shared/csv-actions";
import { DataTable, type Column } from "@/features/shared/data-table";
import { FormDialog } from "@/features/shared/form-dialog";
import { OptionSelect } from "@/features/shared/form-fields";
import { PageHeader } from "@/features/shared/page-header";
import { Pager } from "@/features/shared/pager";
import { SearchInput } from "@/features/shared/search-input";
import { EmptyState, ErrorState, ListState } from "@/features/shared/states";
import { ErrorBadges, ToneBadge } from "@/features/shared/status-badge";
import { usePaged } from "@/features/shared/use-paged";
import { useApi, useRevision } from "@/hooks/use-api";
import { relative } from "@/lib/format";
import { roleLabels } from "@/lib/labels";
import { IconCircleCheck, IconPlus, IconUsers } from "@tabler/icons-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { roleOptions, UserForm } from "./user-form";
import type { ManagedUser, UserInError } from "./user-types";

export function UserState({ user }: { user: ManagedUser }) {
  if (!user.enabled) {
    return <ToneBadge tone="neutral">Désactivé</ToneBadge>;
  }
  if (!user.email_verified) {
    return <ToneBadge tone="warning">À vérifier</ToneBadge>;
  }
  return <ToneBadge tone="success">Actif</ToneBadge>;
}

const columns: Column<ManagedUser>[] = [
  {
    id: "name",
    header: "Utilisateur",
    sort: (user) => user.display_name,
    cell: (user) => (
      <span className="flex flex-col">
        <span className="font-medium">{user.display_name}</span>
        <span className="text-xs text-muted-foreground">{user.email}</span>
      </span>
    ),
  },
  { id: "role", header: "Rôle", sort: (user) => user.role, cell: (user) => roleLabels[user.role] },
  {
    id: "state",
    header: "État",
    sort: (user) => (user.enabled ? 1 : 0),
    cell: (user) => <UserState user={user} />,
  },
  {
    id: "sites",
    header: "Sites",
    align: "right",
    sort: (user) => user.site_count ?? 0,
    cell: (user) => user.site_count ?? 0,
  },
  {
    id: "login",
    header: "Dernière connexion",
    sort: (user) => user.last_login_at ?? "",
    cell: (user) => (user.last_login_at ? relative(user.last_login_at) : "Jamais"),
  },
];

const states = [
  { value: "all", label: "Tous les états" },
  { value: "active", label: "Actifs" },
  { value: "disabled", label: "Désactivés" },
  { value: "unverified", label: "Adresse à vérifier" },
];

function UsersList({ revision }: { revision: number }) {
  const router = useRouter();
  const [search, setSearch] = useState("");
  const [role, setRole] = useState("all");
  const [state, setState] = useState("all");
  const list = usePaged<ManagedUser>(
    "users",
    { search, role: role === "all" ? null : role, state: state === "all" ? null : state },
    { revision },
  );
  return (
    <section className="flex flex-col gap-4" aria-label="Utilisateurs">
      <div className="flex flex-wrap items-end gap-3">
        <SearchInput
          value={search}
          onChange={setSearch}
          placeholder="Nom ou adresse…"
          label="Rechercher un utilisateur"
        />
        <OptionSelect
          value={role}
          onChange={setRole}
          label="Rôle"
          className="w-full sm:w-44"
          options={[{ value: "all", label: "Tous les rôles" }, ...roleOptions]}
        />
        <OptionSelect
          value={state}
          onChange={setState}
          label="État"
          className="w-full sm:w-48"
          options={states}
        />
      </div>
      {list.error && list.data && <ErrorState error={list.error} onRetry={list.refresh} />}
      <ListState
        data={list.data}
        items={list.items}
        loading={list.loading}
        error={list.error}
        onRetry={list.refresh}
        empty={
          <EmptyState
            icon={<IconUsers />}
            title="Aucun utilisateur"
            description="Modifiez la recherche ou les filtres."
          />
        }
      >
        <DataTable
          label="Utilisateurs"
          rows={list.items}
          columns={columns}
          rowKey={(user) => user.id}
          onRowClick={(user) => router.push(`/users/${user.id}`)}
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
    </section>
  );
}

function UsersInError({ revision }: { revision: number }) {
  const router = useRouter();
  const { data, loading, error } = useApi<UserInError[]>("users/in-error", revision);
  const rows = (data ?? []).map((item) => ({ ...item.user, errors: item.errors }));
  return (
    <ListState
      data={data}
      items={rows}
      loading={loading}
      error={error}
      empty={
        <EmptyState
          icon={<IconCircleCheck />}
          title="Aucun utilisateur en erreur"
          description="Tous les comptes sont vérifiés, actifs et équipés d’un badge."
        />
      }
    >
      <DataTable
        label="Utilisateurs en erreur"
        rows={rows}
        rowKey={(user) => user.id}
        onRowClick={(user) => router.push(`/users/${user.id}`)}
        columns={[
          ...(columns.slice(0, 2) as Column<ManagedUser & { errors: string[] }>[]),
          {
            id: "errors",
            header: "Anomalies",
            cell: (user) => <ErrorBadges errors={user.errors} />,
          },
        ]}
      />
    </ListState>
  );
}

export function UsersScreen() {
  const router = useRouter();
  const [revision, refresh] = useRevision();
  const [creating, setCreating] = useState(false);
  return (
    <>
      <PageHeader
        title="Utilisateurs et rôles"
        description="Comptes de supervision et conducteurs, rôles, accès aux sites et moyens de paiement."
        actions={
          <>
            <ImportButton
              path="users/import"
              title="Importer des utilisateurs"
              columns="email;name;role;badge"
              onImported={refresh}
            />
            <ExportButton path="users/export" filename="utilisateurs" />
            <Button onClick={() => setCreating(true)}>
              <IconPlus data-icon="inline-start" />
              Ajouter un utilisateur
            </Button>
          </>
        }
      />
      <Tabs defaultValue="all">
        <TabsList variant="line">
          <TabsTrigger value="all">Tous les utilisateurs</TabsTrigger>
          <TabsTrigger value="errors">En erreur</TabsTrigger>
        </TabsList>
        <TabsContent value="all" className="pt-4">
          <UsersList revision={revision} />
        </TabsContent>
        <TabsContent value="errors" className="pt-4">
          <UsersInError revision={revision} />
        </TabsContent>
      </Tabs>
      <FormDialog
        open={creating}
        onOpenChange={setCreating}
        title="Ajouter un utilisateur"
        description="Un courriel l’invitera à définir son mot de passe."
        wide
      >
        <UserForm
          user={null}
          onSaved={({ id }) => {
            setCreating(false);
            refresh();
            if (id) {
              router.push(`/users/${id}`);
            }
          }}
        />
      </FormDialog>
    </>
  );
}
