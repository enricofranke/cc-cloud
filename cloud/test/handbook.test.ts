// The in-game handbook (GuideMe) is generated from the docs: committed pages are up to date and convert cleanly.
import { describe, expect, it } from "vitest";
import { existsSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { convert, escapeText, generate, rewriteLinks } from "../../tools/gen_handbook.ts";
import { PAGES } from "../web/src/docs/content/index.ts";

const OUT = fileURLToPath(new URL("../../src/main/resources/assets/cccloud/guides/cccloud/handbook/", import.meta.url));

describe("handbook generator", () => {
  it("escapes MDX characters outside code only", () => {
    expect(escapeText("a {b} <c> `x {y} <z>`")).toBe("a \\{b\\} \\<c> `x {y} <z>`");
  });

  it("rewrites docs links to page files", () => {
    expect(rewriteLinks("[x](/docs/start#power) [y](/docs/lua) [z](https://a.b)")).toBe("[x](index.md) [y](lua.md) [z](https://a.b)");
  });

  it("turns recipe blocks into RecipesFor tags (one per item) and drops heading ids", () => {
    const md = convert("network", "en", '## Relays {#relays}\n```recipe\n{"result":"cccloud:cloud_relay"}\n```\n```lua\nlocal x = {1}\n```', 5, "Network");
    expect(md).toContain("## Relays\n");
    expect(md).toContain('<RecipesFor id="cccloud:cloud_relay" fallbackText=');
    // a recipe and its fallback variant right below each other: one tag shows every recipe the game loaded for the item
    const probe = convert("recipes", "en", '### Probe\n```recipe\n{"file":"cloud_probe","result":"cccloud:cloud_probe"}\n```\n```recipe\n{"file":"cloud_probe_fallback","result":"cccloud:cloud_probe"}\n```', 1, "R");
    expect(probe.match(/<RecipesFor id="cccloud:cloud_probe"/g)).toHaveLength(1);
    // the GuideME guide item is shared by every guide: our recipe by its id
    const guide = convert("recipes", "en", '```recipe\n{"file":"handbook","result":"guideme:guide"}\n```', 1, "R");
    expect(guide).toContain('<Recipe id="cccloud:handbook" fallbackText=');
    expect(md).toContain("local x = {1}");
    expect(md).toMatch(/navigation:\n {2}title: "Network"\n {2}position: 5\n {2}icon: cccloud:cloud_relay/);
  });

  it("committed pages match the docs (run node tools/gen_handbook.ts)", () => {
    const files = generate();
    expect(files.size).toBe(PAGES.length * 2);
    for (const [path, content] of files) {
      expect(existsSync(OUT + path), path).toBe(true);
      expect(readFileSync(OUT + path, "utf8"), path).toBe(content);
    }
  });
});
