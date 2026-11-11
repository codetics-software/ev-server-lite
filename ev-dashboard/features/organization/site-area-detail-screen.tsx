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
import { t } from "@/lib/i18n";

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
        <PageHeader title={t("Zone")} back={{ href: "/site-areas", label: t("Zones") }} />
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
          label: sites.get(siteId) ?? t("Zones"),
        }}
        title={area.name}
        description={t("{power} disponibles · {phases} {voltage} V", {
          power: kw(field(area, "maximumPowerKw")),
          phases: t(field(area, "numberOfPhases") === 1 ? "monophasé" : "triphasé"),
          voltage: String(field(area, "voltage") ?? 230),
        })}
        meta={
          components.smart_charging && (
            <ToneBadge tone={smart ? "success" : "neutral"}>
              {smart ? t("Recharge intelligente active") : t("Sans recharge intelligente")}
            </ToneBadge>
          )
        }
        actions={
          admin && (
            <ConfirmButton
              title={t("Supprimer {name} ?", { name: area.name })}
              description={t(
                "Impossible tant que des sous-zones ou des équipements y sont rattachés.",
              )}
              confirmLabel={t("Supprimer")}
              onConfirm={() =>
                action.run("delete", () => deleteRecord("site-areas", area.id), {
                  success: t("Zone supprimée."),
                  onDone: () => router.replace("/site-areas"),
                })
              }
            >
              {t("Supprimer")}
            </ConfirmButton>
          )
        }
      />
      <Tabs defaultValue="stations">
        <TabsList variant="line">
          <TabsTrigger value="stations">{t("Bornes")}</TabsTrigger>
          {components.smart_charging && (
            <TabsTrigger value="smart">{t("Recharge intelligente")}</TabsTrigger>
          )}
          <TabsTrigger value="consumption">{t("Consommation")}</TabsTrigger>
          <TabsTrigger value="children">{t("Sous-zones")}</TabsTrigger>
          {components.assets && <TabsTrigger value="assets">{t("Équipements")}</TabsTrigger>}
          <TabsTrigger value="sessions">{t("Sessions")}</TabsTrigger>
          {admin && <TabsTrigger value="properties">{t("Propriétés")}</TabsTrigger>}
        </TabsList>
        <TabsContent value="stations" className="flex flex-col gap-4 pt-4">
          {operate && (
            <Field className="w-full sm:w-80">
              <FieldLabel htmlFor="area-station">{t("Rattacher une borne")}</FieldLabel>
              <EntityPicker
                id="area-station"
                source="stations"
                value={picker}
                placeholder={t("Rechercher une borne…")}
                onChange={(stationId) => {
                  if (!stationId) {
                    return;
                  }
                  setPicker("");
                  void action.run("assign", () => assignStation(stationId, area.id), {
                    success: t("Borne rattachée à la zone."),
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
                      success: t("Borne retirée de la zone."),
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
            title={t("Consommation de la zone")}
            description={t(
              "Puissance des bornes et des équipements, par tranches de cinq minutes, et limite appliquée.",
            )}
          />
        </TabsContent>
        <TabsContent value="children" className="pt-4">
          <RecordList
            kind="site-areas"
            label={t("Sous-zones")}
            revision={childRevision}
            filters={{ siteId }}
            filterRows={(record) => field(record, "parentSiteAreaId") === area.id}
            columns={siteAreaColumns(sites, components.smart_charging).filter(
              (column) => column.id !== "smart",
            )}
            onSelect={(record) => router.push(`/site-areas/${record.id}`)}
            emptyTitle={t("Aucune sous-zone")}
            emptyDescription={t(
              "Une sous-zone partage la puissance de cette zone avec ses propres bornes.",
            )}
            toolbar={
              admin && (
                <Button variant="outline" onClick={() => setCreatingChild(true)}>
                  <IconPlus data-icon="inline-start" />
                  {t("Ajouter une sous-zone")}
                </Button>
              )
            }
          />
        </TabsContent>
        {components.assets && (
          <TabsContent value="assets" className="pt-4">
            <RecordList
              kind="assets"
              label={t("Équipements de la zone")}
              filters={{ siteAreaId: area.id }}
              columns={assetColumns(new Map())}
              onSelect={(record) => router.push(`/assets/${record.id}`)}
              emptyTitle={t("Aucun équipement")}
              emptyDescription={t(
                "Rattachez un compteur ou une production pour l’intégrer au calcul de puissance.",
              )}
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
        title={t("Ajouter une sous-zone")}
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
