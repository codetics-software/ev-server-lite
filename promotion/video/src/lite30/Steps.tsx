import React from "react";
import { useAbsFrame } from "../components/AbsTime";
import { at, BACK, Cue, DISPLAY, K, MONO, OUT, ramp, TEXT, useFormat } from "./brand";
import { Headline, Rise, Shell } from "./parts";
import { Box, Check, Chip, Stage } from "./three";

const FIXES: { cue: Cue; title: string; sub: string }[] = [
  { cue: "fix.1", title: "Certificats racine compromis retirés", sub: "L’image serveur ne fait plus confiance aux autorités révoquées" },
  { cue: "fix.2", title: "Démarrage lent sans coupure", sub: "Délai du mandataire allongé au lancement" },
  { cue: "fix.3", title: "Messages OCPP fiabilisés", sub: "Horodatages et validation des échanges renforcés" },
];

export const Fixes: React.FC = () => {
  const frame = useAbsFrame();
  const { tall } = useFormat();
  return (
    <Shell to={at("install")} gap={tall ? 44 : 30}>
      <Headline main="Corrigé" accent="et fiabilisé." at={at("fixes")} size={tall ? 124 : 112} />
      <div style={{ display: "flex", flexDirection: "column", gap: 20, perspective: 1400, marginTop: 10 }}>
        {FIXES.map((f) => {
          const c = at(f.cue);
          const flip = ramp(frame, c - 7, 16, BACK);
          const done = ramp(frame, c, 12);
          return (
            <div
              key={f.cue}
              style={{
                width: 940,
                display: "flex",
                alignItems: "center",
                gap: 28,
                padding: "28px 34px",
                borderRadius: 30,
                background: K.card,
                border: `2px solid ${K.line}`,
                boxShadow: "0 1px 0 #14111f0a, 0 12px 32px -16px #14111f2e",
                transformOrigin: "50% 0%",
                transform: `rotateX(${(1 - flip) * -100}deg)`,
                opacity: Math.min(1, flip * 3),
              }}
            >
              <div style={{ position: "relative", width: 68, height: 68, flex: "none" }}>
                <div
                  style={{
                    position: "absolute",
                    inset: 0,
                    borderRadius: 34,
                    background: K.tangerine,
                    color: "#fff",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    fontFamily: DISPLAY,
                    fontWeight: 800,
                    fontSize: 40,
                    opacity: 1 - done,
                    scale: 1 - 0.4 * done,
                  }}
                >
                  !
                </div>
                <div style={{ position: "absolute", inset: 0 }}>
                  <Check size={68} progress={done} />
                </div>
              </div>
              <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                <div style={{ fontFamily: TEXT, fontWeight: 700, fontSize: 38, color: K.ink, letterSpacing: "-0.01em" }}>{f.title}</div>
                <div style={{ fontFamily: TEXT, fontWeight: 500, fontSize: 28, color: K.ink2 }}>{f.sub}</div>
              </div>
            </div>
          );
        })}
      </div>
      <Rise at={at("fix.3") + 10}>
        <div style={{ display: "flex", alignItems: "center", gap: 16, fontFamily: TEXT, fontSize: 28, color: K.ink2, fontWeight: 600 }}>
          Mise à jour
          <span style={{ fontFamily: MONO, fontSize: 28, color: K.ink, background: K.paper2, border: `2px solid ${K.line}`, padding: "10px 18px", borderRadius: 14 }}>
            git pull && docker compose up -d --build
          </span>
        </div>
      </Rise>
    </Shell>
  );
};

const COMMANDS: { cue: Cue; text: string }[] = [
  { cue: "install.1", text: "git clone github.com/codetics-software/ev-server-lite" },
  { cue: "install.2", text: "cd ev-server-lite/deploy && cp .env.example .env" },
  { cue: "install.3", text: "docker compose up -d --build" },
];
const SERVICES = ["postgres", "server", "dashboard", "caddy"];

export const Install: React.FC = () => {
  const frame = useAbsFrame();
  const { tall } = useFormat();
  const start = at("install");
  const tilt = ramp(frame, start - 4, 40, OUT);
  const ok = at("install.ok");
  return (
    <Shell to={at("deploy")} gap={tall ? 50 : 34}>
      <Headline main="Installation" accent="en 3 commandes." at={start} size={tall ? 120 : 108} />
      <Stage perspective={1800} style={{ width: 900, height: tall ? 500 : 440 }}>
        <div
          style={{
            position: "absolute",
            left: -450,
            top: tall ? -250 : -220,
            width: 900,
            height: tall ? 500 : 440,
            transform: `rotateX(${18 - 10 * tilt}deg) rotateY(${-22 + 16 * tilt}deg) translateZ(${(1 - tilt) * -300}px)`,
            borderRadius: 30,
            background: K.night,
            boxShadow: "0 50px 80px -40px rgba(20,17,31,0.7)",
            padding: "26px 34px",
            fontFamily: MONO,
            fontSize: 24,
            lineHeight: 1.55,
            color: "#e9e4f5",
            overflow: "hidden",
          }}
        >
          <div style={{ display: "flex", gap: 12, marginBottom: 22 }}>
            {[K.tangerine, K.butter, K.mint].map((c) => (
              <div key={c} style={{ width: 18, height: 18, borderRadius: 9, background: c }} />
            ))}
          </div>
          {COMMANDS.map((c, i) => {
            const typed = Math.max(0, Math.floor((frame - at(c.cue)) * 3.2));
            if (frame < at(c.cue)) return null;
            const done = typed >= c.text.length;
            const next = COMMANDS[i + 1];
            const cursor = !done || (next ? frame < at(next.cue) : frame < ok);
            return (
              <div key={c.cue} style={{ whiteSpace: "pre", marginBottom: 6 }}>
                <span style={{ color: K.mint }}>$ </span>
                {c.text.slice(0, typed)}
                {cursor && Math.floor(frame / 6) % 2 === 0 ? <span style={{ background: "#e9e4f5" }}> </span> : null}
              </div>
            );
          })}
          {SERVICES.map((s, i) =>
            frame >= ok + i * 3 ? (
              <div key={s} style={{ whiteSpace: "pre", color: "#a9a3bf" }}>
                <span style={{ color: K.mint }}>✔</span> Container {s.padEnd(10)} <span style={{ color: K.mint }}>Started</span>
              </div>
            ) : null,
          )}
        </div>
      </Stage>
      <div style={{ display: "flex", gap: 14, flexWrap: "wrap", justifyContent: "center", maxWidth: 960 }}>
        {["HTTPS Let’s Encrypt", "PostgreSQL inclus", "1 VM · 2 vCPU · 4 Go"].map((c, i) => (
          <Rise key={c} at={ok + 4 + i * 4} dist={24}>
            <Chip size={28} style={{ background: K.card, border: `2px solid ${K.line}` }}>
              {c}
            </Chip>
          </Rise>
        ))}
      </div>
    </Shell>
  );
};

const LAYERS: { cue: Cue; title: string; sub: string; color: string }[] = [
  { cue: "deploy.edge", title: "PostgreSQL ou MongoDB", sub: "Base gérée ou locale", color: K.lilac },
  { cue: "deploy.app", title: "Serveur + tableau de bord", sub: "Kotlin · Next.js", color: K.blue },
  { cue: "deploy.data", title: "Caddy ou Nginx", sub: "TLS · WebSocket OCPP", color: K.tangerine },
];
const PLATFORMS = ["Docker Compose", "Kubernetes"];
const PROVIDERS = ["Google Cloud", "AWS", "Azure", "Scaleway", "Oracle Cloud"];

export const Deploy: React.FC = () => {
  const frame = useAbsFrame();
  const { tall } = useFormat();
  const start = at("deploy");
  const clouds = at("deploy.clouds");
  const gapZ = 92;
  return (
    <Shell to={at("free")} gap={tall ? 48 : 26}>
      <Headline main="Déploiement" accent="où vous voulez." at={start} size={tall ? 120 : 104} />
      <div style={{ position: "relative", width: 1000, height: tall ? 520 : 440, display: "flex", alignItems: "center" }}>
        <Stage perspective={1700} style={{ width: 440, height: "100%" }}>
          <div style={{ transformStyle: "preserve-3d", transform: `translateY(${tall ? 90 : 70}px) rotateX(60deg) rotateZ(${-42 + (frame - start) * 0.25}deg)` }}>
            {LAYERS.map((l, i) => {
              const drop = ramp(frame, at(l.cue) - 6, 16, BACK);
              return (
                <Box
                  key={l.cue}
                  w={260}
                  h={260}
                  d={44}
                  color={l.color}
                  transform={`translateZ(${i * gapZ + (1 - drop) * 700}px)`}
                  style={{ visibility: drop > 0.001 ? "visible" : "hidden" }}
                />
              );
            })}
          </div>
        </Stage>
        <div style={{ display: "flex", flexDirection: "column-reverse", gap: tall ? 46 : 30, flex: 1, paddingLeft: 30 }}>
          {LAYERS.map((l) => (
            <Rise key={l.cue} at={at(l.cue)} dist={30}>
              <div style={{ display: "flex", gap: 18, alignItems: "center" }}>
                <div style={{ width: 22, height: 22, borderRadius: 6, background: l.color, flex: "none" }} />
                <div>
                  <div style={{ fontFamily: DISPLAY, fontWeight: 700, fontSize: 40, color: K.ink, letterSpacing: "-0.02em", lineHeight: 1.05 }}>{l.title}</div>
                  <div style={{ fontFamily: TEXT, fontWeight: 500, fontSize: 28, color: K.ink2 }}>{l.sub}</div>
                </div>
              </div>
            </Rise>
          ))}
        </div>
      </div>
      <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 14 }}>
        <div style={{ display: "flex", gap: 14 }}>
          {PLATFORMS.map((p, i) => {
            const s = ramp(frame, clouds - 6 + i * 3, 12, BACK);
            return (
              <Chip key={p} size={32} style={{ background: K.ink, color: "#fff", scale: s }}>
                {p}
              </Chip>
            );
          })}
        </div>
        <div style={{ display: "flex", gap: 12, flexWrap: "wrap", justifyContent: "center", maxWidth: 980 }}>
          {PROVIDERS.map((p, i) => {
            const s = ramp(frame, clouds + Math.round(i * 3.75), 12, BACK);
            return (
              <Chip key={p} size={28} style={{ background: K.card, border: `2px solid ${K.line}`, scale: s }}>
                {p}
              </Chip>
            );
          })}
        </div>
      </div>
    </Shell>
  );
};
