import { loadFont } from "@remotion/fonts";
import { staticFile } from "remotion";

export const brandFontsReady = Promise.all([
  loadFont({ family: "Bricolage Grotesque", url: staticFile("fonts/bricolage-grotesque.ttf"), weight: "200 800" }),
  loadFont({ family: "Figtree", url: staticFile("fonts/figtree.ttf"), weight: "300 900" }),
  loadFont({ family: "Instrument Serif", url: staticFile("fonts/instrument-serif-italic.ttf"), style: "italic" }),
  loadFont({ family: "Geist Mono", url: staticFile("fonts/geist-mono.ttf"), weight: "100 900" }),
]);
