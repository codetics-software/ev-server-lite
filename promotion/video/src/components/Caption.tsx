import React from "react";
import { interpolate } from "remotion";
import { C, FONT } from "../theme";
import { EASE_OUT } from "../time";
import { useAbsFrame } from "./AbsTime";

type Props = {
  readonly from: number;
  readonly to: number;
  readonly kicker?: string;
  readonly title: string;
  readonly sub?: string;
  readonly dark?: boolean;
  readonly align?: "left" | "center";
};

const Words: React.FC<{ readonly text: string; readonly start: number; readonly step: number }> = ({
  text,
  start,
  step,
}) => {
  const frame = useAbsFrame();
  return (
    <>
      {text.split(" ").map((w, i) => {
        const p = interpolate(frame, [start + i * step, start + i * step + 14], [0, 1], {
          extrapolateLeft: "clamp",
          extrapolateRight: "clamp",
          easing: EASE_OUT,
        });
        return (
          <span
            key={i}
            style={{
              display: "inline-block",
              whiteSpace: "pre",
              opacity: p,
              translate: `0 ${(1 - p) * 34}px`,
              filter: `blur(${(1 - p) * 8}px)`,
            }}
          >
            {w + " "}
          </span>
        );
      })}
    </>
  );
};

export const Caption: React.FC<Props> = ({ from, to, kicker, title, sub, dark, align = "left" }) => {
  const frame = useAbsFrame();
  if (frame < from || frame > to) return null;
  const out = interpolate(frame, [to - 10, to], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const kick = interpolate(frame, [from, from + 12], [0, 1], { extrapolateRight: "clamp", easing: EASE_OUT });
  const titleWords = title.split(" ").length;
  return (
    <div
      style={{
        position: "absolute",
        left: 120,
        right: 120,
        bottom: 96,
        fontFamily: FONT,
        textAlign: align,
        opacity: out,
        translate: `0 ${(1 - out) * -16}px`,
      }}
    >
      {kicker && (
        <div
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: 14,
            fontSize: 28,
            fontWeight: 600,
            letterSpacing: 2,
            textTransform: "uppercase",
            color: dark ? C.energy : C.greenMid,
            opacity: kick,
            translate: `${(1 - kick) * -30}px 0`,
            marginBottom: 14,
          }}
        >
          <span style={{ width: 36 * kick, height: 3, background: dark ? C.energy : C.greenMid, borderRadius: 2 }} />
          {kicker}
        </div>
      )}
      <div style={{ fontSize: 76, fontWeight: 600, lineHeight: 1.08, color: dark ? "#fff" : C.ink, letterSpacing: -1 }}>
        <Words text={title} start={from + 4} step={2} />
      </div>
      {sub && (
        <div style={{ marginTop: 16, fontSize: 40, lineHeight: 1.3, color: dark ? "#b9c8bf" : C.muted, maxWidth: 1500, marginLeft: align === "center" ? "auto" : 0, marginRight: align === "center" ? "auto" : 0 }}>
          <Words text={sub} start={from + 8 + titleWords * 2} step={1} />
        </div>
      )}
    </div>
  );
};
