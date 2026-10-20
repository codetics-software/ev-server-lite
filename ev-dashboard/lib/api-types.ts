export type UserRole = "ADMIN" | "OPERATOR" | "VIEWER" | "DRIVER";
export const userRoles = [
  "DRIVER",
  "VIEWER",
  "OPERATOR",
  "ADMIN",
] as const satisfies readonly UserRole[];
export type CommandState = "QUEUED" | "SENT" | "SUCCEEDED" | "FAILED" | "TIMED_OUT" | "CANCELLED";
export type AuthorizationStatus = "Accepted" | "Blocked" | "Invalid" | "Expired";

export type User = {
  id: string;
  tenantId: string;
  email: string;
  name: string;
  role: UserRole;
  platformAdmin?: boolean;
  eulaAccepted?: boolean;
};
export type Row = Record<string, unknown>;
export type PageData = { items: Row[]; total: number };
export type Page<T> = { items: T[]; total: number };
export type ProblemDetail = {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
};

export const componentKeys = [
  "organization",
  "pricing",
  "billing",
  "roaming",
  "smart_charging",
  "statistics",
  "assets",
  "cars",
  "public_charging",
] as const;
export type ComponentKey = (typeof componentKeys)[number];
export type Components = Record<ComponentKey, boolean>;

export type Workspace = {
  id: string;
  slug: string;
  name: string;
  active: boolean;
  components?: Partial<Components>;
  logo_url?: string | null;
};

export type RecordItem<T extends Row = Row> = {
  id: string;
  name: string;
  data: T;
  revision: number;
  updated_at: string;
};

export type ImportResult = {
  created: number;
  updated: number;
  skipped: number;
  errors: { line: number; message: string }[];
};

export type CommandReceipt = { id: string; state: CommandState };
