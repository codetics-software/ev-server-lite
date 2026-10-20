"use client";

import { CodeticsMark } from "@/components/codetics-mark";
import { ThemeToggle } from "@/features/workspace/theme-toggle";
import Image from "next/image";
import Link from "next/link";
import { useState, type ReactNode } from "react";

export function PortalLayout({ children, workspace }: { children: ReactNode; workspace?: string }) {
  const [failed, setFailed] = useState(false);
  return (
    <div className="min-h-svh bg-muted/40">
      <header className="mx-auto flex max-w-2xl items-center justify-between gap-4 px-5 py-6">
        {workspace && !failed ? (
          <Image
            src={`/api/backend/api/v1/public/workspaces/${encodeURIComponent(workspace)}/logo`}
            alt="Logo de l’exploitant"
            width={120}
            height={40}
            unoptimized
            className="h-10 w-auto max-w-40 object-contain"
            onError={() => setFailed(true)}
          />
        ) : (
          <Link href="https://codetics.fr" className="flex items-center gap-2 font-semibold">
            <CodeticsMark />
            Codetics
          </Link>
        )}
        <div className="flex items-center gap-2">
          <span className="text-sm text-muted-foreground">Recharge sans compte</span>
          <ThemeToggle />
        </div>
      </header>
      <main className="mx-auto flex max-w-2xl flex-col gap-6 px-5 pb-8">{children}</main>
      <footer className="mx-auto max-w-2xl px-5 pb-6 text-xs text-muted-foreground">
        Supervision par Codetics · Paiement sécurisé par Stripe
      </footer>
    </div>
  );
}
