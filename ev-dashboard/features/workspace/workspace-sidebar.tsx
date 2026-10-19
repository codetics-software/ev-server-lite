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
import type { User } from "@/lib/api-types";
import { IconLogout } from "@tabler/icons-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { navigationFor, roleLabels } from "./navigation-access";

export function WorkspaceSidebar({ user, section }: { user: User; section: string }) {
  const router = useRouter();
  const sidebar = useSidebar();
  const [error, setError] = useState("");
  const [signingOut, setSigningOut] = useState(false);
  const visible = navigationFor(user);
  const groups = ["Espace client", "Exploitation", "Réseau", "Gestion", "Administration"].filter(
    (group) => visible.some((item) => item.group === group),
  );
  const initials = user.name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((word) => word[0])
    .join("")
    .toUpperCase();

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
      <SidebarHeader className="px-5 pb-5 pt-6">
        <Link
          href={user.role === "DRIVER" ? "/transactions" : "/"}
          className="flex items-center gap-3"
          aria-label="Codetics Supervision"
        >
          <CodeticsMark />
          <span>
            <span className="block text-base font-semibold tracking-tight">Codetics</span>
            <span className="block text-xs text-sidebar-foreground/70">Supervision</span>
          </span>
        </Link>
      </SidebarHeader>
      <SidebarContent>
        {groups.map((group) => (
          <SidebarGroup key={group}>
            <SidebarGroupLabel>{group}</SidebarGroupLabel>
            <SidebarMenu>
              {visible
                .filter((item) => item.group === group)
                .map((item) => (
                  <SidebarMenuItem key={item.id}>
                    <SidebarMenuButton
                      isActive={section === item.id}
                      onClick={() => sidebar.setOpenMobile(false)}
                      render={
                        <Link
                          href={item.id === "overview" ? "/" : `/${item.id}`}
                          aria-current={section === item.id ? "page" : undefined}
                        />
                      }
                    >
                      <item.icon stroke={1.5} />
                      <span>{item.label}</span>
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
            <AvatarFallback>{initials || "C"}</AvatarFallback>
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
