// The public live demo (https://cccloud.dev/demo/): the real dashboard on top of the mock's simulation, which runs in a
// Web Worker in the visitor's browser. There is no backend. Nothing here ends up in the mod jar: the production build
// (../vite.config.ts) has its own root and never sees this folder.
//   pnpm demo           dev server on :5174/demo/
//   pnpm build:demo     writes demo/dist (DEMO_OUT=<dir> to change, DEMO_BASE=/some/path/ for another base)
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig, type Plugin } from "vite";
import preact from "@preact/preset-vite";
import { prepareAssets } from "./assets.ts";

const here = dirname(fileURLToPath(import.meta.url));
const src = (p: string) => resolve(here, "..", "web/src", p);
const demo = (p: string) => resolve(here, "src", p);

/**
 * Dashboard modules the demo replaces at build time, so it needs no hooks in the production code. A replacement can
 * still import the original with the suffix `?original`. The build fails when an original is renamed or moved.
 */
const SWAPS: Record<string, string> = {
  [src("lib/icons.ts")]: demo("swap/icons.ts"),
  [src("screens/Pair.tsx")]: demo("swap/Pair.tsx"),
  [src("components/DocsLink.tsx")]: demo("swap/DocsLink.tsx"),
  [src("docs/Docs.tsx")]: demo("swap/Docs.tsx"),
};

function swapModules(): Plugin {
  return {
    name: "demo-swap-modules",
    enforce: "pre",
    async buildStart() {
      for (const id of Object.keys(SWAPS)) if (!(await this.fs.stat(id).catch(() => null))) this.error(`swapped module is gone: ${id}`);
    },
    async resolveId(source, importer, options) {
      if (!importer || source.includes("\0")) return null;
      const original = source.endsWith("?original");
      const resolved = await this.resolve(original ? source.slice(0, -"?original".length) : source, importer, { ...options, skipSelf: true });
      if (!resolved) return null;
      if (original) return `${resolved.id}?original`;
      return SWAPS[resolved.id] ?? null;
    },
  };
}

const nodeShims = Object.fromEntries(["crypto", "zlib"].map((m) => [`node:${m}`, demo(`node/${m}.ts`)]));

export default defineConfig(({ isPreview }) => ({
  root: here,
  base: process.env.DEMO_BASE ?? "/demo/",
  plugins: [preact(), swapModules()],
  resolve: { alias: nodeShims },
  // `vite preview` only serves the last build
  define: isPreview ? {} : { __DEMO_STORE_KEY__: JSON.stringify(prepareAssets(resolve(here, "public")).storeKey) },
  worker: { format: "es", plugins: () => [swapModules()] },
  build: {
    outDir: process.env.DEMO_OUT ?? resolve(here, "dist"),
    emptyOutDir: true,
    target: "es2022",
    assetsInlineLimit: 0,
    chunkSizeWarningLimit: 900,
  },
  server: {
    port: 5174,
    // the script gallery bundles ../examples/*.lua from the repository
    fs: { allow: [resolve(here, "../..")] },
  },
}));
