"use client";

import type { ComponentKey, Components, User, Workspace } from "@/lib/api-types";
import { componentKeys } from "@/lib/api-types";
import { createContext, useContext } from "react";

export type WorkspaceSession = {
  user: User;
  workspace: Workspace | null;
  components: Components;
  refreshWorkspace: () => void;
  revision: number;
};

export const WorkspaceContext = createContext<WorkspaceSession | null>(null);

export function resolveComponents(workspace: Workspace | null): Components {
  return Object.fromEntries(
    componentKeys.map((key) => [key, workspace?.components?.[key] !== false]),
  ) as Components;
}

export function useWorkspaceSession(): WorkspaceSession {
  const session = useContext(WorkspaceContext);
  if (!session) {
    throw new Error("Le contenu de l’espace doit être affiché dans son cadre de navigation.");
  }
  return session;
}

export function useWorkspace(): User {
  return useWorkspaceSession().user;
}

export function useComponent(key: ComponentKey) {
  return useWorkspaceSession().components[key];
}

export function workspaceLogo(workspace: Workspace | null, revision = 0) {
  return workspace?.logo_url
    ? `/api/backend/api/v1/public/workspaces/${encodeURIComponent(workspace.slug)}/logo?v=${revision}`
    : null;
}

export const canWrite = (user: User) => user.role === "ADMIN" || user.role === "OPERATOR";
export const isAdmin = (user: User) => user.role === "ADMIN";
export const isStaff = (user: User) => user.role !== "DRIVER";
