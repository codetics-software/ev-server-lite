import React from "react";
import { Audio } from "@remotion/media";
import { AbsoluteFill, Sequence, staticFile, Track } from "remotion";
import { AbsTime } from "../components/AbsTime";
import { Backdrop } from "./Backdrop";
import { at, K, TOTAL } from "./brand";
import { Features } from "./Features";
import { Cta, Free } from "./Finale";
import { Intro } from "./Intro";
import { Deploy, Fixes, Install } from "./Steps";

const Scene: React.FC<{ readonly name: string; readonly from: number; readonly to: number; readonly children: React.ReactNode }> = ({ name, from, to, children }) => (
  <Sequence name={name} from={from} durationInFrames={to - from}>
    <AbsTime offset={from}>{children}</AbsTime>
  </Sequence>
);

export const Lite30: React.FC = () => (
  <AbsoluteFill style={{ background: K.paper }}>
    <Backdrop />
    <Track name="Scènes">
      <Scene name="Intro" from={0} to={at("drop")}>
        <Intro />
      </Scene>
      <Scene name="Nouveautés" from={at("drop")} to={at("fixes")}>
        <Features />
      </Scene>
      <Scene name="Corrections" from={at("fixes")} to={at("install")}>
        <Fixes />
      </Scene>
      <Scene name="Installation" from={at("install")} to={at("deploy")}>
        <Install />
      </Scene>
      <Scene name="Déploiement" from={at("deploy")} to={at("free")}>
        <Deploy />
      </Scene>
    </Track>
    <Scene name="Gratuit" from={at("free") - 8} to={at("cta") + 14}>
      <Free />
    </Scene>
    <Scene name="Codetics" from={at("cta") - 4} to={TOTAL}>
      <Cta />
    </Scene>
    <Audio name="Musique" src={staticFile("music-30s.wav")} durationInFrames={TOTAL} />
  </AbsoluteFill>
);
