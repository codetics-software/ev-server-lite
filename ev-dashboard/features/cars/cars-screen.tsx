"use client";

import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type { Column } from "@/features/shared/data-table";
import { FormDialog } from "@/features/shared/form-dialog";
import { PageHeader } from "@/features/shared/page-header";
import { RecordList } from "@/features/shared/record-list";
import { field } from "@/features/shared/records";
import { useAction } from "@/features/shared/use-action";
import { isAdmin, isStaff, useWorkspace } from "@/features/workspace/workspace-context";
import { useRevision } from "@/hooks/use-api";
import { api } from "@/lib/api";
import type { RecordItem } from "@/lib/api-types";
import { kw, kwh, number } from "@/lib/format";
import { t, translated } from "@/lib/i18n";
import { IconBook, IconPlus, IconRefresh } from "@tabler/icons-react";
import { useState } from "react";
import { CatalogForm } from "./car-forms";
import { CarsList, MyCars } from "./cars-list";

const catalogColumns: Column<RecordItem>[] = translated([
  {
    id: "model",
    header: "Modèle",
    sort: (record) => record.name,
    cell: (record) => (
      <span className="flex flex-col">
        <span className="font-medium">
          {String(field(record, "make") ?? "")} {String(field(record, "model") ?? "")}
        </span>
        <span className="text-xs text-muted-foreground">
          {String(field(record, "version") ?? "")}
        </span>
      </span>
    ),
  },
  {
    id: "battery",
    header: "Batterie",
    align: "right",
    sort: (record) => Number(field(record, "batteryKwh") ?? 0),
    cell: (record) => kwh(field(record, "batteryKwh"), 0),
  },
  { id: "ac", header: "AC", align: "right", cell: (record) => kw(field(record, "maxAcPowerKw")) },
  { id: "dc", header: "DC", align: "right", cell: (record) => kw(field(record, "maxDcPowerKw")) },
  {
    id: "range",
    header: "Autonomie",
    align: "right",
    sort: (record) => Number(field(record, "rangeKm") ?? 0),
    cell: (record) => (field(record, "rangeKm") ? `${number(field(record, "rangeKm"))} km` : "—"),
  },
]);

function Catalog() {
  const user = useWorkspace();
  const admin = isAdmin(user);
  const [revision, refresh] = useRevision();
  const [editing, setEditing] = useState<RecordItem | null | undefined>();
  const action = useAction();
  return (
    <>
      <RecordList
        kind="car-catalog"
        label={t("Catalogue")}
        revision={revision}
        columns={catalogColumns}
        onSelect={admin ? setEditing : undefined}
        emptyIcon={<IconBook />}
        emptyTitle={t("Catalogue vide")}
        emptyDescription={t(
          "Synchronisez le catalogue depuis la base de véhicules ou ajoutez des modèles.",
        )}
        toolbar={
          admin && (
            <>
              <Button
                variant="outline"
                disabled={action.busy}
                onClick={() =>
                  action.run(
                    "sync",
                    () =>
                      api<{
                        created: number;
                        updated: number;
                        skipped?: number;
                        errors?: unknown[];
                      }>("car-catalog/synchronize", {
                        method: "POST",
                      }),
                    {
                      success: (result) =>
                        result?.errors?.length
                          ? t(
                              "Catalogue synchronisé : {created} ajoutés, {updated} mis à jour, {skipped} ignorés, {errors} erreurs.",
                              {
                                created: result.created ?? 0,
                                updated: result.updated ?? 0,
                                skipped: result.skipped ?? 0,
                                errors: result.errors.length,
                              },
                            )
                          : t(
                              "Catalogue synchronisé : {created} ajoutés, {updated} mis à jour, {skipped} ignorés.",
                              {
                                created: result?.created ?? 0,
                                updated: result?.updated ?? 0,
                                skipped: result?.skipped ?? 0,
                              },
                            ),
                      failure: t("La synchronisation a échoué"),
                      onDone: refresh,
                    },
                  )
                }
              >
                <IconRefresh data-icon="inline-start" />
                {action.pending === "sync" ? t("Synchronisation…") : t("Synchroniser")}
              </Button>
              <Button variant="outline" onClick={() => setEditing(null)}>
                <IconPlus data-icon="inline-start" />
                {t("Ajouter un modèle de véhicule")}
              </Button>
            </>
          )
        }
      />
      <FormDialog
        open={editing !== undefined}
        onOpenChange={(open) => !open && setEditing(undefined)}
        title={editing ? editing.name : t("Ajouter un modèle de véhicule")}
        wide
      >
        <CatalogForm
          record={editing ?? null}
          onSaved={() => {
            setEditing(undefined);
            refresh();
          }}
        />
      </FormDialog>
    </>
  );
}

export function CarsScreen() {
  const user = useWorkspace();
  if (!isStaff(user)) {
    return (
      <>
        <PageHeader
          title={t("Mes véhicules")}
          description={t("Vos véhicules électriques. Leur enregistrement est facultatif.")}
        />
        <MyCars />
      </>
    );
  }
  return (
    <>
      <PageHeader
        title={t("Véhicules")}
        description={t("Véhicules des conducteurs et catalogue des modèles électriques.")}
      />
      <Tabs defaultValue="cars">
        <TabsList variant="line">
          <TabsTrigger value="cars">{t("Véhicules")}</TabsTrigger>
          <TabsTrigger value="catalog">{t("Catalogue")}</TabsTrigger>
          <TabsTrigger value="mine">{t("Mes véhicules")}</TabsTrigger>
        </TabsList>
        <TabsContent value="cars" className="pt-4">
          <CarsList />
        </TabsContent>
        <TabsContent value="catalog" className="pt-4">
          <Catalog />
        </TabsContent>
        <TabsContent value="mine" className="pt-4">
          <MyCars />
        </TabsContent>
      </Tabs>
    </>
  );
}
