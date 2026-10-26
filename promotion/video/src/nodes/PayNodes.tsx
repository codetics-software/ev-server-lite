import React from "react";
import { interpolate } from "remotion";
import { useAbsFrame } from "../components/AbsTime";
import { Icon, Logo } from "../components/Art";
import { CardHeader, Pill } from "../components/FloatCard";
import { PAY } from "../story";
import { C, NNBSP } from "../theme";
import { cue } from "../time";

const Row: React.FC<{ readonly l: string; readonly r: string; readonly strong?: boolean; readonly color?: string }> = ({ l, r, strong, color }) => (
  <div style={{ display: "flex", justifyContent: "space-between", padding: "9px 0", borderBottom: `1px solid ${C.border}`, fontSize: 17, fontWeight: strong ? 600 : 400, color: color ?? C.ink }}>
    <span>{l}</span>
    <span>{r}</span>
  </div>
);

export const PhoneNode: React.FC = () => {
  const frame = useAbsFrame();
  const click = cue("pay.click");
  const press = interpolate(frame, [click - 2, click, click + 6], [1, 0.9, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const screen = frame < click + 4 ? 0 : frame < PAY.confirmed ? 1 : frame < cue("pay.receipt") ? 2 : 3;
  const enter = (at: number) => interpolate(frame, [at, at + 10], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const progress = interpolate(frame, [PAY.start, cue("pay.receipt")], [0, 0.62], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });

  return (
    <div style={{ position: "absolute", inset: 0, background: "#0f1a14", borderRadius: 22, padding: 10 }}>
      <div style={{ position: "absolute", inset: 10, background: "#f7f8f6", borderRadius: 16, overflow: "hidden", padding: "16px 18px" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8, fontWeight: 600, fontSize: 17 }}>
          <Logo size={24} />
          Codetics
          <span style={{ marginLeft: "auto", fontWeight: 400, fontSize: 13, color: C.muted }}>Recharge sans compte</span>
        </div>
        <div style={{ fontSize: 13, color: C.muted, marginTop: 16 }}>Réseau Codetics</div>
        <div style={{ fontSize: 22, fontWeight: 600, lineHeight: 1.15 }}>Lyon · Parking visiteurs</div>
        <div style={{ fontSize: 13, color: C.muted, marginTop: 4 }}>Borne CDT-1002 · Connecteur 1</div>
        {screen === 0 && (
          <div style={{ marginTop: 16, background: "#fff", border: `1px solid ${C.border}`, borderRadius: 14, padding: "12px 14px" }}>
            <Row l="Énergie" r={`0,39${NNBSP}€ / kWh`} />
            <Row l="Frais de session" r={`0,50${NNBSP}€`} />
            <div style={{ fontSize: 14, fontWeight: 600, marginTop: 12 }}>Crédit de recharge</div>
            <div style={{ marginTop: 6, padding: "8px 10px", borderRadius: 8, border: `1px solid ${C.border}`, fontSize: 20, fontWeight: 600 }}>20{NNBSP}€</div>
            <div style={{ marginTop: 16, scale: press, padding: "12px 0", borderRadius: 10, background: C.green, color: "#fff", textAlign: "center", fontSize: 17, fontWeight: 600 }}>
              Payer et démarrer
            </div>
          </div>
        )}
        {screen === 1 && (
          <div style={{ marginTop: 40, textAlign: "center", opacity: enter(click + 4) }}>
            <svg width="70" height="70" viewBox="0 0 50 50" style={{ rotate: `${frame * 12}deg` }}>
              <circle cx="25" cy="25" r="20" stroke="#dfe6e1" strokeWidth="5" fill="none" />
              <path d="M25 5 A20 20 0 0 1 45 25" stroke={C.greenMid} strokeWidth="5" fill="none" strokeLinecap="round" />
            </svg>
            <div style={{ fontSize: 19, fontWeight: 600, marginTop: 14 }}>Paiement sécurisé…</div>
          </div>
        )}
        {screen === 2 && (
          <div style={{ marginTop: 20, opacity: enter(PAY.confirmed) }}>
            <Pill color={C.greenBright} size={17}>Recharge en cours</Pill>
            <div style={{ fontSize: 44, fontWeight: 600, marginTop: 14 }}>
              {(progress * 20).toFixed(2).replace(".", ",")}
              {NNBSP}€
            </div>
            <div style={{ fontSize: 15, color: C.muted }}>sur un crédit de 20,00{NNBSP}€</div>
            <div style={{ marginTop: 14, height: 10, borderRadius: 5, background: "#e4eae6" }}>
              <div style={{ width: `${progress * 100}%`, height: "100%", borderRadius: 5, background: `linear-gradient(90deg, ${C.greenMid}, ${C.energy})` }} />
            </div>
          </div>
        )}
        {screen === 3 && (
          <div style={{ marginTop: 18, background: "#fff", border: `1px solid ${C.border}`, borderRadius: 14, padding: "12px 14px", opacity: enter(cue("pay.receipt")) }}>
            <div style={{ fontSize: 17, fontWeight: 600, marginBottom: 6 }}>Session terminée</div>
            <Row l="Crédit payé" r={`20,00${NNBSP}€`} />
            <Row l="Consommé" r={`12,40${NNBSP}€`} />
            <Row l="Remboursé" r={`7,60${NNBSP}€`} strong color={C.greenMid} />
            <div style={{ display: "flex", alignItems: "center", gap: 8, marginTop: 12, fontSize: 15, color: C.greenMid, fontWeight: 600 }}>
              <Icon.check size={18} color={C.greenMid} stroke={3} />
              Remboursement automatique
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export const PaymentNode: React.FC = () => {
  const frame = useAbsFrame();
  const click = cue("pay.click");
  const status =
    frame < click
      ? { l: "En attente", c: C.faint }
      : frame < PAY.confirmed
        ? { l: "Vérification…", c: C.amber }
        : { l: "Payé", c: C.greenBright };
  const refunded = frame >= cue("pay.receipt");
  return (
    <>
      <CardHeader title="Paiement sécurisé" right={<span style={{ color: "#635bff", fontWeight: 700, fontSize: 22 }}>Stripe</span>} />
      <div style={{ padding: "14px 22px" }}>
        <div style={{ display: "flex", alignItems: "baseline", justifyContent: "space-between" }}>
          <div style={{ fontSize: 46, fontWeight: 600 }}>20,00{NNBSP}€</div>
          <Pill color={status.c} size={18}>
            {status.l}
          </Pill>
        </div>
        <div
          style={{
            marginTop: 14,
            display: "flex",
            alignItems: "center",
            gap: 10,
            fontSize: 20,
            color: refunded ? C.greenMid : C.faint,
            fontWeight: 600,
          }}
        >
          {refunded ? <Icon.check size={22} color={C.greenMid} stroke={3} /> : <Icon.euro size={22} color={C.faint} />}
          {refunded ? `Solde remboursé · 7,60${NNBSP}€` : "Solde remboursé en fin de session"}
        </div>
      </div>
    </>
  );
};
