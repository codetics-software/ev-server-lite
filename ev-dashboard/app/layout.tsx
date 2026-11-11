import { Skeleton } from "@/components/ui/skeleton";
import { Toaster } from "@/components/ui/toast";
import { TooltipProvider } from "@/components/ui/tooltip";
import { themeScript } from "@/hooks/use-theme";
import { I18nProvider } from "@/lib/i18n/provider";
import { getRequestLocale, getServerTranslator } from "@/lib/i18n/server";
import type { Metadata } from "next";
import { Suspense } from "react";
import "./globals.css";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getServerTranslator();
  return {
    title: "Codetics Supervision",
    description: t("Supervision des réseaux de recharge par Codetics."),
  };
}

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  const locale = await getRequestLocale();
  return (
    <html lang={locale} className="h-full antialiased font-sans" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeScript }} />
      </head>
      <body className="min-h-full flex flex-col">
        <I18nProvider locale={locale}>
          <Toaster>
            <TooltipProvider>
              <Suspense fallback={<Skeleton className="m-8 h-96" />}>{children}</Suspense>
            </TooltipProvider>
          </Toaster>
        </I18nProvider>
      </body>
    </html>
  );
}
