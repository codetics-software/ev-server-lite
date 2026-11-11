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
import { t } from "@/lib/i18n";

function badgeState(badge: Badge) {
  if (!badge.enabled) {
    return <ToneBadge tone="neutral">{t("Bloqué")}</ToneBadge>;
  }
  if (badge.expires_at && new Date(badge.expires_at) < new Date()) {
    return <ToneBadge tone="warning">{t("Expiré")}</ToneBadge>;
  }
  return <ToneBadge tone="success">{t("Actif")}</ToneBadge>;
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
      header: t("Badge"),
      sort: (badge) => badge.visual_id || badge.uid,
      cell: (badge) =>
        badge.vehicle_id ? (
          <span className="flex flex-col items-start gap-1">
            <span className="font-mono text-xs font-medium">{badge.uid}</span>
            <ToneBadge tone="progress">{t("Véhicule")}</ToneBadge>
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
      header: t("État"),
      sort: (badge) => (badge.enabled ? 1 : 0),
      cell: (badge) => (
        <span className="flex flex-wrap items-center gap-1">
          {badgeState(badge)}
          {badge.default && <ToneBadge tone="progress">{t("Par défaut")}</ToneBadge>}
        </span>
      ),
    },
    ...(!userId
      ? [
          {
            id: "user",
            header: t("Utilisateur"),
            sort: (badge: Badge) => badge.user_name ?? "",
            cell: (badge: Badge) =>
              badge.user_id && isAdmin(user) ? (
                <Link
                  href={`/users/${badge.user_id}`}
                  className="hover:text-primary dark:hover:text-sidebar-primary"
                  onClick={(event) => event.stopPropagation()}
                >
                  {badge.user_name ?? t("Utilisateur")}
                </Link>
              ) : (
                (badge.user_name ?? (
                  <span className="text-muted-foreground">{t("Non attribué")}</span>
                ))
              ),
          },
        ]
      : []),
    {
      id: "description",
      header: t("Description"),
      cell: (badge) =>
        badge.vehicle_id ? (
          <span className="flex max-w-64 flex-col">
            {cars ? (
              <Link
                href="/cars"
                className="truncate hover:text-primary dark:hover:text-sidebar-primary"
                onClick={(event) => event.stopPropagation()}
              >
                {badge.vehicle_name || badge.description || t("Véhicule")}
              </Link>
            ) : (
              <span className="truncate">
                {badge.vehicle_name || badge.description || t("Véhicule")}
              </span>
            )}
            <span className="text-xs text-muted-foreground">
              {t("Recharge automatique, gérée depuis la fiche du véhicule")}
            </span>
          </span>
        ) : (
          <span className="block max-w-64 truncate">{badge.description || "—"}</span>
        ),
    },
    {
      id: "used",
      header: t("Dernière utilisation"),
      sort: (badge) => badge.last_used_at ?? "",
      cell: (badge) => (badge.last_used_at ? relative(badge.last_used_at) : "Jamais"),
    },
    {
      id: "expires",
      header: t("Expiration"),
      sort: (badge) => badge.expires_at ?? "",
      cell: (badge) => date(badge.expires_at),
    },
    ...(write
      ? [
          {
            id: "actions",
            header: t("Actions"),
            srOnlyHeader: true,
            align: "right" as const,
            cell: (badge: Badge) =>
              badge.vehicle_id ? (
                <div onClick={(event) => event.stopPropagation()} className="flex justify-end">
                  <ConfirmButton
                    title={t("Révoquer cet identifiant de véhicule ?")}
                    description={t(
                      "Les bornes ne démarreront plus automatiquement la recharge de ce véhicule.",
                    )}
                    confirmLabel={t("Révoquer")}
                    variant="ghost"
                    size="icon-sm"
                    ariaLabel={t("Révoquer {uid}", { uid: badge.uid })}
                    onConfirm={() =>
                      action.run(
                        "revoke",
                        () => api(`tokens/${encodeURIComponent(badge.uid)}`, { method: "DELETE" }),
                        { success: t("Identifiant du véhicule révoqué."), onDone: list.refresh },
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
                          aria-label={t("Actions du badge {uid}", { uid: badge.uid })}
                        />
                      }
                    >
                      <IconDots />
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuGroup>
                        <DropdownMenuItem onClick={() => setEditing(badge)}>
                          {t("Modifier")}
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
                                  success: t("Badge retiré de l’utilisateur."),
                                  onDone: list.refresh,
                                },
                              )
                            }
                          >
                            {t("Retirer l’utilisateur")}
                          </DropdownMenuItem>
                        ) : (
                          <DropdownMenuItem onClick={() => setAssigning(badge)}>
                            {t("Attribuer à un utilisateur")}
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
                                success: badge.enabled ? t("Badge bloqué.") : t("Badge débloqué."),
                                onDone: list.refresh,
                              },
                            )
                          }
                        >
                          {badge.enabled ? t("Bloquer") : t("Débloquer")}
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
    <section className="flex min-w-0 flex-col gap-4" aria-label={t("Badges")}>
      <div className="flex flex-wrap items-end gap-3">
        <SearchInput
          value={search}
          onChange={setSearch}
          placeholder={t("Identifiant, numéro, utilisateur…")}
          label={t("Rechercher un badge")}
        />
        <div className="ml-auto flex flex-wrap gap-2">
          {toolbar}
          {write && <Button onClick={() => setEditing(null)}>{t("Ajouter un badge")}</Button>}
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
            title={search ? t("Aucun badge correspondant") : t("Aucun badge")}
            description={
              search
                ? t("Essayez une autre recherche.")
                : t("Ajoutez un badge RFID pour autoriser la recharge.")
            }
          />
        }
      >
        <DataTable
          label={t("Badges")}
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
        title={
          editing
            ? t("Badge {name}", { name: editing.visual_id || editing.uid })
            : t("Ajouter un badge")
        }
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
              title={t("Supprimer ce badge ?")}
              description={t(
                "Impossible tant qu’une session est en cours avec ce badge. L’historique est conservé.",
              )}
              confirmLabel={t("Supprimer")}
              onConfirm={() =>
                action.run(
                  "delete",
                  () => api(`tokens/${encodeURIComponent(editing.uid)}`, { method: "DELETE" }),
                  {
                    success: t("Badge supprimé."),
                    onDone: () => {
                      setEditing(undefined);
                      list.refresh();
                    },
                  },
                )
              }
            >
              {t("Supprimer le badge")}
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
        title={t("Attribuer le badge")}
        description={
          assigning ? t("Badge {name}", { name: assigning.visual_id || assigning.uid }) : undefined
        }
      >
        <div className="flex flex-col gap-4">
          <Field>
            <FieldLabel htmlFor="badge-assignee">{t("Utilisateur")}</FieldLabel>
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
                  success: t("Badge attribué."),
                  onDone: () => {
                    setAssigning(null);
                    setAssignee("");
                    list.refresh();
                  },
                },
              )
            }
          >
            {t("Attribuer")}
          </Button>
        </div>
      </FormDialog>
    </section>
  );
}
