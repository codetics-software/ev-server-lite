import React from "react";
import { AbsoluteFill, interpolate } from "remotion";
import { useAbsFrame } from "../components/AbsTime";
import { DISPLAY, IN_OUT, K, OUT, ramp, SERIF, useFormat } from "./brand";

export const Shell: React.FC<{ readonly to: number; readonly gap?: number; readonly children: React.ReactNode }> = ({ to, gap = 44, children }) => {
  const frame = useAbsFrame();
  const { top, bottom } = useFormat();
  const out = interpolate(frame, [to - 9, to], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: IN_OUT });
  return (
    <AbsoluteFill
      style={{
        zIndex: 1,
        isolation: "isolate",
        paddingTop: top,
        paddingBottom: bottom,
        alignItems: "center",
        justifyContent: "center",
        flexDirection: "column",
        gap,
        opacity: 1 - out,
        scale: 1 + 0.08 * out,
        filter: out > 0 ? `blur(${out * 14}px)` : undefined,
      }}
    >
      {children}
    </AbsoluteFill>
  );
};

export const Words: React.FC<{ readonly text: string; readonly at: number; readonly stagger?: number; readonly style?: React.CSSProperties }> = ({
  text,
  at,
  stagger = 3,
  style,
}) => {
  const frame = useAbsFrame();
  return (
    <span style={style}>
      {text.split(" ").map((w, i) => {
        const p = ramp(frame, at + i * stagger, 16);
        return (
          <span
            key={i}
            style={{
              display: "inline-block",
              marginRight: "0.24em",
              opacity: Math.min(1, p * 1.6),
              transformOrigin: "50% 100%",
              transform: `perspective(700px) rotateX(${(1 - p) * -95}deg) translateY(${(1 - p) * 30}px)`,
            }}
          >
            {w}
          </span>
        );
      })}
    </span>
  );
};

export const Headline: React.FC<{ readonly main: string; readonly accent: string; readonly at: number; readonly size?: number }> = ({ main, accent, at, size = 116 }) => (
  <div style={{ textAlign: "center", lineHeight: 0.98, letterSpacing: "-0.035em", color: K.ink, padding: "0 60px" }}>
    <Words text={main} at={at} style={{ fontFamily: DISPLAY, fontWeight: 700, fontSize: size }} />
    <br />
    <Words text={accent} at={at + 6} style={{ fontFamily: SERIF, fontStyle: "italic", fontSize: size * 1.08, color: K.blue, letterSpacing: "-0.01em" }} />
  </div>
);

export const Rise: React.FC<{ readonly at: number; readonly children: React.ReactNode; readonly style?: React.CSSProperties; readonly dist?: number }> = ({
  at,
  children,
  style,
  dist = 40,
}) => {
  const frame = useAbsFrame();
  const p = ramp(frame, at, 18, OUT);
  return <div style={{ opacity: p, translate: `0 ${(1 - p) * dist}px`, ...style }}>{children}</div>;
};
