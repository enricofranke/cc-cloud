// Wave 4: dimension names/icons/ranges on the network map and the Peripheral card's settings data.
import { describe, expect, it } from "vitest";
import { dimHasCeiling, dimIcon, dimLabel, dimRange } from "../web/src/lib/dims.ts";
import { normalizeCardSettings, normalizeNetwork } from "../web/src/lib/conn.ts";
import { dimsOf } from "../web/src/lib/network.ts";

describe("dimensions", () => {
  it("names the ATM11 dimensions and falls back to the id", () => {
    expect(dimLabel("minecraft:the_nether")).toBe("Nether");
    expect(dimLabel("aether_ii:aether_holy_isles")).toBe("Aether");
    expect(dimLabel("undergarden:undergarden")).toBe("Undergarden");
    expect(dimLabel("allthemodium:the_beyond")).toBe("The Beyond");
    expect(dimLabel("somemod:crystal_caves/deep")).toBe("Crystal Caves Deep");
  });

  it("picks an icon for known and unknown dimensions", () => {
    expect(dimIcon("minecraft:overworld")).toBe("overworld");
    expect(dimIcon("undergarden:undergarden")).toBe("cave");
    expect(dimIcon("aether_ii:aether_holy_isles")).toBe("cloud");
    expect(dimIcon("nullscape:void_lands")).toBe("ender");
    expect(dimIcon("ad_astra:mars")).toBe("planet");
    expect(dimIcon("somemod:hellscape")).toBe("flame");
    expect(dimIcon("somemod:plain")).toBe("globe");
  });

  it("uses the server's height range and roof, with vanilla defaults", () => {
    expect(dimRange("allthemodium:the_beyond", { minY: -128, maxY: 383 })).toEqual({ minY: -128, maxY: 383 });
    expect(dimRange("minecraft:overworld")).toEqual({ minY: -64, maxY: 319 });
    expect(dimHasCeiling("undergarden:undergarden", { ceiling: true })).toBe(true);
    expect(dimHasCeiling("minecraft:the_nether")).toBe(true);
    expect(dimHasCeiling("minecraft:overworld")).toBe(false);
  });

  it("keeps valid dims from the network snapshot and drops broken ones", () => {
    const net = normalizeNetwork({
      nodes: [
        { id: "a", type: "mainframe", dim: "minecraft:overworld", status: "online", range: 64 },
        { id: "b", type: "ender_relay", dim: "aether_ii:aether_holy_isles", status: "online", range: 64 },
        { id: "c", type: "ender_relay", dim: "undergarden:undergarden", status: "online", range: 64 },
      ],
      links: [],
      routes: {},
      dims: {
        "minecraft:overworld": { minY: -64, maxY: 319 },
        "aether_ii:aether_holy_isles": { minY: -32, maxY: 383 },
        "undergarden:undergarden": { minY: -64, maxY: 127, ceiling: true },
        "bad id": { minY: 0, maxY: 1 },
        "x:y": { minY: 10, maxY: 5 },
      },
    })!;
    expect(net.dims?.["aether_ii:aether_holy_isles"]).toEqual({ minY: -32, maxY: 383 });
    expect(net.dims?.["undergarden:undergarden"]?.ceiling).toBe(true);
    expect(Object.keys(net.dims ?? {})).toHaveLength(3);
    expect(dimsOf(net)).toEqual(["minecraft:overworld", "aether_ii:aether_holy_isles", "undergarden:undergarden"]);
  });
});

describe("peripheral card settings", () => {
  it("normalizes what the card offers", () => {
    const info = normalizeCardSettings({
      kind: "peripheral",
      type: "minecraft:chest",
      types: ["minecraft:chest", "inventory", "<script>"],
      getters: [
        { name: "size", caption: "Size", value: "number" },
        { name: "list", caption: "List", value: "text" },
        { name: "bad name()", caption: "x" },
      ],
      setters: [{ name: "setEnabled", caption: "Enabled" }],
      unknown: 2,
      settings: { show: ["size", "1nope", "list"], charts: ["size"], toggle: "setEnabled", pin: false },
    })!;
    expect(info.types).toEqual(["minecraft:chest", "inventory"]);
    expect(info.getters.map((g) => g.name)).toEqual(["size", "list"]);
    expect(info.settings).toEqual({ show: ["size", "list"], charts: ["size"], toggle: "setEnabled", pin: false });
    expect(info.unknown).toBe(2);
    expect(normalizeCardSettings({ getters: [] })).toBeUndefined();
    expect(normalizeCardSettings({ kind: "peripheral", getters: [], setters: [], settings: {} })!.settings.pin).toBe(true);
  });
});
