import type { FormField } from "../types";

export const networkFields: Record<string, FormField[]> = {
  companies: [
    { key: "name", label: "Nom de l’entreprise", required: true },
    { key: "address", label: "Adresse" },
    { key: "vatNumber", label: "Numéro de TVA" },
  ],
  sites: [
    { key: "name", label: "Nom du site", required: true },
    { key: "companyId", label: "Identifiant de l’entreprise" },
    { key: "address", label: "Adresse" },
    { key: "city", label: "Ville" },
    { key: "country", label: "Pays" },
    { key: "latitude", label: "Latitude", type: "number" },
    { key: "longitude", label: "Longitude", type: "number" },
  ],
  "site-areas": [
    { key: "name", label: "Nom de la zone", required: true },
    { key: "siteId", label: "Identifiant du site", required: true },
    { key: "maximumPowerKw", label: "Puissance disponible (kW)", type: "number" },
  ],
  assets: [
    { key: "name", label: "Nom de l’équipement", required: true },
    {
      key: "type",
      label: "Type d’équipement",
      options: ["METER", "SOLAR", "BATTERY", "BUILDING"],
    },
    { key: "siteAreaId", label: "Identifiant de la zone" },
    { key: "maximumPowerKw", label: "Puissance (kW)", type: "number" },
  ],
  cars: [
    { key: "name", label: "Nom du véhicule", required: true },
    { key: "registration", label: "Immatriculation" },
    { key: "make", label: "Fabricant" },
    { key: "model", label: "Modèle" },
    { key: "batteryKwh", label: "Capacité de la batterie (kWh)", type: "number" },
  ],
  "charging-station-templates": [
    { key: "name", label: "Nom du modèle de configuration", required: true },
    { key: "vendor", label: "Fabricant" },
    { key: "model", label: "Modèle" },
    { key: "connectorCount", label: "Nombre de connecteurs", type: "number" },
  ],
  "pricing-definitions": [
    { key: "name", label: "Nom du tarif", required: true },
    { key: "currency", label: "Devise", required: true },
    { key: "energyPrice", label: "Prix par kWh (€)", type: "number" },
    { key: "timePrice", label: "Prix par heure (€)", type: "number" },
    { key: "flatPrice", label: "Frais de session (€)", type: "number" },
  ],
  settings: [
    { key: "name", label: "Nom du paramètre", required: true },
    { key: "value", label: "Valeur", required: true },
  ],
};
