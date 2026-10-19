export type PaymentState =
  | "PENDING_PAYMENT"
  | "PAID"
  | "START_REQUESTED"
  | "CHARGING"
  | "STOP_REQUESTED"
  | "SETTLING"
  | "REFUND_PENDING"
  | "COMPLETED"
  | "CANCELLED"
  | "FAILED"
  | "DISPUTED";

export type Payment = {
  id: string;
  state: PaymentState;
  prepaidMinor: number;
  billedMinor: number | null;
  refundedMinor: number;
  currency: string;
  transactionId: number | null;
  message: string | null;
};

export type OperatorPayment = Payment & {
  stationId: string;
  connectorId: number;
  createdAt: string;
};
export type PaymentSettings = {
  configured: boolean;
  enabled: boolean;
  accountId: string | null;
  ready: boolean;
  country: string;
  currency: string;
};

export const paymentLabels: Record<PaymentState, string> = {
  PENDING_PAYMENT: "En attente de paiement",
  PAID: "Crédit payé",
  START_REQUESTED: "Démarrage demandé",
  CHARGING: "Recharge en cours",
  STOP_REQUESTED: "Arrêt demandé",
  SETTLING: "Calcul du solde",
  REFUND_PENDING: "Remboursement en cours",
  COMPLETED: "Terminée",
  CANCELLED: "Annulée",
  FAILED: "Intervention nécessaire",
  DISPUTED: "Paiement contesté",
};

export function money(minor: number | null, currency = "EUR") {
  return minor === null
    ? "—"
    : new Intl.NumberFormat("fr-FR", { style: "currency", currency }).format(minor / 100);
}
