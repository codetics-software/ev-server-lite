"use client";

import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { DataTable, type Column } from "@/features/shared/data-table";
import { useLabels } from "@/features/shared/entity-picker";
import { FormDialog } from "@/features/shared/form-dialog";
import { PageHeader } from "@/features/shared/page-header";
import { RecordList } from "@/features/shared/record-list";
import { field } from "@/features/shared/records";
import { EmptyState, ListState } from "@/features/shared/states";
import { ErrorBadges } from "@/features/shared/status-badge";
import { isAdmin, useWorkspace } from "@/features/workspace/workspace-context";
import { useApi, useRevision } from "@/hooks/use-api";
import type { RecordItem, Row } from "@/lib/api-types";
import { display, kw } from "@/lib/format";
import { IconCircleCheck, IconPlus, IconSolarPanel } from "@tabler/icons-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { AssetForm, assetKinds, assetTypes, connectionTypes } from "./asset-form";
import { t } from "@/lib/i18n";

const label = (options: { value: string; label: string }[], value: unknown) =>
  options.find((option) => option.value === value)?.label ?? display(value);

export function assetColumns(areas: Map<string, string>): Column<RecordItem>[] {
  return [
    {
      id: "name",
      header: t("Équipement"),
      sort: (record) => record.name,
      cell: (record) => (
        <span className="flex flex-col">
          <span className="font-medium">{record.name}</span>
          <span className="text-xs text-muted-foreground">
            {label(assetKinds, field(record, "kind"))} · {label(assetTypes, field(record, "type"))}
          </span>
        </span>
      ),
    },
    {
      id: "area",
      header: t("Zone"),
      cell: (record) =>
        field(record, "siteAreaId")
          ? (areas.get(String(field(record, "siteAreaId"))) ?? t("Zone rattachée"))
          : t("Sans zone"),
    },
    {
      id: "power",
      header: t("Puissance"),
      align: "right",
      sort: (record) => Number(field(record, "maximumPowerKw") ?? 0),
      cell: (record) => kw(field(record, "maximumPowerKw")),
    },
    {
      id: "connection",
      header: t("Mesures"),
      cell: (record) =>
        label(connectionTypes, (field<Row>(record, "connection") ?? {}).type ?? "NONE"),
    },
  ];
}

export function AssetsScreen() {
  const user = useWorkspace();
  const router = useRouter();
  const [revision, refresh] = useRevision();
  const [creating, setCreating] = useState(false);
  const areas = useLabels("site-areas");
  const inError = useApi<{ asset: RecordItem; errors: string[] }[]>("assets/in-error", revision);
  const errorRows = (inError.data ?? []).map((item) => ({ ...item.asset, errors: item.errors }));
  return (
    <>
      <PageHeader
        title={t("Équipements")}
        description={t(
          "Compteurs, productions et batteries intégrés au calcul de puissance des zones.",
        )}
        actions={
          isAdmin(user) && (
            <Button onClick={() => setCreating(true)}>
              <IconPlus data-icon="inline-start" />
              {t("Ajouter un équipement")}
            </Button>
          )
        }
      />
      <Tabs defaultValue="all">
        <TabsList variant="line">
          <TabsTrigger value="all">{t("Tous les équipements")}</TabsTrigger>
          <TabsTrigger value="errors">{t("En erreur")}</TabsTrigger>
        </TabsList>
        <TabsContent value="all" className="pt-4">
          <RecordList
            kind="assets"
            label={t("Équipements")}
            revision={revision}
            columns={assetColumns(areas)}
            onSelect={(record) => router.push(`/assets/${record.id}`)}
            emptyIcon={<IconSolarPanel />}
            emptyTitle={t("Aucun équipement")}
            emptyDescription={t(
              "Ajoutez un compteur ou une production solaire pour piloter la puissance de vos zones.",
            )}
          />
        </TabsContent>
        <TabsContent value="errors" className="pt-4">
          <ListState
            data={inError.data}
            items={errorRows}
            loading={inError.loading}
            error={inError.error}
            empty={
              <EmptyState
                icon={<IconCircleCheck />}
                title={t("Aucun équipement en erreur")}
                description={t("Tous les équipements transmettent leurs mesures.")}
              />
            }
          >
            <DataTable
              label={t("Équipements en erreur")}
              rows={errorRows}
              rowKey={(record) => record.id}
              onRowClick={(record) => router.push(`/assets/${record.id}`)}
              columns={[
                ...assetColumns(areas).slice(0, 2),
                {
                  id: "errors",
                  header: t("Anomalies"),
                  cell: (record) => <ErrorBadges errors={record.errors} />,
                },
              ]}
            />
          </ListState>
        </TabsContent>
      </Tabs>
      <FormDialog
        open={creating}
        onOpenChange={setCreating}
        title={t("Ajouter un équipement")}
        wide
      >
        <AssetForm
          record={null}
          onSaved={(id) => {
            setCreating(false);
            refresh();
            if (id) {
              router.push(`/assets/${id}`);
            }
          }}
        />
      </FormDialog>
    </>
  );
}
