import { Easing, interpolate, useVideoConfig } from "remotion";
import tl from "./timeline.json";

export const K = {
  paper: "#faf8f4",
  paper2: "#f2eee6",
  card: "#ffffff",
  ink: "#14111f",
  ink2: "#5b566b",
  line: "#e4dfd4",
  blue: "#3346e8",
  blueDeep: "#2433c4",
  blueSoft: "#8f9bff",
  tangerine: "#ff7a3d",
  lilac: "#c9b8ff",
  butter: "#ffe27a",
  mint: "#7be0b0",
  ok: "#1f9d6b",
  night: "#0f0d18",
  nightCard: "#1f1b36",
  chip: "#ede7ff",
};

export const DISPLAY = '"Bricolage Grotesque", ui-sans-serif, system-ui, sans-serif';
export const TEXT = '"Figtree", ui-sans-serif, system-ui, sans-serif';
export const SERIF = '"Instrument Serif", ui-serif, Georgia, serif';
export const MONO = '"Geist Mono", ui-monospace, "SF Mono", Menlo, monospace';
export const NNBSP = " ";

export type Cue = keyof typeof tl.cues;
export const BEAT = tl.framesPerBeat;
export const TOTAL = tl.bars * 4 * BEAT;
export const at = (name: Cue) => Math.round(tl.cues[name] * BEAT);

export const OUT = Easing.bezier(0.16, 1, 0.3, 1);
export const IN_OUT = Easing.bezier(0.65, 0, 0.35, 1);
export const BACK = Easing.bezier(0.34, 1.56, 0.64, 1);

export const ramp = (frame: number, start: number, duration: number, easing = OUT) =>
  interpolate(frame, [start, start + duration], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing });

export const useFormat = () => {
  const { width, height } = useVideoConfig();
  const tall = height / width > 1.5;
  return { W: width, H: height, tall, top: tall ? 210 : 72, bottom: tall ? 330 : 72 };
};

export const shade = (hex: string, amount: number) => {
  const n = parseInt(hex.slice(1), 16);
  const mix = (c: number) => Math.round(amount >= 0 ? c + (255 - c) * amount : c * (1 + amount));
  const r = mix(n >> 16);
  const g = mix((n >> 8) & 255);
  const b = mix(n & 255);
  return `rgb(${r}, ${g}, ${b})`;
};
