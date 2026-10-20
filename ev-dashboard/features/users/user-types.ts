import type { UserRole } from "@/lib/api-types";

export type ManagedUser = {
  id: string;
  email: string;
  display_name: string;
  role: UserRole;
  enabled: boolean;
  email_verified: boolean;
  phone?: string | null;
  locale?: string | null;
  created_at?: string | null;
  last_login_at?: string | null;
  billing_customer?: boolean;
  default_car_id?: string | null;
  site_count?: number;
  eula_accepted?: boolean;
};

export type UserInError = { user: ManagedUser; errors: string[] };
