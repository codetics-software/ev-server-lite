"use client";

import { Field, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { ExportButton } from "@/features/shared/csv-actions";
import { DataTable } from "@/features/shared/data-table";
import { EntityPicker } from "@/features/shared/entity-picker";
import { Pager } from "@/features/shared/pager";
import { SearchInput } from "@/features/shared/search-input";
import { EmptyState, ErrorState, ListState } from "@/features/shared/states";
import { usePaged } from "@/features/shared/use-paged";
import { isAdmin, isStaff, useWorkspaceSession } from "@/features/workspace/workspace-context";
import { IconBolt } from "@tabler/icons-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { sessionColumns } from "./session-columns";
import type { ChargingSession } from "./session-types";

function startOfDay(value: string) {
  return value ? new Date(`${value}T00:00:00`).toISOString() : null;
}

function endOfDay(value: string) {
  return value ? new Date(`${value}T23:59:59.999`).toISOString() : null;
}

export function SessionsList({
  status,
  fixed = {},
  filters = true,
  revision = 0,
}: {
  status: "active" | "completed" | null;
  fixed?: {
    stationId?: string;
    userId?: string;
    siteId?: string;
    siteAreaId?: string;
    tokenUid?: string;
  };
  filters?: boolean;
  revision?: number;
}) {
  const router = useRouter();
  const { user, components } = useWorkspaceSession();
  const [search, setSearch] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [stationId, setStationId] = useState("");
  const [siteId, setSiteId] = useState("");
  const [userId, setUserId] = useState("");
  const parameters = {
    status,
    search,
    from: startOfDay(from),
    to: endOfDay(to),
    stationId: fixed.stationId ?? stationId,
    siteId: fixed.siteId ?? siteId,
    userId: fixed.userId ?? userId,
    siteAreaId: fixed.siteAreaId,
    tokenUid: fixed.tokenUid,
  };
  const list = usePaged<ChargingSession>("transactions", parameters, { revision });
  const { refresh } = list;
  const staff = isStaff(user);

  useEffect(() => {
    if (status !== "active") {
      return;
    }
    const timer = setInterval(refresh, 20000);
    return () => clearInterval(timer);
  }, [status, refresh]);

  const columns = sessionColumns({
    showUser: staff && !fixed.userId,
    showStation: !fixed.stationId,
    active: status === "active",
  });

  return (
    <section className="flex min-w-0 flex-col gap-4" aria-label="Sessions">
      {filters && (
        <div className="flex flex-wrap items-end gap-3">
          <SearchInput value={search} onChange={setSearch} placeholder="Badge, borne, session…" />
          {status !== "active" && (
            <>
              <Field className="w-full sm:w-40">
                <FieldLabel htmlFor="sessions-from" className="text-xs text-muted-foreground">
                  Du
                </FieldLabel>
                <Input
                  id="sessions-from"
                  type="date"
                  value={from}
                  max={to || undefined}
                  onChange={(event) => setFrom(event.target.value)}
                />
              </Field>
              <Field className="w-full sm:w-40">
                <FieldLabel htmlFor="sessions-to" className="text-xs text-muted-foreground">
                  Au
                </FieldLabel>
                <Input
                  id="sessions-to"
                  type="date"
                  value={to}
                  min={from || undefined}
                  onChange={(event) => setTo(event.target.value)}
                />
              </Field>
            </>
          )}
          {staff && !fixed.stationId && (
            <Field className="w-full sm:w-52">
              <FieldLabel htmlFor="sessions-station" className="text-xs text-muted-foreground">
                Borne
              </FieldLabel>
              <EntityPicker
                id="sessions-station"
                source="stations"
                value={stationId}
                onChange={setStationId}
                placeholder="Toutes les bornes"
              />
            </Field>
          )}
          {staff &&
            components.organization &&
            !fixed.stationId &&
            !fixed.siteId &&
            !fixed.siteAreaId && (
              <Field className="w-full sm:w-52">
                <FieldLabel htmlFor="sessions-site" className="text-xs text-muted-foreground">
                  Site
                </FieldLabel>
                <EntityPicker
                  id="sessions-site"
                  source="sites"
                  value={siteId}
                  onChange={setSiteId}
                  placeholder="Tous les sites"
                />
              </Field>
            )}
          {isAdmin(user) && !fixed.userId && (
            <Field className="w-full sm:w-52">
              <FieldLabel htmlFor="sessions-user" className="text-xs text-muted-foreground">
                Utilisateur
              </FieldLabel>
              <EntityPicker
                id="sessions-user"
                source="users"
                value={userId}
                onChange={setUserId}
                placeholder="Tous les utilisateurs"
              />
            </Field>
          )}
          <div className="ml-auto">
            <ExportButton path="transactions/export" filename="sessions" parameters={parameters} />
          </div>
        </div>
      )}
      {list.error && list.data && <ErrorState error={list.error} onRetry={list.refresh} />}
      <ListState
        data={list.data}
        items={list.items}
        loading={list.loading}
        error={list.error}
        onRetry={list.refresh}
        empty={
          <EmptyState
            icon={<IconBolt />}
            title={status === "active" ? "Aucune session en cours" : "Aucune session"}
            description={
              status === "active"
                ? "Les recharges en cours apparaissent ici en temps réel."
                : "Modifiez la période ou les filtres pour retrouver une session."
            }
          />
        }
      >
        <DataTable
          label="Sessions de recharge"
          rows={list.items}
          columns={columns}
          rowKey={(session) => String(session.id)}
          onRowClick={(session) => router.push(`/transactions/${session.id}`)}
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
