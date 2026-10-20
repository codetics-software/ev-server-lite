export type Badge = {
  uid: string;
  visual_id?: string | null;
  description?: string | null;
  user_id: string | null;
  user_name?: string | null;
  type: string;
  enabled: boolean;
  expires_at: string | null;
  default?: boolean;
  last_used_at?: string | null;
};
