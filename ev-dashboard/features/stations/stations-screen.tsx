"use client";

import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ExportButton } from "@/features/shared/csv-actions";
import { PageHeader } from "@/features/shared/page-header";
import { isAdmin, useWorkspace } from "@/features/workspace/workspace-context";
import { useRevision } from "@/hooks/use-api";
import { IconPlus, IconRefresh } from "@tabler/icons-react";
import { useState } from "react";
import { ProvisionDialog } from "./provision-dialog";
import { RegistrationTokens } from "./registration-tokens";
import { StationsInError } from "./stations-in-error";
import { StationsList } from "./stations-list";
import { t } from "@/lib/i18n";

export function StationsScreen() {
  const user = useWorkspace();
  const [revision, refresh] = useRevision();
  const [provisioning, setProvisioning] = useState(false);

  return (
    <>
      <PageHeader
        title={t("Bornes de recharge")}
        description={t(
          "Connexion des bornes, état des connecteurs en temps réel et commandes à distance.",
        )}
        actions={
          <>
            <Button variant="outline" size="icon" aria-label={t("Actualiser")} onClick={refresh}>
              <IconRefresh />
            </Button>
            <ExportButton path="stations/export" filename="bornes" />
            {isAdmin(user) && (
              <Button onClick={() => setProvisioning(true)}>
                <IconPlus data-icon="inline-start" />
                {t("Ajouter une borne")}
              </Button>
            )}
          </>
        }
      />
      <Tabs defaultValue="all">
        <TabsList variant="line">
          <TabsTrigger value="all">{t("Toutes les bornes")}</TabsTrigger>
          <TabsTrigger value="errors">{t("En erreur")}</TabsTrigger>
          <TabsTrigger value="tokens">{t("Jetons d’enregistrement")}</TabsTrigger>
        </TabsList>
        <TabsContent value="all" className="pt-4">
          <StationsList revision={revision} />
        </TabsContent>
        <TabsContent value="errors" className="pt-4">
          <StationsInError revision={revision} />
        </TabsContent>
        <TabsContent value="tokens" className="pt-4">
          <RegistrationTokens />
        </TabsContent>
      </Tabs>
      <ProvisionDialog open={provisioning} onOpenChange={setProvisioning} onCreated={refresh} />
    </>
  );
}
