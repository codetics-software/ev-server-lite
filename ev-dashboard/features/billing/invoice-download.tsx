"use client";

import { Button } from "@/components/ui/button";
import { notifyError } from "@/features/shared/use-action";
import { api, errorMessage } from "@/lib/api";
import { IconDownload } from "@tabler/icons-react";
import { useState, type ComponentProps } from "react";

export function InvoiceDownload({
  id,
  label = "PDF",
  variant = "ghost",
}: {
  id: string;
  label?: string;
  variant?: ComponentProps<typeof Button>["variant"];
}) {
  const [pending, setPending] = useState(false);
  async function open() {
    const target = window.open("", "_blank", "noopener");
    setPending(true);
    try {
      const { url } = await api<{ url: string }>(`invoices/${id}/download`);
      if (target) {
        target.location.href = url;
      } else {
        window.location.assign(url);
      }
    } catch (error) {
      target?.close();
      notifyError(
        "La facture n’a pas pu être ouverte",
        errorMessage(error, "Réessayez plus tard."),
      );
    } finally {
      setPending(false);
    }
  }
  return (
    <Button variant={variant} size="sm" onClick={open} disabled={pending}>
      <IconDownload data-icon="inline-start" />
      {label}
    </Button>
  );
}
