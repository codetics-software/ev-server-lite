"use client";

import { ExportButton, ImportButton } from "@/features/shared/csv-actions";
import { PageHeader } from "@/features/shared/page-header";
import { isAdmin, useWorkspace } from "@/features/workspace/workspace-context";
import { useRevision } from "@/hooks/use-api";
import { BadgesList } from "./badges-list";
import { t } from "@/lib/i18n";

export function BadgesScreen() {
  const user = useWorkspace();
  const [revision, refresh] = useRevision();
  return (
    <>
      <PageHeader
        title={t("Badges")}
        description={t("Badges RFID autorisés à recharger et utilisateurs associés.")}
      />
      <BadgesList
        revision={revision}
        toolbar={
          <>
            {isAdmin(user) && (
              <ImportButton
                path="tokens/import"
                title={t("Importer des badges")}
                columns="uid;visualId;description;userEmail"
                onImported={refresh}
              />
            )}
            <ExportButton path="tokens/export" filename="badges" />
          </>
        }
      />
    </>
  );
}
