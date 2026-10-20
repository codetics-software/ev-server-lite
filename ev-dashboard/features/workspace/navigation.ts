import type { ComponentKey, UserRole } from "@/lib/api-types";
import {
  IconActivity,
  IconBell,
  IconBuilding,
  IconBuildingFactory2,
  IconCar,
  IconChargingPile,
  IconChartBar,
  IconCreditCard,
  IconDashboard,
  IconFileInvoice,
  IconFileSettings,
  IconHistory,
  IconListDetails,
  IconMapPin,
  IconQrcode,
  IconReceipt,
  IconServer2,
  IconSettings,
  IconSitemap,
  IconSolarPanel,
  IconTag,
  IconUser,
  IconUsers,
  IconWorld,
} from "@tabler/icons-react";

const STAFF: readonly UserRole[] = ["ADMIN", "OPERATOR", "VIEWER"];
const EVERYONE: readonly UserRole[] = ["ADMIN", "OPERATOR", "VIEWER", "DRIVER"];
const ADMIN: readonly UserRole[] = ["ADMIN"];
const OPERATIONS: readonly UserRole[] = ["ADMIN", "OPERATOR"];

export type Section = {
  id: string;
  label: string;
  driverLabel?: string;
  group: string;
  icon: typeof IconDashboard;
  roles: readonly UserRole[];
  component?: ComponentKey;
  platformAdmin?: boolean;
};

export const groups = [
  "Supervision",
  "Réseau",
  "Utilisateurs",
  "Facturation",
  "Itinérance",
  "Administration",
  "Mon espace",
] as const;

export const sections: readonly Section[] = [
  {
    id: "overview",
    label: "Vue d’ensemble",
    group: "Supervision",
    icon: IconDashboard,
    roles: STAFF,
  },
  {
    id: "stations",
    label: "Bornes de recharge",
    group: "Supervision",
    icon: IconChargingPile,
    roles: STAFF,
  },
  {
    id: "transactions",
    label: "Sessions de recharge",
    driverLabel: "Mes sessions",
    group: "Supervision",
    icon: IconActivity,
    roles: EVERYONE,
  },
  {
    id: "statistics",
    label: "Statistiques",
    driverLabel: "Mes statistiques",
    group: "Supervision",
    icon: IconChartBar,
    roles: EVERYONE,
    component: "statistics",
  },
  {
    id: "commands",
    label: "Suivi des commandes",
    group: "Supervision",
    icon: IconHistory,
    roles: STAFF,
  },
  {
    id: "companies",
    label: "Entreprises",
    group: "Réseau",
    icon: IconBuildingFactory2,
    roles: STAFF,
    component: "organization",
  },
  {
    id: "sites",
    label: "Sites",
    group: "Réseau",
    icon: IconMapPin,
    roles: STAFF,
    component: "organization",
  },
  {
    id: "site-areas",
    label: "Zones de recharge",
    group: "Réseau",
    icon: IconSitemap,
    roles: STAFF,
    component: "organization",
  },
  {
    id: "assets",
    label: "Équipements",
    group: "Réseau",
    icon: IconSolarPanel,
    roles: STAFF,
    component: "assets",
  },
  {
    id: "users",
    label: "Utilisateurs et rôles",
    group: "Utilisateurs",
    icon: IconUsers,
    roles: ADMIN,
  },
  { id: "tokens", label: "Badges", group: "Utilisateurs", icon: IconTag, roles: STAFF },
  {
    id: "cars",
    label: "Véhicules",
    driverLabel: "Mes véhicules",
    group: "Utilisateurs",
    icon: IconCar,
    roles: EVERYONE,
    component: "cars",
  },
  {
    id: "pricing-definitions",
    label: "Tarifs",
    group: "Facturation",
    icon: IconReceipt,
    roles: STAFF,
    component: "pricing",
  },
  {
    id: "invoices",
    label: "Factures",
    driverLabel: "Mes factures",
    group: "Facturation",
    icon: IconFileInvoice,
    roles: EVERYONE,
    component: "billing",
  },
  {
    id: "billing",
    label: "Facturation des utilisateurs",
    group: "Facturation",
    icon: IconCreditCard,
    roles: ADMIN,
    component: "billing",
  },
  {
    id: "payments",
    label: "Paiements publics",
    group: "Facturation",
    icon: IconCreditCard,
    roles: OPERATIONS,
    component: "public_charging",
  },
  {
    id: "evses",
    label: "Points de charge publics",
    group: "Facturation",
    icon: IconQrcode,
    roles: STAFF,
    component: "public_charging",
  },
  {
    id: "roaming",
    label: "Partenaires OCPI",
    group: "Itinérance",
    icon: IconWorld,
    roles: ADMIN,
    component: "roaming",
  },
  {
    id: "charging-station-templates",
    label: "Modèles de bornes",
    group: "Administration",
    icon: IconFileSettings,
    roles: STAFF,
  },
  {
    id: "notifications",
    label: "Notifications",
    group: "Administration",
    icon: IconBell,
    roles: ADMIN,
  },
  {
    id: "logs",
    label: "Journal d’activité",
    group: "Administration",
    icon: IconListDetails,
    roles: ADMIN,
  },
  {
    id: "settings",
    label: "Paramètres avancés",
    group: "Administration",
    icon: IconSettings,
    roles: ADMIN,
  },
  {
    id: "workspace",
    label: "Espace client",
    group: "Administration",
    icon: IconBuilding,
    roles: ADMIN,
  },
  {
    id: "platform",
    label: "Plateforme",
    group: "Administration",
    icon: IconServer2,
    roles: EVERYONE,
    platformAdmin: true,
  },
  { id: "account", label: "Mon compte", group: "Mon espace", icon: IconUser, roles: EVERYONE },
];

export function sectionHref(id: string) {
  return id === "overview" ? "/" : `/${id}`;
}
