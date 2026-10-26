import React from "react";
import { AbsoluteFill, interpolate, spring, useVideoConfig } from "remotion";
import { useAbsFrame } from "../components/AbsTime";
import { CarArt, ChargerArt, Icon } from "../components/Art";
import { Caption } from "../components/Caption";
import { C, FONT, NNBSP } from "../theme";
import { beat, cue, CueName, EASE_IN_OUT, EASE_OUT, sectionFrom } from "../time";

type Q = { cue: CueName; text: string; tone: "amber" | "red" | "gray"; icon: keyof typeof Icon; x: number; y: number; r: number };

const QUESTIONS: Q[] = [
  { cue: "problem.q1", text: `La borne est-elle en ligne${NNBSP}?`, tone: "amber", icon: "question", x: 1120, y: 130, r: -4 },
  { cue: "problem.q2", text: `Qui recharge en ce moment${NNBSP}?`, tone: "amber", icon: "question", x: 1420, y: 250, r: 5 },
  { cue: "problem.q3", text: `Combien facturer${NNBSP}?`, tone: "amber", icon: "euro", x: 1060, y: 380, r: 3 },
  { cue: "problem.q4", text: "Session bloquée depuis 3 h", tone: "red", icon: "alert", x: 1400, y: 510, r: -6 },
  { cue: "problem.q5", text: "Panne signalée… par un client", tone: "red", icon: "alert", x: 1130, y: 630, r: 6 },
  { cue: "problem.q6", text: "Tableur à mettre à jour", tone: "gray", icon: "table", x: 1620, y: 130, r: 7 },
  { cue: "problem.q7", text: "Déplacement sur site", tone: "gray", icon: "pin", x: 860, y: 260, r: -8 },
  { cue: "problem.q8", text: "Paiement non reçu", tone: "red", icon: "euro", x: 1660, y: 640, r: 4 },
  { cue: "problem.q9", text: "Borne hors ligne", tone: "red", icon: "alert", x: 860, y: 500, r: -5 },
  { cue: "problem.q10", text: "Appel du support", tone: "gray", icon: "phone", x: 1600, y: 380, r: -3 },
];

const toneColor = { amber: C.amber, red: C.red, gray: "#8fa197" };

const ChaosCard: React.FC<{ readonly q: Q; readonly i: number }> = ({ q, i }) => {
  const frame = useAbsFrame();
  const { fps } = useVideoConfig();
  const at = cue(q.cue);
  if (frame < at) return null;
  const s = spring({ frame: frame - at, fps, config: { damping: 9, stiffness: 160 } });
  const t = frame / fps;
  const box = cue("problem.box");
  const crowd = interpolate(frame, [box - 6, box + 10], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const shake = interpolate(frame, [beat(34), beat(40)], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const jx = Math.sin(t * 9 + i * 3) * 4 * shake;
  const jy = Math.cos(t * 7 + i) * 4 * shake;
  const I = Icon[q.icon];
  const color = toneColor[q.tone];
  return (
    <div
      style={{
        position: "absolute",
        left: q.x,
        top: q.y,
        translate: `calc(-50% + ${jx + Math.sin(t * 1.8 + i) * 14}px) calc(-50% + ${jy + Math.cos(t * 1.4 + i * 2) * 12}px)`,
        rotate: `${q.r + Math.sin(t * 1.3 + i) * 4}deg`,
        scale: s * (1 - 0.15 * crowd),
        opacity: Math.min(1, s * 1.5) * (1 - 0.75 * crowd),
        filter: `blur(${crowd * 5}px)`,
        display: "flex",
        alignItems: "center",
        gap: 14,
        padding: "16px 24px",
        borderRadius: 16,
        background: C.nightCard,
        border: `1.5px solid ${color}55`,
        boxShadow: `0 20px 50px rgba(0,0,0,0.5), 0 0 30px ${color}22`,
        color: "#e8efe9",
        fontSize: 32,
        fontWeight: 600,
        whiteSpace: "nowrap",
      }}
    >
      <I size={34} color={color} />
      {q.text}
    </div>
  );
};

export const Problem: React.FC = () => {
  const frame = useAbsFrame();
  const start = sectionFrom("problem");
  const plug = cue("problem.plug");
  const box = cue("problem.box");
  const end = sectionFrom("reveal");
  const drive = interpolate(frame, [start, plug - 4], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: EASE_OUT });
  const carX = interpolate(drive, [0, 1], [-560, 610]);
  const shrink = interpolate(frame, [beat(24), beat(26)], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: EASE_IN_OUT });
  const cable = interpolate(frame, [plug, plug + 8], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const boxP = interpolate(frame, [box, box + 14], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: EASE_OUT });
  const fadeIn = interpolate(frame, [start, start + 14], [0, 1], { extrapolateRight: "clamp" });
  const out = interpolate(frame, [end - 8, end], [1, 0.0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const qMark = frame >= plug ? 0.6 + 0.4 * Math.sin(frame / 4) : 0;

  return (
    <AbsoluteFill style={{ background: C.night, fontFamily: FONT, overflow: "hidden" }}>
      <AbsoluteFill style={{ opacity: fadeIn * out }}>
        <AbsoluteFill style={{ backgroundImage: "radial-gradient(circle, #1d2c25 2px, transparent 2.4px)", backgroundSize: "48px 48px", opacity: 0.8 }} />
        <div style={{ position: "absolute", left: 0, right: 0, top: 740, height: 4, background: "#1a2a22" }} />
        {[0, 1, 2, 3, 4, 5].map((i) => (
          <div key={i} style={{ position: "absolute", left: 80 + i * 320, top: 770, width: 160, height: 6, borderRadius: 3, background: "#18261f" }} />
        ))}
        <div
          style={{
            position: "absolute",
            left: 0,
            top: 0,
            width: 1920,
            height: 1080,
            transformOrigin: "640px 740px",
            scale: 1 - 0.28 * shrink,
            translate: `${-240 * shrink}px ${-30 * shrink}px`,
          }}
        >
          <div style={{ position: "absolute", left: 780, top: 300 }}>
            <div style={{ position: "absolute", left: 40, top: -70, width: 140, textAlign: "center", fontSize: 90, fontWeight: 600, color: C.amber, opacity: qMark }}>?</div>
            <ChargerArt height={440} led={frame >= plug ? C.amber : "#3d5a4b"} screen={<div style={{ color: C.amber, fontSize: 30, fontWeight: 600, opacity: qMark }}>?</div>} />
          </div>
          <svg style={{ position: "absolute", left: 0, top: 0, overflow: "visible" }} width={1} height={1}>
            <path d="M 805 590 C 760 640, 720 660, 690 650" stroke="#0b0f0d" strokeWidth={14} fill="none" strokeLinecap="round" strokeDasharray={200} strokeDashoffset={200 * (1 - cable)} />
            <path d="M 805 590 C 760 640, 720 660, 690 650" stroke="#2b3a33" strokeWidth={8} fill="none" strokeLinecap="round" strokeDasharray={200} strokeDashoffset={200 * (1 - cable)} />
          </svg>
          <div style={{ position: "absolute", left: carX - 300, top: 600 }}>
            <CarArt width={420} wheelTurn={drive * 900} body="#2a4a3b" portGlow={0} />
          </div>
          <div
            style={{
              position: "absolute",
              left: 700,
              top: 220,
              width: 600,
              height: 560,
              borderRadius: 40,
              background: "linear-gradient(160deg, #050807, #000)",
              border: "2px solid #22322a",
              boxShadow: "0 40px 120px rgba(0,0,0,0.8)",
              opacity: boxP,
              scale: 0.6 + 0.4 * boxP,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              color: "#33463c",
              fontSize: 220,
              fontWeight: 600,
            }}
          >
            ?
          </div>
        </div>
        {QUESTIONS.map((q, i) => (
          <ChaosCard key={q.cue} q={q} i={i} />
        ))}
        <AbsoluteFill style={{ background: `linear-gradient(0deg, ${C.night} 0%, ${C.night}ee 22%, ${C.night}00 38%)` }} />
        <Caption from={start + 8} to={beat(24) - 2} dark kicker="Le constat" title="Chaque jour, des voitures électriques se branchent." />
        <Caption from={beat(24)} to={box - 2} dark kicker="Le problème" title="Mais pour l’exploitant, tout est flou." sub="Pannes invisibles, sessions bloquées, facturation à la main…" />
        <Caption from={box} to={end} dark kicker="Le problème" title="Sans supervision, une borne est une boîte noire." />
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
