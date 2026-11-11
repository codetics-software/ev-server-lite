import { translated } from "@/lib/i18n";
// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

export type CardOffer = {
  available: boolean;
  price_minor: number | null;
  currency: string;
  description: string | null;
  shipping: boolean;
};

export type CardOrderState = "PENDING_PAYMENT" | "PAID" | "SHIPPED" | "ACTIVATED" | "CANCELLED";

export type CardOrder = {
  id: string;
  state: CardOrderState;
  amount_minor: number;
  currency: string;
  created_at: string;
  paid_at: string | null;
  shipped_at: string | null;
  activated_at: string | null;
  refunded: boolean;
  user_name?: string | null;
  user_email?: string | null;
  recipient_name?: string | null;
  address?: string | null;
  postal_code?: string | null;
  city?: string | null;
  token_uid?: string | null;
  visual_id?: string | null;
};

export const cardStateLabels: Record<CardOrderState, string> = translated({
  PENDING_PAYMENT: "En attente de paiement",
  PAID: "Payée, à préparer",
  SHIPPED: "Carte prête, à activer",
  ACTIVATED: "Activée",
  CANCELLED: "Annulée",
});

export const cardStateTones = {
  PENDING_PAYMENT: "neutral",
  PAID: "warning",
  SHIPPED: "progress",
  ACTIVATED: "success",
  CANCELLED: "neutral",
} as const satisfies Record<CardOrderState, string>;
