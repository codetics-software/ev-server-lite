"use client";

import type { User } from "@/lib/api-types";
import { createContext, useContext } from "react";

export const WorkspaceContext = createContext<User | null>(null);

export function useWorkspace(): User {
  const user = useContext(WorkspaceContext);
  if (!user) {
    throw new Error("Workspace content must be rendered inside WorkspaceShell");
  }
  return user;
}
