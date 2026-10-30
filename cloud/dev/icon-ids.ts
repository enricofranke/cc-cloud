// Every item id the website docs and the dashboard mock show an icon for – the input of `./gradlew exportIcons`
// (docs/BUILD.md "Item icons for the website and the mock"): recipe results and ingredients (a tag becomes a typical
// member), the docs' named items, the card icons of the "Supported mods" page and the mock's storage items.
//   node dev/icon-ids.ts [outFile]      (prints the list without outFile)
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import GEN from "../web/src/docs/content/recipes.gen.json" with { type: "json" };
import { ITEMS, iconItem } from "../web/src/docs/content/index.ts";
import { modIconIds } from "../web/src/docs/content/mods-meta.ts";
import { buildItems } from "./ae2-sim.ts";

const ID = /^[a-z0-9_.-]+:[a-z0-9_.-]+(\/[a-z0-9_.-]+)*$/;

/** Icons the website docs show: recipe grids, the docs' named items and the "Supported mods" cards. */
export function docsIconIds(): string[] {
  const ids = new Set<string>();
  const add = (id: string) => ids.add(iconItem(id));
  for (const r of GEN.recipes as { result: string; key?: Record<string, string>; items?: string[] }[]) {
    add(r.result);
    Object.values(r.key ?? {}).forEach(add);
    (r.items ?? []).forEach(add);
  }
  Object.keys(ITEMS).forEach(add);
  modIconIds().forEach(add);
  return [...ids].filter((id) => ID.test(id)).sort();
}

/** Icons the dashboard mock serves: its storage items. */
export function mockIconIds(): string[] {
  return [...buildItems().keys()].filter((id) => ID.test(id)).sort();
}

export function iconIds(): string[] {
  return [...new Set([...docsIconIds(), ...mockIconIds()])].sort();
}

if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  const ids = iconIds();
  const out = process.argv[2];
  if (!out) console.log(ids.join("\n"));
  else {
    mkdirSync(dirname(resolve(out)), { recursive: true });
    writeFileSync(out, `${ids.join("\n")}\n`);
    console.log(`icon-ids: ${ids.length} item ids -> ${out}`);
  }
}
