import React from "react";
import { useAbsFrame } from "../components/AbsTime";
import { AutochargeArt, CloudArt, HardwareArt, SecurityArt } from "./Arts";
import { at, BACK, Cue, DISPLAY, IN_OUT, K, MONO, ramp, TEXT, useFormat } from "./brand";
import { Shell } from "./parts";
import { Chip } from "./three";

const FEATURES: {
  cue: Cue;
  title: string;
  body: string;
  chips: string[];
  tint: string;
  Art: typeof CloudArt;
}[] = [
  {
    cue: "feature.cloud",
    title: "Multi-cloud",
    body: "Docker Compose ou Kubernetes, avec un guide pour chaque cloud.",
    chips: ["Docker Compose", "Kubernetes", "Caddy · Nginx"],
    tint: K.chip,
    Art: CloudArt,
  },
  {
    cue: "feature.security",
    title: "Sécurité OCPP",
    body: "Profils 1, 2 et 3 : TLS, mTLS et certificats des bornes, depuis le tableau de bord.",
    chips: ["Basic", "TLS", "mTLS"],
    tint: "#fff1e8",
    Art: SecurityArt,
  },
  {
    cue: "feature.hardware",
    title: "Plus de bornes",
    body: "Interopérabilité OCPP 1.6J renforcée et relevés de comptage signés.",
    chips: ["OCPP 1.6J", "Relevés signés", "Paramètres OCPP"],
    tint: "#e9f9f1",
    Art: HardwareArt,
  },
  {
    cue: "feature.autocharge",
    title: "Autocharge",
    body: "Le véhicule est reconnu au branchement : la session démarre seule.",
    chips: ["Sans badge", "Sans application"],
    tint: "#fff8d9",
    Art: AutochargeArt,
  },
];

const Card: React.FC<{ readonly i: number; readonly w: number; readonly h: number }> = ({ i, w, h }) => {
  const frame = useAbsFrame();
  const f = FEATURES[i];
  const t = frame - at(f.cue);
  const artH = h * 0.52;
  return (
    <div
      style={{
        position: "absolute",
        left: -w / 2,
        top: -h / 2,
        width: w,
        height: h,
        transform: `rotateY(${i * 90}deg) translateZ(${w / 2}px)`,
        backfaceVisibility: "hidden",
        borderRadius: 44,
        background: K.card,
        border: `2px solid ${K.line}`,
        overflow: "hidden",
        display: "flex",
        flexDirection: "column",
      }}
    >
      <div style={{ position: "relative", height: artH, background: `radial-gradient(80% 90% at 50% 100%, ${f.tint}, ${K.paper} 80%)`, borderBottom: `2px solid ${K.line}` }}>
        <div style={{ position: "absolute", left: 40, top: 32, fontFamily: MONO, fontSize: 26, color: K.ink2, letterSpacing: 2 }}>0{i + 1} / 04</div>
        <f.Art t={t} w={w} h={artH} />
      </div>
      <div style={{ padding: "40px 56px", display: "flex", flexDirection: "column", gap: 22, flex: 1, justifyContent: "center" }}>
        <div style={{ fontFamily: DISPLAY, fontWeight: 750, fontSize: 84, letterSpacing: "-0.035em", color: K.ink, lineHeight: 1 }}>{f.title}</div>
        <div style={{ fontFamily: TEXT, fontSize: 36, lineHeight: 1.3, color: K.ink2, fontWeight: 500 }}>{f.body}</div>
        <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginTop: 6 }}>
          {f.chips.map((c, k) => (
            <Chip key={c} size={26} style={{ opacity: ramp(t, 8 + k * 3, 10), background: k === 0 ? K.ink : K.chip, color: k === 0 ? "#fff" : K.ink }}>
              {c}
            </Chip>
          ))}
        </div>
      </div>
    </div>
  );
};

export const Features: React.FC = () => {
  const frame = useAbsFrame();
  const { tall } = useFormat();
  const w = 900;
  const h = tall ? 1100 : 930;
  const enter = ramp(frame, at("drop") - 4, 22, BACK);
  const turns = FEATURES.slice(1).reduce((s, f) => s + ramp(frame, at(f.cue) - 12, 14, IN_OUT), 0);
  const idx = Math.min(3, Math.round(turns));
  return (
    <Shell to={at("fixes")} gap={tall ? 40 : 28}>
      <div style={{ display: "flex", alignItems: "center", gap: 18, opacity: ramp(frame, at("drop"), 12) }}>
        <Chip dot={K.tangerine} size={32}>
          Nouveautés
        </Chip>
        <div style={{ display: "flex", gap: 8 }}>
          {FEATURES.map((_, k) => (
            <div key={k} style={{ width: k === idx ? 44 : 14, height: 14, borderRadius: 7, background: k === idx ? K.blue : K.line }} />
          ))}
        </div>
      </div>
      <div style={{ position: "relative", width: w, height: h, perspective: 2600 }}>
        <div
          style={{
            position: "absolute",
            left: "50%",
            top: "50%",
            transformStyle: "preserve-3d",
            transform: `translateZ(${-w / 2}px) translateY(${(1 - enter) * 500}px) rotateX(${(1 - enter) * -40}deg) rotateY(${-turns * 90}deg)`,
          }}
        >
          {FEATURES.map((_, i) => (
            <Card key={i} i={i} w={w} h={h} />
          ))}
        </div>
      </div>
    </Shell>
  );
};
