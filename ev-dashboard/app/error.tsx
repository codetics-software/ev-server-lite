"use client";

import { Button } from "@/components/ui/button";

export default function ErrorPage({ reset }: { reset: () => void }) {
  return (
    <main className="mx-auto flex min-h-64 max-w-lg flex-col justify-center gap-4 px-6">
      <h1 className="text-2xl font-semibold">La page n’a pas pu s’afficher</h1>
      <p>Réessayez dans quelques instants.</p>
      <Button onClick={reset}>Réessayer</Button>
    </main>
  );
}
