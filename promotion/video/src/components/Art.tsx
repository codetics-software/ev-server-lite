import React from "react";
import { Img, staticFile } from "remotion";
import { C } from "../theme";

export const Logo: React.FC<{ readonly size: number; readonly style?: React.CSSProperties }> = ({ size, style }) => (
  <Img src={staticFile("codetics-logo.svg")} style={{ width: size, height: size * 0.802, ...style }} />
);

export const CarArt: React.FC<{
  readonly width: number;
  readonly wheelTurn?: number;
  readonly body?: string;
  readonly portGlow?: number;
}> = ({ width, wheelTurn = 0, body = C.green, portGlow = 0 }) => (
  <svg width={width} height={width * 0.42} viewBox="0 0 320 134">
    <ellipse cx="162" cy="126" rx="140" ry="7" fill="rgba(0,0,0,0.12)" />
    <path d="M18 92 C14 70 26 60 52 56 L84 52 C104 28 128 16 166 16 L204 16 C236 16 256 30 280 54 L294 58 C306 62 312 72 310 92 C309 102 302 106 292 106 L28 106 C20 106 18 100 18 92 Z" fill={body} />
    <path d="M96 54 C112 34 132 26 160 26 L162 54 Z" fill="#d8efe3" />
    <path d="M172 26 L202 26 C224 26 240 36 258 54 L172 54 Z" fill="#d8efe3" />
    <path d="M30 70 L300 70" stroke="rgba(255,255,255,0.18)" strokeWidth="3" />
    <rect x="290" y="64" width="16" height="8" rx="4" fill="#fff6c8" />
    <rect x="20" y="64" width="12" height="8" rx="4" fill="#ff6b6b" />
    <rect x="38" y="72" width="16" height="14" rx="4" fill={portGlow > 0 ? C.energy : "rgba(255,255,255,0.25)"} opacity={0.4 + 0.6 * portGlow} />
    {[82, 244].map((cx) => (
      <g key={cx} transform={`rotate(${wheelTurn} ${cx} 106)`}>
        <circle cx={cx} cy="106" r="22" fill="#121a16" />
        <circle cx={cx} cy="106" r="11" fill="#c9d3cd" />
        <rect x={cx - 2} y="95" width="4" height="22" fill="#8c9a92" />
        <rect x={cx - 11} y="104" width="22" height="4" fill="#8c9a92" />
      </g>
    ))}
  </svg>
);

export const ChargerArt: React.FC<{
  readonly height: number;
  readonly led: string;
  readonly screen?: React.ReactNode;
  readonly qr?: number;
}> = ({ height, led, screen, qr = 0 }) => (
  <svg width={height * 0.56} height={height} viewBox="0 0 140 250">
    <ellipse cx="70" cy="244" rx="58" ry="6" fill="rgba(0,0,0,0.12)" />
    <rect x="24" y="8" width="92" height="232" rx="22" fill="#1b2a23" />
    <rect x="32" y="16" width="76" height="216" rx="16" fill="#24362d" />
    <rect x="40" y="34" width="60" height="58" rx="8" fill="#0d1612" />
    <foreignObject x="40" y="34" width="60" height="58">
      <div style={{ width: 60, height: 58, display: "flex", alignItems: "center", justifyContent: "center" }}>{screen}</div>
    </foreignObject>
    <rect x="46" y="104" width="48" height="6" rx="3" fill={led} style={{ filter: `drop-shadow(0 0 6px ${led})` }} />
    <rect x="16" y="140" width="14" height="40" rx="6" fill="#0d1612" />
    <circle cx="23" cy="160" r="4" fill={led} />
    <g opacity={qr}>
      <rect x="48" y="124" width="44" height="44" rx="6" fill="#fff" />
      {[0, 1, 2, 3, 4, 5].flatMap((r) =>
        [0, 1, 2, 3, 4, 5].map((c) =>
          (r * 7 + c * 3 + r * c) % 3 !== 0 || (r < 2 && c < 2) || (r > 3 && c < 2) || (r < 2 && c > 3) ? (
            <rect key={`${r}-${c}`} x={52 + c * 6} y={128 + r * 6} width="5.4" height="5.4" fill="#0f1a14" />
          ) : null,
        ),
      )}
    </g>
    <rect x="52" y="200" width="36" height="6" rx="3" fill="#33473d" />
  </svg>
);

type IconProps = { readonly size?: number; readonly color?: string; readonly stroke?: number };
const Svg: React.FC<IconProps & { readonly children: React.ReactNode }> = ({ size = 28, color = C.ink, stroke = 2, children }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth={stroke} strokeLinecap="round" strokeLinejoin="round">
    {children}
  </svg>
);

export const Icon = {
  badge: (p: IconProps) => (
    <Svg {...p}>
      <rect x="3" y="5" width="18" height="14" rx="3" />
      <path d="M7 10h5M7 14h3" />
      <path d="M15.5 9.5a3 3 0 0 1 0 5M17.5 8a5 5 0 0 1 0 8" />
    </Svg>
  ),
  qr: (p: IconProps) => (
    <Svg {...p}>
      <rect x="3" y="3" width="7" height="7" rx="1" />
      <rect x="14" y="3" width="7" height="7" rx="1" />
      <rect x="3" y="14" width="7" height="7" rx="1" />
      <path d="M14 14h3v3M21 14v7h-4M14 18v3" />
    </Svg>
  ),
  car: (p: IconProps) => (
    <Svg {...p}>
      <path d="M5 16H3v-4l2-5h11l3 5h2v4h-2" />
      <circle cx="7.5" cy="16.5" r="2" />
      <circle cx="16.5" cy="16.5" r="2" />
      <path d="M9.5 16.5h5M6 12h13" />
    </Svg>
  ),
  bolt: (p: IconProps) => (
    <Svg {...p}>
      <path d="M13 2 4 14h7l-1 8 9-12h-7z" />
    </Svg>
  ),
  check: (p: IconProps) => (
    <Svg {...p}>
      <path d="m5 12 5 5 9-10" />
    </Svg>
  ),
  globe: (p: IconProps) => (
    <Svg {...p}>
      <circle cx="12" cy="12" r="9" />
      <path d="M3 12h18M12 3c2.5 2.8 3.8 5.8 3.8 9S14.5 18.2 12 21c-2.5-2.8-3.8-5.8-3.8-9S9.5 5.8 12 3z" />
    </Svg>
  ),
  feather: (p: IconProps) => (
    <Svg {...p}>
      <path d="M20 4c-6 0-11 3-13 9l-2 7 7-2c6-2 9-7 8-14z" />
      <path d="M5 20 15 10M9 15h5" />
    </Svg>
  ),
  shield: (p: IconProps) => (
    <Svg {...p}>
      <path d="M12 3 4 6v6c0 4.5 3.4 8 8 9 4.6-1 8-4.5 8-9V6z" />
      <path d="m9 12 2 2 4-4" />
    </Svg>
  ),
  target: (p: IconProps) => (
    <Svg {...p}>
      <circle cx="12" cy="12" r="9" />
      <circle cx="12" cy="12" r="5" />
      <circle cx="12" cy="12" r="1" />
    </Svg>
  ),
  eye: (p: IconProps) => (
    <Svg {...p}>
      <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z" />
      <circle cx="12" cy="12" r="3" />
    </Svg>
  ),
  puzzle: (p: IconProps) => (
    <Svg {...p}>
      <path d="M10 3h4v3a2 2 0 1 0 4 0V3h3v7h-3a2 2 0 1 0 0 4h3v7h-7v-3a2 2 0 1 0-4 0v3H3v-7h3a2 2 0 1 0 0-4H3V3z" />
    </Svg>
  ),
  alert: (p: IconProps) => (
    <Svg {...p}>
      <path d="M12 3 2 20h20z" />
      <path d="M12 10v4M12 17h.01" />
    </Svg>
  ),
  question: (p: IconProps) => (
    <Svg {...p}>
      <circle cx="12" cy="12" r="9" />
      <path d="M9.5 9a2.5 2.5 0 1 1 3.5 2.3c-.7.3-1 .9-1 1.7M12 17h.01" />
    </Svg>
  ),
  phone: (p: IconProps) => (
    <Svg {...p}>
      <rect x="6" y="2" width="12" height="20" rx="3" />
      <path d="M11 18h2" />
    </Svg>
  ),
  table: (p: IconProps) => (
    <Svg {...p}>
      <rect x="3" y="4" width="18" height="16" rx="2" />
      <path d="M3 9h18M3 14h18M9 4v16" />
    </Svg>
  ),
  pin: (p: IconProps) => (
    <Svg {...p}>
      <path d="M12 21s-7-6.2-7-12a7 7 0 0 1 14 0c0 5.8-7 12-7 12z" />
      <circle cx="12" cy="9" r="2.5" />
    </Svg>
  ),
  euro: (p: IconProps) => (
    <Svg {...p}>
      <path d="M17 6.5A7 7 0 1 0 17 17.5M4 10h9M4 14h9" />
    </Svg>
  ),
  server: (p: IconProps) => (
    <Svg {...p}>
      <rect x="3" y="3" width="18" height="7" rx="2" />
      <rect x="3" y="14" width="18" height="7" rx="2" />
      <path d="M7 6.5h.01M7 17.5h.01" />
    </Svg>
  ),
  chart: (p: IconProps) => (
    <Svg {...p}>
      <path d="M3 3v18h18" />
      <path d="m7 15 4-4 3 3 6-7" />
    </Svg>
  ),
};
