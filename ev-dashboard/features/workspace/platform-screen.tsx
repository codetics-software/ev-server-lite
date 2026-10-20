"use client";

import { Button } from "@/components/ui/button";
import {
  Field,
  FieldContent,
  FieldGroup,
  FieldLabel,
  FieldLegend,
  FieldSet,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { DataTable, type Column } from "@/features/shared/data-table";
import { FormDialog } from "@/features/shared/form-dialog";
import { PageHeader } from "@/features/shared/page-header";
import { Pager } from "@/features/shared/pager";
import { SearchInput } from "@/features/shared/search-input";
import { EmptyState, ErrorState, ListState } from "@/features/shared/states";
import { ToneBadge } from "@/features/shared/status-badge";
import { useAction } from "@/features/shared/use-action";
import { usePaged } from "@/features/shared/use-paged";
import { api } from "@/lib/api";
import type { Components } from "@/lib/api-types";
import { day, number } from "@/lib/format";
import { IconServer2 } from "@tabler/icons-react";
import { useState } from "react";
import { componentsInput, ComponentToggles } from "./workspace-settings";
import { resolveComponents } from "./workspace-context";

type PlatformWorkspace = {
  id: string;
  slug: string;
  name: string;
  active: boolean;
  created_at: string;
  components: Partial<Components>;
  users: number;
  stations: number;
};

function Editor({ workspace, onSaved }: { workspace: PlatformWorkspace; onSaved: () => void }) {
  const [name, setName] = useState(workspace.name);
  const [active, setActive] = useState(workspace.active);
  const [components, setComponents] = useState(resolveComponents(workspace));
  const action = useAction();
  return (
    <FieldGroup>
      <Field>
        <FieldLabel htmlFor="platform-name">Nom</FieldLabel>
        <Input
          id="platform-name"
          value={name}
          maxLength={200}
          onChange={(event) => setName(event.target.value)}
        />
      </Field>
      <Field orientation="horizontal">
        <Switch
          id="platform-active"
          checked={active}
          onCheckedChange={(checked) => setActive(checked === true)}
        />
        <FieldContent>
          <FieldLabel htmlFor="platform-active">Espace actif</FieldLabel>
        </FieldContent>
      </Field>
      <FieldSet>
        <FieldLegend>Fonctionnalités</FieldLegend>
        <ComponentToggles value={components} onChange={setComponents} />
      </FieldSet>
      <Button
        className="w-fit self-end"
        disabled={!name.trim() || action.busy}
        onClick={() =>
          action.run(
            "save",
            () =>
              api(`platform/workspaces/${workspace.id}`, {
                method: "PUT",
                body: JSON.stringify({
                  name: name.trim(),
                  active,
                  components: componentsInput(components),
                }),
              }),
            { success: "Espace enregistré.", onDone: onSaved },
          )
        }
      >
        Enregistrer
      </Button>
    </FieldGroup>
  );
}

export function PlatformScreen() {
  const [search, setSearch] = useState("");
  const [editing, setEditing] = useState<PlatformWorkspace | null>(null);
  const list = usePaged<PlatformWorkspace>("platform/workspaces", { search });
  const columns: Column<PlatformWorkspace>[] = [
    {
      id: "name",
      header: "Espace",
      sort: (workspace) => workspace.name,
      cell: (workspace) => (
        <span className="flex flex-col">
          <span className="font-medium">{workspace.name}</span>
          <code className="text-xs text-muted-foreground">{workspace.slug}</code>
        </span>
      ),
    },
    {
      id: "state",
      header: "État",
      sort: (workspace) => (workspace.active ? 1 : 0),
      cell: (workspace) => (
        <ToneBadge tone={workspace.active ? "success" : "neutral"}>
          {workspace.active ? "Actif" : "Suspendu"}
        </ToneBadge>
      ),
    },
    {
      id: "users",
      header: "Utilisateurs",
      align: "right",
      sort: (workspace) => workspace.users,
      cell: (workspace) => number(workspace.users),
    },
    {
      id: "stations",
      header: "Bornes",
      align: "right",
      sort: (workspace) => workspace.stations,
      cell: (workspace) => number(workspace.stations),
    },
    {
      id: "components",
      header: "Fonctionnalités",
      cell: (workspace) =>
        `${Object.values(resolveComponents(workspace)).filter(Boolean).length} actives`,
    },
    {
      id: "created",
      header: "Création",
      sort: (workspace) => workspace.created_at,
      cell: (workspace) => day(workspace.created_at),
    },
  ];
  return (
    <>
      <PageHeader
        title="Plateforme"
        description="Espaces clients hébergés sur cette installation."
      />
      <SearchInput value={search} onChange={setSearch} label="Rechercher un espace" />
      {list.error && list.data && <ErrorState error={list.error} onRetry={list.refresh} />}
      <ListState
        data={list.data}
        items={list.items}
        loading={list.loading}
        error={list.error}
        onRetry={list.refresh}
        empty={<EmptyState icon={<IconServer2 />} title="Aucun espace" />}
      >
        <DataTable
          label="Espaces"
          rows={list.items}
          columns={columns}
          rowKey={(workspace) => workspace.id}
          onRowClick={setEditing}
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
      <FormDialog
        open={editing !== null}
        onOpenChange={(open) => !open && setEditing(null)}
        title={editing?.name ?? ""}
        description={editing?.slug}
        wide
      >
        {editing && (
          <Editor
            workspace={editing}
            onSaved={() => {
              setEditing(null);
              list.refresh();
            }}
          />
        )}
      </FormDialog>
    </>
  );
}
