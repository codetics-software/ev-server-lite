import type { FormField } from "../types";

export const managementFields: Record<string, FormField[]> = {
  stations: [
    { key: "id", label: "Identifiant de la borne", required: true },
    { key: "name", label: "Nom de la borne", required: true },
  ],
  tokens: [
    { key: "uid", label: "Identifiant du badge RFID", required: true },
    { key: "userId", label: "Identifiant de l’utilisateur (facultatif)" },
    { key: "enabled", label: "Autoriser la recharge", type: "checkbox" },
    { key: "expiresAt", label: "Expiration (facultative)", type: "datetime-local" },
  ],
  users: [
    { key: "email", label: "Adresse électronique", type: "email", required: true },
    { key: "name", label: "Nom complet", required: true },
    {
      key: "role",
      label: "Rôle",
      options: ["DRIVER", "VIEWER", "OPERATOR", "ADMIN"],
      required: true,
    },
    { key: "enabled", label: "Compte activé", type: "checkbox" },
  ],
  roaming: [
    { key: "name", label: "Nom du partenaire", required: true },
    { key: "countryCode", label: "Code pays", required: true },
    { key: "partyId", label: "Identifiant de l’opérateur", required: true },
    {
      key: "localRole",
      label: "Notre rôle",
      options: ["cpo", "emsp"],
      required: true,
    },
    {
      key: "remoteUrl",
      label: "Adresse de découverte du partenaire (HTTPS)",
      required: true,
    },
  ],
};
