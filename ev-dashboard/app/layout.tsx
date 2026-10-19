import { Skeleton } from "@/components/ui/skeleton";
import type { Metadata } from "next";
import { Suspense } from "react";
import "./globals.css";
export const metadata: Metadata = {
  title: "Codetics Supervision",
  description: "Charging network supervision by Codetics.",
};
export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className="h-full antialiased font-sans">
      <body className="min-h-full flex flex-col">
        <Suspense fallback={<Skeleton className="m-8 h-96" />}>{children}</Suspense>
      </body>
    </html>
  );
}
