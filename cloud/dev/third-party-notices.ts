// Writes licenses.txt into the dashboard bundle: the licence text of every package that really ended up in the build,
// taken from the modules Rollup saw, plus the bundled font. The mod jar ships this file, so its MIT, ISC and OFL
// notices travel with every copy of the dashboard.
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import type { Plugin } from "vite";

const HERE = dirname(fileURLToPath(import.meta.url));
const PACKAGE = /^(.*\/node_modules\/((?:@[^/]+\/)?[^/]+))\//;
const LICENSE_FILE = /^(licen[cs]e|copying|notice)(\.|$)/i;
const RULE = "=".repeat(78);

interface Entry {
  name: string;
  version: string;
  license: string;
  texts: string[];
}

function licenseTexts(dir: string): string[] {
  return readdirSync(dir)
    .filter((f) => LICENSE_FILE.test(f))
    .sort()
    .map((f) => readFileSync(join(dir, f), "utf8").trim());
}

/** The packages behind these module ids; a package without a licence file or field fails the build. */
export function collectPackages(moduleIds: Iterable<string>): Entry[] {
  const found = new Map<string, Entry>();
  for (const raw of moduleIds) {
    const id = raw.replace(/^\0/, "").split("?")[0].replaceAll("\\", "/");
    const match = PACKAGE.exec(id);
    if (!match) continue;
    const [, dir, name] = match;
    const manifest = join(dir, "package.json");
    if (!existsSync(manifest)) continue;
    const pkg = JSON.parse(readFileSync(manifest, "utf8")) as { version: string; license?: string };
    const key = `${name}@${pkg.version}`;
    if (found.has(key)) continue;
    const texts = licenseTexts(dir);
    if (!pkg.license && texts.length === 0) throw new Error(`no licence information for ${key}`);
    found.set(key, { name, version: pkg.version, license: pkg.license ?? "see text", texts });
  }
  return [...found.values()].sort((a, b) => a.name.localeCompare(b.name));
}

export function renderNotices(entries: Entry[], font: string): string {
  const parts = [
    "CC: Cloud dashboard: third-party notices",
    "",
    "CC: Cloud is licensed under the Mozilla Public License 2.0.",
    "Source code: https://github.com/enricofranke/cc-cloud",
    "The packages below are part of this dashboard and keep their own licences.",
  ];
  for (const e of entries) {
    parts.push("", RULE, `${e.name} ${e.version} (${e.license})`, RULE, "", ...(e.texts.length ? e.texts : [`Licensed under ${e.license}.`]));
  }
  parts.push("", RULE, "Silkscreen font (SIL Open Font License 1.1)", RULE, "", font.trim(), "");
  return parts.join("\n");
}

export function thirdPartyNotices(): Plugin {
  return {
    name: "cccloud-third-party-notices",
    apply: "build",
    generateBundle() {
      const font = readFileSync(join(HERE, "../web/src/assets/silkscreen-OFL.txt"), "utf8");
      this.emitFile({ type: "asset", fileName: "licenses.txt", source: renderNotices(collectPackages(this.getModuleIds()), font) });
    },
  };
}
