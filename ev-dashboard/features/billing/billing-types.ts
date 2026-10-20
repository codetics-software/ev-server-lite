export type Invoice = {
  id: string;
  number: string | null;
  user_id: string;
  user_name: string | null;
  status: "DRAFT" | "OPEN" | "PAID" | "UNCOLLECTIBLE" | "VOID";
  amount_minor: number;
  currency: string;
  created_at: string;
  session_ids: number[];
  hosted_url: string | null;
  last_error?: string | null;
};

export type PaymentMethod = {
  id: string;
  brand: string | null;
  last4: string | null;
  exp_month: number | null;
  exp_year: number | null;
  default: boolean;
  created_at: string | null;
};

export type Tax = { id: string; display_name: string; percentage: number; inclusive: boolean };

export type UserBilling = {
  enabled: boolean;
  mode: "PER_SESSION" | "MONTHLY";
  tax_rate_id?: string | null;
  taxRateId?: string | null;
};

export const brandLabels: Record<string, string> = {
  visa: "Visa",
  mastercard: "Mastercard",
  amex: "American Express",
  cartes_bancaires: "Cartes Bancaires",
  discover: "Discover",
  unionpay: "UnionPay",
};
