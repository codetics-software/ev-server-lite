import React from "react";
import { interpolate } from "remotion";
import { useAbsFrame } from "../components/AbsTime";
import { ChargerArt, Icon } from "../components/Art";
import { CardHeader, Pill } from "../components/FloatCard";
import { chargerState, powerAt } from "../story";
import { C, FONT } from "../theme";
import { cue } from "../time";

export const ChargerNode: React.FC = () => {
  const frame = useAbsFrame();
  const s = chargerState(frame);
  const p = powerAt(frame);
  const qr = interpolate(frame, [cue("phone.appear"), cue("phone.appear") + 12], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  const screen =
    p > 0 ? (
      <div style={{ fontFamily: FONT, color: C.energy, fontSize: 15, fontWeight: 600, textAlign: "center", lineHeight: 1 }}>
        <Icon.bolt size={18} color={C.energy} />
        <div>{p} kW</div>
      </div>
    ) : (
      <div style={{ width: 12, height: 12, borderRadius: 6, background: s.led, opacity: 0.6 + 0.4 * Math.sin(frame / 4) }} />
    );
  return (
    <>
      <CardHeader title="Borne CDT-1002" />
      <div style={{ display: "flex", justifyContent: "center", marginTop: 14 }}>
        <ChargerArt height={270} led={s.led} screen={screen} qr={qr} />
      </div>
      <div style={{ display: "flex", justifyContent: "center", marginTop: 14 }}>
        <Pill color={s.color} size={19}>
          {s.label}
        </Pill>
      </div>
    </>
  );
};

export const IdChip: React.FC<{
  readonly x: number;
  readonly y: number;
  readonly at: number;
  readonly label: string;
  readonly icon: keyof typeof Icon;
  readonly winner?: boolean;
}> = ({ x, y, at, label, icon, winner }) => {
  const frame = useAbsFrame();
  if (frame < at) return null;
  const p = interpolate(frame, [at, at + 12], [0, 1], { extrapolateRight: "clamp" });
  const pop = interpolate(frame, [at, at + 6, at + 12], [0.6, 1.12, 1], { extrapolateRight: "clamp" });
  const win = winner ? interpolate(frame, [cue("authorized"), cue("authorized") + 8], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }) : 0;
  const dim = !winner ? interpolate(frame, [cue("authorized"), cue("authorized") + 12], [1, 0.55], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }) : 1;
  const I = Icon[icon];
  return (
    <div
      style={{
        position: "absolute",
        left: x,
        top: y,
        translate: "-50% -50%",
        scale: pop,
        opacity: p * dim,
        display: "flex",
        alignItems: "center",
        gap: 10,
        padding: "10px 18px",
        borderRadius: 999,
        background: win > 0.5 ? C.green : C.card,
        color: win > 0.5 ? "#fff" : C.ink,
        border: `1.5px solid ${win > 0.5 ? C.green : C.border}`,
        boxShadow: "0 10px 24px rgba(15,40,28,0.12)",
        fontFamily: FONT,
        fontSize: 22,
        fontWeight: 600,
        whiteSpace: "nowrap",
      }}
    >
      <I size={24} color={win > 0.5 ? C.energy : C.greenMid} />
      {label}
      {win > 0.5 && <Icon.check size={22} color={C.energy} stroke={3} />}
    </div>
  );
};
