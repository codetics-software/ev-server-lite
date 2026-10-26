import { readFileSync, writeFileSync } from "node:fs";

const tl = JSON.parse(readFileSync(new URL("../src/timeline.json", import.meta.url), "utf8"));

const SR = 44100;
const SPB = 60 / tl.bpm;
const TOTAL_BEATS = tl.bars * 4;
const N = Math.ceil(TOTAL_BEATS * SPB * SR);
const TAU = Math.PI * 2;

let seed = 20261023;
const rand = () => {
  seed |= 0;
  seed = (seed + 0x6d2b79f5) | 0;
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
};
const noise = () => rand() * 2 - 1;

const mtof = (m) => 440 * Math.pow(2, (m - 69) / 12);
const sec = (beat) => beat * SPB;
const idx = (beat) => Math.round(sec(beat) * SR);
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const lerp = (a, b, t) => a + (b - a) * t;
const smooth = (t) => {
  const x = clamp(t, 0, 1);
  return x * x * (3 - 2 * x);
};

const bus = () => [new Float32Array(N), new Float32Array(N)];
const B = {
  kick: bus(),
  drums: bus(),
  bass: bus(),
  pad: bus(),
  arp: bus(),
  lead: bus(),
  fx: bus(),
  sfx: bus(),
  verb: bus(),
  delay: bus(),
};

const add = (b, i, l, r = l) => {
  if (i < 0 || i >= N) return;
  b[0][i] += l;
  b[1][i] += r;
};
const panLR = (p) => [Math.cos(((p + 1) * Math.PI) / 4), Math.sin(((p + 1) * Math.PI) / 4)];

class Biquad {
  constructor() {
    this.x1 = this.x2 = this.y1 = this.y2 = 0;
    this.b0 = 1;
    this.b1 = this.b2 = this.a1 = this.a2 = 0;
  }
  set(type, f, q = 0.707) {
    const w = (TAU * clamp(f, 10, SR * 0.45)) / SR;
    const cs = Math.cos(w);
    const a = Math.sin(w) / (2 * q);
    let b0, b1, b2;
    if (type === "lp") [b0, b1, b2] = [(1 - cs) / 2, 1 - cs, (1 - cs) / 2];
    else if (type === "hp") [b0, b1, b2] = [(1 + cs) / 2, -(1 + cs), (1 + cs) / 2];
    else [b0, b1, b2] = [a, 0, -a];
    const a0 = 1 + a;
    this.b0 = b0 / a0;
    this.b1 = b1 / a0;
    this.b2 = b2 / a0;
    this.a1 = (-2 * cs) / a0;
    this.a2 = (1 - a) / a0;
    return this;
  }
  run(x) {
    const y = this.b0 * x + this.b1 * this.x1 + this.b2 * this.x2 - this.a1 * this.y1 - this.a2 * this.y2;
    this.x2 = this.x1;
    this.x1 = x;
    this.y2 = this.y1;
    this.y1 = y;
    return y;
  }
}

const blep = (t, dt) => {
  if (t < dt) {
    t /= dt;
    return t + t - t * t - 1;
  }
  if (t > 1 - dt) {
    t = (t - 1) / dt;
    return t * t + t + t + 1;
  }
  return 0;
};

const CHORDS = {
  Am: { bass: 45, pad: [57, 60, 64, 67] },
  F: { bass: 41, pad: [53, 57, 60, 64] },
  C: { bass: 36, pad: [55, 60, 64, 67] },
  G: { bass: 43, pad: [55, 59, 62, 69] },
  Dm: { bass: 38, pad: [53, 57, 62, 65] },
  E: { bass: 40, pad: [56, 59, 64, 68] },
};

const chordAt = (bar) => {
  if (bar < 4) return ["Am", "F", "C", "G"][bar];
  if (bar < 12) return ["Am", "F", "Dm", "E"][(bar - 4) % 4];
  if (bar < 14) return ["F", "G"][bar - 12];
  if (bar >= 50) return ["Dm", "G", "C", "C"][bar - 50];
  return ["C", "G", "Am", "F"][(bar - 14) % 4];
};

const inBars = (bar, a, b) => bar >= a && bar < b;
const S = tl.sections;
const isMain = (bar) => inBars(bar, 14, 40) || inBars(bar, 42, 52);

function renderPad() {
  const detunes = [-0.13, 0, 0.12];
  const pans = [-0.7, 0, 0.7];
  for (let bar = 0; bar < tl.bars; bar++) {
    const ch = CHORDS[chordAt(bar)];
    const start = idx(bar * 4);
    const holdBars = bar === 52 ? 2 : 1;
    if (bar === 53) continue;
    const len = sec(4 * holdBars);
    const atk = bar < 4 ? 0.9 : inBars(bar, 12, 14) ? 0.15 : 0.35;
    const rel = bar >= 52 ? 2.5 : 0.8;
    const total = Math.floor((len + rel) * SR);
    const gain = bar < 4 ? 0.075 : 0.05;
    ch.pad.forEach((note, ni) => {
      detunes.forEach((dc, vi) => {
        const f = mtof(note + dc + (ni === 3 ? 0 : 0));
        const dt = f / SR;
        let ph = rand();
        const [pl, pr] = panLR(pans[vi] * (ni % 2 ? -1 : 1));
        for (let k = 0; k < total; k++) {
          const t = k / SR;
          const env = t < atk ? smooth(t / atk) : t < len ? 1 : Math.exp(-(t - len) / (rel / 3));
          ph += dt;
          if (ph >= 1) ph -= 1;
          const v = (2 * ph - 1 - blep(ph, dt)) * env * gain;
          add(B.pad, start + k, v * pl, v * pr);
        }
      });
    });
  }
  const cutoffAt = (beat) => {
    const bar = beat / 4;
    if (bar < 4) return lerp(500, 1800, bar / 4);
    if (bar < 12) return 900 + 300 * Math.sin(bar);
    if (bar < 14) return lerp(900, 5200, Math.pow((bar - 12) / 2, 2));
    if (bar < 40) return 3200;
    if (bar < 42) return lerp(1400, 3200, (bar - 40) / 2);
    if (bar < 50) return 3200;
    return lerp(2600, 900, (bar - 50) / 4);
  };
  const fl = new Biquad();
  const fr = new Biquad();
  for (let i = 0; i < N; i++) {
    if (i % 128 === 0) {
      const c = cutoffAt(i / SR / SPB);
      fl.set("lp", c, 0.8);
      fr.set("lp", c, 0.8);
    }
    B.pad[0][i] = fl.run(B.pad[0][i]);
    B.pad[1][i] = fr.run(B.pad[1][i]);
  }
}

function pluck(target, beat, note, vel, opts = {}) {
  const f = mtof(note);
  const dt = f / SR;
  const start = idx(beat);
  const dur = opts.dur ?? 0.32;
  const len = Math.floor(dur * SR);
  const flt = new Biquad();
  const [pl, pr] = panLR(opts.pan ?? 0);
  const peak = opts.bright ?? 4200;
  let ph = rand();
  let ph2 = rand();
  for (let k = 0; k < len; k++) {
    const t = k / SR;
    if (k % 32 === 0) flt.set("lp", 380 + peak * Math.exp(-t / 0.06), 1.1);
    ph += dt;
    if (ph >= 1) ph -= 1;
    ph2 += dt * 0.5;
    if (ph2 >= 1) ph2 -= 1;
    const osc = 2 * ph - 1 - blep(ph, dt) + 0.35 * (ph2 < 0.5 ? 1 : -1);
    const env = Math.min(1, t / 0.002) * Math.exp(-t / (opts.decay ?? 0.13)) * (1 - k / len);
    const v = flt.run(osc) * env * vel;
    add(target, start + k, v * pl, v * pr);
    if (opts.send) add(B.delay, start + k, v * opts.send, v * opts.send);
  }
}

function renderArp() {
  for (let bar = 0; bar < 52; bar++) {
    const ch = CHORDS[chordAt(bar)];
    const tones = [...ch.pad, ch.pad[0] + 12, ch.pad[1] + 12, ch.pad[2] + 12];
    let step, vel, bright, pattern;
    if (bar < 2) continue;
    if (bar < 4) [step, vel, bright] = [0.5, 0.09, 1400];
    else if (bar < 12) [step, vel, bright] = [0.5, 0.07, 1800];
    else if (bar < 14) [step, vel, bright] = [0.25, lerp(0.06, 0.11, bar - 12), 3000];
    else if (inBars(bar, 40, 42)) [step, vel, bright] = [0.25, 0.08, 2200];
    else [step, vel, bright] = [0.25, 0.1, 3600];
    pattern = step === 0.5 ? [0, 2, 1, 3, 0, 2, 4, 2] : [0, 1, 2, 3, 4, 3, 2, 1, 0, 2, 4, 6, 5, 3, 2, 1];
    const steps = 4 / step;
    for (let s = 0; s < steps; s++) {
      const beat = bar * 4 + s * step;
      const accent = s % 4 === 0 ? 1.15 : s % 2 === 0 ? 0.95 : 0.8;
      const note = tones[pattern[s % pattern.length] % tones.length] + (bar >= 14 ? 12 : 0);
      pluck(B.arp, beat, note, vel * accent * (0.9 + 0.2 * rand()), {
        pan: s % 2 ? 0.35 : -0.35,
        bright,
        send: 0.45,
        decay: bar >= 14 ? 0.11 : 0.16,
      });
    }
  }
}

function bassNote(beat, note, durBeats, vel, cutoff = 900) {
  const f = mtof(note);
  const dt = f / SR;
  const start = idx(beat);
  const len = Math.floor(sec(durBeats) * SR);
  const rel = Math.floor(0.03 * SR);
  const flt = new Biquad();
  let ph = 0;
  let sp = 0;
  for (let k = 0; k < len + rel; k++) {
    const t = k / SR;
    if (k % 32 === 0) flt.set("lp", cutoff * 0.6 + cutoff * 1.4 * Math.exp(-t / 0.05), 1.2);
    ph += dt;
    if (ph >= 1) ph -= 1;
    sp += TAU * f / SR;
    const env = Math.min(1, t / 0.004) * (k < len ? 1 : 1 - (k - len) / rel);
    const v = (flt.run(2 * ph - 1 - blep(ph, dt)) * 0.7 + Math.sin(sp) * 0.6) * env * vel;
    add(B.bass, start + k, v);
  }
}

function renderBass() {
  for (let bar = 0; bar < tl.bars; bar++) {
    const root = CHORDS[chordAt(bar)].bass;
    if (inBars(bar, 6, 12)) {
      for (let e = 0; e < 8; e++) bassNote(bar * 4 + e * 0.5, root, 0.35, e % 2 ? 0.22 : 0.3, 420);
    } else if (inBars(bar, 12, 14)) {
      bassNote(bar * 4, root, 4, 0.28, lerp(400, 1400, bar - 12));
    } else if (isMain(bar)) {
      for (let e = 0; e < 8; e++) {
        const n = e === 7 ? root + 12 : e === 6 && bar % 2 ? root + 7 : root;
        bassNote(bar * 4 + e * 0.5, n, 0.42, e % 2 ? 0.42 : 0.34, 950);
      }
    } else if (bar === 52) {
      bassNote(bar * 4, root, 7, 0.32, 600);
    }
  }
}

const LEAD_A = [
  [0, 76, 1], [1, 79, 1], [2, 84, 1.5], [3.5, 83, 0.5],
  [4, 83, 1], [5, 81, 0.5], [5.5, 79, 1.5], [7, 74, 1],
  [8, 76, 1], [9, 81, 1], [10, 84, 1.5], [11.5, 83, 0.5],
  [12, 81, 1], [13, 79, 0.5], [13.5, 77, 0.5], [14, 76, 2],
];
const LEAD_B = [...LEAD_A.slice(0, 12), [12, 81, 0.5], [12.5, 84, 0.5], [13, 86, 1], [14, 84, 2]];

function bell(target, beat, note, durBeats, vel, pan = 0) {
  const f = mtof(note);
  const start = idx(beat);
  const len = Math.floor((sec(durBeats) + 1.2) * SR);
  const [pl, pr] = panLR(pan);
  let pc = 0;
  let pm = 0;
  let pt = 0;
  for (let k = 0; k < len; k++) {
    const t = k / SR;
    pm += (TAU * f * 2) / SR;
    pc += (TAU * f) / SR;
    pt += (TAU * f * 1.002) / SR;
    const idxMod = 2.2 * Math.exp(-t / 0.18);
    const env = Math.min(1, t / 0.004) * Math.exp(-t / 0.55);
    const v = (Math.sin(pc + idxMod * Math.sin(pm)) * 0.7 + Math.sin(pt) * 0.3) * env * vel;
    add(target, start + k, v * pl, v * pr);
    add(B.delay, start + k, v * 0.35, v * 0.35);
  }
}

function renderLead() {
  const phrases = [22, 26, 30, 34, 38, 42, 46];
  phrases.forEach((p, pi) => {
    const motif = pi % 2 ? LEAD_B : LEAD_A;
    const big = p === 34 || p === 38;
    motif.forEach(([b, n, d]) => {
      const beat = p * 4 + b;
      const bar = Math.floor(beat / 4);
      const soft = inBars(bar, 40, 42) ? 0.7 : 1;
      bell(B.lead, beat, n, d, 0.11 * soft, -0.15);
      if (big) bell(B.lead, beat, n + 12, d, 0.04, 0.3);
    });
  });
}

function kick(beat, vel = 1) {
  const start = idx(beat);
  const len = Math.floor(0.45 * SR);
  let ph = 0;
  for (let k = 0; k < len; k++) {
    const t = k / SR;
    const f = 46 + 130 * Math.exp(-t / 0.032);
    ph += (TAU * f) / SR;
    const amp = Math.exp(-t / 0.26) * Math.min(1, t / 0.001);
    const click = noise() * Math.exp(-t / 0.0025) * 0.35;
    const v = Math.tanh(1.8 * Math.sin(ph) * amp) * 0.85 * vel + click * vel;
    add(B.kick, start + k, v);
  }
}

function noiseHit(target, beat, { type = "hp", f = 8000, q = 0.7, decay = 0.04, len = 0.15, vel = 0.1, pan = 0, f2 }) {
  const start = idx(beat);
  const n = Math.floor(len * SR);
  const a = new Biquad().set(type, f, q);
  const b = new Biquad().set(type, f2 ?? f, q);
  const [pl, pr] = panLR(pan);
  for (let k = 0; k < n; k++) {
    const t = k / SR;
    const env = Math.exp(-t / decay) * (1 - k / n);
    add(target, start + k, a.run(noise()) * env * vel * pl * 1.4, b.run(noise()) * env * vel * pr * 1.4);
  }
}

function clap(beat, vel = 1) {
  const start = idx(beat);
  const n = Math.floor(0.4 * SR);
  const bl = new Biquad().set("bp", 1400, 0.9);
  const br = new Biquad().set("bp", 1550, 0.9);
  for (let k = 0; k < n; k++) {
    const t = k / SR;
    let env = 0;
    for (const o of [0, 0.011, 0.022]) if (t >= o) env = Math.max(env, Math.exp(-(t - o) / 0.006));
    if (t >= 0.03) env = Math.max(env, 0.8 * Math.exp(-(t - 0.03) / 0.13));
    const vl = bl.run(noise()) * env * 0.5 * vel;
    const vr = br.run(noise()) * env * 0.5 * vel;
    add(B.drums, start + k, vl, vr);
    add(B.verb, start + k, vl * 0.5, vr * 0.5);
  }
}

function snare(beat, vel = 1) {
  const start = idx(beat);
  const n = Math.floor(0.25 * SR);
  const bp = new Biquad().set("bp", 2200, 0.7);
  let ph = 0;
  for (let k = 0; k < n; k++) {
    const t = k / SR;
    ph += (TAU * (180 + 60 * Math.exp(-t / 0.01))) / SR;
    const v = (bp.run(noise()) * Math.exp(-t / 0.08) * 0.6 + Math.sin(ph) * Math.exp(-t / 0.045) * 0.4) * vel;
    add(B.drums, start + k, v);
    add(B.verb, start + k, v * 0.3);
  }
}

function crash(beat, vel = 0.25) {
  noiseHit(B.drums, beat, { type: "hp", f: 4500, f2: 4700, q: 0.5, decay: 0.7, len: 2.6, vel });
  const start = idx(beat);
  for (let k = 0; k < SR * 0.4; k++) add(B.verb, start + k, noise() * 0.05 * Math.exp(-k / SR / 0.15));
}

function impact(beat, vel = 1) {
  const start = idx(beat);
  const n = Math.floor(2.2 * SR);
  let ph = 0;
  const lp = new Biquad().set("lp", 900, 0.7);
  for (let k = 0; k < n; k++) {
    const t = k / SR;
    ph += (TAU * (32 + 70 * Math.exp(-t / 0.08))) / SR;
    const boom = Math.sin(ph) * Math.exp(-t / 0.7) * 0.75;
    const rumble = lp.run(noise()) * Math.exp(-t / 0.5) * 0.6;
    add(B.fx, start + k, (boom + rumble) * vel);
    add(B.verb, start + k, rumble * 0.4 * vel);
  }
  crash(beat, 0.3 * vel);
}

function riser(fromBeat, toBeat, vel = 0.2) {
  const start = idx(fromBeat);
  const n = idx(toBeat) - start;
  const bl = new Biquad();
  const br = new Biquad();
  let ph = 0;
  for (let k = 0; k < n; k++) {
    const p = k / n;
    if (k % 64 === 0) {
      const f = 300 * Math.pow(30, p);
      bl.set("bp", f, 2.2);
      br.set("bp", f * 1.04, 2.2);
    }
    ph += (TAU * mtof(48 + 36 * p * p)) / SR;
    const g = Math.pow(p, 2.2) * vel;
    const tone = Math.sin(ph) * 0.12 * g;
    add(B.fx, start + k, bl.run(noise()) * g * 2 + tone, br.run(noise()) * g * 2 + tone);
  }
}

function downlifter(fromBeat, toBeat, vel = 0.15) {
  const start = idx(fromBeat);
  const n = idx(toBeat) - start;
  const bl = new Biquad();
  const br = new Biquad();
  for (let k = 0; k < n; k++) {
    const p = k / n;
    if (k % 64 === 0) {
      const f = 7000 * Math.pow(0.03, p);
      bl.set("bp", f, 1.8);
      br.set("bp", f * 1.05, 1.8);
    }
    const g = Math.pow(1 - p, 1.6) * vel * Math.min(1, k / (SR * 0.02));
    add(B.fx, start + k, bl.run(noise()) * g * 2, br.run(noise()) * g * 2);
  }
}

function renderDrums() {
  for (let bar = 0; bar < tl.bars; bar++) {
    const b0 = bar * 4;
    if (inBars(bar, 4, 12)) {
      kick(b0, 0.6);
      if (bar >= 6) kick(b0 + 0.75, 0.3);
      if (bar >= 8) kick(b0 + 2, 0.5);
      if (bar >= 6)
        for (let e = 0; e < 8; e++)
          noiseHit(B.drums, b0 + e * 0.5, { f: 9000, decay: 0.012, len: 0.05, vel: e % 2 ? 0.05 : 0.08, pan: 0.3 });
      if (bar >= 8) snare(b0 + 3, 0.25);
    }
    if (bar === 12) for (let e = 0; e < 8; e++) snare(b0 + e * 0.5, 0.18 + e * 0.02);
    if (bar === 13) {
      for (let s = 0; s < 8; s++) snare(b0 + s * 0.25, 0.3 + s * 0.02);
      for (let s = 0; s < 14; s++) snare(b0 + 2 + s * 0.125, 0.4 + s * 0.025);
    }
    if (bar === 33) for (let s = 0; s < 8; s++) snare(b0 + 2 + s * 0.25, 0.2 + s * 0.04);
    if (bar === 41) for (let s = 0; s < 16; s++) snare(b0 + s * 0.25, 0.12 + s * 0.02);
    if (isMain(bar)) {
      for (let q = 0; q < 4; q++) {
        if (bar === 33 && q === 3) continue;
        if (bar === 39 && q === 3) continue;
        kick(b0 + q, 1);
      }
      clap(b0 + 1, 0.9);
      clap(b0 + 3, bar === 39 ? 1.2 : 0.9);
      for (let s = 0; s < 16; s++) {
        const v = [0.11, 0.045, 0.075, 0.045][s % 4];
        noiseHit(B.drums, b0 + s * 0.25, { f: 8500, decay: 0.028, len: 0.08, vel: v, pan: 0.25 });
      }
      if (bar >= 16 && !inBars(bar, 42, 44))
        for (let q = 0; q < 4; q++)
          noiseHit(B.drums, b0 + q + 0.5, { f: 7000, decay: 0.16, len: 0.42, vel: 0.06, pan: -0.25 });
    }
    if (bar === 52) kick(b0, 1);
  }
  [14, 16, 34, 42, 50].forEach((bar) => crash(bar * 4, bar === 14 || bar === 34 ? 0.32 : 0.22));
  impact(14 * 4, 1);
  impact(34 * 4, 0.6);
  impact(52 * 4, 0.45);
  riser(12 * 4, 14 * 4, 0.34);
  riser(33 * 4, 34 * 4, 0.12);
  downlifter(40 * 4, 42 * 4, 0.1);
}

const SFX = {
  snap(beat) {
    const start = idx(beat);
    for (let k = 0; k < SR * 0.09; k++) {
      const t = k / SR;
      const v =
        Math.sin(TAU * 2600 * t) * Math.exp(-t / 0.012) * 0.18 +
        Math.sin(TAU * (160 + 200 * Math.exp(-t / 0.01)) * t) * Math.exp(-t / 0.03) * 0.3 +
        noise() * Math.exp(-t / 0.003) * 0.15;
      add(B.sfx, start + k, v);
      add(B.verb, start + k, v * 0.15);
    }
  },
  pop(beat) {
    const start = idx(beat);
    let ph = 0;
    for (let k = 0; k < SR * 0.12; k++) {
      const t = k / SR;
      ph += (TAU * (520 + 900 * (1 - Math.exp(-t / 0.02)))) / SR;
      const v = Math.sin(ph) * Math.exp(-t / 0.035) * 0.2;
      add(B.sfx, start + k, v);
      add(B.verb, start + k, v * 0.2);
    }
  },
  click(beat) {
    const start = idx(beat);
    for (let k = 0; k < SR * 0.05; k++) {
      const t = k / SR;
      const v = (Math.sin(TAU * 3200 * t) * 0.15 + Math.sin(TAU * 1100 * t) * 0.2) * Math.exp(-t / 0.006);
      add(B.sfx, start + k, v);
    }
  },
  confirm(beat) {
    bell(B.sfx, beat, 88, 0.5, 0.09, -0.2);
    bell(B.sfx, beat + 0.25, 95, 1, 0.08, 0.2);
  },
  shimmer(beat) {
    [84, 88, 91, 96, 100].forEach((n, i) => bell(B.sfx, beat + i * 0.125, n, 1, 0.05, i % 2 ? 0.4 : -0.4));
  },
  thud(beat) {
    const start = idx(beat);
    let ph = 0;
    const lp = new Biquad().set("lp", 300, 0.7);
    for (let k = 0; k < SR * 0.7; k++) {
      const t = k / SR;
      ph += (TAU * (38 + 60 * Math.exp(-t / 0.05))) / SR;
      const v = Math.sin(ph) * Math.exp(-t / 0.25) * 0.7 + lp.run(noise()) * Math.exp(-t / 0.1) * 0.5;
      add(B.fx, start + k, v);
      add(B.verb, start + k, v * 0.2);
    }
  },
  whoosh(beat) {
    const start = idx(beat - 0.5);
    const n = Math.floor(0.85 * SR);
    const bl = new Biquad();
    const br = new Biquad();
    for (let k = 0; k < n; k++) {
      const p = k / n;
      if (k % 64 === 0) {
        const f = 500 + 3200 * Math.sin(Math.PI * p);
        bl.set("bp", f, 1.4);
        br.set("bp", f * 1.08, 1.4);
      }
      const env = Math.pow(Math.sin(Math.PI * Math.pow(p, 0.8)), 2) * 0.22;
      const pan = lerp(-0.8, 0.8, p);
      const [pl, pr] = panLR(pan);
      add(B.sfx, start + k, bl.run(noise()) * env * pl * 1.6, br.run(noise()) * env * pr * 1.6);
    }
  },
};

function renderSfx() {
  Object.values(tl.cues).forEach(({ b, s }) => {
    if (s && SFX[s]) SFX[s](b);
  });
}

function sidechain() {
  const duck = new Float32Array(N).fill(1);
  for (let bar = 0; bar < tl.bars; bar++) {
    if (!isMain(bar)) continue;
    for (let q = 0; q < 4; q++) {
      const s = idx(bar * 4 + q);
      const len = Math.floor(0.26 * SR);
      for (let k = 0; k < len && s + k < N; k++) {
        const g = 1 - 0.62 * (1 - smooth(k / len));
        duck[s + k] = Math.min(duck[s + k], g);
      }
    }
  }
  return duck;
}

function pingPong() {
  const d = Math.floor(sec(0.75) * SR);
  const L = new Float32Array(N);
  const R = new Float32Array(N);
  const lp = new Biquad().set("lp", 3500, 0.6);
  const lp2 = new Biquad().set("lp", 3500, 0.6);
  for (let i = 0; i < N; i++) {
    const inp = (B.delay[0][i] + B.delay[1][i]) * 0.5;
    const fl = i >= d ? R[i - d] : 0;
    const fr = i >= d ? L[i - d] : 0;
    L[i] = lp.run(inp + fl * 0.42);
    R[i] = lp2.run(fr * 0.95);
  }
  return [L, R];
}

function reverb(input) {
  const combs = [1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617];
  const aps = [556, 441, 341, 225];
  const out = [new Float32Array(N), new Float32Array(N)];
  for (let c = 0; c < 2; c++) {
    const spread = c ? 23 : 0;
    const bufs = combs.map((l) => ({ b: new Float32Array(l + spread), i: 0, s: 0 }));
    const abufs = aps.map((l) => ({ b: new Float32Array(l + spread), i: 0 }));
    const src = input[c];
    const dst = out[c];
    for (let n = 0; n < N; n++) {
      const x = src[n] * 0.015;
      let y = 0;
      for (const cb of bufs) {
        const o = cb.b[cb.i];
        cb.s = o * 0.7 + cb.s * 0.3;
        cb.b[cb.i] = x + cb.s * 0.86;
        cb.i = (cb.i + 1) % cb.b.length;
        y += o;
      }
      for (const ab of abufs) {
        const o = ab.b[ab.i];
        ab.b[ab.i] = y + o * 0.5;
        ab.i = (ab.i + 1) % ab.b.length;
        y = o - y;
      }
      dst[n] = y;
    }
  }
  return out;
}

const t0 = Date.now();
renderPad();
renderArp();
renderBass();
renderLead();
renderDrums();
renderSfx();

for (let i = 0; i < N; i++) {
  for (let c = 0; c < 2; c++) {
    B.verb[c][i] += B.pad[c][i] * 0.5 + B.arp[c][i] * 0.5 + B.lead[c][i] * 0.6;
  }
}
const duck = sidechain();
const [dl, dr] = pingPong();
const verb = reverb(B.verb);

const master = [new Float32Array(N), new Float32Array(N)];
const hpL = new Biquad().set("hp", 28, 0.7);
const hpR = new Biquad().set("hp", 28, 0.7);
const fadeOut = Math.floor(1.2 * SR);
let peak = 0;
for (let i = 0; i < N; i++) {
  const d = duck[i];
  const dd = [dl[i], dr[i]];
  for (let c = 0; c < 2; c++) {
    let v =
      B.kick[c][i] * 0.7 +
      B.drums[c][i] * 2.2 +
      B.bass[c][i] * d * 0.8 +
      B.pad[c][i] * (0.35 + 0.65 * d) * 1.0 +
      B.arp[c][i] * (0.4 + 0.6 * d) * 1.9 +
      B.lead[c][i] * 1.9 +
      B.fx[c][i] * 0.9 +
      B.sfx[c][i] * 1.0 +
      verb[c][i] * 0.9 +
      dd[c] * 0.6;
    v = c === 0 ? hpL.run(v) : hpR.run(v);
    v = Math.tanh(v * 1.1) / Math.tanh(1.1);
    if (i > N - fadeOut) v *= (N - i) / fadeOut;
    if (i < 400) v *= i / 400;
    master[c][i] = v;
    peak = Math.max(peak, Math.abs(v));
  }
}

const norm = 0.89 / peak;
const data = Buffer.alloc(44 + N * 4);
data.write("RIFF", 0);
data.writeUInt32LE(36 + N * 4, 4);
data.write("WAVE", 8);
data.write("fmt ", 12);
data.writeUInt32LE(16, 16);
data.writeUInt16LE(1, 20);
data.writeUInt16LE(2, 22);
data.writeUInt32LE(SR, 24);
data.writeUInt32LE(SR * 4, 28);
data.writeUInt16LE(4, 32);
data.writeUInt16LE(16, 34);
data.write("data", 36);
data.writeUInt32LE(N * 4, 40);
for (let i = 0; i < N; i++) {
  data.writeInt16LE(Math.round(clamp(master[0][i] * norm, -1, 1) * 32767), 44 + i * 4);
  data.writeInt16LE(Math.round(clamp(master[1][i] * norm, -1, 1) * 32767), 46 + i * 4);
}
writeFileSync(new URL("../public/music.wav", import.meta.url), data);

const report = Object.entries(S).map(([name, [a, b]]) => {
  let sum = 0;
  const s = idx(a * 4);
  const e = idx(b * 4);
  for (let i = s; i < e; i++) sum += (master[0][i] * norm) ** 2;
  return `${name.padEnd(10)} ${(20 * Math.log10(Math.sqrt(sum / (e - s)) + 1e-9)).toFixed(1)} dBFS`;
});
const busReport = Object.entries(B).map(([name, b]) => {
  const out = [];
  for (const [a, e] of [[0, 4], [4, 12], [12, 14], [16, 34]]) {
    let sum = 0;
    for (let i = idx(a * 4); i < idx(e * 4); i++) sum += b[0][i] ** 2;
    out.push((20 * Math.log10(Math.sqrt(sum / (idx(e * 4) - idx(a * 4))) + 1e-9)).toFixed(1).padStart(7));
  }
  return `${name.padEnd(6)}${out.join("")}`;
});
console.log(report.join("\n"));
console.log("bus    intro problem reveal  main\n" + busReport.join("\n"));
console.log(`music.wav ${(N / SR).toFixed(1)}s, peak gain ${norm.toFixed(2)}, ${Date.now() - t0} ms`);
