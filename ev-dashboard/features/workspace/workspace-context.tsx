"use client";

import type { User } from "@/lib/api-types";
import { createContext, useContext } from "react";

export const WorkspaceContext = createContext<User | null>(null);

export function useWorkspace(): User {
  const user = useContext(WorkspaceContext);
  if (!user) {
    throw new Error("Le contenu de l’espace doit être affiché dans son cadre de navigation.");
  }
  return user;
}
