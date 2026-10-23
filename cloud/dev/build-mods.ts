// Generates web/src/docs/content/mods.gen.json – the "Supported mods" docs page – from docs/COMPAT.md
// (wave 2 and 3 tables) and docs/BUILD.md (the v0.5 integrations table). Run by `pnpm build`.
//   node dev/build-mods.ts
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const root = new URL("../../", import.meta.url);
const compat = readFileSync(new URL("docs/COMPAT.md", root), "utf8");
const build = readFileSync(new URL("docs/BUILD.md", root), "utf8");

export interface ModEntry {
  group: string;
  mod: string;
  id: string;
  version: string;
  what: string;
  controls: string;
  tested: string;
  skipped: boolean;
}

const cells = (line: string) =>
  line
    .trim()
    .replace(/^\||\|$/g, "")
    .split(/(?<!\\)\|/)
    .map((c) => c.trim());
/** "Name (`id`)" → name + id */
function nameId(raw: string) {
  const m = /^(.*?)\s*\(`([a-z0-9_]+)`\)\s*(.*)$/.exec(raw);
  return m ? { mod: `${m[1]}${m[3] ? ` ${m[3]}` : ""}`.trim(), id: m[2] } : { mod: raw, id: "" };
}

const entries: ModEntry[] = [];

// ---- v0.5 integrations (docs/BUILD.md): names and versions, short descriptions here
const V05: Record<string, { what: string; controls: string }> = {
  powah: { what: "Energy cells, generators and the reactor: stored FE, in/out, reactor temperature and fuel", controls: "reactor on/off where exposed" },
  enderio: { what: "Capacitor banks and machines: stored FE, in/out, progress", controls: "redstone mode" },
  oritech: { what: "Energy storage and generators: stored FE, generation", controls: "–" },
  energizedpower: { what: "Machines and batteries: stored FE, progress", controls: "redstone mode" },
  energymeter: { what: "Flow per tick, total transferred, a 60-minute chart", controls: "transfer mode" },
  generatorgalore: { what: "Generators: stored FE, generation, fuel", controls: "redstone mode" },
  hostilenetworks: { what: "Simulation chamber: running, data model, accuracy, output", controls: "–" },
  productivebees: { what: "Hives and centrifuges: honey level, bees inside, output", controls: "–" },
  mffs: { what: "Force field projector: fortron, field blocks, status", controls: "field on/off (PIN)" },
  merequester: { what: "Kept-in-stock requests: amount, status", controls: "request on/off, amount" },
  spark: { what: "Server card: TPS, MSPT, CPU", controls: "–" },
  simplebackups: { what: "Backups card: last backup, size, running", controls: "backup now" },
};
const v05 = build.slice(build.indexOf("## Optional integration mods"), build.indexOf("### Wave 2"));
for (const line of v05.split("\n")) {
  if (!line.startsWith("| ") || line.startsWith("| Mod") || line.startsWith("| ---")) continue;
  const c = cells(line);
  const m = /^(.*?)\s+([0-9][^ ]*)?\s*\(`([a-z0-9_]+)`\)/.exec(c[0]);
  if (m && V05[m[3]]) entries.push({ group: "v05", mod: m[1], id: m[3], version: m[2] ?? "", ...V05[m[3]], tested: "yes", skipped: false });
  else if (c[0].startsWith("FTB Teams"))
    entries.push({ group: "v05", mod: "FTB Teams / FTB Chunks", id: "ftbteams", version: "26.1.2", what: "Team trust for roles, team claims on the network map", controls: "–", tested: "yes", skipped: false });
}

// ---- docs/COMPAT.md: every table with a "Mod (id)" header, grouped by its ### heading
let group = "";
let header: string[] = [];
for (const line of compat.split("\n")) {
  const h = /^###\s+(.*)$/.exec(line) ?? /^##\s+(Wave 3.*)$/.exec(line);
  if (h) {
    group = h[1].replace(/^\d+\.\s*/, "").replace(/\s*\(.*\)$/, "").trim();
    header = [];
    continue;
  }
  if (!line.startsWith("|")) continue;
  const c = cells(line);
  if (c[0] === "Mod (id)" || c[0] === "Mod") {
    header = c.map((x) => x.toLowerCase());
    continue;
  }
  if (c[0].startsWith("---") || !header.length) continue;
  const col = (name: string) => c[header.findIndex((x) => x.startsWith(name))] ?? "";
  const { mod, id } = nameId(c[0]);
  const what = col("what") || col("card") || "";
  if (/^already done/i.test(what)) continue; // listed with the v0.5 integrations
  const skipped = /^skipped|^no own card|^no card|^none/i.test(what) || /skipped/i.test(col("card"));
  entries.push({
    group,
    mod,
    id,
    version: col("version").replace(/^–$/, ""),
    what: col("blocks") && col("card") ? `${col("blocks")}: ${col("card")} card` : what,
    controls: col("controls"),
    tested: col("tested"),
    skipped,
  });
}

const outFile = fileURLToPath(new URL("../web/src/docs/content/mods.gen.json", import.meta.url));
writeFileSync(outFile, `${JSON.stringify({ source: "docs/COMPAT.md, docs/BUILD.md", mods: entries }, null, 1)}\n`);
console.log(`wrote ${entries.length} mods to web/src/docs/content/mods.gen.json`);
