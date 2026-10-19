export type User = {
  id: string;
  tenantId: string;
  email: string;
  name: string;
  role: "ADMIN" | "OPERATOR" | "VIEWER" | "DRIVER";
};
export type Row = Record<string, unknown>;
export type PageData = { items: Row[]; total: number };
