import React from "react";
import { Composition, delayRender, continueRender } from "remotion";
import { fontsReady } from "./fonts";
import { Promo } from "./Promo";

const handle = delayRender("Chargement des polices");
fontsReady.then(() => continueRender(handle));

export const RemotionRoot: React.FC = () => (
  <Composition id="Promo" component={Promo} durationInFrames={3240} fps={30} width={1920} height={1080} />
);
