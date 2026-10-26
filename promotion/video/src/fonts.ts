import { loadFont } from "@remotion/fonts";
import { staticFile } from "remotion";

export const fontsReady = Promise.all(
  (["regular", "medium", "semibold"] as const).map((w, i) =>
    loadFont({
      family: "Source Sans 3",
      url: staticFile(`fonts/source-sans-3-${w}.ttf`),
      weight: String(400 + i * 100),
    }),
  ),
);
