import type { User } from "@/lib/api-types";
import { sections } from "./navigation";

export function navigationFor(user: User) {
  return sections.filter((section) => {
    if (user.role === "DRIVER") {
      return section.id === "transactions" || section.id === "account";
    }
    if (section.id === "payments" && user.role === "VIEWER") {
      return false;
    }
    return !("admin" in section && section.admin && user.role !== "ADMIN");
  });
}

export const roleLabels: Record<User["role"], string> = {
  ADMIN: "Administrateur",
  OPERATOR: "Exploitant",
  VIEWER: "Lecteur",
  DRIVER: "Conducteur",
};
