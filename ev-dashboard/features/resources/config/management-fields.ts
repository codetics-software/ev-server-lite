import type { FormField } from "../types";

export const managementFields: Record<string, FormField[]> = {
  stations: [
    { key: "id", label: "Station identity", required: true },
    { key: "name", label: "Station name", required: true },
  ],
  tokens: [
    { key: "uid", label: "RFID / token identifier", required: true },
    { key: "userId", label: "User ID (optional)" },
    { key: "enabled", label: "Allow charging", type: "checkbox" },
    { key: "expiresAt", label: "Expiry (optional)", type: "datetime-local" },
  ],
  users: [
    { key: "email", label: "Email address", type: "email", required: true },
    { key: "name", label: "Full name", required: true },
    {
      key: "role",
      label: "Role",
      options: ["DRIVER", "VIEWER", "OPERATOR", "ADMIN"],
      required: true,
    },
    { key: "enabled", label: "Account enabled", type: "checkbox" },
  ],
  roaming: [
    { key: "name", label: "Partner name", required: true },
    { key: "countryCode", label: "Country code", required: true },
    { key: "partyId", label: "Party ID", required: true },
    {
      key: "localRole",
      label: "Our role",
      options: ["cpo", "emsp"],
      required: true,
    },
    {
      key: "remoteUrl",
      label: "Partner discovery URL (HTTPS)",
      required: true,
    },
  ],
};
