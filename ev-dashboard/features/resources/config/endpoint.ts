import { columns } from "./table-columns";

export function endpoint(section: string): string {
  if (section === "roaming") {
    return "ocpi/partners";
  }

  return columns[section] ? section : `records/${section}`;
}
