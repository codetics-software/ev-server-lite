"use client";

import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Field, FieldLabel } from "@/components/ui/field";
import { ConfirmButton } from "@/features/shared/confirm-button";
import { DataTable, type Column } from "@/features/shared/data-table";
import { EntityPicker } from "@/features/shared/entity-picker";
import { FormDialog } from "@/features/shared/form-dialog";
import { Pager } from "@/features/shared/pager";
import { SearchInput } from "@/features/shared/search-input";
import { EmptyState, ErrorState, ListState } from "@/features/shared/states";
import { ToneBadge } from "@/features/shared/status-badge";
import { useAction } from "@/features/shared/use-action";
import { usePaged } from "@/features/shared/use-paged";
import {
  canWrite,
  isAdmin,
  useComponent,
  useWorkspace,
} from "@/features/workspace/workspace-context";
import { api } from "@/lib/api";
import { date, relative } from "@/lib/format";
import { IconDots, IconTag, IconTrash } from "@tabler/icons-react";
import Link from "next/link";
import { useState, type ReactNode } from "react";
import { BadgeForm } from "./badge-form";
import type { Badge } from "./badge-types";

function badgeState(badge: Badge) {
  if (!badge.enabled) {
    return <ToneBadge tone="neutral">Bloqué</ToneBadge>;
  }
  if (badge.expires_at && new Date(badge.expires_at) < new Date()) {
    return <ToneBadge tone="warning">Expiré</ToneBadge>;
  }
  return <ToneBadge tone="success">Actif</ToneBadge>;
}

export function BadgesList({
  userId,
  revision = 0,
  toolbar,
}: {
  userId?: string;
  revision?: number;
  toolbar?: ReactNode;
}) {
  const user = useWorkspace();
  const write = canWrite(user);
  const cars = useComponent("cars");
  const [search, setSearch] = useState("");
  const list = usePaged<Badge>("tokens", { search, userId }, { revision });
  const [editing, setEditing] = useState<Badge | null | undefined>();
  const [assigning, setAssigning] = useState<Badge | null>(null);
  const [assignee, setAssignee] = useState("");
  const action = useAction();

  const columns: Column<Badge>[] = [
    {
      id: "uid",
      header: "Badge",
      sort: (badge) => badge.visual_id || badge.uid,
      cell: (badge) =>
        badge.vehicle_id ? (
          <span className="flex flex-col items-start gap-1">
            <span className="font-mono text-xs font-medium">{badge.uid}</span>
            <ToneBadge tone="progress">Véhicule</ToneBadge>
          </span>
        ) : (
          <span className="flex flex-col">
            <span className="font-medium">{badge.visual_id || badge.uid}</span>
            <span className="font-mono text-xs text-muted-foreground">
              {badge.visual_id ? badge.uid : badge.description || badge.type}
            </span>
          </span>
        ),
    },
    {
      id: "state",
      header: "État",
      sort: (badge) => (badge.enabled ? 1 : 0),
      cell: (badge) => (
        <span className="flex flex-wrap items-center gap-1">
          {badgeState(badge)}
          {badge.default && <ToneBadge tone="progress">Par défaut</ToneBadge>}
        </span>
      ),
    },
    ...(!userId
      ? [
          {
            id: "user",
            header: "Utilisateur",
            sort: (badge: Badge) => badge.user_name ?? "",
            cell: (badge: Badge) =>
              badge.user_id && isAdmin(user) ? (
                <Link
                  href={`/users/${badge.user_id}`}
                  className="hover:text-primary dark:hover:text-sidebar-primary"
                  onClick={(event) => event.stopPropagation()}
                >
                  {badge.user_name ?? "Utilisateur"}
                </Link>
              ) : (
                (badge.user_name ?? <span className="text-muted-foreground">Non attribué</span>)
              ),
          },
        ]
      : []),
    {
      id: "description",
      header: "Description",
      cell: (badge) =>
        badge.vehicle_id ? (
          <span className="flex max-w-64 flex-col">
            {cars ? (
              <Link
                href="/cars"
                className="truncate hover:text-primary dark:hover:text-sidebar-primary"
                onClick={(event) => event.stopPropagation()}
              >
                {badge.vehicle_name || badge.description || "Véhicule"}
              </Link>
            ) : (
              <span className="truncate">
                {badge.vehicle_name || badge.description || "Véhicule"}
              </span>
            )}
            <span className="text-xs text-muted-foreground">
              Recharge automatique, gérée depuis la fiche du véhicule
            </span>
          </span>
        ) : (
          <span className="block max-w-64 truncate">{badge.description || "—"}</span>
        ),
    },
    {
      id: "used",
      header: "Dernière utilisation",
      sort: (badge) => badge.last_used_at ?? "",
      cell: (badge) => (badge.last_used_at ? relative(badge.last_used_at) : "Jamais"),
    },
    {
      id: "expires",
      header: "Expiration",
      sort: (badge) => badge.expires_at ?? "",
      cell: (badge) => date(badge.expires_at),
    },
    ...(write
      ? [
          {
            id: "actions",
            header: "Actions",
            srOnlyHeader: true,
            align: "right" as const,
            cell: (badge: Badge) =>
              badge.vehicle_id ? (
                <div onClick={(event) => event.stopPropagation()} className="flex justify-end">
                  <ConfirmButton
                    title="Révoquer cet identifiant de véhicule ?"
                    description="Les bornes ne démarreront plus automatiquement la recharge de ce véhicule."
                    confirmLabel="Révoquer"
                    variant="ghost"
                    size="icon-sm"
                    ariaLabel={`Révoquer ${badge.uid}`}
                    onConfirm={() =>
                      action.run(
                        "revoke",
                        () => api(`tokens/${encodeURIComponent(badge.uid)}`, { method: "DELETE" }),
                        { success: "Identifiant du véhicule révoqué.", onDone: list.refresh },
                      )
                    }
                  >
                    <IconTrash />
                  </ConfirmButton>
                </div>
              ) : (
                <div onClick={(event) => event.stopPropagation()} className="flex justify-end">
                  <DropdownMenu>
                    <DropdownMenuTrigger
                      render={
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          aria-label={`Actions du badge ${badge.uid}`}
                        />
                      }
                    >
                      <IconDots />
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuGroup>
                        <DropdownMenuItem onClick={() => setEditing(badge)}>
                          Modifier
                        </DropdownMenuItem>
                        {badge.user_id ? (
                          <DropdownMenuItem
                            onClick={() =>
                              action.run(
                                "unassign",
                                () =>
                                  api(`tokens/${encodeURIComponent(badge.uid)}/unassign`, {
                                    method: "POST",
                                  }),
                                {
                                  success: "Badge retiré de l’utilisateur.",
                                  onDone: list.refresh,
                                },
                              )
                            }
                          >
                            Retirer l’utilisateur
                          </DropdownMenuItem>
                        ) : (
                          <DropdownMenuItem onClick={() => setAssigning(badge)}>
                            Attribuer à un utilisateur
                          </DropdownMenuItem>
                        )}
                        <DropdownMenuItem
                          onClick={() =>
                            action.run(
                              "toggle",
                              () =>
                                api("tokens", {
                                  method: "POST",
                                  body: JSON.stringify({
                                    uid: badge.uid,
                                    visualId: badge.visual_id ?? undefined,
                                    description: badge.description ?? undefined,
                                    userId: badge.user_id ?? undefined,
                                    enabled: !badge.enabled,
                                    expiresAt: badge.expires_at ?? undefined,
                                    default: badge.default ?? false,
                                  }),
                                }),
                              {
                                success: badge.enabled ? "Badge bloqué." : "Badge débloqué.",
                                onDone: list.refresh,
                              },
                            )
                          }
                        >
                          {badge.enabled ? "Bloquer" : "Débloquer"}
                        </DropdownMenuItem>
                      </DropdownMenuGroup>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </div>
              ),
          },
        ]
      : []),
  ];

  return (
    <section className="flex min-w-0 flex-col gap-4" aria-label="Badges">
      <div className="flex flex-wrap items-end gap-3">
        <SearchInput
          value={search}
          onChange={setSearch}
          placeholder="Identifiant, numéro, utilisateur…"
          label="Rechercher un badge"
        />
        <div className="ml-auto flex flex-wrap gap-2">
          {toolbar}
          {write && <Button onClick={() => setEditing(null)}>Ajouter un badge</Button>}
        </div>
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
            icon={<IconTag />}
            title={search ? "Aucun badge correspondant" : "Aucun badge"}
            description={
              search
                ? "Essayez une autre recherche."
                : "Ajoutez un badge RFID pour autoriser la recharge."
            }
          />
        }
      >
        <DataTable
          label="Badges"
          rows={list.items}
          columns={columns}
          rowKey={(badge) => badge.uid}
          onRowClick={write ? (badge) => !badge.vehicle_id && setEditing(badge) : undefined}
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
        open={editing !== undefined}
        onOpenChange={(open) => !open && setEditing(undefined)}
        title={editing ? `Badge ${editing.visual_id || editing.uid}` : "Ajouter un badge"}
        wide
      >
        <BadgeForm
          badge={editing ?? null}
          userId={userId}
          onSaved={() => {
            setEditing(undefined);
            list.refresh();
          }}
        />
        {editing && isAdmin(user) && (
          <div className="border-t pt-4">
            <ConfirmButton
              title="Supprimer ce badge ?"
              description="Impossible tant qu’une session est en cours avec ce badge. L’historique est conservé."
              confirmLabel="Supprimer"
              onConfirm={() =>
                action.run(
                  "delete",
                  () => api(`tokens/${encodeURIComponent(editing.uid)}`, { method: "DELETE" }),
                  {
                    success: "Badge supprimé.",
                    onDone: () => {
                      setEditing(undefined);
                      list.refresh();
                    },
                  },
                )
              }
            >
              Supprimer le badge
            </ConfirmButton>
          </div>
        )}
      </FormDialog>
      <FormDialog
        open={assigning !== null}
        onOpenChange={(open) => {
          if (!open) {
            setAssigning(null);
            setAssignee("");
          }
        }}
        title="Attribuer le badge"
        description={assigning ? `Badge ${assigning.visual_id || assigning.uid}` : undefined}
      >
        <div className="flex flex-col gap-4">
          <Field>
            <FieldLabel htmlFor="badge-assignee">Utilisateur</FieldLabel>
            <EntityPicker
              id="badge-assignee"
              source="users"
              value={assignee}
              onChange={setAssignee}
            />
          </Field>
          <Button
            className="w-fit self-end"
            disabled={!assignee || action.busy}
            onClick={() =>
              assigning &&
              action.run(
                "assign",
                () =>
                  api(`tokens/${encodeURIComponent(assigning.uid)}/assign`, {
                    method: "POST",
                    body: JSON.stringify({ userId: assignee }),
                  }),
                {
                  success: "Badge attribué.",
                  onDone: () => {
                    setAssigning(null);
                    setAssignee("");
                    list.refresh();
                  },
                },
              )
            }
          >
            Attribuer
          </Button>
        </div>
      </FormDialog>
    </section>
  );
}
