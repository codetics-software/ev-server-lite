import React from "react";
import { Img, staticFile } from "remotion";
import { K, shade, TEXT } from "./brand";

type BoxProps = {
  readonly w: number;
  readonly h: number;
  readonly d: number;
  readonly color: string;
  readonly transform?: string;
  readonly style?: React.CSSProperties;
  readonly top?: React.ReactNode;
  readonly front?: React.ReactNode;
  readonly right?: React.ReactNode;
};

const face = (w: number, h: number, transform: string, background: string, children?: React.ReactNode): React.CSSProperties & { children?: React.ReactNode } => ({
  position: "absolute",
  left: 0,
  top: 0,
  width: w,
  height: h,
  transform,
  background,
  backfaceVisibility: "hidden",
  overflow: "hidden",
  children,
});

export const Box: React.FC<BoxProps> = ({ w, h, d, color, transform = "", style, top, front, right }) => {
  const faces = [
    face(w, h, `translateZ(${d / 2}px)`, shade(color, 0), front),
    face(w, h, `rotateY(180deg) translateZ(${d / 2}px)`, shade(color, -0.35)),
    face(d, h, `translateX(${w / 2 - d / 2}px) rotateY(90deg) translateZ(${w / 2}px)`, shade(color, -0.18), right),
    face(d, h, `translateX(${w / 2 - d / 2}px) rotateY(-90deg) translateZ(${w / 2}px)`, shade(color, -0.25)),
    face(w, d, `translateY(${h / 2 - d / 2}px) rotateX(90deg) translateZ(${h / 2}px)`, shade(color, 0.22), top),
    face(w, d, `translateY(${h / 2 - d / 2}px) rotateX(-90deg) translateZ(${h / 2}px)`, shade(color, -0.45)),
  ];
  return (
    <div style={{ position: "absolute", left: -w / 2, top: -h / 2, width: w, height: h, transformStyle: "preserve-3d", transform, ...style }}>
      {faces.map(({ children, ...s }, i) => (
        <div key={i} style={s}>
          {children}
        </div>
      ))}
    </div>
  );
};

export const Stage: React.FC<{ readonly perspective?: number; readonly style?: React.CSSProperties; readonly children: React.ReactNode }> = ({
  perspective = 1600,
  style,
  children,
}) => (
  <div style={{ position: "relative", perspective, transformStyle: "preserve-3d", ...style }}>
    <div style={{ position: "absolute", left: "50%", top: "50%", transformStyle: "preserve-3d" }}>{children}</div>
  </div>
);

export const Logo3D: React.FC<{ readonly size: number; readonly transform?: string; readonly depth?: number }> = ({ size, transform = "", depth = 14 }) => {
  const h = size * 0.802;
  return (
    <div style={{ position: "absolute", left: -size / 2, top: -h / 2, width: size, height: h, transformStyle: "preserve-3d", transform }}>
      {Array.from({ length: depth }, (_, i) => (
        <Img
          key={i}
          src={staticFile("codetics-logo.svg")}
          style={{
            position: "absolute",
            inset: 0,
            width: size,
            height: h,
            transform: `translateZ(${(depth / 2 - i) * (size / 110)}px)`,
            filter: i === 0 ? undefined : `brightness(${0.55 - (i / depth) * 0.2}) saturate(1.3)`,
          }}
        />
      ))}
    </div>
  );
};

export const Chip: React.FC<{ readonly children: React.ReactNode; readonly dot?: string; readonly style?: React.CSSProperties; readonly size?: number }> = ({
  children,
  dot,
  style,
  size = 30,
}) => (
  <div
    style={{
      display: "inline-flex",
      alignItems: "center",
      gap: size * 0.4,
      padding: `${size * 0.36}px ${size * 0.8}px`,
      borderRadius: 999,
      background: K.chip,
      color: K.ink,
      fontSize: size,
      fontFamily: TEXT,
      fontWeight: 600,
      whiteSpace: "nowrap",
      ...style,
    }}
  >
    {dot ? <span style={{ width: size * 0.4, height: size * 0.4, borderRadius: 99, background: dot, boxShadow: `0 0 0 ${size * 0.18}px ${dot}33` }} /> : null}
    {children}
  </div>
);

export const Check: React.FC<{ readonly size: number; readonly progress: number; readonly color?: string }> = ({ size, progress, color = K.ok }) => (
  <svg width={size} height={size} viewBox="0 0 48 48">
    <circle cx="24" cy="24" r="22" fill={color} opacity={Math.min(1, progress * 2)} />
    <path d="M14 25 L21 32 L35 17" fill="none" stroke="#fff" strokeWidth="5" strokeLinecap="round" strokeLinejoin="round" strokeDasharray="34" strokeDashoffset={34 * (1 - progress)} />
  </svg>
);
