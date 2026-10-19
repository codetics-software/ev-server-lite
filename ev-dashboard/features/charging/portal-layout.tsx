import { CodeticsMark } from "@/components/codetics-mark";
import Link from "next/link";
import type { ReactNode } from "react";

export function PortalLayout({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-svh bg-muted/40">
      <header className="mx-auto flex max-w-2xl items-center justify-between gap-4 px-5 py-6">
        <Link href="https://codetics.fr" className="flex items-center gap-2 font-semibold">
          <CodeticsMark />
          Codetics
        </Link>
        <span className="text-sm text-muted-foreground">Recharge sans compte</span>
      </header>
      <main className="mx-auto flex max-w-2xl flex-col gap-6 px-5 pb-8">{children}</main>
      <footer className="mx-auto max-w-2xl px-5 pb-6 text-xs text-muted-foreground">
        Supervision par Codetics · Paiement sécurisé par Stripe
      </footer>
    </div>
  );
}
