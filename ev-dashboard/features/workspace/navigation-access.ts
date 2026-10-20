import type { Components, User } from "@/lib/api-types";
import { sections, type Section } from "./navigation";

export function canSee(section: Section, user: User, components: Components) {
  if (section.platformAdmin && !user.platformAdmin) {
    return false;
  }
  if (section.component && !components[section.component]) {
    return false;
  }
  return section.roles.includes(user.role);
}

export function navigationFor(user: User, components: Components) {
  return sections.filter((section) => canSee(section, user, components));
}

export function homeFor(user: User) {
  return user.role === "DRIVER" ? "/transactions" : "/";
}

export function sectionLabel(section: Section, user: User) {
  return user.role === "DRIVER" && section.driverLabel ? section.driverLabel : section.label;
}
