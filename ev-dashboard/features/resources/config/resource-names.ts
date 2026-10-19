const names: Record<string, string> = {
  stations: "station",
  transactions: "session",
  commands: "command",
  tokens: "token",
  users: "person",
  roaming: "roaming partner",
  companies: "company",
  sites: "site",
  "site-areas": "site area",
  assets: "energy asset",
  cars: "vehicle",
  "charging-station-templates": "station template",
  "pricing-definitions": "pricing definition",
  settings: "setting",
  audit: "audit event",
};

export function resourceName(section: string) {
  return names[section] ?? "configuration";
}
