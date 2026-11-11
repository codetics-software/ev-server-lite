"use client";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import { Skeleton } from "@/components/ui/skeleton";
import { useWorkspace } from "@/features/workspace/workspace-context";
import { useApi } from "@/hooks/use-api";
import { useState } from "react";
import type { OperatorPayment } from "./payment-types";
import { PaymentsTable } from "./payments-table";
import { StripeSettings } from "./stripe-settings";
import { t } from "@/lib/i18n";

export function PaymentsScreen() {
  const user = useWorkspace();
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const { data, error, loading } = useApi<OperatorPayment[]>(`payments?page=${page}`, revision);
  return (
    <>
      <div className="page-heading">
        <div>
          <h1>{t("Paiements")}</h1>
          <p>{t("Crédits de recharge, montants facturés et remboursements.")}</p>
        </div>
        <Button
          variant="outline"
          disabled={loading}
          onClick={() => setRevision((value) => value + 1)}
        >
          {t("Actualiser")}
        </Button>
      </div>
      {user.role === "ADMIN" && <StripeSettings />}
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error.message}</AlertDescription>
        </Alert>
      )}
      {!data ? (
        <Skeleton className="h-48" />
      ) : data.length ? (
        <PaymentsTable payments={data} />
      ) : (
        <Empty className="border">
          <EmptyHeader>
            <EmptyTitle>{t("Aucun paiement")}</EmptyTitle>
            <EmptyDescription>
              {t("Les crédits payés depuis les portails publics apparaîtront ici.")}
            </EmptyDescription>
          </EmptyHeader>
        </Empty>
      )}
      <div className="flex items-center justify-between gap-3 text-sm">
        <span>{t("Page {page}", { page: page + 1 })}</span>
        <div className="flex gap-2">
          <Button
            variant="outline"
            disabled={!page || loading}
            onClick={() => setPage((value) => value - 1)}
          >
            {t("Précédent")}
          </Button>
          <Button
            variant="outline"
            disabled={data?.length !== 50 || loading}
            onClick={() => setPage((value) => value + 1)}
          >
            {t("Suivant")}
          </Button>
        </div>
      </div>
    </>
  );
}
