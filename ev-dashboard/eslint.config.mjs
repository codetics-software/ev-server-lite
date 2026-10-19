import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  {
    files: [
      "app/**/*.{ts,tsx}",
      "lib/**/*.{ts,tsx}",
      "hooks/**/*.{ts,tsx}",
      "features/**/*.{ts,tsx}",
      "components/codetics-mark.tsx",
    ],
    rules: {
      curly: ["error", "all"],
    },
  },
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
  ]),
]);

export default eslintConfig;
