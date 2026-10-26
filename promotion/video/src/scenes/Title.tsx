import React from "react";
import { AbsoluteFill, interpolate, spring, useVideoConfig } from "remotion";
import { useAbsFrame } from "../components/AbsTime";
import { Icon, Logo } from "../components/Art";
import { C, FONT } from "../theme";
import { cue, EASE_IN_OUT, sectionFrom } from "../time";

const ORBIT: { icon: keyof typeof Icon; label: string; x: number; y: number }[] = [
  { icon: "car", label: "Véhicules", x: 300, y: 290 },
  { icon: "bolt", label: "Bornes", x: 1620, y: 290 },
  { icon: "server", label: "Serveur", x: 250, y: 540 },
  { icon: "chart", label: "Supervision", x: 1670, y: 540 },
  { icon: "euro", label: "Paiement", x: 300, y: 790 },
  { icon: "globe", label: "Itinérance", x: 1620, y: 790 },
];

export const Title: React.FC = () => {
  const frame = useAbsFrame();
  const { fps } = useVideoConfig();
  const drop = cue("title.drop");
  const s = spring({ frame: frame - drop, fps, config: { damping: 11, stiffness: 140 } });
  const flash = interpolate(frame, [drop, drop + 12], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const exit = interpolate(frame, [cue("title.badge") - 4, sectionFrom("plug")], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: EASE_IN_OUT });
  return (
    <AbsoluteFill style={{ background: C.bg, fontFamily: FONT, overflow: "hidden" }}>
      <AbsoluteFill style={{ backgroundImage: "radial-gradient(circle, #c9d3cc 2px, transparent 2.4px)", backgroundSize: "48px 48px", opacity: 0.7 }} />
      <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", opacity: 1 - exit, scale: 1 - 0.5 * exit, translate: `${-700 * exit}px ${-380 * exit}px` }}>
        {ORBIT.map((o, i) => {
          const p = spring({ frame: frame - drop - 4 - i * 3, fps, config: { damping: 12, stiffness: 120 } });
          const t = frame / fps;
          const I = Icon[o.icon];
          return (
            <div
              key={o.label}
              style={{
                position: "absolute",
                left: 960 + (o.x - 960) * p,
                top: 540 + (o.y - 540) * p + Math.sin(t * 2 + i) * 8,
                translate: "-50% -50%",
                opacity: p,
                display: "flex",
                alignItems: "center",
                gap: 12,
                padding: "14px 22px",
                borderRadius: 18,
                background: C.card,
                border: `1.5px solid ${C.border}`,
                boxShadow: "0 18px 40px rgba(15,40,28,0.12)",
                fontSize: 28,
                fontWeight: 600,
                color: C.ink,
              }}
            >
              <I size={32} color={C.greenMid} />
              {o.label}
            </div>
          );
        })}
        <div style={{ display: "flex", flexDirection: "column", alignItems: "center", scale: 0.6 + 0.4 * s, opacity: Math.min(1, s * 2) }}>
          <Logo size={170} />
          <div style={{ fontSize: 150, fontWeight: 600, letterSpacing: -4, color: C.ink, lineHeight: 1, marginTop: 24 }}>Supervision Lite</div>
          <div style={{ fontSize: 46, color: C.muted, marginTop: 18 }}>
            par <span style={{ color: C.greenMid, fontWeight: 600 }}>Codetics</span>
          </div>
        </div>
      </AbsoluteFill>
      <AbsoluteFill style={{ background: "#fff", opacity: flash }} />
    </AbsoluteFill>
  );
};

export const Badge: React.FC = () => {
  const frame = useAbsFrame();
  const inP = interpolate(frame, [cue("title.badge") + 6, sectionFrom("plug") + 6], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const outP = interpolate(frame, [sectionFrom("method") - 12, sectionFrom("method")], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  return (
    <div
      style={{
        position: "absolute",
        right: 64,
        bottom: 60,
        display: "flex",
        alignItems: "center",
        gap: 12,
        fontFamily: FONT,
        fontSize: 26,
        fontWeight: 600,
        color: C.ink,
        opacity: inP * outP * 0.9,
        translate: `${(1 - inP) * 30}px 0`,
      }}
    >
      <Logo size={40} />
      Codetics
    </div>
  );
};
