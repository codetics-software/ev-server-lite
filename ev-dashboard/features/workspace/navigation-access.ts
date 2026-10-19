import type { User } from "@/lib/api-types";
import { sections } from "./navigation";

export function navigationFor(user: User) {
  return sections.filter((section) => {
    if (user.role === "DRIVER") {
      return section.id === "transactions" || section.id === "account";
    }
    return !("admin" in section && section.admin && user.role !== "ADMIN");
  });
}

export const roleLabels: Record<User["role"], string> = {
  ADMIN: "Administrator",
  OPERATOR: "Operator",
  VIEWER: "Viewer",
  DRIVER: "Driver",
};
