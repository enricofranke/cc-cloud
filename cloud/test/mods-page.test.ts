// The "Supported mods" docs page is generated from docs/COMPAT.md: every mod id there must be on it.
import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { CONTENT } from "../web/src/docs/content/index.ts";

describe("supported mods page", () => {
  it("lists every mod of docs/COMPAT.md in both languages (run node dev/build-mods.ts after editing COMPAT.md)", () => {
    const compat = readFileSync(new URL("../../docs/COMPAT.md", import.meta.url), "utf8");
    const ids = [...compat.matchAll(/^\| [^|]*\(`([a-z0-9_]+)`\)/gm)].map((m) => m[1]);
    expect(ids.length).toBeGreaterThan(30);
    for (const lang of ["en", "de"] as const) {
      const page = CONTENT[lang].mods;
      for (const id of ids) expect(page, `${lang}: ${id}`).toContain(id);
      expect(page).toContain("powah");
    }
  });
});
