import React from "react";
import { AbsoluteFill, interpolate } from "remotion";
import { useAbsFrame } from "../components/AbsTime";
import { C, FONT, NNBSP } from "../theme";
import { cue, sectionFrom } from "../time";

export const Reveal: React.FC = () => {
  const frame = useAbsFrame();
  const start = sectionFrom("reveal");
  const end = sectionFrom("title");
  const q = cue("reveal.question");
  const grow = interpolate(frame, [start, end], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const r = Math.pow(grow, 3.2) * 1300;
  const words = `Et si chaque borne devenait visible${NNBSP}?`.split(" ");
  const textOut = interpolate(frame, [end - 10, end - 2], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  return (
    <AbsoluteFill style={{ background: C.night, fontFamily: FONT, alignItems: "center", justifyContent: "center", overflow: "hidden" }}>
      {Array.from({ length: 28 }).map((_, i) => {
        const a = (i / 28) * Math.PI * 2 + i;
        const d = interpolate(grow, [0, 1], [900 + (i % 5) * 120, 0]);
        return (
          <div
            key={i}
            style={{
              position: "absolute",
              left: 960 + Math.cos(a) * d,
              top: 540 + Math.sin(a) * d,
              width: 8,
              height: 8,
              borderRadius: 4,
              background: i % 3 ? C.energy : C.cyan,
              opacity: 0.25 + 0.6 * grow,
              boxShadow: `0 0 12px ${C.energy}`,
            }}
          />
        );
      })}
      <div
        style={{
          position: "absolute",
          width: r * 2,
          height: r * 2,
          borderRadius: r,
          background: `radial-gradient(circle, #ffffff 0%, ${C.bg} 55%, rgba(243,245,242,0.0) 72%)`,
          opacity: interpolate(grow, [0, 0.1], [0, 1], { extrapolateRight: "clamp" }),
        }}
      />
      <div style={{ fontSize: 104, fontWeight: 600, letterSpacing: -2, textAlign: "center", maxWidth: 1500, lineHeight: 1.1, opacity: textOut }}>
        {words.map((w, i) => {
          const p = interpolate(frame, [q + i * 4, q + i * 4 + 14], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
          return (
            <span key={i} style={{ display: "inline-block", whiteSpace: "pre", opacity: p, translate: `0 ${(1 - p) * 40}px`, color: "#fff", mixBlendMode: "difference" }}>
              {w + " "}
            </span>
          );
        })}
      </div>
    </AbsoluteFill>
  );
};
