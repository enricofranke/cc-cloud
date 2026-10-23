// Web and Java must migrate layouts identically: src/test/resources/layout/migration_fixture.json
// is shared with the mod's LayoutMigration test.
import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { migrateLayout, minWidgetW, normalizeLayout } from "../web/src/lib/layout.ts";

const file = fileURLToPath(new URL("../../src/test/resources/layout/migration_fixture.json", import.meta.url));
const fixture = JSON.parse(readFileSync(file, "utf8")) as { cases: { uplinks: never[]; v1: unknown; v2: unknown }[] };

describe("layout migration fixture (shared with the mod)", () => {
  fixture.cases.forEach((c, i) =>
    it(`case ${i + 1}: migrateLayout(v1) equals the fixture's v2`, () => {
      expect(JSON.parse(JSON.stringify(migrateLayout(c.uplinks, normalizeLayout(c.v1))))).toEqual(c.v2);
    }),
  );
  it("uses the server's per-type minimum widths", () => {
    for (const t of ["item_search", "watchlist", "jobs", "redstone_sides"]) expect(minWidgetW(t)).toBe(6);
    expect(minWidgetW("chart")).toBe(4);
    expect(minWidgetW("gauge")).toBe(2);
    for (const t of ["label", "button", "toggle", "slider", "select", "number", "text", "buttons", "progress", "indicator"]) expect(minWidgetW(t)).toBe(3);
  });
});
