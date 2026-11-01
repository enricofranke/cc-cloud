// Static files of the demo, written to demo/public (gitignored) or DEMO_PUBLIC before Vite runs:
//   api/icon/<ns>/<path>.png   the icons of every item the simulation mentions: the exported ones when
//                              ./gradlew exportIcons has run (Mojang/mod art, never committed), else the mock's samples
//   store/                     a small Script Store, signed with a throwaway key made for this build
//   icon.svg                   the app icon
// DEMO_STORE=<dir> uses another store build instead of the test snapshot.
import { cpSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { generateKeyPairSync, sign } from "node:crypto";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { mockIcon } from "../dev/icons.ts";

const here = dirname(fileURLToPath(import.meta.url));
const SOURCES = ["ae2-sim.ts", "compat-cards.ts", "mock-core.ts", "production-sim.ts", "world-sim.ts"].map((f) => resolve(here, "../dev", f));
const ITEM_ID = /"([a-z0-9_.-]+:[a-z0-9_./-]+)"/g;

function icons(out: string) {
  const ids = new Set<string>();
  for (const f of SOURCES) for (const m of readFileSync(f, "utf8").matchAll(ITEM_ID)) ids.add(m[1]);
  const written: string[] = [];
  for (const id of ids) {
    const png = mockIcon(id);
    if (!png) continue;
    const [ns, ...path] = id.split(":");
    const file = join(out, ns, `${path.join(":")}.png`);
    mkdirSync(dirname(file), { recursive: true });
    writeFileSync(file, png);
    written.push(id);
  }
  return written;
}

function store(out: string) {
  const from = resolve(process.env.DEMO_STORE ?? resolve(here, "../test/fixtures/store"));
  if (!existsSync(join(from, "index.json"))) throw new Error(`no Script Store build in ${from}`);
  cpSync(from, out, { recursive: true });
  const { publicKey, privateKey } = generateKeyPairSync("ed25519");
  const spki = publicKey.export({ type: "spki", format: "der" });
  writeFileSync(join(out, "index.json.sig"), sign(null, readFileSync(join(out, "index.json")), privateKey).toString("base64"));
  return Buffer.from(spki.subarray(spki.length - 32)).toString("base64");
}

export function prepareAssets(publicDir: string) {
  for (const dir of ["api", "store"]) rmSync(join(publicDir, dir), { recursive: true, force: true });
  mkdirSync(publicDir, { recursive: true });
  cpSync(resolve(here, "../web/public/icon.svg"), join(publicDir, "icon.svg"));
  if (resolve(publicDir) !== resolve(here, "public")) cpSync(resolve(here, "public/boot.js"), join(publicDir, "boot.js"));
  const iconIds = icons(join(publicDir, "api", "icon"));
  const storeKey = store(join(publicDir, "store"));
  console.log(`[demo] ${iconIds.length} item icons, Script Store signed`);
  return { storeKey, iconIds };
}
