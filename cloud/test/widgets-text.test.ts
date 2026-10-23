// Label captions, long multi-entry values and button label fitting (real-usage bugs).
import { describe, expect, it } from "vitest";
import { captionOf, resolveLayout } from "../web/src/lib/layout.ts";
import { labelFit, splitEntries } from "../web/src/lib/format.ts";
import type { Uplink } from "../web/src/lib/types.ts";

describe("captions", () => {
  it("never shows the raw widget id when a script set no caption", () => {
    const u: Uplink = { uplinkId: "u", title: "Kontrollraum", online: true, widgets: [{ id: "types", type: "label", value: "Types: 3" }, { id: "tps", type: "label", label: "TPS", value: "20" }] };
    const [page] = resolveLayout([u], null);
    const ws = page.cards[0].widgets;
    expect(captionOf(ws.find((w) => w.w.id === "types")!)).toBeUndefined();
    expect(captionOf(ws.find((w) => w.w.id === "tps")!)).toBe("TPS");
    // a caption set in the layout editor still wins
    const [p2] = resolveLayout([u], { version: 1, pages: [], cards: {}, widgets: { "u/types": { label: "Item types" } } } as never);
    expect(captionOf(p2.cards[0].widgets.find((w) => w.w.id === "types")!)).toBe("Item types");
  });
});

describe("multi-entry values", () => {
  it("splits the server card's tick times per dimension and adds TPS", () => {
    const e = splitEntries("overworld 12.4 ms · the_nether 3.1 ms · the_end 62.5 ms");
    expect(e).toEqual([
      { name: "overworld", value: "12.4 ms", tps: "20.0" },
      { name: "the_nether", value: "3.1 ms", tps: "20.0" },
      { name: "the_end", value: "62.5 ms", tps: "16.0" },
    ]);
    expect(splitEntries("a: 1; b: two")).toEqual([
      { name: "a", value: "1" },
      { name: "b", value: "two" },
    ]);
    expect(splitEntries("Running")).toEqual([]);
  });
});

describe("button labels", () => {
  it("long labels get tighter type instead of being cut", () => {
    expect(labelFit("SCRAM")).toBe("");
    expect(labelFit("Tuer 5 s oeffnen")).toBe("long");
    expect(labelFit("Programm starten")).toBe("long");
  });
});
