"use client";

import { Button } from "@/components/ui/button";
import { ConfirmButton } from "@/features/shared/confirm-button";
import type { Column } from "@/features/shared/data-table";
import { useLabels } from "@/features/shared/entity-picker";
import { FormDialog } from "@/features/shared/form-dialog";
import { PageHeader } from "@/features/shared/page-header";
import { RecordList } from "@/features/shared/record-list";
import { deleteRecord, field } from "@/features/shared/records";
import { ToneBadge } from "@/features/shared/status-badge";
import { useAction } from "@/features/shared/use-action";
import { isAdmin, useWorkspace } from "@/features/workspace/workspace-context";
import { useRevision } from "@/hooks/use-api";
import type { RecordItem, Row } from "@/lib/api-types";
import { day, money } from "@/lib/format";
import { IconPlus, IconReceipt } from "@tabler/icons-react";
import { useState } from "react";
import { days, PricingForm } from "./pricing-form";
import { PricingResolve } from "./pricing-resolve";

function prices(record: RecordItem) {
  const currency = field(record, "currency") ?? "EUR";
  return [
    field(record, "flatPrice") != null
      ? `${money(field(record, "flatPrice"), currency)} / session`
      : null,
    field(record, "energyPrice") != null
      ? `${money(field(record, "energyPrice"), currency, 4)} / kWh`
      : null,
    field(record, "timePrice") != null
      ? `${money(field(record, "timePrice"), currency)} / h`
      : null,
    field(record, "parkingPrice") != null
      ? `${money(field(record, "parkingPrice"), currency)} / h d’inactivité`
      : null,
  ].filter(Boolean);
}

function restrictionsText(record: RecordItem) {
  const restrictions = (field<Row>(record, "restrictions") ?? {}) as Row;
  const parts: string[] = [];
  const selected = restrictions.daysOfWeek as number[] | undefined;
  if (selected?.length) {
    parts.push(selected.map((value) => days[value - 1]?.label).join(" "));
  }
  if (restrictions.timeFrom) {
    parts.push(`${restrictions.timeFrom}–${restrictions.timeTo}`);
  }
  if (field(record, "validFrom") || field(record, "validTo")) {
    parts.push(`${day(field(record, "validFrom"))} → ${day(field(record, "validTo"))}`);
  }
  return parts.join(" · ") || "Toujours";
}

export function PricingScreen() {
  const user = useWorkspace();
  const admin = isAdmin(user);
  const [revision, refresh] = useRevision();
  const [editing, setEditing] = useState<RecordItem | null | undefined>();
  const action = useAction();
  const sites = useLabels("sites");
  const areas = useLabels("site-areas");

  function scopeText(record: RecordItem) {
    const scope = (field<Row>(record, "scope") ?? {}) as Row;
    if (scope.stationId) {
      return `Borne ${scope.stationId}${scope.connectorId ? ` · connecteur ${scope.connectorId}` : ""}`;
    }
    if (scope.siteAreaId) {
      return `Zone ${areas.get(String(scope.siteAreaId)) ?? ""}`;
    }
    if (scope.siteId) {
      return `Site ${sites.get(String(scope.siteId)) ?? ""}`;
    }
    return "Tout l’espace";
  }

  const columns: Column<RecordItem>[] = [
    {
      id: "name",
      header: "Tarif",
      sort: (record) => record.name,
      cell: (record) => (
        <span className="flex flex-col gap-1">
          <span className="flex items-center gap-2 font-medium">
            {record.name}
            {field(record, "active") === false && <ToneBadge tone="neutral">Inactif</ToneBadge>}
          </span>
          <span className="text-xs text-muted-foreground">
            {prices(record).join(" · ") || "Gratuit"}
          </span>
        </span>
      ),
    },
    { id: "scope", header: "Portée", cell: scopeText },
    { id: "restrictions", header: "Application", cell: restrictionsText },
    {
      id: "priority",
      header: "Priorité",
      align: "right",
      sort: (record) => Number(field(record, "priority") ?? 0),
      cell: (record) => String(field(record, "priority") ?? 0),
    },
  ];

  return (
    <>
      <PageHeader
        title="Tarifs"
        description="Définitions de prix par portée, période et conditions. Chaque session terminée est valorisée."
        actions={
          admin && (
            <Button onClick={() => setEditing(null)}>
              <IconPlus data-icon="inline-start" />
              Ajouter un tarif
            </Button>
          )
        }
      />
      <div className="grid items-start gap-6 2xl:grid-cols-[1fr_26rem]">
        <RecordList
          kind="pricing-definitions"
          label="Tarifs"
          revision={revision}
          columns={columns}
          onSelect={admin ? setEditing : undefined}
          emptyIcon={<IconReceipt />}
          emptyTitle="Aucun tarif"
          emptyDescription="Sans tarif, les sessions ne sont pas facturées."
        />
        <PricingResolve onOpen={(record) => admin && setEditing(record)} />
      </div>
      <FormDialog
        open={editing !== undefined}
        onOpenChange={(open) => !open && setEditing(undefined)}
        title={editing ? `Modifier ${editing.name}` : "Ajouter un tarif"}
        wide
      >
        <PricingForm
          record={editing ?? null}
          onSaved={() => {
            setEditing(undefined);
            refresh();
          }}
        />
        {editing && (
          <div className="border-t pt-4">
            <ConfirmButton
              title={`Supprimer ${editing.name} ?`}
              description="Les sessions déjà valorisées conservent leur prix."
              confirmLabel="Supprimer"
              onConfirm={() =>
                action.run("delete", () => deleteRecord("pricing-definitions", editing.id), {
                  success: "Tarif supprimé.",
                  onDone: () => {
                    setEditing(undefined);
                    refresh();
                  },
                })
              }
            >
              Supprimer le tarif
            </ConfirmButton>
          </div>
        )}
      </FormDialog>
    </>
  );
}
