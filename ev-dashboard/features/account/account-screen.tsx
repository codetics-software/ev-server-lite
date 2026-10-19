"use client";

import { useWorkspace } from "@/features/workspace/workspace-context";
import { AccountView } from "./account-view";

export function AccountScreen() {
  return <AccountView user={useWorkspace()} />;
}
