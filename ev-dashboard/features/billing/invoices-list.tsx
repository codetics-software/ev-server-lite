"use client";

import { buttonVariants } from "@/components/ui/button";
import { Button } from "@/components/ui/button";
import { DataTable, type Column } from "@/features/shared/data-table";
import { OptionSelect } from "@/features/shared/form-fields";
import { Pager } from "@/features/shared/pager";
import { EmptyState, ErrorState, ListState } from "@/features/shared/states";
import { StateBadge } from "@/features/shared/status-badge";
import { useAction } from "@/features/shared/use-action";
import { usePaged } from "@/features/shared/use-paged";
import { isAdmin, isStaff, useWorkspace } from "@/features/workspace/workspace-context";
import { api } from "@/lib/api";
import { date, display, moneyMinor } from "@/lib/format";
import { IconFileInvoice } from "@tabler/icons-react";
import Link from "next/link";
import { useState } from "react";
import type { Invoice } from "./billing-types";
import { InvoiceDownload } from "./invoice-download";

const statuses = ["DRAFT", "OPEN", "PAID", "UNCOLLECTIBLE", "VOID"];

export function InvoicesList({ userId }: { userId?: string }) {
  const user = useWorkspace();
  const [status, setStatus] = useState("all");
  const list = usePaged<Invoice>("invoices", { userId, status: status === "all" ? null : status });
  const action = useAction();
  const staff = isStaff(user);
  const columns: Column<Invoice>[] = [
    {
      id: "number",
      header: "Facture",
      sort: (invoice) => invoice.number ?? invoice.id,
      cell: (invoice) => <span className="font-medium">{invoice.number ?? "Brouillon"}</span>,
    },
    {
      id: "date",
      header: "Date",
      sort: (invoice) => invoice.created_at,
      cell: (invoice) => date(invoice.created_at),
    },
    ...(staff && !userId
      ? [
          {
            id: "user",
            header: "Utilisateur",
            sort: (invoice: Invoice) => invoice.user_name ?? "",
            cell: (invoice: Invoice) =>
              isAdmin(user) ? (
                <Link
                  className="hover:text-primary dark:hover:text-sidebar-primary"
                  href={`/users/${invoice.user_id}`}
                >
                  {display(invoice.user_name)}
                </Link>
              ) : (
                display(invoice.user_name)
              ),
          },
        ]
      : []),
    {
      id: "status",
      header: "État",
      sort: (invoice) => invoice.status,
      cell: (invoice) => (
        <span className="flex flex-col gap-1">
          <StateBadge state={invoice.status} />
          {invoice.last_error && (
            <span className="max-w-56 truncate text-xs text-destructive">{invoice.last_error}</span>
          )}
        </span>
      ),
    },
    {
      id: "sessions",
      header: "Sessions",
      align: "right",
      cell: (invoice) => invoice.session_ids?.length ?? 0,
    },
    {
      id: "amount",
      header: "Montant",
      align: "right",
      sort: (invoice) => invoice.amount_minor,
      cell: (invoice) => moneyMinor(invoice.amount_minor, invoice.currency),
    },
    {
      id: "actions",
      header: "Actions",
      srOnlyHeader: true,
      align: "right",
      cell: (invoice) => (
        <div className="flex justify-end gap-1">
          {invoice.status !== "DRAFT" && <InvoiceDownload id={invoice.id} />}
          {invoice.status === "OPEN" && invoice.hosted_url && !staff && (
            <a
              className={buttonVariants({ variant: "outline", size: "sm" })}
              href={invoice.hosted_url}
              target="_blank"
              rel="noreferrer"
            >
              Payer
            </a>
          )}
          {invoice.status === "OPEN" && isAdmin(user) && (
            <Button
              variant="outline"
              size="sm"
              disabled={action.busy}
              onClick={() =>
                action.run("pay", () => api(`invoices/${invoice.id}/pay`, { method: "POST" }), {
                  success: "Nouvelle tentative de paiement lancée.",
                  onDone: list.refresh,
                })
              }
            >
              Relancer le paiement
            </Button>
          )}
        </div>
      ),
    },
  ];
  return (
    <section className="flex flex-col gap-4" aria-label="Factures">
      <OptionSelect
        value={status}
        onChange={setStatus}
        label="Filtrer par état"
        className="w-full sm:w-52"
        options={[
          { value: "all", label: "Tous les états" },
          ...statuses.map((value) => ({ value, label: display(value) })),
        ]}
      />
      {list.error && list.data && <ErrorState error={list.error} onRetry={list.refresh} />}
      <ListState
        data={list.data}
        items={list.items}
        loading={list.loading}
        error={list.error}
        onRetry={list.refresh}
        empty={
          <EmptyState
            icon={<IconFileInvoice />}
            title="Aucune facture"
            description="Les factures des recharges apparaissent ici dès leur émission."
          />
        }
      >
        <DataTable
          label="Factures"
          rows={list.items}
          columns={columns}
          rowKey={(invoice) => invoice.id}
        />
      </ListState>
      <Pager
        offset={list.offset}
        count={list.items.length}
        total={list.total}
        pageSize={list.pageSize}
        loading={list.loading}
        onChange={list.setOffset}
      />
    </section>
  );
}
