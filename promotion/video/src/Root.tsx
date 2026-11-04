import React from "react";
import { Composition, delayRender, continueRender } from "remotion";
import { fontsReady } from "./fonts";
import { brandFontsReady } from "./lite30/fonts";
import { TOTAL } from "./lite30/brand";
import { Lite30 } from "./lite30/Lite30";
import { Promo } from "./Promo";

const handle = delayRender("Chargement des polices");
Promise.all([fontsReady, brandFontsReady]).then(() => continueRender(handle));

export const RemotionRoot: React.FC = () => (
  <>
    <Composition id="Promo" component={Promo} durationInFrames={3240} fps={30} width={1920} height={1080} />
    <Composition id="Lite30-4x5" component={Lite30} durationInFrames={TOTAL} fps={30} width={1080} height={1350} />
    <Composition id="Lite30-9x16" component={Lite30} durationInFrames={TOTAL} fps={30} width={1080} height={1920} />
  </>
);
