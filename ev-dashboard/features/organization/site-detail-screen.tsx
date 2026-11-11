"use client";

import { Button, buttonVariants } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { SessionsList } from "@/features/sessions/sessions-list";
import { ConfirmButton } from "@/features/shared/confirm-button";
import { useLabels } from "@/features/shared/entity-picker";
import { FormDialog } from "@/features/shared/form-dialog";
import { PageHeader } from "@/features/shared/page-header";
import { RecordList } from "@/features/shared/record-list";
import { deleteRecord, field } from "@/features/shared/records";
import { ErrorState, LoadingState } from "@/features/shared/states";
import { ToneBadge } from "@/features/shared/status-badge";
import { useAction } from "@/features/shared/use-action";
import { StationsTable } from "@/features/stations/stations-table";
import { isAdmin, useWorkspaceSession } from "@/features/workspace/workspace-context";
import { useRevision } from "@/hooks/use-api";
import { IconMap, IconPlus } from "@tabler/icons-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { SiteAreaForm, SiteForm } from "./organization-forms";
import { siteAreaColumns } from "./site-areas-screen";
import { SiteUsers } from "./site-users";
import { useRecord } from "./use-record";
import { t } from "@/lib/i18n";

export function SiteDetailScreen({ id }: { id: string }) {
  const { user, components } = useWorkspaceSession();
  const router = useRouter();
  const admin = isAdmin(user);
  const { data: site, error, loading, refresh } = useRecord("sites", id);
  const [areasRevision, refreshAreas] = useRevision();
  const [creatingArea, setCreatingArea] = useState(false);
  const companies = useLabels("companies");
  const sites = useLabels("sites");
  const action = useAction();

  if (error && !site) {
    return (
      <>
        <PageHeader title={t("Site")} back={{ href: "/sites", label: t("Sites") }} />
        <ErrorState error={error} onRetry={refresh} />
      </>
    );
  }
  if (loading && !site) {
    return <LoadingState />;
  }
  if (!site) {
    return null;
  }
  const latitude = field<number>(site, "latitude");
  const longitude = field<number>(site, "longitude");
  const address = [field(site, "address"), field(site, "postalCode"), field(site, "city")]
    .filter(Boolean)
    .join(", ");

  return (
    <>
      <PageHeader
        back={{ href: "/sites", label: t("Sites") }}
        title={site.name}
        description={[companies.get(String(field(site, "companyId"))), address]
          .filter(Boolean)
          .join(" · ")}
        meta={
          <>
            <ToneBadge tone={field(site, "accessControl") ? "warning" : "success"}>
              {field(site, "accessControl") ? t("Accès restreint") : t("Accès ouvert")}
            </ToneBadge>
            {field(site, "public") === true && <ToneBadge tone="progress">{t("Public")}</ToneBadge>}
          </>
        }
        actions={
          <>
            {latitude != null && longitude != null && (
              <a
                className={buttonVariants({ variant: "outline" })}
                href={`https://www.openstreetmap.org/?mlat=${latitude}&mlon=${longitude}#map=17/${latitude}/${longitude}`}
                target="_blank"
                rel="noreferrer"
              >
                <IconMap data-icon="inline-start" />
                {t("Voir sur la carte")}
              </a>
            )}
            {admin && (
              <ConfirmButton
                title={t("Supprimer {name} ?", { name: site.name })}
                description={t("Impossible tant que des zones sont rattachées à ce site.")}
                confirmLabel={t("Supprimer")}
                onConfirm={() =>
                  action.run("delete", () => deleteRecord("sites", site.id), {
                    success: t("Site supprimé."),
                    onDone: () => router.replace("/sites"),
                  })
                }
              >
                {t("Supprimer")}
              </ConfirmButton>
            )}
          </>
        }
      />
      <Tabs defaultValue="areas">
        <TabsList variant="line">
          <TabsTrigger value="areas">{t("Zones")}</TabsTrigger>
          <TabsTrigger value="stations">{t("Bornes")}</TabsTrigger>
          {admin && <TabsTrigger value="users">{t("Utilisateurs")}</TabsTrigger>}
          <TabsTrigger value="sessions">{t("Sessions")}</TabsTrigger>
          {admin && <TabsTrigger value="properties">{t("Propriétés")}</TabsTrigger>}
        </TabsList>
        <TabsContent value="areas" className="pt-4">
          <RecordList
            kind="site-areas"
            label={t("Zones du site")}
            filters={{ siteId: site.id }}
            revision={areasRevision}
            columns={siteAreaColumns(sites, components.smart_charging)}
            onSelect={(record) => router.push(`/site-areas/${record.id}`)}
            emptyTitle={t("Aucune zone")}
            emptyDescription={t("Créez une zone pour y rattacher les bornes de ce site.")}
            toolbar={
              admin && (
                <Button variant="outline" onClick={() => setCreatingArea(true)}>
                  <IconPlus data-icon="inline-start" />
                  {t("Ajouter une zone")}
                </Button>
              )
            }
          />
        </TabsContent>
        <TabsContent value="stations" className="pt-4">
          <StationsTable filters={{ siteId: site.id }} />
        </TabsContent>
        {admin && (
          <TabsContent value="users" className="pt-4">
            <SiteUsers siteId={site.id} canEdit={admin} />
          </TabsContent>
        )}
        <TabsContent value="sessions" className="pt-4">
          <SessionsList status={null} fixed={{ siteId: site.id }} />
        </TabsContent>
        {admin && (
          <TabsContent value="properties" className="max-w-3xl pt-4">
            <SiteForm key={site.revision} record={site} onSaved={refresh} />
          </TabsContent>
        )}
      </Tabs>
      <FormDialog
        open={creatingArea}
        onOpenChange={setCreatingArea}
        title={t("Ajouter une zone")}
        wide
      >
        <SiteAreaForm
          record={null}
          defaults={{ siteId: site.id }}
          onSaved={() => {
            setCreatingArea(false);
            refreshAreas();
          }}
        />
      </FormDialog>
    </>
  );
}
