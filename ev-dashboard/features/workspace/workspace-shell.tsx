"use client";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Separator } from "@/components/ui/separator";
import { SidebarInset, SidebarProvider, SidebarTrigger } from "@/components/ui/sidebar";
import { Skeleton } from "@/components/ui/skeleton";
import { ReportErrorButton } from "@/features/notifications/report-error-dialog";
import { useApi, useRevision } from "@/hooks/use-api";
import { ApiError } from "@/lib/api";
import type { User, Workspace } from "@/lib/api-types";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useMemo, type ReactNode } from "react";
import { homeFor, navigationFor, sectionLabel } from "./navigation-access";
import { sections } from "./navigation";
import { ThemeToggle } from "./theme-toggle";
import { resolveComponents, WorkspaceContext } from "./workspace-context";
import { WorkspaceSidebar } from "./workspace-sidebar";

export function WorkspaceShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const [revision, refreshWorkspace] = useRevision();
  const { data: user, error, loading } = useApi<User>("auth/me");
  const { data: workspace, loading: workspaceLoading } = useApi<Workspace>(
    user ? "workspace" : null,
    revision,
  );
  const section = pathname.split("/").filter(Boolean)[0] ?? "overview";
  const components = useMemo(() => resolveComponents(workspace), [workspace]);
  const visible = user ? navigationFor(user, components) : [];
  const current = visible.find((item) => item.id === section);
  const known = sections.some((item) => item.id === section);
  const redirect =
    user && !workspaceLoading && !current && section === "overview" && user.role === "DRIVER";

  useEffect(() => {
    if (error instanceof ApiError && error.status === 401) {
      router.replace("/auth/login");
    }
    if (error instanceof ApiError && error.type?.toLowerCase().includes("eula")) {
      router.replace("/auth/eula");
    }
    if (user?.eulaAccepted === false) {
      router.replace("/auth/eula");
    }
    if (redirect && user) {
      router.replace(homeFor(user));
    }
  }, [error, redirect, router, user]);

  if (
    loading ||
    !user ||
    redirect ||
    user.eulaAccepted === false ||
    (workspaceLoading && !workspace)
  ) {
    return (
      <main className="mx-auto flex w-full max-w-5xl flex-col gap-6 p-8" aria-busy={!error}>
        {error ? (
          <Alert variant="destructive">
            <AlertTitle>Impossible d’ouvrir votre espace</AlertTitle>
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

  return (
    <WorkspaceContext value={{ user, workspace, components, refreshWorkspace, revision }}>
      <SidebarProvider style={{ "--sidebar-width": "15rem" } as React.CSSProperties}>
        <WorkspaceSidebar
          user={user}
          workspace={workspace}
          section={section}
          items={visible}
          revision={revision}
        />
        <SidebarInset className="min-w-0">
          <header className="flex h-12 shrink-0 items-center justify-between gap-3 px-4 lg:px-6">
            <div className="flex min-w-0 items-center gap-2">
              <SidebarTrigger />
              <Separator orientation="vertical" className="h-4" />
              <span className="truncate text-sm">
                {current ? sectionLabel(current, user) : "Accès restreint"}
              </span>
            </div>
            <div className="flex items-center gap-1">
              <span className="mr-2 hidden text-xs text-muted-foreground lg:block">
                {workspace?.name ?? "Codetics · Supervision de recharge"}
              </span>
              <ReportErrorButton user={user} />
              <ThemeToggle />
            </div>
          </header>
          <main className="workspace-canvas flex min-w-0 flex-1 flex-col gap-6">
            {current ? (
              children
            ) : (
              <Alert>
                <AlertTitle>{known ? "Accès restreint" : "Page introuvable"}</AlertTitle>
                <AlertDescription>
                  {known
                    ? "Votre rôle ou la configuration de l’espace ne permet pas d’accéder à cette page."
                    : "Cette page n’existe pas ou n’est plus disponible."}
                </AlertDescription>
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
