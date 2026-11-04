import React from "react";
import { CarArt } from "../components/Art";
import { BACK, DISPLAY, K, MONO, OUT, ramp, TEXT } from "./brand";
import { Box, Check, Stage } from "./three";

type ArtProps = { readonly t: number; readonly w: number; readonly h: number };

const CLOUDS = ["Google Cloud", "AWS", "Azure", "Scaleway", "Oracle"];

export const CloudArt: React.FC<ArtProps> = ({ t, w, h }) => {
  const grow = ramp(t, -6, 20, BACK);
  return (
    <Stage perspective={1300} style={{ width: w, height: h }}>
      <div style={{ transformStyle: "preserve-3d", transform: `rotateX(-14deg) scale3d(${grow}, ${grow}, ${grow})` }}>
        <Box
          w={150}
          h={150}
          d={150}
          color={K.blue}
          transform={`rotateX(-18deg) rotateY(${t * 2.2 + 30}deg)`}
          front={<div style={{ display: "flex", height: "100%", alignItems: "center", justifyContent: "center", color: "#fff", fontFamily: DISPLAY, fontWeight: 700, fontSize: 40 }}>Lite</div>}
        />
        {CLOUDS.map((c, i) => {
          const a = t * 0.035 + (i / CLOUDS.length) * Math.PI * 2;
          const pop = ramp(t, i * 3, 14, BACK);
          return (
            <div
              key={c}
              style={{
                position: "absolute",
                transform: `translate3d(${Math.cos(a) * 300}px, ${40 + Math.sin(a) * 26}px, ${Math.sin(a) * 260}px) translate(-50%, -50%) scale(${pop})`,
                padding: "14px 26px",
                borderRadius: 999,
                background: K.card,
                border: `2px solid ${K.line}`,
                boxShadow: "0 18px 30px -18px rgba(20,17,31,0.45)",
                fontFamily: TEXT,
                fontWeight: 700,
                fontSize: 30,
                color: K.ink,
                whiteSpace: "nowrap",
              }}
            >
              {c}
            </div>
          );
        })}
      </div>
    </Stage>
  );
};

const PROFILES = [
  { n: "1", c: K.lilac },
  { n: "2", c: K.blue },
  { n: "3", c: K.tangerine },
];

export const SecurityArt: React.FC<ArtProps> = ({ t, w, h }) => (
  <Stage perspective={1600} style={{ width: w, height: h }}>
    <div style={{ transformStyle: "preserve-3d", transform: `translateY(${h * 0.12}px) rotateX(60deg) rotateZ(${-40 + t * 0.35}deg)` }}>
      {PROFILES.map((p, i) => {
        const drop = ramp(t, -4 + i * 6, 16, BACK);
        return (
          <Box
            key={p.n}
            w={250}
            h={250}
            d={30}
            color={p.c}
            transform={`translateZ(${i * 78 + (1 - drop) * 520}px)`}
            style={{ visibility: drop > 0.001 ? "visible" : "hidden" }}
            front={
              <div style={{ display: "flex", height: "100%", alignItems: "center", justifyContent: "center", fontFamily: DISPLAY, fontWeight: 800, fontSize: 120, color: i === 0 ? K.ink : "#fff" }}>
                {p.n}
              </div>
            }
          />
        );
      })}
    </div>
  </Stage>
);

const UNITS = [
  { x: -230, w: 110, hh: 250, c: K.mint },
  { x: 0, w: 136, hh: 320, c: K.ink },
  { x: 230, w: 110, hh: 220, c: K.tangerine },
];

export const HardwareArt: React.FC<ArtProps> = ({ t, w, h }) => {
  const badge = ramp(t, 18, 16, BACK);
  return (
    <Stage perspective={1500} style={{ width: w, height: h }}>
      <div style={{ transformStyle: "preserve-3d", transform: `translateY(${h * 0.36}px) rotateX(-10deg) rotateY(${-26 + 10 * Math.sin(t / 30)}deg) scale3d(0.82, 0.82, 0.82)` }}>
        {UNITS.map((u, i) => {
          const rise = ramp(t, -6 + i * 5, 18, BACK);
          return (
            <Box
              key={i}
              w={u.w}
              h={u.hh}
              d={84}
              color={u.c}
              transform={`translate3d(${u.x}px, ${-u.hh / 2 + (1 - rise) * 500}px, 0px)`}
              front={
                <div style={{ padding: 16, display: "flex", flexDirection: "column", gap: 14, alignItems: "center" }}>
                  <div style={{ width: "100%", height: 64, borderRadius: 10, background: K.night, display: "flex", alignItems: "center", justifyContent: "center" }}>
                    <div style={{ width: "60%", height: 10, borderRadius: 5, background: K.mint, opacity: 0.5 + 0.5 * Math.abs(Math.sin(t / 8 + i)) }} />
                  </div>
                  <div style={{ width: 18, height: 18, borderRadius: 9, background: t > i * 5 + 10 ? K.mint : "#555", boxShadow: t > i * 5 + 10 ? `0 0 16px ${K.mint}` : undefined }} />
                </div>
              }
            />
          );
        })}
        <div
          style={{
            position: "absolute",
            transform: `translate3d(80px, -330px, 160px) translate(-50%, -50%) scale(${badge})`,
            display: "flex",
            alignItems: "center",
            gap: 14,
            padding: "14px 26px 14px 16px",
            borderRadius: 999,
            background: K.card,
            border: `2px solid ${K.line}`,
            boxShadow: "0 24px 40px -20px rgba(20,17,31,0.5)",
            fontFamily: TEXT,
            fontWeight: 700,
            fontSize: 30,
            whiteSpace: "nowrap",
            color: K.ink,
          }}
        >
          <Check size={40} progress={ramp(t, 22, 12)} />
          Relevés signés
        </div>
      </div>
    </Stage>
  );
};

export const AutochargeArt: React.FC<ArtProps> = ({ t, w, h }) => {
  const drive = ramp(t, -8, 22, OUT);
  const plug = ramp(t, 12, 10, OUT);
  const fill = ramp(t, 22, 30, OUT);
  return (
    <div style={{ position: "relative", width: w, height: h }}>
      <Stage perspective={1400} style={{ position: "absolute", left: 150, top: h * 0.62, width: 0, height: 0 }}>
        <Box w={110} h={260} d={80} color={K.blue} transform={`rotateY(28deg) translateY(${-60}px)`} front={
          <div style={{ padding: 14 }}>
            <div style={{ height: 56, borderRadius: 8, background: K.night, display: "flex", alignItems: "center", justifyContent: "center", fontFamily: MONO, fontSize: 20, color: K.mint }}>
              {plug >= 1 ? "⚡" : "··"}
            </div>
          </div>
        } />
      </Stage>
      <svg width={w} height={h} style={{ position: "absolute", inset: 0 }}>
        <path
          d={`M 196 ${h * 0.52} C 260 ${h * 0.92}, 330 ${h * 0.86}, 372 ${h * 0.66}`}
          fill="none"
          stroke={K.ink}
          strokeWidth={10}
          strokeLinecap="round"
          pathLength={1}
          strokeDasharray={1}
          strokeDashoffset={1 - plug}
        />
      </svg>
      <div style={{ position: "absolute", left: 330 + (1 - drive) * 600, top: h * 0.62 - 150 }}>
        <CarArt width={420} wheelTurn={(1 - drive) * 600} body={K.tangerine} portGlow={plug} />
      </div>
      <div
        style={{
          position: "absolute",
          right: 40,
          top: 20,
          width: 300,
          padding: 20,
          borderRadius: 24,
          background: K.card,
          border: `2px solid ${K.line}`,
          boxShadow: "0 24px 40px -24px rgba(20,17,31,0.5)",
          opacity: ramp(t, 16, 10),
          translate: `0 ${(1 - ramp(t, 16, 14)) * 20}px`,
          fontFamily: TEXT,
        }}
      >
        <div style={{ fontSize: 24, color: K.ink2, fontWeight: 600 }}>{fill > 0 ? "Session démarrée" : "Véhicule reconnu"}</div>
        <div style={{ marginTop: 12, height: 18, borderRadius: 9, background: K.paper2, overflow: "hidden" }}>
          <div style={{ width: `${10 + 70 * fill}%`, height: "100%", borderRadius: 9, background: `linear-gradient(90deg, ${K.mint}, ${K.ok})` }} />
        </div>
      </div>
    </div>
  );
};
