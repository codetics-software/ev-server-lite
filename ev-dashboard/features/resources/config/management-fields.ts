import { userRoles } from "@/lib/api-types";
import type { FormField } from "../types";

export const managementFields: Record<string, FormField[]> = {
  stations: [
    {
      key: "id",
      label: "Identifiant de la borne",
      required: true,
      maxLength: 100,
      pattern: { regex: /^[A-Za-z0-9_-]+$/, message: "Identifiant de borne invalide." },
    },
    { key: "name", label: "Nom de la borne", required: true, maxLength: 200 },
  ],
  tokens: [
    {
      key: "uid",
      label: "Identifiant du badge RFID",
      required: true,
      maxLength: 20,
      pattern: {
        regex: /^[A-Za-z0-9_-]+$/,
        message: "L’identifiant du badge OCPP 1.6 doit contenir au plus 20 caractères.",
      },
    },
    { key: "userId", label: "Identifiant de l’utilisateur (facultatif)" },
    { key: "enabled", label: "Autoriser la recharge", type: "checkbox" },
    { key: "expiresAt", label: "Expiration (facultative)", type: "datetime-local" },
  ],
  users: [
    { key: "email", label: "Adresse électronique", type: "email", required: true },
    { key: "name", label: "Nom complet", required: true, maxLength: 200 },
    {
      key: "role",
      label: "Rôle",
      options: userRoles,
      required: true,
    },
    { key: "enabled", label: "Compte activé", type: "checkbox" },
  ],
  roaming: [
    { key: "name", label: "Nom du partenaire", required: true, maxLength: 200 },
    {
      key: "countryCode",
      label: "Code pays",
      required: true,
      maxLength: 2,
      pattern: { regex: /^[A-Z]{2}$/, message: "Le code pays contient deux lettres majuscules." },
    },
    {
      key: "partyId",
      label: "Identifiant de l’opérateur",
      required: true,
      maxLength: 3,
      pattern: {
        regex: /^[A-Z0-9]{3}$/,
        message: "L’identifiant de l’opérateur contient trois lettres majuscules ou chiffres.",
      },
    },
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
