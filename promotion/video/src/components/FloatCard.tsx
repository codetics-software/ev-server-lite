import React from "react";
import { interpolate, spring, useVideoConfig } from "remotion";
import { C, FONT, MONO } from "../theme";
import { useAbsFrame } from "./AbsTime";

type Props = {
  readonly x: number;
  readonly y: number;
  readonly w: number;
  readonly h: number;
  readonly appear: number;
  readonly snap: number;
  readonly tag: string;
  readonly from?: { dx: number; dy: number; rot: number };
  readonly bare?: boolean;
  readonly children: React.ReactNode;
};

export const FloatCard: React.FC<Props> = ({
  x,
  y,
  w,
  h,
  appear,
  snap,
  tag,
  from = { dx: 0, dy: 160, rot: -8 },
  bare,
  children,
}) => {
  const frame = useAbsFrame();
  const { fps } = useVideoConfig();
  if (frame < appear) return null;

  const enter = spring({ frame: frame - appear, fps, config: { damping: 16, stiffness: 110, mass: 0.9 } });
  const stick = spring({ frame: frame - snap, fps, config: { damping: 10, stiffness: 280, mass: 0.6 } });
  const floating = 1 - Math.min(1, stick);
  const t = frame / fps;
  const bob = Math.sin(t * 2.4 + x * 0.013) * 12 * floating;
  const sway = Math.sin(t * 1.6 + y * 0.017) * 2.4 * floating;
  const ring = interpolate(frame - snap, [0, 20], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const ringOn = frame >= snap ? 1 - ring : 0;
  const tagOpacity =
    enter * interpolate(frame, [snap + 8, snap + 22], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });

  return (
    <div
      style={{
        position: "absolute",
        left: x - w / 2,
        top: y - h / 2,
        width: w,
        height: h,
        opacity: Math.min(1, enter * 1.6),
        translate: `${from.dx * (1 - enter)}px ${from.dy * (1 - enter) + bob - 18 * floating}px`,
        rotate: `${from.rot * (1 - enter) + sway}deg`,
        scale: (0.82 + 0.18 * enter) * (1 + 0.05 * (1 - stick)),
      }}
    >
      <div
        style={{
          position: "absolute",
          inset: 0,
          borderRadius: 22,
          background: bare ? "transparent" : C.card,
          border: bare ? "none" : `1.5px solid ${C.border}`,
          boxShadow: bare
            ? "none"
            : `0 ${10 + 36 * floating}px ${24 + 60 * floating}px rgba(15,40,28,${0.1 + 0.12 * floating}), 0 0 0 ${3 + 26 * ring}px rgba(47,191,113,${0.55 * ringOn})`,
          overflow: "hidden",
          fontFamily: FONT,
          color: C.ink,
        }}
      >
        {children}
      </div>
      <div
        style={{
          position: "absolute",
          inset: -10,
          borderRadius: 28,
          border: `2px dashed ${C.greenBright}`,
          opacity: floating * enter * 0.9,
        }}
      />
      {[
        [-14, -14],
        [w + 4, -14],
        [-14, h + 4],
        [w + 4, h + 4],
      ].map(([hx, hy], i) => (
        <div
          key={i}
          style={{
            position: "absolute",
            left: hx,
            top: hy,
            width: 10,
            height: 10,
            borderRadius: 2,
            background: C.card,
            border: `2px solid ${C.greenBright}`,
            opacity: floating * enter,
          }}
        />
      ))}
      <div
        style={{
          position: "absolute",
          left: -10,
          top: -54,
          padding: "6px 14px",
          borderRadius: 10,
          background: C.greenBright,
          color: "#fff",
          fontFamily: MONO,
          fontSize: 20,
          fontWeight: 600,
          whiteSpace: "nowrap",
          opacity: tagOpacity,
        }}
      >
        {`<${tag} />`}
      </div>
    </div>
  );
};

export const CardHeader: React.FC<{ readonly title: string; readonly right?: React.ReactNode }> = ({
  title,
  right,
}) => (
  <div
    style={{
      display: "flex",
      alignItems: "center",
      justifyContent: "space-between",
      padding: "18px 22px 0",
      fontSize: 22,
      fontWeight: 600,
      color: C.ink,
    }}
  >
    <span>{title}</span>
    {right}
  </div>
);

export const Pill: React.FC<{
  readonly color: string;
  readonly children: React.ReactNode;
  readonly size?: number;
  readonly solid?: boolean;
}> = ({ color, children, size = 18, solid }) => (
  <span
    style={{
      display: "inline-flex",
      alignItems: "center",
      gap: 8,
      padding: `${size * 0.3}px ${size * 0.7}px`,
      borderRadius: 999,
      fontSize: size,
      fontWeight: 600,
      whiteSpace: "nowrap",
      color: solid ? "#fff" : color,
      background: solid ? color : `${color}1f`,
    }}
  >
    <span style={{ width: size * 0.45, height: size * 0.45, borderRadius: 99, background: solid ? "#fff" : color }} />
    {children}
  </span>
);
