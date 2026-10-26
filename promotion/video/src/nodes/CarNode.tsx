import React from "react";
import { interpolate } from "remotion";
import { useAbsFrame } from "../components/AbsTime";
import { CarArt, Icon } from "../components/Art";
import { CardHeader } from "../components/FloatCard";
import { batteryAt, fmt } from "../story";
import { C, NNBSP } from "../theme";
import { cue } from "../time";

export const CarNode: React.FC = () => {
  const frame = useAbsFrame();
  const pct = batteryAt(frame);
  const charging = frame >= cue("authorized");
  const glow = charging ? 0.6 + 0.4 * Math.sin(frame / 5) : 0;
  return (
    <>
      <CardHeader
        title="Véhicule"
        right={
          <span style={{ display: "flex", alignItems: "center", gap: 6, color: charging ? C.greenBright : C.muted, fontSize: 22 }}>
            {charging && <Icon.bolt size={22} color={C.greenBright} />}
            {fmt(pct, 0)}
            {NNBSP}%
          </span>
        }
      />
      <div style={{ display: "flex", justifyContent: "center", marginTop: 10 }}>
        <CarArt width={320} portGlow={glow} />
      </div>
      <div style={{ margin: "14px 22px 0", height: 14, borderRadius: 7, background: "#e8ede9", overflow: "hidden" }}>
        <div
          style={{
            width: `${pct}%`,
            height: "100%",
            borderRadius: 7,
            background: `linear-gradient(90deg, ${C.greenMid}, ${C.energy})`,
            boxShadow: charging ? `0 0 ${interpolate(glow, [0, 1], [0, 12])}px ${C.energy}` : "none",
          }}
        />
      </div>
    </>
  );
};
