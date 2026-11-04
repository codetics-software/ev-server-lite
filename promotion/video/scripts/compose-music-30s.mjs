import { readFileSync, writeFileSync } from "node:fs";

const tl = JSON.parse(readFileSync(new URL("../src/lite30/timeline.json", import.meta.url), "utf8"));
const OUT = new URL("../public/music-30s.wav", import.meta.url);

const SR = 44100;
const SPB = 60 / tl.bpm;
const BEATS = tl.bars * 4;
const N = Math.ceil(BEATS * SPB * SR);
const TAU = Math.PI * 2;
const cue = (name) => tl.cues[name];

let seed = 20261027;
const rand = () => {
  seed |= 0;
  seed = (seed + 0x6d2b79f5) | 0;
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
};
const noise = () => rand() * 2 - 1;

const mtof = (m) => 440 * Math.pow(2, (m - 69) / 12);
const idx = (beat) => Math.round(beat * SPB * SR);
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));

const bus = () => [new Float32Array(N), new Float32Array(N)];
const B = { drums: bus(), bass: bus(), pad: bus(), pluck: bus(), lead: bus(), fx: bus(), verb: bus(), delay: bus() };

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
  F: { root: 41, notes: [53, 57, 60, 64] },
  C: { root: 36, notes: [52, 55, 60, 62] },
  Dm: { root: 38, notes: [53, 57, 60, 62] },
  Bb: { root: 34, notes: [53, 58, 60, 62] },
};
const LOOP = ["F", "C", "Dm", "Bb"];

const chordAt = (beat) => {
  const bar = Math.floor(beat / 4);
  if (bar === 12) return "Dm";
  if (bar === 13) return beat - 52 < 2 ? "Bb" : "C";
  if (bar >= 14) return "F";
  return LOOP[bar % 4];
};

const segments = () => {
  const out = [];
  for (let b = 0; b < BEATS; b += 2) {
    const name = chordAt(b);
    const last = out[out.length - 1];
    if (last && last.name === name) last.dur += 2;
    else out.push({ name, at: b, dur: 2 });
  }
  return out;
};

const drumsOn = (beat) => (beat >= cue("drop") && beat < cue("free")) || (beat >= cue("cta") && beat < cue("final"));

function voice(b, o) {
  const { at, dur, midi, gain, pan = 0, wave = "saw", voices = 1, spread = 0.1, attack = 0.005, release = 0.12, cutoff = 2000, envAmt = 0, envDecay = 0.15, q = 0.8, verb = 0, delay = 0, cutoffTo } = o;
  const start = idx(at);
  const hold = dur * SPB;
  const len = Math.round((hold + release * 5) * SR);
  const filters = [new Biquad(), new Biquad()];
  const phases = Array.from({ length: voices }, () => rand());
  const [pl, pr] = panLR(pan);
  for (let i = 0; i < len; i++) {
    const t = i / SR;
    let amp = Math.min(1, t / attack);
    if (t > hold) amp *= Math.exp(-(t - hold) / release);
    if (amp < 1e-4 && t > hold) break;
    if (i % 32 === 0) {
      const base = cutoffTo === undefined ? cutoff : cutoff + (cutoffTo - cutoff) * clamp(t / hold, 0, 1);
      const fc = base + envAmt * Math.exp(-t / envDecay);
      filters[0].set("lp", fc, q);
      filters[1].set("lp", fc, q);
    }
    let s = 0;
    for (let v = 0; v < voices; v++) {
      const det = voices === 1 ? 0 : (v / (voices - 1) - 0.5) * 2 * spread;
      const f = mtof(midi + det);
      const dt = f / SR;
      phases[v] = (phases[v] + dt) % 1;
      const p = phases[v];
      if (wave === "saw") s += 2 * p - 1 - blep(p, dt);
      else if (wave === "square") s += (p < 0.5 ? 1 : -1) + blep(p, dt) - blep((p + 0.5) % 1, dt);
      else s += Math.sin(TAU * p);
    }
    s = filters[1].run(filters[0].run(s / Math.sqrt(voices))) * amp * gain;
    add(b, start + i, s * pl, s * pr);
    if (verb) add(B.verb, start + i, s * verb * pl, s * verb * pr);
    if (delay) add(B.delay, start + i, s * delay * pl, s * delay * pr);
  }
}

const kickTimes = [];
function kick(at, g = 1) {
  kickTimes.push(idx(at));
  const s = idx(at);
  let ph = 0;
  for (let i = 0; i < 0.55 * SR; i++) {
    const t = i / SR;
    const f = 46 + 120 * Math.exp(-t / 0.028);
    ph += (TAU * f) / SR;
    const v = Math.tanh(1.6 * Math.sin(ph) * Math.exp(-t / 0.3)) + noise() * Math.exp(-t / 0.0025) * 0.35;
    add(B.drums, s + i, v * g * 0.9);
  }
}

function noiseHit(b, at, { gain, type = "bp", f = 1500, q = 1, decay = 0.1, len = 0.5, pan = 0, verb = 0, bursts = [0] }) {
  const s = idx(at);
  const flt = new Biquad().set(type, f, q);
  const [pl, pr] = panLR(pan);
  for (let i = 0; i < len * SR; i++) {
    const t = i / SR;
    let env = 0;
    for (const o of bursts) if (t >= o) env = Math.max(env, Math.exp(-(t - o) / (o === bursts[bursts.length - 1] ? decay : 0.007)));
    const v = flt.run(noise()) * env * gain;
    add(b, s + i, v * pl, v * pr);
    if (verb) add(B.verb, s + i, v * verb * pl, v * verb * pr);
  }
}

const clap = (at) => noiseHit(B.drums, at, { gain: 0.55, f: 1300, q: 0.9, decay: 0.13, bursts: [0, 0.011, 0.022], verb: 0.35 });
const hat = (at, open = false, g = 1) => noiseHit(B.drums, at, { gain: 0.22 * g, type: "hp", f: 7500, decay: open ? 0.11 : 0.028, len: open ? 0.4 : 0.12, pan: open ? 0.25 : -0.2 });
function snare(at, g) {
  noiseHit(B.drums, at, { gain: 0.4 * g, f: 2200, q: 0.7, decay: 0.09, len: 0.3, verb: 0.2 });
  voice(B.drums, { at, dur: 0.05, midi: 55, gain: 0.25 * g, wave: "sine", release: 0.05, cutoff: 3000 });
}

function sweep(at, beats, { from, to, gain, curve = 2, verb = 0.3, shape = "rise" }) {
  const s = idx(at);
  const len = Math.round(beats * SPB * SR);
  const fl = new Biquad();
  const fr = new Biquad();
  for (let i = 0; i < len; i++) {
    const x = i / len;
    if (i % 32 === 0) {
      const f = from * Math.pow(to / from, x);
      fl.set("bp", f, 1.4);
      fr.set("bp", f * 1.03, 1.4);
    }
    const env = shape === "rise" ? Math.pow(x, curve) : Math.sin(Math.PI * Math.pow(x, 1.6));
    const l = fl.run(noise()) * env * gain;
    const r = fr.run(noise()) * env * gain;
    add(B.fx, s + i, l, r);
    add(B.verb, s + i, l * verb, r * verb);
  }
}

function impact(at, g = 1) {
  const s = idx(at);
  let ph = 0;
  for (let i = 0; i < 2.2 * SR; i++) {
    const t = i / SR;
    ph += (TAU * (32 + 45 * Math.exp(-t / 0.18))) / SR;
    add(B.fx, s + i, Math.sin(ph) * Math.exp(-t / 0.9) * 0.75 * g);
  }
  noiseHit(B.fx, at, { gain: 0.5 * g, type: "lp", f: 4500, decay: 0.7, len: 2.5, verb: 0.6 });
  noiseHit(B.fx, at, { gain: 0.18 * g, type: "hp", f: 6000, decay: 1.1, len: 3, pan: 0.3, verb: 0.4 });
}

function bell(at, midi, g = 1, pan = 0) {
  const s = idx(at);
  const f = mtof(midi);
  const [pl, pr] = panLR(pan);
  for (let i = 0; i < 1.8 * SR; i++) {
    const t = i / SR;
    const mod = Math.sin(TAU * f * 3.5 * t) * 2.2 * Math.exp(-t / 0.25);
    const v = Math.sin(TAU * f * t + mod) * Math.exp(-t / 0.55) * 0.22 * g * Math.min(1, t / 0.002);
    add(B.fx, s + i, v * pl, v * pr);
    add(B.delay, s + i, v * 0.4 * pl, v * 0.4 * pr);
    add(B.verb, s + i, v * 0.5 * pl, v * 0.5 * pr);
  }
}

function blip(at, g = 1) {
  const s = idx(at);
  let ph = 0;
  for (let i = 0; i < 0.16 * SR; i++) {
    const t = i / SR;
    ph += (TAU * (1320 + 700 * Math.min(1, t / 0.03))) / SR;
    const v = (Math.sin(ph) + 0.3 * Math.sin(2 * ph)) * Math.exp(-t / 0.05) * 0.2 * g;
    add(B.fx, s + i, v, v);
    add(B.delay, s + i, v * 0.5, v * 0.5);
  }
}

function thud(at) {
  const s = idx(at);
  let ph = 0;
  for (let i = 0; i < 0.3 * SR; i++) {
    const t = i / SR;
    ph += (TAU * (75 + 140 * Math.exp(-t / 0.04))) / SR;
    add(B.fx, s + i, Math.sin(ph) * Math.exp(-t / 0.11) * 0.45 + noise() * Math.exp(-t / 0.004) * 0.15);
  }
}

const keyClick = (at) => noiseHit(B.fx, at, { gain: 0.12 + rand() * 0.06, type: "hp", f: 3500, decay: 0.008, len: 0.04, pan: rand() * 0.6 - 0.3 });

for (let beat = 0; beat < BEATS; beat++) {
  const inBar = beat % 4;
  if (drumsOn(beat)) {
    const fill = beat >= 46 && beat < 48;
    if (!fill) kick(beat);
    if (inBar === 1 || inBar === 3) if (!fill) clap(beat);
    hat(beat + 0.5, true);
    if (beat >= cue("install")) {
      hat(beat + 0.25, false, 0.6);
      hat(beat + 0.75, false, 0.6);
    }
  }
}
for (let i = 0; i < 8; i++) snare(46 + i * 0.25, 0.4 + i * 0.04);
for (let i = 0; i < 8; i++) snare(47 + i * 0.125, 0.7 + i * 0.04);
kick(cue("free"), 1.1);
kick(cue("final"), 1.1);
for (const b of [6, 7, 7.5, 7.75]) hat(b, false, 0.8);

for (const seg of segments()) {
  const c = CHORDS[seg.name];
  const intro = seg.at < cue("drop");
  const brk = seg.at >= cue("free") && seg.at < cue("cta");
  const end = seg.at >= cue("final");
  for (const [k, m] of c.notes.entries()) {
    voice(B.pad, {
      at: seg.at,
      dur: end ? 4 : seg.dur,
      midi: m,
      gain: intro ? 0.16 : brk ? 0.2 : 0.13,
      pan: (k / 3 - 0.5) * 1.2,
      voices: 3,
      spread: 0.12,
      attack: intro || brk ? 0.4 : 0.03,
      release: end ? 1.2 : 0.25,
      cutoff: intro ? 500 + seg.at * 120 : brk ? 900 : 2200,
      cutoffTo: intro ? 600 + (seg.at + seg.dur) * 160 : brk ? 3200 : undefined,
      verb: 0.35,
    });
  }
}

for (let s = 0; s < BEATS * 4; s++) {
  const beat = s / 4;
  if (beat >= cue("final")) break;
  if (beat >= cue("free") && beat < cue("cta")) continue;
  const intro = beat < cue("drop");
  if (intro && s % 2) continue;
  if (!intro && beat < cue("install") && s % 2 && beat < cue("fixes")) continue;
  const c = CHORDS[chordAt(beat)];
  const order = [0, 1, 2, 3, 2, 1, 3, 1];
  const step = intro ? (s / 2) % 8 : s % 8;
  voice(B.pluck, {
    at: beat,
    dur: 0.12,
    midi: c.notes[order[step]] + 12,
    gain: intro ? 0.2 : 0.16,
    pan: step % 2 ? 0.35 : -0.35,
    wave: "saw",
    voices: 2,
    spread: 0.08,
    release: 0.08,
    cutoff: 700,
    envAmt: intro ? 1200 + beat * 260 : 3600,
    envDecay: 0.07,
    delay: 0.35,
    verb: 0.15,
  });
}

for (let beat = 0; beat < BEATS; beat++) {
  const bassBar = (beat >= cue("drop") && beat < cue("free")) || (beat >= cue("cta") && beat < cue("final"));
  if (bassBar && !(beat >= 46 && beat < 48)) {
    const root = CHORDS[chordAt(beat)].root;
    voice(B.bass, { at: beat + 0.5, dur: 0.38, midi: root, gain: 0.5, wave: "saw", voices: 2, spread: 0.05, release: 0.05, cutoff: 260, envAmt: 900, envDecay: 0.08, q: 1.1 });
    voice(B.bass, { at: beat + 0.5, dur: 0.38, midi: root - 12, gain: 0.4, wave: "sine", release: 0.05, cutoff: 400 });
  }
}
voice(B.bass, { at: cue("free"), dur: 4, midi: 26, gain: 0.45, wave: "sine", attack: 0.01, release: 0.4, cutoff: 300 });
voice(B.bass, { at: cue("final"), dur: 3.2, midi: 29, gain: 0.55, wave: "sine", attack: 0.01, release: 0.6, cutoff: 300 });
voice(B.bass, { at: cue("final"), dur: 3.2, midi: 41, gain: 0.25, wave: "saw", voices: 2, release: 0.6, cutoff: 500 });

const MOTIF = {
  F: [[81, 0, 0.5], [79, 0.75, 0.5], [77, 1.5, 1], [72, 3, 0.5]],
  C: [[79, 0, 0.5], [76, 0.75, 0.5], [74, 1.5, 1], [72, 3, 0.4], [74, 3.5, 0.4]],
  Dm: [[77, 0, 0.5], [76, 0.75, 0.5], [74, 1.5, 1], [69, 3, 0.5]],
  Bb: [[74, 0, 0.5], [72, 0.75, 0.5], [70, 1.5, 1.4], [72, 3.5, 0.4]],
};
const leadBars = [2, 3, 4, 5, 13];
for (const bar of leadBars) {
  const name = bar === 13 ? "C" : chordAt(bar * 4);
  for (const [m, o, d] of MOTIF[name]) {
    voice(B.lead, { at: bar * 4 + o, dur: d, midi: m, gain: 0.17, pan: 0.05, wave: "square", voices: 2, spread: 0.06, attack: 0.006, release: 0.1, cutoff: 1400, envAmt: 2600, envDecay: 0.12, delay: 0.3, verb: 0.25 });
  }
}
voice(B.lead, { at: cue("final"), dur: 2.6, midi: 77, gain: 0.16, wave: "square", voices: 2, spread: 0.06, release: 0.8, cutoff: 1800, envAmt: 2000, envDecay: 0.3, delay: 0.3, verb: 0.4 });

sweep(4, 4, { from: 250, to: 7000, gain: 0.32, curve: 2.4 });
sweep(44, 4, { from: 250, to: 8000, gain: 0.36, curve: 2.4 });
for (const t of ["feature.security", "feature.hardware", "feature.autocharge", "fixes", "install", "deploy", "cta"]) sweep(cue(t) - 0.8, 0.8, { from: 600, to: 5000, gain: 0.3, shape: "swell", verb: 0.2 });
impact(cue("logo"), 0.6);
impact(cue("drop"), 0.9);
impact(cue("free"), 1);
impact(cue("final"), 1);
bell(cue("logo"), 77, 1, -0.2);
bell(cue("title"), 84, 0.8, 0.2);
bell(cue("free"), 81, 1);
bell(cue("cta.button"), 84, 0.9);
bell(cue("final"), 89, 0.9);
for (const t of ["fix.1", "fix.2", "fix.3", "install.ok"]) blip(cue(t));
for (const t of ["deploy.edge", "deploy.app", "deploy.data"]) thud(cue(t));
for (let k = 0; k < 5; k++) blip(cue("deploy.clouds") + k * 0.25, 0.55);
for (const t of ["install.1", "install.2", "install.3"]) for (let s = 0; s < 10; s++) if (rand() > 0.2) keyClick(cue(t) + s * 0.125 + rand() * 0.03);

const duck = new Float32Array(N).fill(1);
for (const k of kickTimes)
  for (let i = 0; i < 0.3 * SR && k + i < N; i++) duck[k + i] = Math.min(duck[k + i], 1 - 0.7 * Math.exp(-i / SR / 0.085));

function reverb([inL, inR]) {
  const out = bus();
  const sc = SR / 44100;
  const combs = [1116, 1188, 1277, 1356, 1422, 1491];
  const aps = [556, 441, 341];
  for (const [ch, input, off] of [[0, inL, 0], [1, inR, 23]]) {
    const pre = Math.round(0.02 * SR);
    const cb = combs.map((c) => ({ buf: new Float32Array(Math.round((c + off) * sc)), i: 0, z: 0 }));
    const ab = aps.map((a) => ({ buf: new Float32Array(Math.round((a + off) * sc)), i: 0 }));
    for (let n = 0; n < N; n++) {
      const x = n >= pre ? input[n - pre] * 0.2 : 0;
      let y = 0;
      for (const c of cb) {
        const o = c.buf[c.i];
        c.z = o * 0.75 + c.z * 0.25;
        c.buf[c.i] = x + c.z * 0.86;
        c.i = (c.i + 1) % c.buf.length;
        y += o;
      }
      for (const a of ab) {
        const o = a.buf[a.i];
        a.buf[a.i] = y + o * 0.5;
        a.i = (a.i + 1) % a.buf.length;
        y = o - y * 0.5;
      }
      out[ch][n] = y;
    }
  }
  return out;
}

function pingPong([inL, inR]) {
  const out = bus();
  const d = Math.round(0.75 * SPB * SR);
  const lpL = new Biquad().set("lp", 3500);
  const lpR = new Biquad().set("lp", 3500);
  for (let n = 0; n < N; n++) {
    const fromR = n >= d ? out[1][n - d] : 0;
    const fromL = n >= d ? out[0][n - d] : 0;
    out[0][n] = lpL.run((n >= d ? inL[n - d] + inR[n - d] : 0) * 0.5 + fromR * 0.42);
    out[1][n] = lpR.run(fromL * 0.42);
  }
  return out;
}

const verb = reverb(B.verb);
const dly = pingPong(B.delay);
const master = bus();
const hpL = new Biquad().set("hp", 28);
const hpR = new Biquad().set("hp", 28);
for (let ch = 0; ch < 2; ch++) {
  const hp = ch ? hpR : hpL;
  for (let n = 0; n < N; n++) {
    const sc = duck[n];
    const x =
      B.drums[ch][n] * 0.85 +
      B.bass[ch][n] * sc * 0.9 +
      B.pad[ch][n] * (0.35 + 0.65 * sc) * 0.8 +
      B.pluck[ch][n] * (0.3 + 0.7 * sc) * 0.8 +
      B.lead[ch][n] * 0.7 +
      B.fx[ch][n] * 0.75 +
      verb[ch][n] * 0.55 +
      dly[ch][n] * 0.45;
    master[ch][n] = hp.run(x);
  }
}

let peak = 0;
for (let ch = 0; ch < 2; ch++) for (let n = 0; n < N; n++) peak = Math.max(peak, Math.abs(master[ch][n]));
const drive = 1.25 / peak;
const fadeIn = Math.round(0.01 * SR);
const fadeOut = Math.round(1.1 * SR);
const pcm = Buffer.alloc(44 + N * 4);
pcm.write("RIFF", 0);
pcm.writeUInt32LE(36 + N * 4, 4);
pcm.write("WAVEfmt ", 8);
pcm.writeUInt32LE(16, 16);
pcm.writeUInt16LE(1, 20);
pcm.writeUInt16LE(2, 22);
pcm.writeUInt32LE(SR, 24);
pcm.writeUInt32LE(SR * 4, 28);
pcm.writeUInt16LE(4, 32);
pcm.writeUInt16LE(16, 34);
pcm.write("data", 36);
pcm.writeUInt32LE(N * 4, 40);
for (let n = 0; n < N; n++) {
  const g = Math.min(1, n / fadeIn) * Math.min(1, (N - n) / fadeOut);
  for (let ch = 0; ch < 2; ch++) {
    const v = (Math.tanh(master[ch][n] * drive) / Math.tanh(1.25)) * 0.93 * g;
    pcm.writeInt16LE(Math.round(clamp(v, -1, 1) * 32767), 44 + n * 4 + ch * 2);
  }
}
writeFileSync(OUT, pcm);
console.log(`music-30s.wav · ${(N / SR).toFixed(2)} s · ${tl.bpm} BPM`);
