import React from "react";
import { AbsoluteFill, interpolate, spring, useVideoConfig } from "remotion";
import { useAbsFrame } from "../components/AbsTime";
import { Logo } from "../components/Art";
import { C, FONT } from "../theme";
import { cue, EASE_IN_OUT, EASE_OUT, sectionFrom, TOTAL_FRAMES } from "../time";

export const Outro: React.FC = () => {
  const frame = useAbsFrame();
  const { fps } = useVideoConfig();
  const start = sectionFrom("outro");
  const wipe = interpolate(frame, [start, start + 22], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: EASE_IN_OUT });
  const s = spring({ frame: frame - start - 8, fps, config: { damping: 14, stiffness: 110 } });
  const line = interpolate(frame, [start + 20, start + 40], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: EASE_OUT });
  const cta = spring({ frame: frame - cue("outro.cta"), fps, config: { damping: 12, stiffness: 140 } });
  const black = interpolate(frame, [TOTAL_FRAMES - 24, TOTAL_FRAMES - 2], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  return (
    <AbsoluteFill style={{ background: C.bg, fontFamily: FONT, overflow: "hidden" }}>
      <div
        style={{
          position: "absolute",
          left: 960 - 1200 * wipe,
          top: 540 - 1200 * wipe,
          width: 2400 * wipe,
          height: 2400 * wipe,
          borderRadius: "50%",
          background: `radial-gradient(circle at 50% 45%, #1d5a3e 0%, ${C.green} 35%, #0b2219 70%)`,
        }}
      />
      <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", flexDirection: "column" }}>
        <div style={{ scale: 0.7 + 0.3 * s, opacity: Math.min(1, s * 2), display: "flex", flexDirection: "column", alignItems: "center" }}>
          <div style={{ width: 210, height: 210, borderRadius: 60, background: "#fff", display: "flex", alignItems: "center", justifyContent: "center", boxShadow: "0 30px 80px rgba(0,0,0,0.35)" }}>
            <Logo size={150} />
          </div>
          <div style={{ fontSize: 120, fontWeight: 600, color: "#fff", letterSpacing: -3, marginTop: 40, lineHeight: 1 }}>Supervision Lite</div>
        </div>
        <div style={{ fontSize: 46, color: "#cfe9dc", marginTop: 26, opacity: line, translate: `0 ${(1 - line) * 20}px` }}>
          La supervision de recharge, sans le superflu.
        </div>
        <div
          style={{
            marginTop: 56,
            padding: "22px 44px",
            borderRadius: 999,
            background: "#fff",
            color: C.green,
            fontSize: 40,
            fontWeight: 600,
            scale: cta,
            opacity: Math.min(1, cta * 2),
            boxShadow: `0 0 0 ${10 * (1 - Math.min(1, cta))}px rgba(62,224,143,0.4)`,
          }}
        >
          Parlons de votre réseau · <span style={{ color: C.greenMid }}>codetics.fr</span>
        </div>
      </AbsoluteFill>
      <AbsoluteFill style={{ background: "#000", opacity: black }} />
    </AbsoluteFill>
  );
};
