// Build step for the docs: every crafting recipe of the mod (JSON in
// src/main/resources/data/cccloud/recipe) plus the item names from the mod's
// lang files, so the docs' recipe grids cover every item and cannot drift.
//
//   node dev/build-recipes.ts      -> web/src/docs/content/recipes.gen.json
import { readFileSync, readdirSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";

const HERE = fileURLToPath(new URL(".", import.meta.url));
const RES = join(HERE, "../../src/main/resources");
export const RECIPE_DIR = join(RES, "data/cccloud/recipe");
const OUT = join(HERE, "../web/src/docs/content/recipes.gen.json");

export interface GenRecipe {
  /** file name without .json */
  file: string;
  result: string;
  count?: number;
  shapeless?: boolean;
  grid?: string[];
  key?: Record<string, string>;
  items?: string[];
  /** neoforge:conditions as "mod:<id>" (needs the mod) / "!mod:<id>" (only without it) */
  requires?: string[];
}

const ingredient = (v: unknown): string => {
  if (typeof v === "string") return v;
  const o = v as { item?: string; tag?: string } | unknown[];
  if (Array.isArray(o)) return ingredient(o[0]);
  if (o && typeof o === "object" && "tag" in o && o.tag) return `#${o.tag}`;
  if (o && typeof o === "object" && "item" in o && o.item) return o.item;
  throw new Error(`unsupported ingredient ${JSON.stringify(v)}`);
};

function condition(c: { type: string; modid?: string; value?: { type: string; modid?: string } }): string {
  if (c.type === "neoforge:mod_loaded" && c.modid) return `mod:${c.modid}`;
  if (c.type === "neoforge:not" && c.value?.type === "neoforge:mod_loaded" && c.value.modid) return `!mod:${c.value.modid}`;
  return c.type;
}

export function readRecipes(): GenRecipe[] {
  const out: GenRecipe[] = [];
  for (const f of readdirSync(RECIPE_DIR).filter((x) => x.endsWith(".json")).sort()) {
    const j = JSON.parse(readFileSync(join(RECIPE_DIR, f), "utf8"));
    const r: GenRecipe = { file: f.slice(0, -5), result: j.result.id, ...(j.result.count > 1 ? { count: j.result.count } : {}) };
    if (j.type === "minecraft:crafting_shapeless") {
      r.shapeless = true;
      r.items = j.ingredients.map(ingredient);
    } else if (j.type === "minecraft:crafting_shaped") {
      r.grid = j.pattern;
      r.key = Object.fromEntries(Object.entries(j.key).map(([k, v]) => [k, ingredient(v)]));
    } else throw new Error(`${f}: unsupported recipe type ${j.type}`);
    const cond = (j["neoforge:conditions"] ?? []).map(condition);
    if (cond.length) r.requires = cond;
    out.push(r);
  }
  return out;
}

/** cccloud item/block names from the lang files: id -> {en, de}. */
export function readNames(): Record<string, { en: string; de: string }> {
  const en = JSON.parse(readFileSync(join(RES, "assets/cccloud/lang/en_us.json"), "utf8")) as Record<string, string>;
  const de = JSON.parse(readFileSync(join(RES, "assets/cccloud/lang/de_de.json"), "utf8")) as Record<string, string>;
  const out: Record<string, { en: string; de: string }> = {};
  for (const [k, v] of Object.entries(en)) {
    const m = /^(?:item|block)\.cccloud\.([a-z0-9_]+)$/.exec(k);
    if (m) out[`cccloud:${m[1]}`] = { en: v, de: de[k] ?? v };
  }
  return out;
}

export function generate() {
  return { recipes: readRecipes(), names: readNames() };
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const g = generate();
  writeFileSync(OUT, `${JSON.stringify(g, null, 1)}\n`);
  console.log(`recipes.gen.json: ${g.recipes.length} recipes, ${Object.keys(g.names).length} names`);
}
