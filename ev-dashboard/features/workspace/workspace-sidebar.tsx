"use client";

import { CodeticsMark } from "@/components/codetics-mark";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar";
import { api } from "@/lib/api";
import type { User, Workspace } from "@/lib/api-types";
import { initials } from "@/lib/format";
import { roleLabels } from "@/lib/labels";
import { IconLogout } from "@tabler/icons-react";
import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { groups, sectionHref, type Section } from "./navigation";
import { homeFor, sectionLabel } from "./navigation-access";
import { workspaceLogo } from "./workspace-context";

export function WorkspaceSidebar({
  user,
  workspace,
  section,
  items,
  revision,
}: {
  user: User;
  workspace: Workspace | null;
  section: string;
  items: readonly Section[];
  revision: number;
}) {
  const router = useRouter();
  const sidebar = useSidebar();
  const [error, setError] = useState("");
  const [signingOut, setSigningOut] = useState(false);
  const logo = workspaceLogo(workspace, revision);
  const visibleGroups = groups.filter((group) => items.some((item) => item.group === group));

  async function signOut() {
    setSigningOut(true);
    setError("");
    try {
      await api("auth/signout", { method: "POST" });
      router.replace("/auth/login");
    } catch {
      setError("La déconnexion a échoué. Réessayez.");
      setSigningOut(false);
    }
  }

  return (
    <Sidebar variant="inset">
      <SidebarHeader className="px-4 pb-4 pt-5">
        <Link
          href={homeFor(user)}
          className="flex min-w-0 items-center gap-3"
          aria-label={workspace?.name ?? "Codetics Supervision"}
        >
          {logo ? (
            <Image
              src={logo}
              alt=""
              width={40}
              height={40}
              unoptimized
              className="size-9 shrink-0 rounded-md object-contain"
            />
          ) : (
            <CodeticsMark />
          )}
          <span className="min-w-0">
            <span className="block truncate text-base font-semibold tracking-tight">
              {logo && workspace ? workspace.name : "Codetics"}
            </span>
            <span className="block truncate text-xs text-sidebar-foreground/70">
              {logo ? "Supervision · Codetics" : (workspace?.name ?? "Supervision")}
            </span>
          </span>
        </Link>
      </SidebarHeader>
      <SidebarContent>
        {visibleGroups.map((group) => (
          <SidebarGroup key={group}>
            <SidebarGroupLabel>{group}</SidebarGroupLabel>
            <SidebarMenu>
              {items
                .filter((item) => item.group === group)
                .map((item) => (
                  <SidebarMenuItem key={item.id}>
                    <SidebarMenuButton
                      isActive={section === item.id}
                      onClick={() => sidebar.setOpenMobile(false)}
                      render={
                        <Link
                          href={sectionHref(item.id)}
                          aria-current={section === item.id ? "page" : undefined}
                        />
                      }
                    >
                      <item.icon stroke={1.5} />
                      <span>{sectionLabel(item, user)}</span>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                ))}
            </SidebarMenu>
          </SidebarGroup>
        ))}
      </SidebarContent>
      <SidebarFooter className="gap-3 p-4">
        {error && (
          <p role="alert" className="text-xs text-destructive">
            {error}
          </p>
        )}
        <div className="flex items-center gap-3">
          <Avatar>
            <AvatarFallback>{initials(user.name)}</AvatarFallback>
          </Avatar>
          <Link href="/account" className="min-w-0 flex-1">
            <span className="block truncate text-sm font-medium">{user.name}</span>
            <span className="block text-xs text-sidebar-foreground/70">
              {roleLabels[user.role]}
            </span>
          </Link>
          <Button
            variant="ghost"
            size="icon"
            aria-label="Se déconnecter"
            disabled={signingOut}
            onClick={signOut}
          >
            <IconLogout />
          </Button>
        </div>
      </SidebarFooter>
    </Sidebar>
  );
}
