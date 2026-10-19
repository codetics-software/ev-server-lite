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
};
export type Row = Record<string, unknown>;
export type PageData = { items: Row[]; total: number };
export type ProblemDetail = {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
};
