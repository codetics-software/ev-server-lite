"use client";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import type { ReactNode } from "react";

export function FormFooter({
  error,
  submitting,
  label = "Enregistrer",
  extra,
}: {
  error?: string;
  submitting: boolean;
  label?: string;
  extra?: ReactNode;
}) {
  return (
    <>
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex gap-2">{extra}</div>
        <Button type="submit" disabled={submitting}>
          {submitting ? "Enregistrement…" : label}
        </Button>
      </div>
    </>
  );
}
