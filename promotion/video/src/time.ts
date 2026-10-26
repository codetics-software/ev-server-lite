import { Easing, interpolate } from "remotion";
import tl from "./timeline.json";

export type CueName = keyof typeof tl.cues;
export type SectionName = keyof typeof tl.sections;

export const BEAT = tl.framesPerBeat;
export const BAR = tl.framesPerBar;
export const TOTAL_FRAMES = tl.bars * BAR;

export const beat = (b: number) => Math.round(b * BEAT);
export const cue = (name: CueName) => beat(tl.cues[name].b);
export const sectionFrom = (name: SectionName) => tl.sections[name][0] * BAR;
export const sectionDuration = (name: SectionName) => (tl.sections[name][1] - tl.sections[name][0]) * BAR;

export const EASE_OUT = Easing.bezier(0.16, 1, 0.3, 1);
export const EASE_IN_OUT = Easing.bezier(0.65, 0, 0.35, 1);

export const ramp = (frame: number, start: number, duration: number, easing = EASE_OUT) =>
  interpolate(frame, [start, start + duration], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing,
  });
