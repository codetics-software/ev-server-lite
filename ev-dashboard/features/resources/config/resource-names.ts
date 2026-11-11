import { t, translated } from "@/lib/i18n";
const names: Record<string, string> = translated({
  stations: "une borne",
  transactions: "une session",
  commands: "une commande",
  tokens: "un badge",
  users: "un utilisateur",
  roaming: "un partenaire",
  companies: "une entreprise",
  sites: "un site",
  "site-areas": "une zone",
  assets: "un équipement",
  cars: "un véhicule",
  "charging-station-templates": "un modèle",
  "pricing-definitions": "un tarif",
  settings: "un paramètre",
  audit: "un événement",
});

export function resourceName(section: string) {
  return names[section] ?? t("configuration");
}
