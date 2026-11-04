import React from "react";
import { spring, useVideoConfig } from "remotion";
import { useAbsFrame } from "../components/AbsTime";
import { at, DISPLAY, K, ramp, SERIF, TEXT, useFormat } from "./brand";
import { Rise, Shell, Words } from "./parts";
import { Chip, Logo3D, Stage } from "./three";

export const Intro: React.FC = () => {
  const frame = useAbsFrame();
  const { fps } = useVideoConfig();
  const { tall } = useFormat();
  const spin = spring({ frame: frame - at("logo"), fps, config: { damping: 16, stiffness: 60, mass: 1.2 } });
  const t = at("title");
  const lite = ramp(frame, t + 10, 22);
  return (
    <Shell to={at("drop")} gap={tall ? 56 : 40}>
      <Stage perspective={1100} style={{ width: 360, height: tall ? 330 : 290 }}>
        <Logo3D
          size={tall ? 330 : 290}
          depth={16}
          transform={`translateZ(${(1 - spin) * -900}px) rotateY(${(1 - spin) * -540 + 14 * Math.sin(frame / 22)}deg) rotateX(${8 * Math.sin(frame / 30)}deg)`}
        />
      </Stage>
      <Rise at={t - 4}>
        <Chip dot={K.tangerine}>Nouvelle version · octobre 2026</Chip>
      </Rise>
      <div style={{ textAlign: "center", lineHeight: 0.92, letterSpacing: "-0.04em", color: K.ink }}>
        <Words text="EV Supervision" at={t} style={{ fontFamily: DISPLAY, fontWeight: 750, fontSize: 138 }} />
        <br />
        <span
          style={{
            display: "inline-block",
            fontFamily: SERIF,
            fontStyle: "italic",
            fontSize: 190,
            color: K.blue,
            letterSpacing: "-0.01em",
            opacity: lite,
            transform: `perspective(900px) rotateY(${(1 - lite) * 80}deg) scale(${0.7 + 0.3 * lite})`,
          }}
        >
          Lite
        </span>
      </div>
      <Rise at={t + 22}>
        <div style={{ fontFamily: TEXT, fontSize: 40, color: K.ink2, textAlign: "center", fontWeight: 500 }}>Supervision de bornes de recharge · OCPP 1.6</div>
      </Rise>
    </Shell>
  );
};
