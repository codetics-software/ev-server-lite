"use client";

import { Button } from "@/components/ui/button";
import { Field, FieldLabel } from "@/components/ui/field";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { SessionsList } from "@/features/sessions/sessions-list";
import { ConfirmButton } from "@/features/shared/confirm-button";
import { EntityPicker, useLabels } from "@/features/shared/entity-picker";
import { FormDialog } from "@/features/shared/form-dialog";
import { PageHeader } from "@/features/shared/page-header";
import { RecordList } from "@/features/shared/record-list";
import { deleteRecord, field } from "@/features/shared/records";
import { ErrorState, LoadingState } from "@/features/shared/states";
import { ToneBadge } from "@/features/shared/status-badge";
import { useAction } from "@/features/shared/use-action";
import { assignStation } from "@/features/stations/assign-station";
import { StationsTable } from "@/features/stations/stations-table";
import { canWrite, isAdmin, useWorkspaceSession } from "@/features/workspace/workspace-context";
import { useRevision } from "@/hooks/use-api";
import { kw } from "@/lib/format";
import { IconPlus } from "@tabler/icons-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { assetColumns } from "./assets-screen";
import { SiteAreaForm } from "./organization-forms";
import { siteAreaColumns } from "./site-areas-screen";
import { ConsumptionPanel, SmartChargingPanel } from "./smart-charging-panel";
import { useRecord } from "./use-record";

export function SiteAreaDetailScreen({ id }: { id: string }) {
  const { user, components } = useWorkspaceSession();
  const router = useRouter();
  const admin = isAdmin(user);
  const operate = canWrite(user);
  const { data: area, error, loading, refresh } = useRecord("site-areas", id);
  const [stationsRevision, refreshStations] = useRevision();
  const [childRevision, refreshChildren] = useRevision();
  const [creatingChild, setCreatingChild] = useState(false);
  const [picker, setPicker] = useState("");
  const sites = useLabels("sites");
  const action = useAction();

  if (error && !area) {
    return (
      <>
        <PageHeader title="Zone" back={{ href: "/site-areas", label: "Zones" }} />
        <ErrorState error={error} onRetry={refresh} />
      </>
    );
  }
  if (loading && !area) {
    return <LoadingState />;
  }
  if (!area) {
    return null;
  }
  const siteId = String(field(area, "siteId") ?? "");
  const smart = field(area, "smartCharging") === true;

  return (
    <>
      <PageHeader
        back={{
          href: siteId ? `/sites/${siteId}` : "/site-areas",
          label: sites.get(siteId) ?? "Zones",
        }}
        title={area.name}
        description={`${kw(field(area, "maximumPowerKw"))} disponibles · ${field(area, "numberOfPhases") === 1 ? "monophasé" : "triphasé"} ${field(area, "voltage") ?? 230} V`}
        meta={
          components.smart_charging && (
            <ToneBadge tone={smart ? "success" : "neutral"}>
              {smart ? "Recharge intelligente active" : "Sans recharge intelligente"}
            </ToneBadge>
          )
        }
        actions={
          admin && (
            <ConfirmButton
              title={`Supprimer ${area.name} ?`}
              description="Impossible tant que des sous-zones ou des équipements y sont rattachés."
              confirmLabel="Supprimer"
              onConfirm={() =>
                action.run("delete", () => deleteRecord("site-areas", area.id), {
                  success: "Zone supprimée.",
                  onDone: () => router.replace("/site-areas"),
                })
              }
            >
              Supprimer
            </ConfirmButton>
          )
        }
      />
      <Tabs defaultValue="stations">
        <TabsList variant="line">
          <TabsTrigger value="stations">Bornes</TabsTrigger>
          {components.smart_charging && (
            <TabsTrigger value="smart">Recharge intelligente</TabsTrigger>
          )}
          <TabsTrigger value="consumption">Consommation</TabsTrigger>
          <TabsTrigger value="children">Sous-zones</TabsTrigger>
          {components.assets && <TabsTrigger value="assets">Équipements</TabsTrigger>}
          <TabsTrigger value="sessions">Sessions</TabsTrigger>
          {admin && <TabsTrigger value="properties">Propriétés</TabsTrigger>}
        </TabsList>
        <TabsContent value="stations" className="flex flex-col gap-4 pt-4">
          {operate && (
            <Field className="w-full sm:w-80">
              <FieldLabel htmlFor="area-station">Rattacher une borne</FieldLabel>
              <EntityPicker
                id="area-station"
                source="stations"
                value={picker}
                placeholder="Rechercher une borne…"
                onChange={(stationId) => {
                  if (!stationId) {
                    return;
                  }
                  setPicker("");
                  void action.run("assign", () => assignStation(stationId, area.id), {
                    success: "Borne rattachée à la zone.",
                    onDone: refreshStations,
                  });
                }}
              />
            </Field>
          )}
          <StationsTable
            filters={{ siteAreaId: area.id }}
            revision={stationsRevision}
            onRemove={
              operate
                ? (station) =>
                    action.run("unassign", () => assignStation(station.id, null), {
                      success: "Borne retirée de la zone.",
                      onDone: refreshStations,
                    })
                : undefined
            }
          />
        </TabsContent>
        {components.smart_charging && (
          <TabsContent value="smart" className="pt-4">
            <SmartChargingPanel siteAreaId={area.id} canTrigger={operate} />
          </TabsContent>
        )}
        <TabsContent value="consumption" className="pt-4">
          <ConsumptionPanel
            path={`site-areas/${area.id}/consumptions`}
            title="Consommation de la zone"
            description="Puissance des bornes et des équipements, par tranches de cinq minutes, et limite appliquée."
          />
        </TabsContent>
        <TabsContent value="children" className="pt-4">
          <RecordList
            kind="site-areas"
            label="Sous-zones"
            revision={childRevision}
            filters={{ siteId }}
            filterRows={(record) => field(record, "parentSiteAreaId") === area.id}
            columns={siteAreaColumns(sites, components.smart_charging).filter(
              (column) => column.id !== "smart",
            )}
            onSelect={(record) => router.push(`/site-areas/${record.id}`)}
            emptyTitle="Aucune sous-zone"
            emptyDescription="Une sous-zone partage la puissance de cette zone avec ses propres bornes."
            toolbar={
              admin && (
                <Button variant="outline" onClick={() => setCreatingChild(true)}>
                  <IconPlus data-icon="inline-start" />
                  Ajouter une sous-zone
                </Button>
              )
            }
          />
        </TabsContent>
        {components.assets && (
          <TabsContent value="assets" className="pt-4">
            <RecordList
              kind="assets"
              label="Équipements de la zone"
              filters={{ siteAreaId: area.id }}
              columns={assetColumns(new Map())}
              onSelect={(record) => router.push(`/assets/${record.id}`)}
              emptyTitle="Aucun équipement"
              emptyDescription="Rattachez un compteur ou une production pour l’intégrer au calcul de puissance."
            />
          </TabsContent>
        )}
        <TabsContent value="sessions" className="pt-4">
          <SessionsList status={null} fixed={{ siteAreaId: area.id }} />
        </TabsContent>
        {admin && (
          <TabsContent value="properties" className="max-w-3xl pt-4">
            <SiteAreaForm key={area.revision} record={area} onSaved={refresh} />
          </TabsContent>
        )}
      </Tabs>
      <FormDialog
        open={creatingChild}
        onOpenChange={setCreatingChild}
        title="Ajouter une sous-zone"
        wide
      >
        <SiteAreaForm
          record={null}
          defaults={{ siteId, parentSiteAreaId: area.id }}
          onSaved={() => {
            setCreatingChild(false);
            refreshChildren();
          }}
        />
      </FormDialog>
    </>
  );
}
