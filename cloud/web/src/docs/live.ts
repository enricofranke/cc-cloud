// Live recipes: what the Minecraft server really uses for CC: Cloud items (GET /api/recipes, docs/SPEC.md "Live
// recipes"). Modpacks change recipes with datapacks, KubeJS or CraftTweaker; the dashboard's docs show the server's
// recipes – with a badge where they differ from the mod's defaults – and fall back to the generated defaults
// (recipes.gen.json) when there is no server (dev mock, offline). Pure functions; Docs.tsx fetches and renders.
import { slug, type Block, type Recipe, type RecipeLive } from "./md.ts";

export type LiveStatus = "default" | "changed" | "added";

export interface LiveRecipe extends Recipe {
  /** recipe id, e.g. cccloud:cloud_core or kubejs:my_core */
  id: string;
  status: LiveStatus;
  kind?: "shaped" | "shapeless" | "other";
  /** recipe serializer, e.g. minecraft:crafting_shaped or minecraft:smelting */
  type?: string;
  /** where it is made (other recipe types), e.g. minecraft:furnace */
  station?: string;
  /** a grid larger than 3×3: `items` lists the distinct ingredients */
  large?: boolean;
}

export interface LiveCatalog {
  recipes: LiveRecipe[];
  /** default recipe ids the server removed */
  removed: string[];
  /** conditional default recipe ids not loaded here by design (the AllTheModium variant without AllTheModium, …) */
  inactive: string[];
  /** a representative item per tag (from the server's tags) */
  icons: Record<string, string>;
  /** English names of other mods' items (server language) */
  names: Record<string, string>;
  truncated: boolean;
}

const ID = /^[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,256}$/;
const ONE = "#?[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,256}";
/** An ingredient: an item, a tag, or up to 8 alternatives ("a|b"). */
const INGREDIENT = new RegExp(`^${ONE}(\\|${ONE}){0,7}$`);
const MAX_RECIPES = 512;
const MAX_ITEMS = 16;

function str(x: unknown, re: RegExp): string | undefined {
  return typeof x === "string" && re.test(x) ? x : undefined;
}

function parseRecipe(x: unknown): LiveRecipe | null {
  if (!x || typeof x !== "object") return null;
  const r = x as Record<string, unknown>;
  const id = str(r.id, ID);
  if (!id || (r.status !== "default" && r.status !== "changed" && r.status !== "added")) return null;
  const kind = r.kind === "shaped" || r.kind === "shapeless" ? r.kind : "other";
  const out: LiveRecipe = { id, status: r.status, kind, result: str(r.result, ID) ?? "" };
  const type = str(r.type, ID);
  if (type) out.type = type;
  const file = str(r.file, /^[a-z0-9_./-]{1,256}$/);
  if (file) out.file = file;
  if (typeof r.count === "number" && Number.isInteger(r.count) && r.count > 1 && r.count < 10000) out.count = r.count;
  const station = str(r.station, INGREDIENT);
  if (station) out.station = station;
  if (kind === "shaped" && Array.isArray(r.grid) && r.key && typeof r.key === "object") {
    if (r.grid.length > 3 || !r.grid.every((row) => typeof row === "string" && /^[A-I ]{0,3}$/.test(row))) return null;
    const key: Record<string, string> = {};
    for (const [k, v] of Object.entries(r.key as Record<string, unknown>)) {
      const ingredient = str(v, INGREDIENT);
      if (!/^[A-I]$/.test(k) || !ingredient) return null;
      key[k] = ingredient;
    }
    if (!(r.grid as string[]).every((row) => [...row].every((ch) => ch === " " || key[ch]))) return null;
    out.grid = r.grid as string[];
    out.key = key;
  } else if (Array.isArray(r.items)) {
    out.items = r.items.slice(0, MAX_ITEMS).filter((i): i is string => typeof i === "string" && INGREDIENT.test(i));
    if (kind === "shapeless") out.shapeless = true;
    if (r.large === true) out.large = true;
  }
  return out;
}

function stringMap(x: unknown, key: RegExp, value: RegExp): Record<string, string> {
  const out: Record<string, string> = {};
  if (!x || typeof x !== "object") return out;
  for (const [k, v] of Object.entries(x as Record<string, unknown>).slice(0, 1024)) if (key.test(k) && typeof v === "string" && value.test(v)) out[k] = v;
  return out;
}

/** Validates the server's answer (shape and bounds); null if it is not a catalog. Invalid entries are dropped. */
export function parseCatalog(x: unknown): LiveCatalog | null {
  if (!x || typeof x !== "object") return null;
  const o = x as Record<string, unknown>;
  if (o.v !== 1 || !Array.isArray(o.recipes) || !Array.isArray(o.removed) || !Array.isArray(o.inactive)) return null;
  const ids = (a: unknown[]) => a.filter((s): s is string => typeof s === "string" && ID.test(s)).slice(0, MAX_RECIPES);
  return {
    recipes: o.recipes
      .slice(0, MAX_RECIPES)
      .map(parseRecipe)
      .filter((r): r is LiveRecipe => r !== null),
    removed: ids(o.removed),
    inactive: ids(o.inactive),
    icons: stringMap(o.icons, /^#[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,256}$/, ID),
    names: stringMap(o.names, ID, /^[^\n<>]{1,64}$/),
    truncated: o.truncated === true,
  };
}

/** The recipe id of a docs recipe (generated ones carry their file name). */
export function recipeId(r: Recipe & { id?: string }): string | null {
  return r.id ?? (r.file ? `cccloud:${r.file}` : null);
}

/** What the server does with one docs recipe; undefined = no live data for it (shown as written). */
export function liveState(r: Recipe, live: LiveCatalog, index?: Map<string, LiveRecipe>): RecipeLive | undefined {
  const id = recipeId(r);
  if (!id) return undefined;
  const l = (index ?? new Map(live.recipes.map((x) => [x.id, x]))).get(id);
  if (l) return { status: l.status, live: l };
  if (live.inactive.includes(id)) return { status: "inactive" };
  if (live.removed.includes(id)) return { status: "removed" };
  return undefined; // a list the server cut short, or a recipe it does not know: nothing to claim
}

export interface ApplyOptions {
  /** "All recipes": the server's added recipes for items the page has no recipe for go into a section of their own. */
  leftovers?: { title: string; name: (id: string) => string; before?: string };
}

/**
 * Annotates the page's recipe blocks with what the server does with them and adds what it has on top: after the last
 * recipe of an item, the server's added recipes for that item and – if none of the page's recipes for it can be
 * crafted here – the ones that can (e.g. the fallback variant of a conditional recipe).
 */
export function applyLive(blocks: Block[], live: LiveCatalog | null | undefined, opts: ApplyOptions = {}): Block[] {
  if (!live) return blocks;
  const index = new Map(live.recipes.map((x) => [x.id, x]));
  const states = blocks.map((b) => (b.t === "recipe" ? liveState(b.recipe, live, index) : undefined));
  const shown = new Set<string>();
  const last = new Map<string, number>();
  const craftable = new Set<string>();
  blocks.forEach((b, i) => {
    if (b.t !== "recipe") return;
    const id = recipeId(b.recipe);
    if (id) shown.add(id);
    last.set(b.recipe.result, i);
    const s = states[i];
    if (!s || s.status === "default" || s.status === "changed" || s.status === "added") craftable.add(b.recipe.result);
  });
  const out: Block[] = [];
  blocks.forEach((b, i) => {
    if (b.t !== "recipe") return void out.push(b);
    const s = states[i];
    out.push(s ? { ...b, live: s } : b);
    const result = b.recipe.result;
    if (last.get(result) !== i) return;
    for (const l of live.recipes) {
      if (l.result !== result || shown.has(l.id)) continue;
      if (l.status === "added" || !craftable.has(result)) out.push({ t: "recipe", recipe: l, live: { status: l.status, live: l } });
    }
  });
  if (opts.leftovers) {
    const rest = live.recipes.filter((l) => l.status === "added" && l.result && !last.has(l.result));
    if (rest.length) {
      const { title, name, before } = opts.leftovers;
      const section: Block[] = [{ t: "h", level: 2, id: "added-here", c: [{ t: "text", v: title }], text: title }];
      for (const result of [...new Set(rest.map((l) => l.result))]) {
        const n = name(result);
        section.push({ t: "h", level: 3, id: `added-${slug(result)}`, c: [{ t: "text", v: n }], text: n });
        for (const l of rest.filter((x) => x.result === result)) section.push({ t: "recipe", recipe: l, live: { status: l.status, live: l } });
      }
      const at = before ? out.findIndex((b) => b.t === "h" && b.id === before) : -1;
      out.splice(at >= 0 ? at : out.length, 0, ...section);
    }
  }
  return out;
}

/** Counts for the banner on the recipes page. */
export function liveSummary(live: LiveCatalog) {
  return {
    changed: live.recipes.filter((r) => r.status === "changed").length,
    added: live.recipes.filter((r) => r.status === "added").length,
    removed: live.removed.length,
  };
}
