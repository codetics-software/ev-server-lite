"use client";

import { ResourcePage } from "@/features/resources/resource-page";
import { useWorkspace } from "./workspace-context";

export function ResourceScreen({ section }: { section: string }) {
  return <ResourcePage section={section} user={useWorkspace()} />;
}
