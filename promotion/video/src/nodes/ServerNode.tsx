import React from "react";
import { interpolate } from "remotion";
import { useAbsFrame } from "../components/AbsTime";
import { Icon } from "../components/Art";
import { CardHeader, Pill } from "../components/FloatCard";
import { SERVER_LOG } from "../story";
import { C } from "../theme";

const ROW = 40;

export const ServerNode: React.FC = () => {
  const frame = useAbsFrame();
  const visible = SERVER_LOG.filter((l) => frame >= l.at);
  const shown = visible.slice(-5);
  const last = visible[visible.length - 1];
  const slide = last ? interpolate(frame, [last.at, last.at + 10], [ROW, 0], { extrapolateRight: "clamp" }) : 0;
  return (
    <>
      <CardHeader title="Serveur Codetics" right={<Pill color={C.greenBright} size={17}>En ligne</Pill>} />
      <div style={{ display: "flex", gap: 18, padding: "16px 22px" }}>
        <div style={{ display: "flex", flexDirection: "column", gap: 10, width: 76 }}>
          {[0, 1, 2, 3].map((r) => {
            const blink = Math.sin(frame / (3 + r) + r * 2) > 0.2;
            const busy = last && frame - last.at < 12;
            return (
              <div key={r} style={{ height: 52, borderRadius: 10, background: "#1b2a23", display: "flex", alignItems: "center", gap: 6, padding: "0 10px" }}>
                <div style={{ width: 9, height: 9, borderRadius: 5, background: blink || busy ? C.energy : "#335244", boxShadow: blink ? `0 0 8px ${C.energy}` : "none" }} />
                <div style={{ width: 9, height: 9, borderRadius: 5, background: r % 2 ? "#335244" : C.cyan, opacity: 0.8 }} />
                <div style={{ flex: 1, height: 4, borderRadius: 2, background: "#335244" }} />
              </div>
            );
          })}
        </div>
        <div style={{ flex: 1, height: ROW * 5 + 30, overflow: "hidden", position: "relative" }}>
          <div style={{ position: "absolute", left: 0, right: 0, bottom: 0, translate: `0 ${slide}px` }}>
            {shown.map((l, i) => {
              const isNew = l === last;
              const flash = isNew ? interpolate(frame, [l.at, l.at + 18], [1, 0], { extrapolateRight: "clamp" }) : 0;
              return (
                <div
                  key={`${l.at}-${i}`}
                  style={{
                    height: ROW,
                    display: "flex",
                    alignItems: "center",
                    gap: 10,
                    fontSize: 20,
                    color: C.ink,
                    borderRadius: 8,
                    padding: "0 8px",
                    background: `rgba(47,191,113,${0.16 * flash})`,
                    opacity: 0.45 + 0.55 * ((i + 1) / shown.length),
                    whiteSpace: "nowrap",
                  }}
                >
                  {l.tone === "ok" ? <Icon.check size={20} color={C.greenBright} stroke={3} /> : <span style={{ color: C.purple, fontWeight: 700, width: 20 }}>→</span>}
                  {l.text}
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </>
  );
};
