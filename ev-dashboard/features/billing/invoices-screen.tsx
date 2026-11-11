"use client";

import { PageHeader } from "@/features/shared/page-header";
import { isStaff, useWorkspace } from "@/features/workspace/workspace-context";
import { InvoicesList } from "./invoices-list";
import { t } from "@/lib/i18n";

export function InvoicesScreen() {
  const user = useWorkspace();
  return (
    <>
      <PageHeader
        title={isStaff(user) ? t("Factures") : t("Mes factures")}
        description={
          isStaff(user)
            ? t("Factures émises aux utilisateurs pour leurs recharges.")
            : t("Vos factures de recharge, à télécharger au format PDF.")
        }
      />
      <InvoicesList userId={isStaff(user) ? undefined : user.id} />
    </>
  );
}
