import { Skeleton } from "@/components/ui/skeleton";
import type { Metadata } from "next";
import { Suspense } from "react";
import "./globals.css";
export const metadata: Metadata = {
  title: "Codetics Supervision",
  description: "Supervision des réseaux de recharge par Codetics.",
};
export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="fr" className="h-full antialiased font-sans">
      <body className="min-h-full flex flex-col">
        <Suspense fallback={<Skeleton className="m-8 h-96" />}>{children}</Suspense>
      </body>
    </html>
  );
}
