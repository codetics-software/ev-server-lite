import Link from "next/link";

export default function NotFound() {
  return (
    <main className="mx-auto flex min-h-svh max-w-lg flex-col justify-center gap-4 px-6">
      <p className="text-sm text-muted-foreground">Erreur 404</p>
      <h1 className="text-3xl font-semibold">Page introuvable</h1>
      <p>Cette page n’existe pas ou n’est plus disponible.</p>
      <Link href="/" className="text-sm text-primary underline underline-offset-4">
        Retour à la supervision
      </Link>
    </main>
  );
}
