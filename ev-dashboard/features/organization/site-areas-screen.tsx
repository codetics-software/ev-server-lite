"use client";

import { Button } from "@/components/ui/button";
import { Field, FieldLabel } from "@/components/ui/field";
import type { Column } from "@/features/shared/data-table";
import { EntityPicker, useLabels } from "@/features/shared/entity-picker";
import { FormDialog } from "@/features/shared/form-dialog";
import { PageHeader } from "@/features/shared/page-header";
import { RecordList } from "@/features/shared/record-list";
import { field } from "@/features/shared/records";
import { BooleanBadge } from "@/features/shared/status-badge";
import { isAdmin, useWorkspaceSession } from "@/features/workspace/workspace-context";
import { useRevision } from "@/hooks/use-api";
import type { RecordItem } from "@/lib/api-types";
import { kw } from "@/lib/format";
import { IconPlus, IconSitemap } from "@tabler/icons-react";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { SiteAreaForm } from "./organization-forms";
import { t } from "@/lib/i18n";

export function siteAreaColumns(
  sites: Map<string, string>,
  smartCharging: boolean,
): Column<RecordItem>[] {
  return [
    {
      id: "name",
      header: t("Zone"),
      sort: (record) => record.name,
      cell: (record) => (
        <span className="flex flex-col">
          <span className="font-medium">{record.name}</span>
          <span className="text-xs text-muted-foreground">
            {sites.get(String(field(record, "siteId"))) ?? "—"}
          </span>
        </span>
      ),
    },
    {
      id: "power",
      header: t("Puissance disponible"),
      align: "right",
      sort: (record) => Number(field(record, "maximumPowerKw") ?? 0),
      cell: (record) => kw(field(record, "maximumPowerKw")),
    },
    {
      id: "phases",
      header: t("Raccordement"),
      cell: (record) =>
        `${t(field(record, "numberOfPhases") === 1 ? "Monophasé" : "Triphasé")} · ${field(record, "voltage") ?? 230} V`,
    },
    ...(smartCharging
      ? [
          {
            id: "smart",
            header: t("Recharge intelligente"),
            cell: (record: RecordItem) => (
              <BooleanBadge
                value={field(record, "smartCharging")}
                yes={t("Active")}
                no={t("Inactive")}
              />
            ),
          },
        ]
      : []),
  ];
}

export function SiteAreasScreen() {
  const { user, components } = useWorkspaceSession();
  const router = useRouter();
  const params = useSearchParams();
  const [siteId, setSiteId] = useState(params.get("siteId") ?? "");
  const [revision, refresh] = useRevision();
  const [creating, setCreating] = useState(false);
  const sites = useLabels("sites");
  return (
    <>
      <PageHeader
        title={t("Zones de recharge")}
        description={t("Regroupez les bornes par raccordement électrique et puissance disponible.")}
        actions={
          isAdmin(user) && (
            <Button onClick={() => setCreating(true)}>
              <IconPlus data-icon="inline-start" />
              {t("Ajouter une zone")}
            </Button>
          )
        }
      />
      <RecordList
        kind="site-areas"
        label={t("Zones")}
        columns={siteAreaColumns(sites, components.smart_charging)}
        revision={revision}
        filters={{ siteId: siteId || undefined }}
        onSelect={(record) => router.push(`/site-areas/${record.id}`)}
        emptyIcon={<IconSitemap />}
        emptyTitle={t("Aucune zone")}
        emptyDescription={t("Créez une zone dans un site, puis rattachez-y vos bornes.")}
        toolbar={
          <Field className="w-full sm:w-56">
            <FieldLabel htmlFor="areas-site" className="sr-only">
              {t("Site")}
            </FieldLabel>
            <EntityPicker
              id="areas-site"
              source="sites"
              value={siteId}
              onChange={setSiteId}
              placeholder={t("Tous les sites")}
            />
          </Field>
        }
      />
      <FormDialog open={creating} onOpenChange={setCreating} title={t("Ajouter une zone")} wide>
        <SiteAreaForm
          record={null}
          defaults={{ siteId }}
          onSaved={(id) => {
            setCreating(false);
            refresh();
            if (id) {
              router.push(`/site-areas/${id}`);
            }
          }}
        />
      </FormDialog>
    </>
  );
}
