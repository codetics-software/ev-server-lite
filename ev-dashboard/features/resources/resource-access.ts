import type { User } from "@/lib/api-types";
import { endpoint } from "./config/endpoint";

export function resourceAccess(section: string, user: User) {
  const isConfiguration = endpoint(section).startsWith("records/");
  const canCreate = user.role === "ADMIN" || (section === "tokens" && user.role === "OPERATOR");

  return {
    canCreate,
    canEdit: canCreate && (isConfiguration || ["tokens", "users"].includes(section)),
    canDelete: canCreate && isConfiguration,
  };
}
