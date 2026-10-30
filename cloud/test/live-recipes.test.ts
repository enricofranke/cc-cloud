// Live recipes (GET /api/recipes) in /docs: parsing, merging into the docs' recipe blocks, badges and the fallback.
import { describe, expect, it } from "vitest";
import { applyLive, liveState, liveSummary, parseCatalog, recipeId, type LiveCatalog } from "../web/src/docs/live.ts";
import { parse, type Block, type Recipe } from "../web/src/docs/md.ts";
import { CONTENT, UI, iconItem, itemName } from "../web/src/docs/content/index.ts";
import { ALL_RECIPES } from "../web/src/docs/content/code.ts";

const byFile = (f: string) => ALL_RECIPES.find((r) => r.file === f)!;

/** What a modded server answers: the lamp changed, the sensor removed, a second core recipe added, ATM off. */
const SERVER = {
  v: 1,
  recipes: [
    { id: "cccloud:cloud_core", file: "cloud_core", status: "default", kind: "shaped", type: "minecraft:crafting_shaped", result: "cccloud:cloud_core", count: 2,
      grid: ["ABA", "BCB", "ABA"], key: { A: "#c:nuggets/gold", B: "minecraft:prismarine_crystals", C: "#c:ender_pearls" } },
    { id: "cccloud:cloud_lamp", file: "cloud_lamp", status: "changed", kind: "shaped", type: "minecraft:crafting_shaped", result: "cccloud:cloud_lamp",
      grid: [" A ", "BCB"], key: { A: "minecraft:glowstone_dust", B: "#c:nuggets/steel", C: "minecraft:redstone_lamp" } },
    { id: "cccloud:cloud_probe_fallback", file: "cloud_probe_fallback", status: "default", kind: "shaped", type: "minecraft:crafting_shaped",
      result: "cccloud:cloud_probe", grid: ["ABA", "CDC", "AEA"], key: { A: "#c:gems/diamond", B: "minecraft:ender_eye", C: "minecraft:netherite_scrap",
        D: "cccloud:advanced_cloud_uplink", E: "cccloud:cloud_core" } },
    { id: "kubejs:cheap_core", status: "added", kind: "shapeless", type: "minecraft:crafting_shapeless", result: "cccloud:cloud_core", shapeless: true,
      items: ["minecraft:ender_pearl", "minecraft:oak_planks|minecraft:spruce_planks"] },
    { id: "kubejs:smelted_core", status: "added", kind: "other", type: "minecraft:smelting", result: "cccloud:quantum_core", station: "minecraft:furnace",
      items: ["#c:ender_pearls"] },
    { id: "kubejs:brand_new", status: "added", kind: "shapeless", type: "minecraft:crafting_shapeless", result: "cccloud:mystery_thing", shapeless: true,
      items: ["minecraft:dirt"] },
  ],
  removed: ["cccloud:cloud_sensor"],
  inactive: ["cccloud:cloud_probe", "cccloud:me_cloud_bridge"],
  icons: { "#c:nuggets/steel": "mekanism:nugget_steel", "#c:nuggets/gold": "minecraft:gold_nugget" },
  names: { "mekanism:nugget_steel": "Steel Nugget" },
  truncated: false,
};

const catalog = () => parseCatalog(JSON.parse(JSON.stringify(SERVER)))!;
const recipeBlock = (r: Recipe): Block => ({ t: "recipe", recipe: r });

describe("parseCatalog", () => {
  it("accepts the server's catalog", () => {
    const c = catalog();
    expect(c.recipes).toHaveLength(6);
    expect(c.removed).toEqual(["cccloud:cloud_sensor"]);
    expect(c.inactive).toContain("cccloud:cloud_probe");
    expect(c.recipes[3].items).toEqual(["minecraft:ender_pearl", "minecraft:oak_planks|minecraft:spruce_planks"]);
    expect(c.recipes[4]).toMatchObject({ kind: "other", station: "minecraft:furnace" });
  });

  it("is null for anything that is not a catalog (mock 404 body, older server, HTML)", () => {
    expect(parseCatalog(null)).toBeNull();
    expect(parseCatalog({ error: "not_found" })).toBeNull();
    expect(parseCatalog({ v: 2, recipes: [], removed: [], inactive: [] })).toBeNull();
    expect(parseCatalog("<html>")).toBeNull();
  });

  it("drops entries with bad ids, statuses, grids or ingredients", () => {
    const c = parseCatalog({
      v: 1,
      removed: ["cccloud:ok", "javascript:alert(1)", 42],
      inactive: [],
      icons: { "#c:x": "minecraft:stone", "c:not_a_tag": "minecraft:stone", "#c:y": "<b>" },
      names: { "minecraft:stone": "Stone", "minecraft:evil": "<img src=x>" },
      recipes: [
        { id: "cccloud:a", status: "default", kind: "shaped", result: "cccloud:a", grid: ["AB"], key: { A: "minecraft:stone" } }, // B missing in key
        { id: "cccloud:b", status: "weird", result: "cccloud:b" },
        { id: "cccloud:c", status: "added", kind: "shaped", result: "cccloud:c", grid: ["AAAA"], key: { A: "minecraft:stone" } },
        { id: "cccloud:d", status: "added", kind: "shaped", result: "cccloud:d", grid: ["A"], key: { A: "<script>" } },
        { id: "cccloud:e", status: "added", kind: "shapeless", result: "cccloud:e", items: ["minecraft:stone", "bad id", "#c:ingots/gold"] },
      ],
    })!;
    expect(c.removed).toEqual(["cccloud:ok"]);
    expect(c.icons).toEqual({ "#c:x": "minecraft:stone" });
    expect(c.names).toEqual({ "minecraft:stone": "Stone" });
    expect(c.recipes.map((r) => r.id)).toEqual(["cccloud:e"]);
    expect(c.recipes[0].items).toEqual(["minecraft:stone", "#c:ingots/gold"]);
  });
});

describe("badges", () => {
  it("maps each docs recipe to what the server does with it", () => {
    const c = catalog();
    expect(liveState(byFile("cloud_core"), c)?.status).toBe("default");
    expect(liveState(byFile("cloud_lamp"), c)?.status).toBe("changed");
    expect(liveState(byFile("cloud_sensor"), c)?.status).toBe("removed");
    expect(liveState(byFile("cloud_probe"), c)?.status).toBe("inactive");
    expect(liveState(byFile("cloud_probe_fallback"), c)?.status).toBe("default");
    // not in the (complete) list at all, or a hand-written grid without a file: nothing is claimed
    expect(liveState(byFile("cloud_relay"), c)).toBeUndefined();
    expect(liveState({ result: "cccloud:cloud_core", grid: ["A"], key: { A: "minecraft:stone" } }, c)).toBeUndefined();
    expect(recipeId(byFile("cloud_core"))).toBe("cccloud:cloud_core");
  });

  it("shows the server's version of a changed recipe", () => {
    const state = liveState(byFile("cloud_lamp"), catalog());
    expect(state && "live" in state && state.live.key?.A).toBe("minecraft:glowstone_dust");
  });

  it("counts for the banner", () => {
    expect(liveSummary(catalog())).toEqual({ changed: 1, added: 3, removed: 1 });
  });

  it("has every label in both languages", () => {
    for (const k of ["liveTitle", "liveFallback", "badgeChanged", "badgeAdded", "badgeRemoved", "inactive", "defaultRecipe", "madeWith", "addedHere"]) {
      expect(UI.en[k], k).toBeTruthy();
      expect(UI.de[k], k).toBeTruthy();
      expect(UI.de[k]).not.toBe(UI.en[k]);
    }
  });
});

describe("applyLive", () => {
  it("falls back to the generated defaults without a server (mock, offline)", () => {
    const blocks = parse(CONTENT.en.recipes);
    expect(applyLive(blocks, null)).toBe(blocks);
    expect(applyLive(blocks, undefined)).toBe(blocks);
  });

  it("annotates the recipes page and adds what the server added", () => {
    const blocks = applyLive(parse(CONTENT.en.recipes), catalog(), { leftovers: { title: "Added by this server", name: (id) => itemName(id, "en"), before: "pack-makers" } });
    const recipes = blocks.filter((b): b is Extract<Block, { t: "recipe" }> => b.t === "recipe");
    const of = (id: string) => recipes.filter((b) => b.live && "live" in b.live ? b.live.live.id === id : b.recipe.file === id.replace("cccloud:", ""));
    expect(of("cccloud:cloud_lamp")[0].live?.status).toBe("changed");
    expect(of("cccloud:cloud_sensor")[0].live?.status).toBe("removed");
    expect(of("cccloud:cloud_probe")[0].live?.status).toBe("inactive");
    // the added core recipe sits right after the default core recipe, once
    const coreAt = blocks.findIndex((b) => b.t === "recipe" && b.recipe.file === "cloud_core");
    const next = blocks[coreAt + 1];
    expect(next.t === "recipe" && next.live?.status).toBe("added");
    expect(recipes.filter((b) => b.live && "live" in b.live && b.live.live.id === "kubejs:cheap_core")).toHaveLength(1);
    // the fallback probe is on the page already: not inserted twice
    expect(recipes.filter((b) => b.recipe.result === "cccloud:cloud_probe")).toHaveLength(2);
    // an added recipe for an item the page has no recipe for: its own section, before "For pack makers" (else at the end)
    const added = blocks.findIndex((b) => b.t === "h" && b.id === "added-here");
    const packMakers = blocks.findIndex((b) => b.t === "h" && b.id === "pack-makers");
    const end = packMakers >= 0 ? packMakers : blocks.length;
    expect(added).toBeGreaterThan(0);
    expect(added).toBeLessThan(end);
    const section = blocks.slice(added, end);
    expect(section.some((b) => b.t === "recipe" && b.recipe.result === "cccloud:mystery_thing")).toBe(true);
    // the smelting recipe for the quantum core joins the quantum core's recipes
    expect(recipes.some((b) => b.live?.status === "added" && b.recipe.result === "cccloud:quantum_core")).toBe(true);
  });

  it("on other pages, an inactive variant brings the variant this server uses", () => {
    // like the start page: only the AllTheModium probe recipe, on a server without AllTheModium
    const blocks = applyLive([{ t: "h", level: 2, id: "probe", c: [], text: "Probe" }, recipeBlock(byFile("cloud_probe"))], catalog());
    expect(blocks).toHaveLength(3);
    expect(blocks[1].t === "recipe" && blocks[1].live?.status).toBe("inactive");
    expect(blocks[2].t === "recipe" && blocks[2].live && "live" in blocks[2].live && blocks[2].live.live.id).toBe("cccloud:cloud_probe_fallback");
    // no leftovers section outside the recipes page
    expect(blocks.some((b) => b.t === "h" && b.id === "added-here")).toBe(false);
  });

  it("keeps the order and everything that is not a recipe", () => {
    const md = CONTENT.de.start;
    const before = parse(md).filter((b) => b.t !== "recipe");
    const after = applyLive(parse(md), catalog()).filter((b) => b.t !== "recipe");
    expect(after).toEqual(before);
  });
});

describe("names and icons from the server", () => {
  it("names a modpack's tag after its representative item, alternatives after the first ones", () => {
    const c = catalog();
    expect(iconItem("#c:nuggets/steel", c)).toBe("mekanism:nugget_steel");
    expect(itemName("#c:nuggets/steel", "en", c)).toBe("Steel Nugget (any)");
    expect(itemName("#c:nuggets/steel", "de", c)).toBe("Steel Nugget (beliebig)");
    expect(itemName("minecraft:oak_planks|minecraft:spruce_planks", "en", c)).toBe("oak planks / spruce planks");
    expect(iconItem("minecraft:oak_planks|minecraft:spruce_planks", c)).toBe("minecraft:oak_planks");
    // the docs' own names (DE/EN) win over the server's English ones
    expect(itemName("#c:nuggets/gold", "de", c)).toBe("Goldklumpen (beliebig)");
    expect(iconItem("#c:nuggets/gold")).toBe("minecraft:gold_nugget");
  });
});

describe("pack makers section", () => {
  it("lists every recipe id in both languages", () => {
    for (const lang of ["en", "de"] as const) {
      const md = CONTENT[lang].recipes;
      for (const r of ALL_RECIPES) expect(md, `${lang} ${r.file}`).toContain(`\`cccloud:${r.file}\``);
      expect(md).toContain("{#pack-makers}");
      expect(md).toContain("ServerEvents.recipes");
      expect(md).toContain("AlmostUnified");
    }
  });
});
