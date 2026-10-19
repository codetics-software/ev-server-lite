"use client";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { SidebarInset, SidebarProvider, SidebarTrigger } from "@/components/ui/sidebar";
import { Skeleton } from "@/components/ui/skeleton";
import { useApi } from "@/hooks/use-api";
import { ApiError } from "@/lib/api";
import type { User } from "@/lib/api-types";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { navigationFor } from "./navigation-access";
import { WorkspaceContext } from "./workspace-context";
import { WorkspaceSidebar } from "./workspace-sidebar";

export function WorkspaceShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { data: user, error, loading } = useApi<User>("auth/me");
  const section = pathname.split("/").filter(Boolean)[0] ?? "overview";
  const driverRedirect = user?.role === "DRIVER" && !["transactions", "account"].includes(section);

  useEffect(() => {
    if (error instanceof ApiError && error.status === 401) {
      router.replace("/auth/login");
    }
    if (driverRedirect) {
      router.replace("/transactions");
    }
  }, [error, driverRedirect, router]);

  if (loading || !user || driverRedirect) {
    return (
      <main className="mx-auto flex w-full max-w-5xl flex-col gap-6 p-8" aria-busy={loading}>
        {error ? (
          <Alert variant="destructive">
            <AlertTitle>Unable to open your workspace</AlertTitle>
            <AlertDescription>{error.message}</AlertDescription>
          </Alert>
        ) : (
          <>
            <Skeleton className="h-9 w-64" />
            <Skeleton className="h-64 w-full" />
          </>
        )}
      </main>
    );
  }

  const current = navigationFor(user).find((item) => item.id === section);

  return (
    <WorkspaceContext value={user}>
      <SidebarProvider style={{ "--sidebar-width": "14.5rem" } as React.CSSProperties}>
        <WorkspaceSidebar user={user} section={section} />
        <SidebarInset className="min-w-0">
          <header className="flex h-12 shrink-0 items-center justify-between gap-4 px-5 lg:px-7">
            <div className="flex min-w-0 items-center gap-3">
              <SidebarTrigger />
              <span className="truncate text-sm">{current?.label ?? "Access restricted"}</span>
            </div>
            <span className="hidden text-xs text-muted-foreground sm:block">
              Codetics · EV operations
            </span>
          </header>
          <main className="workspace-canvas flex min-w-0 flex-1 flex-col gap-6">
            {current ? (
              children
            ) : (
              <Alert>
                <AlertTitle>Access restricted</AlertTitle>
                <AlertDescription>Your role does not have access to this page.</AlertDescription>
              </Alert>
            )}
          </main>
          <footer className="flex flex-wrap items-center justify-between gap-2 px-6 py-3 text-xs text-muted-foreground">
            <span>Kevin Vavelin · Codetics</span>
            <a href="https://codetics.fr" target="_blank" rel="noreferrer">
              codetics.fr
            </a>
          </footer>
        </SidebarInset>
      </SidebarProvider>
    </WorkspaceContext>
  );
}
