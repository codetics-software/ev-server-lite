import React from "react";
import { interpolate } from "remotion";
import { useAbsFrame } from "../components/AbsTime";
import { Icon } from "../components/Art";
import { CardHeader, Pill } from "../components/FloatCard";
import { fmt } from "../story";
import { C, NNBSP } from "../theme";
import { cue, EASE_OUT } from "../time";

export const ZoneNode: React.FC = () => {
  const frame = useAbsFrame();
  const car3 = cue("zone.car3");
  const bal = cue("zone.balance");
  const b = interpolate(frame, [bal, bal + 24], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: EASE_OUT });
  const arrive = interpolate(frame, [car3, car3 + 12], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: EASE_OUT });
  const rows = [
    { name: "Voiture 1", kw: 11 - 3.67 * b, show: 1 },
    { name: "Voiture 2", kw: 11 - 3.67 * b, show: 1 },
    { name: "Voiture 3", kw: 7.33 * b, show: arrive },
  ];
  const total = rows.reduce((s, r) => s + r.kw, 0);
  return (
    <>
      <CardHeader title="Site · Parking visiteurs" right={<span style={{ fontSize: 20, color: C.muted, fontWeight: 400 }}>Limite 22{NNBSP}kW</span>} />
      <div style={{ padding: "12px 22px", display: "flex", flexDirection: "column", gap: 12 }}>
        {rows.map((r) => (
          <div key={r.name} style={{ display: "flex", alignItems: "center", gap: 14, fontSize: 20, opacity: r.show, translate: `${(1 - r.show) * -40}px 0` }}>
            <Icon.car size={30} color={C.greenMid} />
            <div style={{ width: 110 }}>{r.name}</div>
            <div style={{ flex: 1, height: 16, borderRadius: 8, background: "#edf1ee" }}>
              <div style={{ width: `${(r.kw / 11) * 100}%`, height: "100%", borderRadius: 8, background: `linear-gradient(90deg, ${C.greenMid}, ${C.energy})` }} />
            </div>
            <div style={{ width: 86, textAlign: "right", fontWeight: 600 }}>
              {fmt(r.kw)}
              {NNBSP}kW
            </div>
          </div>
        ))}
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginTop: 6, paddingTop: 12, borderTop: `1px solid ${C.border}`, fontSize: 20 }}>
          <span style={{ color: C.muted }}>
            Total {fmt(total)} / 22{NNBSP}kW
          </span>
          <span style={{ opacity: b }}>
            <Pill color={C.greenBright} size={18}>Partage automatique</Pill>
          </span>
        </div>
      </div>
    </>
  );
};

export const RoamingNode: React.FC = () => {
  const frame = useAbsFrame();
  const at = cue("roaming.snap");
  const partners = [
    { n: "Réseau partenaire A", c: C.purple },
    { n: "Réseau partenaire B", c: C.orange },
    { n: "Réseau partenaire C", c: "#1f8fff" },
  ];
  return (
    <>
      <CardHeader title="Itinérance" right={<Pill color={C.purple} size={17}>OCPI</Pill>} />
      <div style={{ display: "flex", gap: 18, padding: "16px 22px", alignItems: "center" }}>
        <div style={{ rotate: `${frame * 0.6}deg` }}>
          <Icon.globe size={92} color={C.greenMid} stroke={1.4} />
        </div>
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {partners.map((p, i) => {
            const s = interpolate(frame, [at + 4 + i * 5, at + 14 + i * 5], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: EASE_OUT });
            return (
              <div key={p.n} style={{ display: "flex", alignItems: "center", gap: 10, fontSize: 20, opacity: s, translate: `${(1 - s) * 30}px 0` }}>
                <span style={{ width: 14, height: 14, borderRadius: 7, background: p.c }} />
                {p.n}
              </div>
            );
          })}
        </div>
      </div>
      <div style={{ padding: "0 22px", fontSize: 18, color: C.muted }}>Sites, tarifs et sessions partagés</div>
    </>
  );
};
