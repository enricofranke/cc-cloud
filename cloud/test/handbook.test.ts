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

  it("turns recipe blocks into RecipeFor tags and drops heading ids", () => {
    const md = convert("network", "en", '## Relays {#relays}\n```recipe\n{"result":"cccloud:cloud_relay"}\n```\n```lua\nlocal x = {1}\n```', 5, "Network");
    expect(md).toContain("## Relays\n");
    expect(md).toContain('<RecipeFor id="cccloud:cloud_relay" fallbackText=');
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
