import { getServerTranslator } from "@/lib/i18n/server";
import Link from "next/link";

export default async function NotFound() {
  const t = await getServerTranslator();
  return (
    <main className="mx-auto flex min-h-svh max-w-lg flex-col justify-center gap-4 px-6">
      <p className="text-sm text-muted-foreground">{t("Erreur 404")}</p>
      <h1 className="text-3xl font-semibold">{t("Page introuvable")}</h1>
      <p>{t("Cette page n’existe pas ou n’est plus disponible.")}</p>
      <Link
        href="/"
        className="text-sm text-primary underline underline-offset-4 dark:text-sidebar-primary"
      >
        {t("Retour à la supervision")}
      </Link>
    </main>
  );
}
