"use client";

import { Field, FieldLabel } from "@/components/ui/field";
import { useWorkspaceSession } from "@/features/workspace/workspace-context";
import { DataTable, type Column } from "@/features/shared/data-table";
import { EntityPicker, useLabels } from "@/features/shared/entity-picker";
import { OptionSelect } from "@/features/shared/form-fields";
import { Pager } from "@/features/shared/pager";
import { SearchInput } from "@/features/shared/search-input";
import { EmptyState, ErrorState, ListState } from "@/features/shared/states";
import { ToneBadge } from "@/features/shared/status-badge";
import { usePaged } from "@/features/shared/use-paged";
import { display, kw, relative } from "@/lib/format";
import { IconChargingPile } from "@tabler/icons-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { ConnectorSummary } from "./connector-summary";
import type { Station } from "./station-types";

const states = [
  { value: "all", label: "Tous les états" },
  { value: "connected", label: "Connectées" },
  { value: "disconnected", label: "Hors ligne" },
  { value: "error", label: "En erreur" },
];

export function StationConnection({ station }: { station: Station }) {
  return (
    <div className="flex flex-col gap-1">
      <ToneBadge tone={!station.enabled ? "neutral" : station.connected ? "success" : "danger"}>
        {!station.enabled ? "Désactivée" : station.connected ? "Connectée" : "Hors ligne"}
      </ToneBadge>
      <span className="text-xs text-muted-foreground">
        {station.last_seen ? `Signal ${relative(station.last_seen)}` : "Jamais connectée"}
      </span>
    </div>
  );
}

export function StationsList({ revision }: { revision: number }) {
  const router = useRouter();
  const { components } = useWorkspaceSession();
  const [search, setSearch] = useState("");
  const [state, setState] = useState("all");
  const [siteId, setSiteId] = useState("");
  const [siteAreaId, setSiteAreaId] = useState("");
  const sites = useLabels("sites");
  const areas = useLabels("site-areas");
  const list = usePaged<Station>(
    "stations",
    {
      search,
      state: state === "all" ? null : state,
      siteId,
      siteAreaId,
    },
    { revision },
  );
  const { refresh } = list;

  useEffect(() => {
    const timer = setInterval(refresh, 30000);
    return () => clearInterval(timer);
  }, [refresh]);

  const columns: Column<Station>[] = [
    {
      id: "name",
      header: "Borne",
      sort: (station) => station.name,
      cell: (station) => (
        <Link
          href={`/stations/${encodeURIComponent(station.id)}`}
          className="flex flex-col hover:text-primary dark:hover:text-sidebar-primary"
          onClick={(event) => event.stopPropagation()}
        >
          <span className="font-medium">{station.name}</span>
          <span className="text-xs text-muted-foreground">{station.id}</span>
        </Link>
      ),
    },
    {
      id: "state",
      header: "Connexion",
      sort: (station) => (station.connected ? 1 : 0),
      cell: (station) => <StationConnection station={station} />,
    },
    {
      id: "connectors",
      header: "Connecteurs",
      cell: (station) => <ConnectorSummary connectors={station.connectors ?? []} />,
    },
    ...(components.organization
      ? [
          {
            id: "site",
            header: "Site · zone",
            sort: (station: Station) => sites.get(station.site_id ?? "") ?? "",
            cell: (station: Station) => (
              <span className="flex flex-col">
                <span>{sites.get(station.site_id ?? "") ?? display(station.site_id)}</span>
                <span className="text-xs text-muted-foreground">
                  {areas.get(station.site_area_id ?? "") ?? "Sans zone"}
                </span>
              </span>
            ),
          },
        ]
      : []),
    {
      id: "power",
      header: "Puissance",
      align: "right",
      sort: (station) => station.maximum_power_kw ?? null,
      cell: (station) => kw(station.maximum_power_kw),
    },
    {
      id: "firmware",
      header: "Matériel",
      sort: (station) => station.vendor ?? "",
      cell: (station) => (
        <span className="flex flex-col">
          <span>{[station.vendor, station.model].filter(Boolean).join(" · ") || "—"}</span>
          <span className="text-xs text-muted-foreground">
            {station.firmware_version ?? "Version inconnue"}
            {station.firmware_status && station.firmware_status !== "Idle"
              ? ` · ${display(station.firmware_status)}`
              : ""}
          </span>
        </span>
      ),
    },
  ];

  return (
    <section className="flex min-w-0 flex-col gap-4" aria-label="Liste des bornes">
      <div className="flex flex-wrap items-end gap-3">
        <SearchInput
          value={search}
          onChange={setSearch}
          placeholder="Nom ou identifiant…"
          label="Rechercher une borne"
        />
        <OptionSelect
          value={state}
          onChange={setState}
          options={states}
          className="w-full sm:w-44"
          label="Filtrer par état"
        />
        {components.organization && (
          <>
            <Field className="w-full sm:w-52">
              <FieldLabel className="sr-only" htmlFor="station-site-filter">
                Site
              </FieldLabel>
              <EntityPicker
                id="station-site-filter"
                source="sites"
                value={siteId}
                onChange={(value) => {
                  setSiteId(value);
                  setSiteAreaId("");
                }}
                placeholder="Tous les sites"
              />
            </Field>
            <Field className="w-full sm:w-52">
              <FieldLabel className="sr-only" htmlFor="station-area-filter">
                Zone
              </FieldLabel>
              <EntityPicker
                id="station-area-filter"
                source="site-areas"
                value={siteAreaId}
                filters={{ siteId: siteId || undefined }}
                onChange={setSiteAreaId}
                placeholder="Toutes les zones"
              />
            </Field>
          </>
        )}
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
            icon={<IconChargingPile />}
            title={search || state !== "all" ? "Aucune borne correspondante" : "Aucune borne"}
            description={
              search || state !== "all"
                ? "Modifiez la recherche ou les filtres."
                : "Ajoutez une borne pour obtenir ses identifiants de connexion OCPP."
            }
          />
        }
      >
        <DataTable
          label="Bornes de recharge"
          rows={list.items}
          columns={columns}
          rowKey={(station) => station.id}
          onRowClick={(station) => router.push(`/stations/${encodeURIComponent(station.id)}`)}
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
