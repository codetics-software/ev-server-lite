"use client";

import { Button } from "@/components/ui/button";
import { ConfirmButton } from "@/features/shared/confirm-button";
import { DataTable, type Column } from "@/features/shared/data-table";
import { useLabels } from "@/features/shared/entity-picker";
import { FormDialog } from "@/features/shared/form-dialog";
import { RecordList } from "@/features/shared/record-list";
import { deleteRecord, field } from "@/features/shared/records";
import { EmptyState, ListState } from "@/features/shared/states";
import { ToneBadge } from "@/features/shared/status-badge";
import { useAction } from "@/features/shared/use-action";
import { isAdmin, useWorkspace } from "@/features/workspace/workspace-context";
import { useApi, useRevision } from "@/hooks/use-api";
import { api } from "@/lib/api";
import type { RecordItem } from "@/lib/api-types";
import { display, kw, kwh } from "@/lib/format";
import { IconCar, IconPlus } from "@tabler/icons-react";
import { useState } from "react";
import { AutoChargeOffer } from "./autocharge-offer";
import { AutoChargePanel } from "./autocharge-panel";
import { CarForm } from "./car-forms";
import { t } from "@/lib/i18n";

function columns(users?: Map<string, string>): Column<RecordItem>[] {
  return [
    {
      id: "name",
      header: t("Véhicule"),
      sort: (record) => record.name,
      cell: (record) => (
        <span className="flex flex-col">
          <span className="flex items-center gap-2 font-medium">
            {record.name}
            {field(record, "default") === true && (
              <ToneBadge tone="progress">{t("Par défaut")}</ToneBadge>
            )}
          </span>
          <span className="text-xs text-muted-foreground">
            {[field(record, "make"), field(record, "model")].filter(Boolean).join(" ") ||
              t("Modèle non renseigné")}
          </span>
        </span>
      ),
    },
    ...(users
      ? [
          {
            id: "user",
            header: t("Conducteur"),
            cell: (record: RecordItem) =>
              field(record, "userId")
                ? (users.get(String(field(record, "userId"))) ?? t("Conducteur"))
                : "—",
          },
        ]
      : []),
    {
      id: "plate",
      header: t("Immatriculation"),
      cell: (record) => display(field(record, "licensePlate")),
    },
    {
      id: "battery",
      header: t("Batterie"),
      align: "right",
      cell: (record) => kwh(field(record, "batteryKwh"), 0),
    },
    {
      id: "power",
      header: "AC · DC",
      align: "right",
      cell: (record) =>
        `${kw(field(record, "maxAcPowerKw"))} · ${kw(field(record, "maxDcPowerKw"))}`,
    },
  ];
}

export function MyCars() {
  const [revision, refresh] = useRevision();
  const { data, loading, error } = useApi<RecordItem[] | { items: RecordItem[] }>(
    "account/cars",
    revision,
  );
  const items = Array.isArray(data) ? data : (data?.items ?? []);
  const [editing, setEditing] = useState<RecordItem | null | undefined>();
  const action = useAction();
  return (
    <section className="flex flex-col gap-4" aria-label={t("Mes véhicules")}>
      <AutoChargeOffer revision={revision} onEnrolled={refresh} />
      <Button className="w-fit" onClick={() => setEditing(null)}>
        <IconPlus data-icon="inline-start" />
        {t("Ajouter un véhicule")}
      </Button>
      <ListState
        data={data}
        items={items}
        loading={loading}
        error={error}
        onRetry={refresh}
        empty={
          <EmptyState
            icon={<IconCar />}
            title={t("Aucun véhicule")}
            description={t(
              "Facultatif : enregistrer votre véhicule améliore l’estimation de la recharge.",
            )}
          />
        }
      >
        <DataTable
          label={t("Mes véhicules")}
          rows={items}
          columns={columns()}
          rowKey={(record) => record.id}
          onRowClick={setEditing}
        />
      </ListState>
      <FormDialog
        open={editing !== undefined}
        onOpenChange={(open) => !open && setEditing(undefined)}
        title={editing ? t("Modifier {name}", { name: editing.name }) : t("Ajouter un véhicule")}
        wide
      >
        <CarForm
          record={editing ?? null}
          mine
          onSaved={() => {
            setEditing(undefined);
            refresh();
          }}
        />
        {editing && <AutoChargePanel car={editing} mine onChanged={refresh} />}
        {editing && (
          <div className="border-t pt-4">
            <ConfirmButton
              title={t("Supprimer ce véhicule ?")}
              description={t("Le véhicule est retiré de votre compte.")}
              confirmLabel={t("Supprimer")}
              onConfirm={() =>
                action.run(
                  "delete",
                  () => api(`account/cars/${editing.id}`, { method: "DELETE" }),
                  {
                    success: t("Véhicule supprimé."),
                    onDone: () => {
                      setEditing(undefined);
                      refresh();
                    },
                  },
                )
              }
            >
              {t("Supprimer le véhicule")}
            </ConfirmButton>
          </div>
        )}
      </FormDialog>
    </section>
  );
}

export function CarsList({ userId }: { userId?: string }) {
  const user = useWorkspace();
  const admin = isAdmin(user);
  const users = useLabels("users");
  const [revision, refresh] = useRevision();
  const [editing, setEditing] = useState<RecordItem | null | undefined>();
  const action = useAction();
  return (
    <>
      <RecordList
        kind="cars"
        label={t("Véhicules")}
        revision={revision}
        filters={{ userId }}
        columns={columns(userId ? undefined : users)}
        onSelect={admin ? setEditing : undefined}
        emptyIcon={<IconCar />}
        emptyTitle={t("Aucun véhicule")}
        emptyDescription={t(
          "Les véhicules sont facultatifs : leur enregistrement n’est pas nécessaire pour recharger.",
        )}
        toolbar={
          admin && (
            <Button variant="outline" onClick={() => setEditing(null)}>
              <IconPlus data-icon="inline-start" />
              {t("Ajouter un véhicule")}
            </Button>
          )
        }
      />
      <FormDialog
        open={editing !== undefined}
        onOpenChange={(open) => !open && setEditing(undefined)}
        title={editing ? t("Modifier {name}", { name: editing.name }) : t("Ajouter un véhicule")}
        wide
      >
        <CarForm
          record={editing ?? null}
          mine={false}
          userId={userId}
          onSaved={() => {
            setEditing(undefined);
            refresh();
          }}
        />
        {editing && <AutoChargePanel car={editing} mine={false} />}
        {editing && (
          <div className="border-t pt-4">
            <ConfirmButton
              title={t("Supprimer ce véhicule ?")}
              description={t("Le véhicule est supprimé définitivement.")}
              confirmLabel={t("Supprimer")}
              onConfirm={() =>
                action.run("delete", () => deleteRecord("cars", editing.id), {
                  success: t("Véhicule supprimé."),
                  onDone: () => {
                    setEditing(undefined);
                    refresh();
                  },
                })
              }
            >
              {t("Supprimer le véhicule")}
            </ConfirmButton>
          </div>
        )}
      </FormDialog>
    </>
  );
}
