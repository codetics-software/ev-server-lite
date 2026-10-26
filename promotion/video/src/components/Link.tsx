import React, { useMemo } from "react";
import { interpolate } from "remotion";
import { EASE_IN_OUT, EASE_OUT } from "../time";
import { C, FONT } from "../theme";
import { useAbsFrame } from "./AbsTime";

type Pt = readonly [number, number];

export type Packet = {
  readonly at: number;
  readonly dur?: number;
  readonly label?: string;
  readonly color?: string;
  readonly reverse?: boolean;
};

type Props = {
  readonly a: Pt;
  readonly b: Pt;
  readonly drawAt: number;
  readonly drawDur?: number;
  readonly packets?: readonly Packet[];
  readonly flow?: { from: number; to?: number; color?: string; reverse?: boolean };
  readonly width?: number;
  readonly cable?: boolean;
  readonly dashed?: boolean;
};

const controls = (a: Pt, b: Pt): [Pt, Pt] => {
  const dx = b[0] - a[0];
  const dy = b[1] - a[1];
  if (Math.abs(dx) >= Math.abs(dy)) {
    const mx = a[0] + dx / 2;
    return [
      [mx, a[1]],
      [mx, b[1]],
    ];
  }
  const my = a[1] + dy / 2;
  return [
    [a[0], my],
    [b[0], my],
  ];
};

const bez = (a: Pt, c1: Pt, c2: Pt, b: Pt, t: number): Pt => {
  const u = 1 - t;
  return [
    u * u * u * a[0] + 3 * u * u * t * c1[0] + 3 * u * t * t * c2[0] + t * t * t * b[0],
    u * u * u * a[1] + 3 * u * u * t * c1[1] + 3 * u * t * t * c2[1] + t * t * t * b[1],
  ];
};

export const Link: React.FC<Props> = ({
  a,
  b,
  drawAt,
  drawDur = 18,
  packets = [],
  flow,
  width = 4,
  cable,
  dashed,
}) => {
  const frame = useAbsFrame();
  const [c1, c2] = useMemo(() => controls(a, b), [a, b]);
  const d = `M ${a[0]} ${a[1]} C ${c1[0]} ${c1[1]}, ${c2[0]} ${c2[1]}, ${b[0]} ${b[1]}`;
  const len = useMemo(() => {
    let l = 0;
    let p = a;
    for (let i = 1; i <= 48; i++) {
      const q = bez(a, c1, c2, b, i / 48);
      l += Math.hypot(q[0] - p[0], q[1] - p[1]);
      p = q;
    }
    return l;
  }, [a, b, c1, c2]);

  if (frame < drawAt) return null;
  const draw = interpolate(frame, [drawAt, drawAt + drawDur], [0, 1], {
    extrapolateRight: "clamp",
    easing: EASE_OUT,
  });
  const flowOn =
    flow && frame >= flow.from
      ? interpolate(frame, [flow.from, flow.from + 10], [0, 1], { extrapolateRight: "clamp" }) *
        (flow.to ? interpolate(frame, [flow.to, flow.to + 10], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }) : 1)
      : 0;
  const ends = [a, b].map((p, i) => (
    <circle key={i} cx={p[0]} cy={p[1]} r={width * 2.2} fill={C.card} stroke={cable ? C.ink : C.greenMid} strokeWidth={width * 0.9} opacity={draw} />
  ));

  return (
    <g>
      {cable && (
        <path d={d} fill="none" stroke={C.ink} strokeWidth={width * 3.2} strokeLinecap="round" strokeDasharray={len} strokeDashoffset={len * (1 - draw)} />
      )}
      <path
        d={d}
        fill="none"
        stroke={cable ? "#2b3a33" : "#c4cfc8"}
        strokeWidth={cable ? width * 2 : width}
        strokeLinecap="round"
        strokeDasharray={dashed ? "2 14" : len}
        strokeDashoffset={dashed ? 0 : len * (1 - draw)}
        opacity={dashed ? draw : 1}
      />
      {flowOn > 0 && (
        <path
          d={d}
          fill="none"
          stroke={flow?.color ?? C.energy}
          strokeWidth={cable ? width * 1.3 : width + 1}
          strokeLinecap="round"
          strokeDasharray="10 26"
          strokeDashoffset={(flow?.reverse ? 1 : -1) * frame * 3.2}
          opacity={flowOn}
          style={{ filter: `drop-shadow(0 0 6px ${flow?.color ?? C.energy})` }}
        />
      )}
      {ends}
      {packets.map((p, i) => {
        const dur = p.dur ?? 16;
        if (frame < p.at || frame > p.at + dur + 6) return null;
        const raw = interpolate(frame, [p.at, p.at + dur], [0, 1], {
          extrapolateLeft: "clamp",
          extrapolateRight: "clamp",
          easing: EASE_IN_OUT,
        });
        const t = p.reverse ? 1 - raw : raw;
        const [px, py] = bez(a, c1, c2, b, t);
        const fade = interpolate(frame, [p.at, p.at + 4, p.at + dur, p.at + dur + 6], [0, 1, 1, 0]);
        const color = p.color ?? C.greenMid;
        const lw = p.label ? p.label.length * 11 + 34 : 0;
        return (
          <g key={i} opacity={fade}>
            <circle cx={px} cy={py} r={14} fill={color} opacity={0.25} />
            <circle cx={px} cy={py} r={8} fill={color} />
            {p.label && (
              <g transform={`translate(${px - lw / 2} ${py - 58})`}>
                <rect width={lw} height={38} rx={19} fill={color} />
                <text x={lw / 2} y={26} textAnchor="middle" fontFamily={FONT} fontSize={21} fontWeight={600} fill="#fff">
                  {p.label}
                </text>
              </g>
            )}
          </g>
        );
      })}
    </g>
  );
};
