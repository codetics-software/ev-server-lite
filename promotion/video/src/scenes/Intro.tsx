import React from "react";
import { AbsoluteFill, interpolate, spring, useVideoConfig } from "remotion";
import { useAbsFrame } from "../components/AbsTime";
import { Logo } from "../components/Art";
import { C, FONT } from "../theme";
import { beat, cue, EASE_OUT, sectionFrom } from "../time";

const Line: React.FC<{ readonly at: number; readonly children: React.ReactNode; readonly color: string }> = ({ at, children, color }) => {
  const frame = useAbsFrame();
  const p = interpolate(frame, [at, at + 18], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: EASE_OUT });
  return (
    <div style={{ opacity: p, translate: `0 ${(1 - p) * 40}px`, filter: `blur(${(1 - p) * 10}px)`, color }}>{children}</div>
  );
};

export const Intro: React.FC = () => {
  const frame = useAbsFrame();
  const { fps } = useVideoConfig();
  const logoAt = cue("intro.logo");
  const s = spring({ frame: frame - logoAt, fps, config: { damping: 13, stiffness: 90 } });
  const lift = interpolate(frame, [cue("intro.text") - 6, cue("intro.text") + 14], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: EASE_OUT });
  const end = sectionFrom("problem");
  const out = interpolate(frame, [end - 14, end], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const glow = interpolate(frame, [logoAt, logoAt + 40], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  return (
    <AbsoluteFill style={{ background: `radial-gradient(circle at 50% 45%, #12271d 0%, ${C.night} 60%)`, fontFamily: FONT }}>
      <AbsoluteFill style={{ opacity: out, alignItems: "center", justifyContent: "center" }}>
        <div
          style={{
            position: "absolute",
            width: 700,
            height: 700,
            borderRadius: 350,
            background: `radial-gradient(circle, rgba(94,250,247,${0.16 * glow}) 0%, rgba(123,60,240,${0.1 * glow}) 35%, transparent 70%)`,
            translate: `0 ${-120 * lift}px`,
          }}
        />
        <div style={{ scale: s * (1 - 0.35 * lift), translate: `0 ${-170 * lift}px`, rotate: `${(1 - s) * -30}deg` }}>
          <Logo size={240} />
        </div>
        <div style={{ position: "absolute", top: 560, textAlign: "center", fontSize: 92, fontWeight: 600, letterSpacing: -1.5, lineHeight: 1.12 }}>
          <Line at={cue("intro.text")} color="#fff">
            Recharger, c’est simple.
          </Line>
          <Line at={cue("intro.text") + beat(3)} color={C.energy}>
            Superviser, beaucoup moins.
          </Line>
        </div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
