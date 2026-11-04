import React from "react";
import { AbsoluteFill, spring, useVideoConfig } from "remotion";
import { useAbsFrame } from "../components/AbsTime";
import { Backdrop } from "./Backdrop";
import { at, BACK, DISPLAY, IN_OUT, K, ramp, SERIF, TEXT, useFormat } from "./brand";
import { Rise, Words } from "./parts";
import { Box, Logo3D, Stage } from "./three";

const SHARDS = Array.from({ length: 14 }, (_, i) => {
  const a = (i / 14) * Math.PI * 2 + (i % 3) * 0.3;
  return { a, r: 420 + (i % 4) * 90, z: -200 + (i % 5) * 140, s: 36 + (i % 3) * 22, c: [K.butter, K.tangerine, K.lilac, K.mint, "#ffffff"][i % 5], sp: 3 + (i % 4) };
});

export const Free: React.FC = () => {
  const frame = useAbsFrame();
  const { fps } = useVideoConfig();
  const { W, H, tall } = useFormat();
  const start = at("free");
  const wipe = ramp(frame, start - 6, 14, IN_OUT);
  const pop = spring({ frame: frame - start, fps, config: { damping: 11, stiffness: 160 } });
  const burst = ramp(frame, start, 40);
  const radius = Math.hypot(W, H) * wipe;
  return (
    <AbsoluteFill style={{ clipPath: `circle(${radius}px at 50% 50%)`, background: `radial-gradient(70% 60% at 50% 40%, #4a5bff, ${K.blue} 55%, ${K.blueDeep})` }}>
      <AbsoluteFill style={{ perspective: 1200 }}>
        <div style={{ position: "absolute", left: "50%", top: "48%", transformStyle: "preserve-3d" }}>
          {SHARDS.map((s, i) => (
            <Box
              key={i}
              w={s.s}
              h={s.s}
              d={s.s}
              color={s.c}
              transform={`translate3d(${Math.cos(s.a) * s.r * burst}px, ${Math.sin(s.a) * s.r * burst * (tall ? 1.5 : 1.1)}px, ${s.z * burst}px) rotateX(${frame * s.sp}deg) rotateY(${frame * s.sp * 0.7}deg) scale3d(${burst}, ${burst}, ${burst})`}
            />
          ))}
        </div>
      </AbsoluteFill>
      <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", flexDirection: "column", gap: 18 }}>
        <div style={{ fontFamily: DISPLAY, fontWeight: 800, fontSize: 250, letterSpacing: "-0.05em", color: "#fff", lineHeight: 0.9, scale: 0.6 + 0.4 * pop, opacity: Math.min(1, pop * 2) }}>
          Gratuit.
        </div>
        <Words text="à installer chez vous." at={start + 8} style={{ fontFamily: SERIF, fontStyle: "italic", fontSize: 104, color: K.butter }} />
        <Rise at={start + 18}>
          <div style={{ marginTop: 26, fontFamily: TEXT, fontSize: 32, fontWeight: 600, color: "#dfe3ff" }}>Code source inclus · licence d’attribution Codetics</div>
        </Rise>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};

export const Cta: React.FC = () => {
  const frame = useAbsFrame();
  const { fps } = useVideoConfig();
  const { W, H, tall, top, bottom } = useFormat();
  const start = at("cta");
  const reveal = ramp(frame, start - 4, 16, IN_OUT);
  const spin = spring({ frame: frame - start, fps, config: { damping: 15, stiffness: 70 } });
  const btn = spring({ frame: frame - at("cta.button"), fps, config: { damping: 10, stiffness: 150 } });
  const pulse = ramp(frame, at("final"), 24);
  const flourish = ramp(frame, at("final") - 2, 30, BACK);
  return (
    <AbsoluteFill style={{ clipPath: `circle(${Math.hypot(W, H) * reveal}px at 50% 50%)` }}>
      <Backdrop />
      <AbsoluteFill style={{ paddingTop: top, paddingBottom: bottom, alignItems: "center", justifyContent: "center", flexDirection: "column", gap: tall ? 46 : 34 }}>
        <Stage perspective={1100} style={{ width: 300, height: tall ? 250 : 220 }}>
          <Logo3D size={tall ? 280 : 250} depth={16} transform={`rotateY(${(1 - spin) * 360 + flourish * 360 + 10 * Math.sin(frame / 18)}deg) rotateX(${6 * Math.sin(frame / 25)}deg)`} />
        </Stage>
        <div style={{ textAlign: "center", lineHeight: 0.98, letterSpacing: "-0.035em" }}>
          <Words text="Besoin de plus ?" at={start + 4} style={{ fontFamily: DISPLAY, fontWeight: 750, fontSize: tall ? 120 : 112, color: K.ink }} />
        </div>
        <Rise at={start + 14}>
          <div style={{ fontFamily: TEXT, fontSize: 38, lineHeight: 1.3, color: K.ink2, fontWeight: 500, textAlign: "center", maxWidth: 880 }}>
            Supervision avancée, intégrations sur mesure et accompagnement : parlons de votre réseau.
          </div>
        </Rise>
        <div
          style={{
            position: "relative",
            display: "flex",
            alignItems: "center",
            gap: 26,
            padding: "30px 56px",
            borderRadius: 999,
            background: K.blue,
            color: "#fff",
            fontFamily: DISPLAY,
            fontWeight: 700,
            fontSize: 86,
            letterSpacing: "-0.03em",
            scale: btn * (1 + 0.05 * Math.sin(pulse * Math.PI)),
            boxShadow: `0 30px 60px -24px ${K.blue}aa, 0 0 0 ${18 * pulse}px rgba(51,70,232,${0.25 * (1 - pulse)})`,
          }}
        >
          codetics.fr
          <span style={{ display: "inline-flex", width: 84, height: 84, borderRadius: 42, background: "#fff", color: K.blue, alignItems: "center", justifyContent: "center", fontSize: 56, translate: `${8 * Math.sin(frame / 6)}px 0` }}>
            →
          </span>
        </div>
        <Rise at={at("cta.button") + 10}>
          <div style={{ fontFamily: TEXT, fontSize: 28, color: K.ink2, fontWeight: 600 }}>
            EV Supervision Lite · par Kevin Vavelin, <span style={{ color: K.ink }}>Codetics</span>
          </div>
        </Rise>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
