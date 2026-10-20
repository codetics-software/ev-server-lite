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
import { isAdmin, useWorkspace } from "@/features/workspace/workspace-context";
import { useRevision } from "@/hooks/use-api";
import type { RecordItem } from "@/lib/api-types";
import { IconMapPin, IconPlus } from "@tabler/icons-react";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { SiteForm } from "./organization-forms";

export function SitesScreen() {
  const user = useWorkspace();
  const router = useRouter();
  const params = useSearchParams();
  const [companyId, setCompanyId] = useState(params.get("companyId") ?? "");
  const [revision, refresh] = useRevision();
  const [creating, setCreating] = useState(false);
  const companies = useLabels("companies");
  const columns: Column<RecordItem>[] = [
    {
      id: "name",
      header: "Site",
      sort: (record) => record.name,
      cell: (record) => (
        <span className="flex flex-col">
          <span className="font-medium">{record.name}</span>
          <span className="text-xs text-muted-foreground">
            {companies.get(String(field(record, "companyId"))) ?? "—"}
          </span>
        </span>
      ),
    },
    {
      id: "address",
      header: "Adresse",
      sort: (record) => String(field(record, "city") ?? ""),
      cell: (record) =>
        [field(record, "address"), field(record, "postalCode"), field(record, "city")]
          .filter(Boolean)
          .join(", ") || "—",
    },
    {
      id: "access",
      header: "Contrôle d’accès",
      cell: (record) => (
        <BooleanBadge value={field(record, "accessControl")} yes="Restreint" no="Ouvert" />
      ),
    },
    {
      id: "public",
      header: "Public",
      cell: (record) => <BooleanBadge value={field(record, "public")} />,
    },
  ];
  return (
    <>
      <PageHeader
        title="Sites"
        description="Les lieux qui accueillent vos bornes, leurs accès et leurs utilisateurs."
        actions={
          isAdmin(user) && (
            <Button onClick={() => setCreating(true)}>
              <IconPlus data-icon="inline-start" />
              Ajouter un site
            </Button>
          )
        }
      />
      <RecordList
        kind="sites"
        label="Sites"
        columns={columns}
        revision={revision}
        filters={{ companyId: companyId || undefined }}
        onSelect={(record) => router.push(`/sites/${record.id}`)}
        emptyIcon={<IconMapPin />}
        emptyTitle="Aucun site"
        emptyDescription="Ajoutez un site pour y regrouper vos zones et vos bornes."
        toolbar={
          <Field className="w-full sm:w-56">
            <FieldLabel htmlFor="sites-company" className="sr-only">
              Entreprise
            </FieldLabel>
            <EntityPicker
              id="sites-company"
              source="companies"
              value={companyId}
              onChange={setCompanyId}
              placeholder="Toutes les entreprises"
            />
          </Field>
        }
      />
      <FormDialog open={creating} onOpenChange={setCreating} title="Ajouter un site" wide>
        <SiteForm
          record={null}
          defaults={{ companyId }}
          onSaved={(id) => {
            setCreating(false);
            refresh();
            if (id) {
              router.push(`/sites/${id}`);
            }
          }}
        />
      </FormDialog>
    </>
  );
}
