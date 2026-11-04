import React from "react";
import { AbsoluteFill, interpolate } from "remotion";
import { useAbsFrame } from "../components/AbsTime";
import { K, OUT, TOTAL, useFormat } from "./brand";
import { Box } from "./three";

const FLOATERS = [
  { x: -0.4, y: -0.41, z: -300, s: 90, c: K.lilac, sp: 0.8 },
  { x: 0.4, y: -0.39, z: -500, s: 70, c: K.tangerine, sp: -1.1 },
  { x: 0.42, y: 0.38, z: -200, s: 110, c: K.blue, sp: 0.6 },
  { x: -0.42, y: 0.4, z: -600, s: 60, c: K.butter, sp: -0.9 },
  { x: 0.3, y: 0.47, z: -900, s: 50, c: K.mint, sp: 1.3 },
  { x: -0.3, y: -0.47, z: -800, s: 64, c: K.lilac, sp: -0.7 },
];

export const Backdrop: React.FC = () => {
  const frame = useAbsFrame();
  const { W, H } = useFormat();
  const drift = frame / TOTAL;
  const grow = interpolate(frame, [0, 24], [0.001, 1], { extrapolateRight: "clamp", easing: OUT });
  return (
    <AbsoluteFill style={{ background: K.paper, overflow: "hidden", isolation: "isolate", zIndex: 0 }}>
      <AbsoluteFill
        style={{
          background: `radial-gradient(48% 40% at ${12 + 8 * Math.sin(drift * 6)}% 8%, ${K.lilac}88, transparent 70%),
            radial-gradient(40% 34% at ${88 - 6 * Math.sin(drift * 5)}% 2%, ${K.tangerine}44, transparent 70%),
            radial-gradient(60% 50% at 62% ${100 - 4 * Math.cos(drift * 4)}%, ${K.blue}30, transparent 70%)`,
        }}
      />
      <div style={{ position: "absolute", inset: 0, perspective: 900, perspectiveOrigin: "50% 30%" }}>
        <div
          style={{
            position: "absolute",
            left: -W,
            width: W * 3,
            top: H * 0.55,
            height: H * 1.4,
            transform: "rotateX(76deg)",
            transformOrigin: "50% 0%",
            backgroundImage: `linear-gradient(${K.ink}14 2px, transparent 2px), linear-gradient(90deg, ${K.ink}14 2px, transparent 2px)`,
            backgroundSize: "120px 120px",
            backgroundPosition: `0px ${frame * 3}px`,
            maskImage: "linear-gradient(to bottom, transparent, black 30%, black 60%, transparent)",
          }}
        />
      </div>
      <AbsoluteFill style={{ perspective: 1400 }}>
        {FLOATERS.map((f, i) => (
          <div key={i} style={{ position: "absolute", left: W / 2 + f.x * W * (1 - f.z / 1400), top: H / 2 + f.y * H * (1 - f.z / 1400), transformStyle: "preserve-3d" }}>
            <Box
              w={f.s}
              h={f.s}
              d={f.s}
              color={f.c}
              transform={`translateZ(${f.z}px) translateY(${18 * Math.sin(frame / 40 + i)}px) rotateX(${frame * f.sp + i * 40}deg) rotateY(${frame * f.sp * 1.3 + i * 25}deg) scale3d(${grow}, ${grow}, ${grow})`}
            />
          </div>
        ))}
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
