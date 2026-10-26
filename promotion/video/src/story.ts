import { C, NNBSP } from "./theme";
import { beat, cue } from "./time";

export const W = {
  car: { x: 0, y: 0, w: 380, h: 250 },
  charger: { x: 620, y: 0, w: 250, h: 420 },
  server: { x: 1300, y: 0, w: 420, h: 340 },
  dashboard: { x: 2050, y: 0, w: 680, h: 430 },
  phone: { x: 620, y: 600, w: 270, h: 500 },
  payment: { x: 1300, y: 600, w: 380, h: 230 },
  zone: { x: 260, y: -660, w: 580, h: 340 },
  roaming: { x: 1600, y: -650, w: 460, h: 280 },
} as const;

export const CMD = {
  toServer: cue("control.click") + 2,
  toCharger: cue("control.click") + 22,
  arrive: cue("control.click") + 40,
  reply: cue("control.confirm"),
  replyToDash: cue("control.confirm") + 18,
  done: cue("control.confirm") + 36,
};

export const OCPP_PACKETS = [
  { at: beat(78), label: "Autorisation", log: "Autorisation vérifiée" },
  { at: beat(80), label: "Démarrage", log: "Session démarrée" },
  { at: beat(82), label: "11 kW", log: "Mesure · 11 kW" },
  { at: beat(84), label: "1,8 kWh", log: "Énergie · 1,8 kWh" },
  { at: beat(86), label: "Signal de vie", log: "Borne en ligne" },
];

export const PAY = {
  toStripe: cue("pay.click") + 2,
  confirmed: cue("pay.ok"),
  start: cue("pay.ok") + 18,
  refund: cue("pay.receipt") - 18,
};

export const powerAt = (f: number) => (f < cue("authorized") ? 0 : f < CMD.reply ? 11 : 7);

export const kwhAt = (f: number) => {
  const a = cue("authorized");
  if (f < a) return 0;
  const fast = Math.min(f, CMD.reply) - a;
  const slow = Math.max(0, f - CMD.reply);
  return (fast * 11 + slow * 7) / 30 / 40;
};

export const batteryAt = (f: number) => Math.min(80, 23 + kwhAt(f) * 9);

export const chargerState = (f: number) => {
  if (f < cue("cable")) return { label: "Disponible", color: C.greenMid, led: "#5b7a6a" };
  if (f < cue("authorized")) return { label: "Identification…", color: C.amber, led: C.amber };
  if (f >= CMD.arrive && f < CMD.reply) return { label: "Commande reçue…", color: C.amber, led: C.amber };
  return { label: `En charge · ${powerAt(f)}${NNBSP}kW`, color: C.greenBright, led: C.energy };
};

export const fmt = (n: number, digits = 1) =>
  n.toLocaleString("fr-FR", { minimumFractionDigits: digits, maximumFractionDigits: digits }).replace(/ | /g, NNBSP);

export const SERVER_LOG = [
  ...OCPP_PACKETS.map((p) => ({ at: p.at + 16, text: p.log, tone: "ok" as const })),
  { at: CMD.toServer + 18, text: "Commande · 7 kW", tone: "out" as const },
  { at: CMD.reply + 16, text: "Commande acceptée", tone: "ok" as const },
  { at: PAY.confirmed + 16, text: "Paiement confirmé", tone: "ok" as const },
  { at: PAY.start + 2, text: "Démarrage demandé", tone: "out" as const },
  { at: PAY.refund + 2, text: "Remboursement 7,60 €", tone: "out" as const },
  { at: cue("zone.balance") + 4, text: "Puissance répartie", tone: "out" as const },
  { at: cue("roaming.snap") + 24, text: "Badge partenaire OK", tone: "ok" as const },
];
