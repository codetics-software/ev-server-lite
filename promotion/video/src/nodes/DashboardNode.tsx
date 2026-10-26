import React from "react";
import { interpolate } from "remotion";
import { useAbsFrame } from "../components/AbsTime";
import { Icon, Logo } from "../components/Art";
import { CMD, fmt, kwhAt, powerAt } from "../story";
import { C, NNBSP } from "../theme";
import { cue, EASE_IN_OUT, EASE_OUT } from "../time";

const Kpi: React.FC<{ readonly label: string; readonly value: string; readonly unit?: string }> = ({ label, value, unit }) => (
  <div style={{ flex: 1, paddingRight: 12 }}>
    <div style={{ fontSize: 15, color: C.muted }}>{label}</div>
    <div style={{ fontSize: 38, fontWeight: 600, lineHeight: 1.1, marginTop: 4 }}>
      {value}
      {unit && <span style={{ fontSize: 17, color: C.muted, marginLeft: 6 }}>{unit}</span>}
    </div>
  </div>
);

export const DashboardNode: React.FC = () => {
  const frame = useAbsFrame();
  const start = cue("dash.link") + 6;
  const k = interpolate(frame, [start, start + 40], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: EASE_OUT });
  const kwh = kwhAt(frame);
  const total = 1284 * k + kwh;
  const price = 0.5 + kwh * 0.39;
  const bars = [
    { label: "En charge", n: 7, color: C.greenBright },
    { label: "Disponible", n: 15, color: C.greenMid },
    { label: "Indisponible", n: 2, color: C.red },
  ];

  const mv = cue("control.move");
  const click = cue("control.click");
  const cx = interpolate(frame, [mv, click - 3], [560, 440], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: EASE_IN_OUT });
  const cy = interpolate(frame, [mv, click - 3], [420, 290], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: EASE_IN_OUT });
  const press = interpolate(frame, [click - 2, click, click + 6], [1, 0.86, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const cursorOn = interpolate(frame, [mv, mv + 8, CMD.done + 20, CMD.done + 30], [0, 1, 1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const pending = frame >= click && frame < CMD.done;
  const applied = frame >= CMD.done;

  return (
    <div style={{ display: "flex", height: "100%" }}>
      <div style={{ width: 150, background: "#0f1f19", padding: "18px 14px", display: "flex", flexDirection: "column", gap: 12 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8, color: "#fff", fontWeight: 600, fontSize: 17, marginBottom: 10 }}>
          <Logo size={28} />
          Codetics
        </div>
        {[0.9, 0.7, 0.8, 0.6, 0.75, 0.65, 0.85, 0.5].map((w, i) => (
          <div key={i} style={{ display: "flex", alignItems: "center", gap: 8, padding: "5px 6px", borderRadius: 6, background: i === 0 ? "rgba(255,255,255,0.08)" : "transparent" }}>
            <div style={{ width: 12, height: 12, borderRadius: 3, background: i === 0 ? C.energy : "#3c5248" }} />
            <div style={{ width: `${w * 80}%`, height: 7, borderRadius: 4, background: i === 0 ? "#cfe9dc" : "#3c5248" }} />
          </div>
        ))}
      </div>
      <div style={{ flex: 1, padding: "20px 24px", position: "relative" }}>
        <div style={{ fontSize: 26, fontWeight: 600 }}>Vue d’ensemble du réseau</div>
        <div style={{ fontSize: 15, color: C.muted, marginTop: 2 }}>22 bornes connectées sur 24 · mis à jour en direct</div>
        <div style={{ display: "flex", marginTop: 16, paddingTop: 14, borderTop: `1px solid ${C.border}` }}>
          <Kpi label="Bornes" value={fmt(24 * k, 0)} />
          <Kpi label="Sessions en cours" value={fmt(7 * k, 0)} />
          <Kpi label="Énergie délivrée" value={fmt(total, 0)} unit="kWh" />
        </div>
        <div style={{ marginTop: 16, display: "flex", flexDirection: "column", gap: 9 }}>
          {bars.map((b) => (
            <div key={b.label} style={{ display: "flex", alignItems: "center", gap: 12, fontSize: 15 }}>
              <div style={{ width: 96, color: C.ink }}>{b.label}</div>
              <div style={{ flex: 1, height: 8, background: "#edf1ee", borderRadius: 4 }}>
                <div style={{ width: `${(b.n / 24) * 100 * k}%`, height: "100%", background: b.color, borderRadius: 4 }} />
              </div>
              <div style={{ width: 22, textAlign: "right" }}>{Math.round(b.n * k)}</div>
            </div>
          ))}
        </div>
        <div
          style={{
            marginTop: 18,
            display: "flex",
            alignItems: "center",
            gap: 14,
            padding: "10px 12px",
            borderRadius: 12,
            border: `1.5px solid ${C.border}`,
            fontSize: 16,
            opacity: k,
          }}
        >
          <Icon.bolt size={20} color={C.greenBright} />
          <div style={{ fontWeight: 600 }}>CDT-1002</div>
          <div style={{ color: C.muted }}>
            {powerAt(frame)}
            {NNBSP}kW · {fmt(kwh)}
            {NNBSP}kWh
          </div>
          <div style={{ fontWeight: 600, color: C.greenMid }}>
            {fmt(price, 2)}
            {NNBSP}€
          </div>
          <div style={{ flex: 1 }} />
          <div
            style={{
              scale: press,
              padding: "7px 14px",
              borderRadius: 9,
              fontWeight: 600,
              fontSize: 15,
              color: applied ? C.greenMid : "#fff",
              background: applied ? `${C.greenBright}22` : pending ? C.greenMid : C.green,
              display: "flex",
              alignItems: "center",
              gap: 6,
              whiteSpace: "nowrap",
            }}
          >
            {applied && <Icon.check size={16} color={C.greenMid} stroke={3} />}
            {applied ? "7 kW appliqué" : pending ? "Envoi…" : "Limiter à 7 kW"}
          </div>
        </div>
        <svg
          width={34}
          height={34}
          viewBox="0 0 24 24"
          style={{ position: "absolute", left: cx, top: cy, opacity: cursorOn, scale: press, filter: "drop-shadow(0 4px 6px rgba(0,0,0,0.3))" }}
        >
          <path d="M4 2 L4 19 L8.5 15 L11.5 22 L14.5 20.7 L11.6 14 L18 14 Z" fill="#0f1a14" stroke="#fff" strokeWidth="1.5" strokeLinejoin="round" />
        </svg>
      </div>
    </div>
  );
};
