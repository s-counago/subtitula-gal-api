import { cloudflareTest } from "@cloudflare/vitest-pool-workers";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [
    cloudflareTest({
      main: "./src/index.ts",
      // Keep unit tests fully local. Workers AI is intentionally omitted here:
      // configuring that remote-only binding would make tests require account auth.
      miniflare: {
        compatibilityDate: "2026-07-29",
        compatibilityFlags: ["nodejs_compat"],
      },
    }),
  ],
});
