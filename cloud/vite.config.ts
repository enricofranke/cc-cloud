import { defineConfig } from "vite";
import preact from "@preact/preset-vite";

// The mod serves the dashboard from the jar (classpath /cccloud_web/).
const target = process.env.MOCK_URL ?? "http://localhost:8787";

export default defineConfig({
  root: "web",
  plugins: [preact()],
  build: {
    outDir: "../../src/main/resources/cccloud_web",
    emptyOutDir: true,
    target: "es2022",
    assetsInlineLimit: 0,
  },
  server: {
    port: 5173,
    proxy: {
      "/api": target,
      "/ws": { target: target.replace(/^http/, "ws"), ws: true },
    },
  },
});
