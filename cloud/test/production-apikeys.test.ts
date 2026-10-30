// v0.10 production statistics and API keys on the client, the i18n tables, and the mock's rate tracker.
import { describe, expect, it } from "vitest";
import { fmtNum, fmtRate, inUnit, normalizeItemStats, normalizeOverview, pinOf, pollSeconds, windowText } from "../web/src/lib/production.ts";
import { EXPIRY, SCOPES, ipProblem, parseApiKeys, parseIps, validIp } from "../web/src/lib/apikeys.ts";
import { STRINGS, tr, type Key } from "../web/src/lib/i18n.ts";
import { detectLang } from "../web/src/lib/lang.ts";
import { parseConfig } from "../web/src/lib/config.ts";
import { STEP_TYPES } from "../web/src/lib/scenes.ts";
import { StorageTracker } from "../dev/production-sim.ts";

describe("production", () => {
  it("formats rates per minute and per hour in both languages", () => {
    expect(fmtRate(1234, "min", "en")).toBe("1,234/min");
    expect(fmtRate(1234, "min", "de")).toBe("1.234/min");
    expect(fmtRate(1234, "h", "en")).toBe("74k/h");
    expect(fmtRate(1234.5, "h", "de")).toBe("74,1k/Std.");
    expect(fmtRate(0.42, "min", "en")).toBe("0.4/min");
    expect(fmtRate(0.42, "min", "de")).toBe("0,4/min");
    expect(fmtRate(-9000, "min", "en", true)).toBe("-9,000/min");
    expect(fmtRate(250, "min", "en", true)).toBe("+250/min");
    expect(fmtNum(4_500_000, "en")).toBe("4.5M");
    expect(fmtNum(12_345, "de")).toBe("12,3k");
    expect(fmtNum(NaN, "en")).toBe("—");
    expect(inUnit(2, "h")).toBe(120);
    expect(windowText(60, 60, "en")).toBe("last 60 min");
    expect(windowText(60, 120, "de")).toBe("letzte 2 Std.");
    expect(windowText(60, 20, "en")).toBe("last 20 min");
    expect(pollSeconds(60)).toBe(60);
    expect(pollSeconds(5)).toBe(15);
    expect(pollSeconds(NaN)).toBe(60);
  });

  it("normalizes the overview and the item drill-down", () => {
    const ov = normalizeOverview({
      interval: 60,
      window: 60,
      since: 5,
      storages: [
        { uplinkId: "me", title: "ME", bridge: "me", samples: 3, gained: 10, consumed: 4, net: 6, crafted: 1, series: { gained: [1, "x", 2] }, producers: [{ name: "minecraft:iron_ingot", displayName: "Iron", rate: -3 }, { name: "<b>", rate: 1 }] },
        { title: "no id" },
      ],
      energy: { cards: [{ uplinkId: "m", title: "Matrix", stored: 5, capacity: 10, in: 3, out: -1 }], stored: 5, capacity: 10, in: 3, out: 1, series: { in: [1], out: [2] } },
      pins: [{ id: "p1", kind: "item", uplinkId: "me", name: "minecraft:iron_ingot", label: "Iron /min" }, { id: "p2", kind: "weird" }],
    });
    expect(ov.storages).toHaveLength(1);
    expect(ov.storages[0].series.gained).toEqual([1, 0, 2]);
    expect(ov.storages[0].producers).toEqual([{ name: "minecraft:iron_ingot", displayName: "Iron", rate: 3 }]);
    expect(ov.energy!.cards[0].out).toBe(0);
    expect(ov.pins.map((p) => p.id)).toEqual(["p1"]);
    expect(pinOf(ov.pins, "item", "me", "minecraft:iron_ingot")?.id).toBe("p1");
    expect(pinOf(ov.pins, "energy")).toBeUndefined();
    expect(normalizeOverview(null)).toMatchObject({ interval: 60, storages: [], energy: null, pins: [], pinLimit: 16 });
    const it = normalizeItemStats({ name: "minecraft:iron_ingot", count: 5, net: -2, jobs: [{ jobId: 1, name: "minecraft:iron_ingot", status: "running", progress: 3 }, { name: "x" }], history: [1, null, "2"] }, "minecraft:iron_ingot");
    expect(it.jobs).toEqual([{ jobId: "1", name: "minecraft:iron_ingot", displayName: "minecraft:iron_ingot", count: 0, status: "running", progress: 1 }]);
    expect(it.history).toEqual([1, null, null]);
    expect(normalizeItemStats({}, "a:b").history).toBeNull();
  });

  it("tracks per-item deltas between snapshots like the mod (per minute)", () => {
    const tr = new StorageTracker(3, 30);
    const snap = (iron: number, stone: number) => new Map([["minecraft:iron_ingot", { count: iron, displayName: "Iron Ingot" }], ["minecraft:stone", { count: stone, displayName: "Stone" }]]);
    tr.sample(snap(100, 10));
    expect(tr.samples).toBe(0); // the first sample only records
    tr.sample(snap(80, 40), 5);
    tr.sample(snap(70, 40));
    const o = tr.overview();
    // two samples of 30 s: iron −20, −10; stone +30, 0 → per minute: gained 30, consumed 30
    expect(o).toMatchObject({ samples: 2, gained: 30, consumed: 30, net: 0, crafted: 5 });
    expect(o.series.gained).toEqual([60, 0]);
    expect(o.producers[0]).toMatchObject({ name: "minecraft:stone", rate: 30 });
    expect(o.consumers[0]).toMatchObject({ name: "minecraft:iron_ingot", rate: 30 });
    expect(tr.item("minecraft:iron_ingot")).toMatchObject({ gained: 0, consumed: 30, net: -30, series: { net: [-40, -20] } });
    expect(tr.item("minecraft:dirt")).toMatchObject({ gained: 0, consumed: 0, net: 0 });
    tr.sample(snap(70, 40));
    tr.sample(snap(70, 40));
    expect(tr.samples).toBe(3); // the window
  });
});

describe("API keys", () => {
  it("checks IPs and CIDRs for the allow-list", () => {
    for (const ok of ["203.0.113.7", "192.168.1.0/24", "10.0.0.0/8", "::1", "2001:db8::/32", "fe80::1%".slice(0, 7), "::ffff:192.0.2.1"]) expect(validIp(ok), ok).toBe(true);
    for (const bad of ["256.1.1.1", "1.2.3", "192.168.1.0/33", "2001:db8::/129", "example.org", "1.2.3.4/", "1:2:3:4:5:6:7:8:9", "1::2::3", ""]) expect(validIp(bad), bad).toBe(false);
    expect(parseIps(" 1.2.3.4,\n10.0.0.0/8 ; ::1 ")).toEqual(["1.2.3.4", "10.0.0.0/8", "::1"]);
    expect(ipProblem(["1.2.3.4", "nope"])).toEqual({ kind: "bad", entry: "nope" });
    expect(ipProblem(Array(9).fill("1.2.3.4"))).toEqual({ kind: "too_many" });
    expect(ipProblem([])).toBeNull();
  });

  it("parses the key list and drops unknown scopes", () => {
    const d = parseApiKeys({ enabled: true, scopes: ["read:sections", "admin"], limit: 5, keys: [{ id: "k1", name: "bot", player: "Enrico", scopes: ["run:scenes", "root"], created: 1, expires: null, lastUsed: 2, lastIp: "1.2.3.4", ips: ["10.0.0.0/8"], own: true }, { name: "no id" }] });
    expect(d.scopes).toEqual(["read:sections"]);
    expect(d.keys).toEqual([{ id: "k1", name: "bot", player: "Enrico", scopes: ["run:scenes"], created: 1, expires: null, lastUsed: 2, lastIp: "1.2.3.4", ips: ["10.0.0.0/8"], expired: false, own: true }]);
    expect(() => parseApiKeys({})).toThrow();
    expect(SCOPES).toHaveLength(5);
    expect(EXPIRY.at(-1)).toBeNull();
  });
});

describe("i18n", () => {
  const keys = Object.keys(STRINGS.en) as Key[];
  it("has every string in English and German with the same placeholders", () => {
    expect(Object.keys(STRINGS.de).sort()).toEqual([...keys].sort());
    const holes = (s: string) => [...s.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort();
    for (const k of keys) {
      expect(STRINGS.en[k].trim(), k).not.toBe("");
      expect(STRINGS.de[k].trim(), k).not.toBe("");
      expect(holes(STRINGS.de[k]), k).toEqual(holes(STRINGS.en[k]));
    }
  });

  it("covers the keys the screens build at run time", () => {
    const dynamic = [
      ...["dashboard", "tablet", "display", "rule", "api"].map((s) => `sc.src.${s}`),
      ...["idle", "ok", "bad", "run", "wait"].map((s) => `sc.state.${s}`),
      ...["cyan", "green", "yellow", "red", "gray"].map((s) => `sc.col.${s}`),
      ...EXPIRY.map((e) => `ak.exp.${e ?? "never"}`),
      ...SCOPES.map((s) => `ak.scope.${s}`),
      ...["top", "bottom", "left", "right", "front", "back"].map((s) => `ed.side.${s}`),
      ...STEP_TYPES.map((s) => `ed.act.${s}`),
    ];
    for (const k of dynamic) expect(keys, k).toContain(k);
  });

  it("fills placeholders, keeps the Rules editor's English labels and detects the language", () => {
    expect(tr("de", "sc.count", { n: 2, limit: 32 })).toBe("2 / 32 Szenen");
    expect(tr("en", "sc.count", { n: 2 })).toBe("2 / {limit} scenes");
    // the shared editor parts must keep the labels the Rules editor (and its screenshots) use
    expect([tr("en", "ed.card"), tr("en", "ed.pickCard"), tr("en", "ed.alertText"), tr("en", "ed.control")]).toEqual(["Card", "Pick a card…", "Alert text", "Control"]);
    expect(detectLang(["de-AT"])).toBe("de");
    expect(detectLang(["it", "en-GB"])).toBe("en");
  });

  it("hides the v0.10 features on older servers (absent switches)", () => {
    expect(parseConfig({ features: { rules: true } }).features).toMatchObject({ rules: true, scenes: false, production: false, api: false });
    expect(parseConfig({ features: { scenes: true, production: true, api: false } }).features).toMatchObject({ scenes: true, production: true, api: false });
    expect(parseConfig(null).features).toMatchObject({ terminal: true, scenes: false });
  });
});
