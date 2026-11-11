"use client";

import { Button } from "@/components/ui/button";
import { t } from "@/lib/i18n";

export default function ErrorPage({ reset }: { reset: () => void }) {
  return (
    <main className="mx-auto flex min-h-64 max-w-lg flex-col justify-center gap-4 px-6">
      <h1 className="text-2xl font-semibold">{t("La page n’a pas pu s’afficher")}</h1>
      <p>{t("Réessayez dans quelques instants.")}</p>
      <Button onClick={reset}>{t("Réessayer")}</Button>
    </main>
  );
}
