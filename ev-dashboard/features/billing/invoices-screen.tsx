"use client";

import { PageHeader } from "@/features/shared/page-header";
import { isStaff, useWorkspace } from "@/features/workspace/workspace-context";
import { InvoicesList } from "./invoices-list";

export function InvoicesScreen() {
  const user = useWorkspace();
  return (
    <>
      <PageHeader
        title={isStaff(user) ? "Factures" : "Mes factures"}
        description={
          isStaff(user)
            ? "Factures émises aux utilisateurs pour leurs recharges."
            : "Vos factures de recharge, à télécharger au format PDF."
        }
      />
      <InvoicesList userId={isStaff(user) ? undefined : user.id} />
    </>
  );
}
