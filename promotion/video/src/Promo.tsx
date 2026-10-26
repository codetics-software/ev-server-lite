import React from "react";
import { Audio } from "@remotion/media";
import { AbsoluteFill, Sequence, staticFile, Track } from "remotion";
import { AbsTime } from "./components/AbsTime";
import { Intro } from "./scenes/Intro";
import { Method } from "./scenes/Method";
import { Outro } from "./scenes/Outro";
import { Problem } from "./scenes/Problem";
import { Reveal } from "./scenes/Reveal";
import { Badge, Title } from "./scenes/Title";
import { World } from "./scenes/World";
import { sectionDuration, sectionFrom, TOTAL_FRAMES } from "./time";

const Scene: React.FC<{ readonly name: string; readonly from: number; readonly duration: number; readonly children: React.ReactNode }> = ({
  name,
  from,
  duration,
  children,
}) => (
  <Sequence name={name} from={from} durationInFrames={duration}>
    <AbsTime offset={from}>{children}</AbsTime>
  </Sequence>
);

export const Promo: React.FC = () => (
  <AbsoluteFill style={{ background: "#000" }}>
    <Track name="Scènes">
      <Scene name="Intro" from={sectionFrom("intro")} duration={sectionDuration("intro")}>
        <Intro />
      </Scene>
      <Scene name="Problème" from={sectionFrom("problem")} duration={sectionDuration("problem")}>
        <Problem />
      </Scene>
      <Scene name="Révélation" from={sectionFrom("reveal")} duration={sectionDuration("reveal")}>
        <Reveal />
      </Scene>
      <Scene name="Titre" from={sectionFrom("title")} duration={sectionDuration("title")}>
        <Title />
      </Scene>
      <Scene name="Infrastructure" from={sectionFrom("plug")} duration={sectionFrom("method") - sectionFrom("plug")}>
        <World />
      </Scene>
      <Scene name="Méthode" from={sectionFrom("method")} duration={sectionDuration("method")}>
        <Method />
      </Scene>
      <Scene name="Fin" from={sectionFrom("outro")} duration={sectionDuration("outro")}>
        <Outro />
      </Scene>
    </Track>
    <Scene name="Badge" from={sectionFrom("title")} duration={sectionFrom("method") - sectionFrom("title")}>
      <Badge />
    </Scene>
    <Audio name="Musique" src={staticFile("music.wav")} durationInFrames={TOTAL_FRAMES} />
  </AbsoluteFill>
);
