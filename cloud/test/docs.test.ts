// /docs: Markdown subset, both languages complete, links resolve, search, recipes match the data files.
import { describe, expect, it } from "vitest";
import { readFileSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { parse, parseInline, safeHref, type Block, type Inline } from "../web/src/docs/md.ts";
import { CONTENT, ITEMS, PAGES, detectLang, itemName, type Lang } from "../web/src/docs/content/index.ts";
import { ALL_RECIPES } from "../web/src/docs/content/code.ts";
import { generate as generateRecipes } from "../dev/build-recipes.ts";
import { search, pageBlocks } from "../web/src/docs/search.ts";

const LANGS: Lang[] = ["en", "de"];

describe("markdown subset", () => {
  it("parses headings with explicit and generated ids", () => {
    const b = parse("# Title\n## Power (FE) {#power}\n### Über uns\n### Über uns");
    expect(b.map((x) => (x.t === "h" ? [x.level, x.id] : null))).toEqual([
      [1, "title"],
      [2, "power"],
      [3, "uber-uns"],
      [3, "uber-uns-2"],
    ]);
  });

  it("parses tables (escaped pipes), lists, callouts, code and recipes", () => {
    const b = parse(
      [
        "| a | b |",
        "|---|---|",
        '| `x` | "line" \\| "spark" |',
        "",
        "- one",
        "- two",
        "",
        "1. first",
        "2. second",
        "",
        "> **Warning:** careful",
        "",
        "```lua",
        "print(1)",
        "```",
        "",
        "```recipe",
        '{"result":"cccloud:cloud_core","grid":["ab"],"key":{"a":"minecraft:stone","b":"minecraft:paper"}}',
        "```",
      ].join("\n"),
    );
    expect(b.map((x) => x.t)).toEqual(["table", "ul", "ol", "callout", "code", "recipe"]);
    const table = b[0] as Extract<Block, { t: "table" }>;
    expect(table.rows[0][1]).toEqual([{ t: "text", v: '"line" | "spark"' }]);
    expect((b[3] as Extract<Block, { t: "callout" }>).kind).toBe("warn");
    expect((b[4] as Extract<Block, { t: "code" }>).code).toBe("print(1)");
  });

  it("parses inline code, bold, italic and links", () => {
    expect(parseInline("a `b` **c** *d* [e](/docs/faq#x)")).toEqual([
      { t: "text", v: "a " },
      { t: "code", v: "b" },
      { t: "text", v: " " },
      { t: "strong", c: [{ t: "text", v: "c" }] },
      { t: "text", v: " " },
      { t: "em", c: [{ t: "text", v: "d" }] },
      { t: "text", v: " " },
      { t: "link", href: "/docs/faq#x", c: [{ t: "text", v: "e" }] },
    ]);
  });

  it("never turns unsafe links into links", () => {
    for (const bad of ["javascript:alert(1)", "data:text/html,x", "//evil.example", "/docs/../x"]) expect(safeHref(bad)).toBe(false);
    expect(parseInline("[x](javascript:alert(1))").some((n) => n.t === "link")).toBe(false);
  });
});

function links(c: Inline[], out: string[] = []) {
  for (const n of c) {
    if (n.t === "link") out.push(n.href);
    if ("c" in n) links(n.c, out);
  }
  return out;
}
function allLinks(blocks: Block[]) {
  const out: string[] = [];
  for (const b of blocks) {
    if (b.t === "p" || b.t === "h") links(b.c, out);
    if (b.t === "ul" || b.t === "ol") b.items.forEach((i) => links(i, out));
    if (b.t === "callout") b.c.forEach((i) => links(i, out));
    if (b.t === "table") [...b.head, ...b.rows.flat()].forEach((i) => links(i, out));
  }
  return out;
}

describe("content", () => {
  it("has every page in both languages with the same section anchors", () => {
    for (const p of PAGES) {
      const ids = (l: Lang) => pageBlocks(l, p.id).filter((b) => b.t === "h" && b.level === 2).map((b) => (b as any).id);
      expect(CONTENT.en[p.id], p.id).toBeTruthy();
      expect(CONTENT.de[p.id], p.id).toBeTruthy();
      expect(ids("de"), p.id).toEqual(ids("en"));
    }
  });

  it("only links to pages and anchors that exist", () => {
    for (const l of LANGS)
      for (const p of PAGES)
        for (const href of allLinks(pageBlocks(l, p.id))) {
          if (!href.startsWith("/docs")) continue;
          const m = /^\/docs\/?([a-z0-9-]*)(?:#([a-z0-9-]+))?$/.exec(href)!;
          const target = m[1] || "start";
          expect(PAGES.some((x) => x.id === target), `${l}/${p.id} -> ${href}`).toBe(true);
          if (m[2]) expect(pageBlocks(l, target).some((b) => b.t === "h" && b.id === m[2]), `${l}/${p.id} -> ${href}`).toBe(true);
        }
  });

  it("covers the topics users asked about", () => {
    for (const l of LANGS) {
      const all = Object.values(CONTENT[l]).join("\n");
      for (const must of ["No such program", "edit startup.lua", "cloud.setTitle", "getEnergyUsage", "findItems", "webAllowInternet", "cloudflared", "/cccloud pin set", "FE/t", "cloud.ui"])
        expect(all, `${l}: ${must}`).toContain(must);
    }
  });

  it("documents every cloud_uplink method the mod exposes", () => {
    const java = readFileSync(fileURLToPath(new URL("../../src/main/java/ai/ezfox/cccloud/peripheral/UplinkPeripheral.java", import.meta.url)), "utf8");
    const methods = [...java.matchAll(/@LuaFunction[^\n]*\n\s*public[^(]*\s(\w+)\(/g)].map((m) => m[1]);
    expect(methods.length).toBeGreaterThan(20);
    for (const l of LANGS) for (const m of methods) expect(CONTENT[l].lua, `${l}: ${m}`).toContain(`${m}(`);
  });
});

describe("recipes", () => {
  it("are generated from the mod's recipe JSONs and up to date", () => {
    const fresh = JSON.parse(JSON.stringify(generateRecipes()));
    const file = fileURLToPath(new URL("../web/src/docs/content/recipes.gen.json", import.meta.url));
    expect(JSON.parse(readFileSync(file, "utf8")), "run node dev/build-recipes.ts").toEqual(fresh);
    const dir = fileURLToPath(new URL("../../src/main/resources/data/cccloud/recipe/", import.meta.url));
    expect(ALL_RECIPES.map((r) => `${r.file}.json`).sort()).toEqual(readdirSync(dir).filter((f) => f.endsWith(".json")).sort());
  });

  it("are all on the All recipes page in both languages, with conditions", () => {
    for (const l of LANGS) {
      const md = CONTENT[l].recipes;
      for (const r of ALL_RECIPES) expect(md, `${l}: ${r.file}`).toContain(JSON.stringify(r));
    }
    expect(ALL_RECIPES.find((r) => r.file === "upgrade_range_vibranium_fallback")!.requires).toEqual(["!mod:allthemodium"]);
  });

  it("names every ingredient in both languages", () => {
    for (const r of ALL_RECIPES)
      for (const id of [r.result, ...Object.values(r.key ?? {}), ...(r.items ?? [])]) {
        expect(ITEMS[id], id).toBeTruthy();
        expect(itemName(id, "de")).not.toBe("");
      }
    expect(itemName("#c:ingots/gold", "de")).toBe("Goldbarren (beliebig)");
    expect(itemName("cccloud:cloud_tablet", "en")).toBe("Cloud Tablet");
  });
});

describe("search and language", () => {
  it("finds the shell error in both languages", () => {
    for (const l of LANGS) {
      const hit = search(l, "No such program")[0];
      expect(hit, l).toMatchObject({ page: expect.stringMatching(/faq|cc-basics/), anchor: expect.stringMatching(/no-such-program|shell-vs-lua/) });
    }
    expect(search("de", "strom")[0]).toMatchObject({ anchor: "power" });
    expect(search("en", "reverse proxy").some((h) => h.anchor === "caddy")).toBe(true);
    expect(search("en", "zzzz-nothing")).toEqual([]);
    expect(search("en", "a")).toEqual([]); // too short
  });

  it("detects German browsers, falls back to English", () => {
    expect(detectLang(["de-DE", "en"])).toBe("de");
    expect(detectLang(["fr-FR", "en-US"])).toBe("en");
    expect(detectLang(["fr"])).toBe("en");
    expect(detectLang([])).toBe("en");
  });
});
