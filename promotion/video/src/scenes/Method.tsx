import React from "react";
import { AbsoluteFill, interpolate, spring, useVideoConfig } from "remotion";
import { useAbsFrame } from "../components/AbsTime";
import { Icon } from "../components/Art";
import { C, FONT, MONO } from "../theme";
import { BEAT, cue, CueName, EASE_OUT, sectionFrom } from "../time";

type P = { cue: CueName; icon: keyof typeof Icon; title: string; text: string; from: [number, number, number] };

const PRINCIPLES: P[] = [
  { cue: "method.1", icon: "feather", title: "Léger", text: "Un serveur, un tableau de bord, une base. Rien de plus.", from: [-500, -300, -14] },
  { cue: "method.2", icon: "target", title: "Fiable", text: "Chaque commande est suivie jusqu’à sa réponse.", from: [200, 600, 10] },
  { cue: "method.3", icon: "eye", title: "Honnête", text: "Une recharge n’est affichée que si la borne la confirme.", from: [0, -620, -8] },
  { cue: "method.4", icon: "shield", title: "Sécurisé", text: "Chaque client dispose de son propre espace, isolé.", from: [-200, 620, 12] },
  { cue: "method.5", icon: "puzzle", title: "Ouvert", text: "Standards OCPP et OCPI, PostgreSQL ou MongoDB, hébergement libre.", from: [520, -300, 14] },
];

const CW = 318;
const CH = 460;
const GAP = 30;
const X0 = (1920 - (CW * 5 + GAP * 4)) / 2;
const Y0 = 300;

const Slot: React.FC<{ readonly i: number }> = ({ i }) => {
  const frame = useAbsFrame();
  const p = interpolate(frame, [cue("method.title") + i * 3, cue("method.title") + 14 + i * 3], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  return (
    <div
      style={{
        position: "absolute",
        left: X0 + i * (CW + GAP),
        top: Y0,
        width: CW,
        height: CH,
        borderRadius: 26,
        border: `2.5px dashed ${C.faint}`,
        opacity: p * 0.7,
        scale: 0.9 + 0.1 * p,
      }}
    />
  );
};

const PrincipleCard: React.FC<{ readonly p: P; readonly i: number }> = ({ p, i }) => {
  const frame = useAbsFrame();
  const { fps } = useVideoConfig();
  const snap = cue(p.cue);
  const appear = snap - BEAT * 2;
  if (frame < appear) return null;
  const enter = spring({ frame: frame - appear, fps, config: { damping: 15, stiffness: 120 } });
  const stick = spring({ frame: frame - snap, fps, config: { damping: 10, stiffness: 280, mass: 0.6 } });
  const floating = 1 - Math.min(1, stick);
  const t = frame / fps;
  const ring = interpolate(frame - snap, [0, 20], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const ringOn = frame >= snap ? 1 - ring : 0;
  const I = Icon[p.icon];
  return (
    <div
      style={{
        position: "absolute",
        left: X0 + i * (CW + GAP),
        top: Y0,
        width: CW,
        height: CH,
        translate: `${p.from[0] * (1 - enter)}px ${p.from[1] * (1 - enter) - 30 * floating + Math.sin(t * 2.6 + i) * 12 * floating}px`,
        rotate: `${p.from[2] * (1 - enter) + Math.sin(t * 1.7 + i) * 3 * floating}deg`,
        scale: 1 + 0.06 * (1 - stick),
        opacity: Math.min(1, enter * 1.5),
        borderRadius: 26,
        background: C.card,
        border: `1.5px solid ${C.border}`,
        boxShadow: `0 ${12 + 40 * floating}px ${28 + 60 * floating}px rgba(15,40,28,${0.1 + 0.12 * floating}), 0 0 0 ${3 + 26 * ring}px rgba(47,191,113,${0.55 * ringOn})`,
        padding: "36px 32px",
        fontFamily: FONT,
      }}
    >
      <div style={{ width: 84, height: 84, borderRadius: 24, background: C.green, display: "flex", alignItems: "center", justifyContent: "center" }}>
        <I size={46} color={C.energy} />
      </div>
      <div style={{ fontSize: 52, fontWeight: 600, color: C.ink, marginTop: 30, letterSpacing: -1 }}>{p.title}</div>
      <div style={{ fontSize: 30, lineHeight: 1.28, color: C.muted, marginTop: 14 }}>{p.text}</div>
      <div
        style={{
          position: "absolute",
          left: -6,
          top: -50,
          padding: "6px 14px",
          borderRadius: 10,
          background: C.greenBright,
          color: "#fff",
          fontFamily: MONO,
          fontSize: 20,
          fontWeight: 600,
          opacity: enter * interpolate(frame, [snap + 8, snap + 22], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }),
        }}
      >
        {`<${p.title.normalize("NFD").replace(/[̀-ͯ]/g, "")} />`}
      </div>
    </div>
  );
};

export const Method: React.FC = () => {
  const frame = useAbsFrame();
  const start = sectionFrom("method");
  const t = cue("method.title");
  const head = interpolate(frame, [t, t + 18], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: EASE_OUT });
  const tag = interpolate(frame, [cue("method.tagline"), cue("method.tagline") + 18], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: EASE_OUT });
  const fadeIn = interpolate(frame, [start, start + 12], [0, 1], { extrapolateRight: "clamp" });
  return (
    <AbsoluteFill style={{ background: C.bg, fontFamily: FONT, overflow: "hidden" }}>
      <AbsoluteFill style={{ opacity: fadeIn }}>
        <AbsoluteFill style={{ backgroundImage: "radial-gradient(circle, #c9d3cc 2px, transparent 2.4px)", backgroundSize: "48px 48px", opacity: 0.7 }} />
        <div style={{ position: "absolute", left: X0, top: 100, opacity: head, translate: `0 ${(1 - head) * 30}px` }}>
          <div style={{ display: "flex", alignItems: "center", gap: 14, fontSize: 28, fontWeight: 600, letterSpacing: 2, textTransform: "uppercase", color: C.greenMid }}>
            <span style={{ width: 36, height: 3, background: C.greenMid, borderRadius: 2 }} />
            Notre méthode
          </div>
          <div style={{ fontSize: 84, fontWeight: 600, color: C.ink, letterSpacing: -2, marginTop: 8 }}>Comment nous construisons chez Codetics</div>
        </div>
        {PRINCIPLES.map((p, i) => (
          <Slot key={`s${i}`} i={i} />
        ))}
        {PRINCIPLES.map((p, i) => (
          <PrincipleCard key={p.cue} p={p} i={i} />
        ))}
        <div style={{ position: "absolute", left: 0, right: 0, top: 845, textAlign: "center", fontSize: 64, fontWeight: 600, letterSpacing: -1, opacity: tag, translate: `0 ${(1 - tag) * 30}px` }}>
          <span style={{ color: C.ink }}>Simple à comprendre. </span>
          <span style={{ color: C.greenMid }}>Solide en production.</span>
        </div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
