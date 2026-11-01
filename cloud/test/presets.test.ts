// Dashboard presets: the pure engine. Format, slot matching, expansion into a normal layout, share codes, page export,
// and the safety rules a preset can never get around.
import { beforeAll, describe, expect, it } from "vitest";
import { deflateRawSync } from "node:zlib";
import { MAX_PAGES, normalizeLayout, resolveLayout, snapshotLayout, type LayoutDoc } from "../web/src/lib/layout.ts";
import type { Uplink, Widget } from "../web/src/lib/types.ts";
import { sha256Js } from "../web/src/lib/scriptstore.ts";
import { PRESET_FORMAT, PRESET_LIMITS, PresetError, cleanText, parsePreset, parseSlots, textOf, type Preset } from "../web/src/lib/presets/format.ts";
import { fits, matchSlots, pickWidgets } from "../web/src/lib/presets/match.ts";
import { expandPreset, matchPreset, mergeLayout, recommend } from "../web/src/lib/presets/expand.ts";
import { rolesOf } from "../web/src/lib/presets/roles.ts";
import { CodeError, MAX_CODE_LENGTH, MAX_JSON_BYTES, decodePreset, encodePreset, readInput } from "../web/src/lib/presets/code.ts";
import { pageToPreset } from "../web/src/lib/presets/export.ts";
import { ACTION_TYPES } from "../web/src/lib/presets/roles.ts";
import { officialPresets } from "../web/src/presets/official.ts";
import { createMockServer } from "../dev/mock-server.ts";

const widget = (id: string, type: Widget["type"], label?: string, opts?: Widget["opts"]): Widget => ({ id, type, label, value: 0, opts, online: true }) as Widget;
const device = (uplinkId: string, title: string, template: string | undefined, widgets: Widget[], extra: Partial<Uplink> = {}): Uplink => ({ uplinkId, title, online: true, template, widgets, ...extra });

const battery = (id: string, title = "Battery") =>
  device(id, title, "energy", [widget("stored", "gauge", "Stored", { warn: 10 }), widget("energy", "label", "Energy"), widget("flow", "label", "Flow"), widget("history", "chart", "History")]);
const chest = (id: string) => device(id, "Main storage", "storage", [widget("storage", "gauge", "Storage"), widget("items", "label", "Items"), widget("search", "item_search", "Search")]);
const reactorProgram = (id = "r1") =>
  device(id, "Fission Reactor", undefined, [
    widget("scram", "button", "SCRAM", { confirm: true, pin: true } as Widget["opts"]),
    widget("power", "toggle", "Power"),
    widget("temp", "gauge", "Temperature", { unit: "K", warn: 70 }),
    widget("status", "label", "Status"),
  ], { mode: "lua" });

/** The mock's bases: a base of programs with hardly any device card, and one with every card of the mod. */
let mockBases: Record<"main" | "showcase", Uplink[]>;
const bases = () => mockBases;
beforeAll(async () => {
  const mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, extras: true, log: () => {} });
  const of = (name: string) => [...[...mock.mainframes.values()].find((m) => m.name.startsWith(name))!.uplinks.values()].map((u) => structuredClone(u) as Uplink);
  const first = [...mock.mainframes.values()][0].name;
  mockBases = { main: of(first), showcase: of("Mod Showcase") };
  await mock.close();
});

const preset = (over: Partial<Preset> = {}): Preset =>
  parsePreset({
    format: PRESET_FORMAT,
    name: "Test",
    slots: [{ id: "cell", label: "Battery", roles: ["energy"], min: 1, max: 2 }],
    pages: [{ title: "Power", cards: [{ w: 6, each: "cell", title: "{device}", widgets: [{ slot: "cell", ids: ["stored"] }, { slot: "cell", ids: ["flow"] }] }] }],
    ...over,
  });

describe("preset format", () => {
  it("reads a full preset", () => {
    const p = parsePreset({
      $schema: "x",
      format: 1,
      name: { en: "Power", de: "Strom" },
      description: "All of it",
      icon: "bolt",
      color: "gold",
      tags: ["energy"],
      slots: [{ id: "cell", label: { en: "Battery", de: "Batterie" }, kinds: ["energy"], roles: ["energy"], has: ["stored"], types: ["gauge"], text: ["CELL"], source: "card", min: 1, max: 3 }],
      pages: [{ title: { en: "Power", de: "Strom" }, icon: "bolt", cards: [{ x: 0, y: 0, w: 6, h: 8, title: "Cells", color: "gold", icon: "bolt", style: "compact", widgets: [{ slot: "cell", ids: ["stored"], types: ["gauge"], x: 0, y: 0, w: 6, h: 3, display: "bar", label: "Fill", color: "gold", icon: "bolt" }] }] }],
    });
    expect(p.slots[0].text).toEqual(["cell"]);
    expect(textOf(p.name, "de")).toBe("Strom");
    expect(textOf(p.name, "en")).toBe("Power");
    expect(textOf("One", "de")).toBe("One");
  });

  const bad = (mutate: (p: any) => void, path?: string) => {
    const p = JSON.parse(JSON.stringify(preset()));
    mutate(p);
    let error: unknown;
    try {
      parsePreset(p);
    } catch (e) {
      error = e;
    }
    expect(error, JSON.stringify(p)).toBeInstanceOf(PresetError);
    if (path) expect((error as PresetError).path).toBe(path);
  };

  it("rejects everything it does not know", () => {
    bad((p) => (p.lua = "os.run()"), "preset.lua");
    bad((p) => (p.install = ["x"]), "preset.install");
    bad((p) => (p.slots[0].script = "x"));
    bad((p) => (p.pages[0].cards[0].widgets[0].onClick = "x"));
    bad((p) => (p.pages[0].cards[0].run = "x"));
  });

  it("is not fooled by prototype keys", () => {
    expect(() => parsePreset(JSON.parse('{"__proto__":{"x":1},"format":1,"name":"a","slots":[],"pages":[]}'))).toThrow(PresetError);
    expect(({} as { x?: number }).x).toBeUndefined();
  });

  it("names a newer format instead of calling it broken", () => {
    let error: PresetError | undefined;
    try {
      parsePreset({ ...JSON.parse(JSON.stringify(preset())), format: PRESET_FORMAT + 1 });
    } catch (e) {
      error = e as PresetError;
    }
    expect(error?.code).toBe("newer_format");
  });

  it("checks references and shapes", () => {
    bad((p) => (p.pages[0].cards[0].widgets[0].slot = "nope"));
    bad((p) => (p.pages[0].cards[0].each = "nope"));
    bad((p) => (p.pages[0].cards[0].widgets[0] = { slot: "cell" }));
    bad((p) => (p.slots[0].roles = ["root"]));
    bad((p) => (p.slots[0].min = 3));
    bad((p) => (p.slots.push({ ...p.slots[0] })));
    bad((p) => (p.slots[0].id = "Not Valid"));
    bad((p) => (p.pages[0].cards[0].w = 2));
    bad((p) => (p.pages[0].cards[0].x = 11));
    bad((p) => (p.pages[0].cards[0].color = "javascript:alert(1)"));
    bad((p) => (p.pages[0].icon = "nope"));
    bad((p) => (p.pages[0].cards[0].widgets[0].display = "nope"));
    bad((p) => (p.pages[0].cards[0].widgets[0].as = "nope"));
  });

  it("caps sizes", () => {
    bad((p) => (p.name = "x".repeat(PRESET_LIMITS.NAME + 1)));
    bad((p) => (p.pages[0].title = "x".repeat(PRESET_LIMITS.TITLE + 1)));
    bad((p) => (p.pages[0].cards[0].widgets[0].label = "x".repeat(PRESET_LIMITS.LABEL + 1)));
    bad((p) => (p.slots = Array.from({ length: PRESET_LIMITS.SLOTS + 1 }, (_, i) => ({ id: `s${i}`, label: "x", min: 0, max: 1 }))));
    bad((p) => (p.pages = Array.from({ length: PRESET_LIMITS.PAGES + 1 }, () => p.pages[0])));
  });

  it("takes control and direction characters out of texts", () => {
    expect(cleanText("Sa‮fe\u0000 ​button\n")).toBe("Safe button");
    expect(textOf(preset({ name: "Pl‮ain" } as Partial<Preset>).name, "en")).toBe("Plain");
  });

  it("reads the slots of a store index on their own", () => {
    expect(parseSlots([{ id: "a", label: "A", roles: ["energy"], min: 1, max: 1 }])[0].id).toBe("a");
    expect(() => parseSlots([{ id: "a", label: "A", roles: ["nope"], min: 1, max: 1 }])).toThrow(PresetError);
    expect(() => parseSlots("x")).toThrow(PresetError);
  });
});

describe("device roles", () => {
  it("comes from the kind", () => {
    expect(rolesOf(battery("a")).has("energy")).toBe(true);
    expect(rolesOf(chest("a")).has("storage")).toBe(true);
    expect(rolesOf(device("a", "Whatever", "powah_reactor", [])).has("reactor")).toBe(true);
  });
  it("comes from title, labels and units for programs", () => {
    expect(rolesOf(reactorProgram()).has("reactor")).toBe(true);
    expect(rolesOf(device("a", "Monitor", undefined, [widget("x", "label", "Output", { unit: "FE/t" })], { mode: "lua" })).has("energy")).toBe(true);
  });
  it("gives a restricted section no ordinary role", () => {
    const u = device("c", "Claims", "claims", [], { restricted: true });
    expect(rolesOf(u).has("server")).toBe(true);
  });
});

describe("slot matching", () => {
  it("fills slots exclusively, required ones first", () => {
    const p = preset({
      pages: [{ title: "x", cards: [{ w: 6, widgets: [{ slot: "main", ids: ["stored"] }] }] }],
      slots: [{ id: "extra", label: "x", roles: ["energy"], min: 0, max: 1 }, { id: "main", label: "x", roles: ["energy"], min: 1, max: 1 }],
    });
    const [extra, main] = matchSlots(p, [battery("a")]);
    expect(main.chosen.map((u) => u.uplinkId)).toEqual(["a"]);
    expect(extra.chosen).toEqual([]);
  });

  it("reports what is missing", () => {
    const m = matchPreset(preset(), [chest("x")], "en");
    expect(m.state).toBe("missing");
    expect(m.slots[0].missing).toBe(1);
  });

  it("offers a choice only when there are more candidates than room", () => {
    const one = matchSlots(preset(), [battery("a"), battery("b")]);
    expect(one[0].choice).toBe(false);
    const three = matchSlots(preset(), [battery("a"), battery("b"), battery("c")]);
    expect(three[0].choice).toBe(true);
    expect(three[0].chosen.length).toBe(2);
  });

  it("follows the user's pick", () => {
    const m = matchSlots(preset(), [battery("a"), battery("b"), battery("c")], { cell: ["c"] });
    expect(m[0].chosen.map((u) => u.uplinkId)).toEqual(["c"]);
  });

  it("ignores a pick the slot cannot hold", () => {
    const m = matchSlots(preset(), [battery("a"), chest("b")], { cell: ["b"] });
    expect(m[0].chosen).toEqual([]);
    expect(m[0].missing).toBe(1);
  });

  it("prefers working devices", () => {
    const m = matchSlots(preset(), [battery("off"), battery("on")].map((u, i) => ({ ...u, online: i === 1 })));
    expect(m[0].chosen[0].uplinkId).toBe("on");
  });

  it("never uses a blocked device", () => {
    const blocked = { ...battery("a"), blocked: { reason: "claim" } } as unknown as Uplink;
    expect(fits(blocked, preset().slots[0])).toBe(false);
  });

  it("separates cards from programs", () => {
    const slot = { ...preset().slots[0], source: "card" as const };
    expect(fits(battery("a"), slot)).toBe(true);
    expect(fits(device("p", "Battery", undefined, [widget("x", "gauge", "Energy", { unit: "FE" })], { mode: "lua" }), slot)).toBe(false);
  });

  it("picks widgets by id, falls back to type and text", () => {
    const u = battery("a");
    const pick = (ref: object) => pickWidgets(u, { slot: "s", ...ref } as never, new Set()).map((w) => w.id);
    expect(pick({ ids: ["flow", "energy"], count: 2 })).toEqual(["flow", "energy"]);
    expect(pick({ ids: ["input"], types: ["label"] })).toEqual(["energy"]);
    expect(pick({ ids: ["input"] })).toEqual([]);
    expect(pick({ types: ["label"], text: ["flow"] })).toEqual(["flow"]);
    expect(pick({ types: ["gauge"], limits: true })).toEqual(["stored"]);
    expect(pick({ as: "gauge", types: ["label"] })).toEqual([]);
  });

  it("does not put one widget into a card twice in the same shape", () => {
    const used = new Set<string>();
    expect(pickWidgets(battery("a"), { slot: "s", ids: ["stored"] }, used)).toHaveLength(1);
    expect(pickWidgets(battery("a"), { slot: "s", ids: ["stored"] }, used)).toHaveLength(0);
  });
});

describe("expansion", () => {
  const uplinks = () => [battery("a"), battery("b", "Second")];

  it("builds a valid layout of views and custom cards", () => {
    const { expanded, state } = matchPreset(preset(), uplinks(), "en");
    expect(state).toBe("ready");
    expect(expanded.pages).toHaveLength(1);
    expect(Object.keys(expanded.doc.cards).every((k) => k.startsWith("c:"))).toBe(true);
    expect(Object.keys(expanded.doc.widgets).every((k) => k.startsWith("v:"))).toBe(true);
    expect(normalizeLayout(expanded.doc).cards).toEqual(expanded.doc.cards);
    const cards = resolveLayout(uplinks(), expanded.doc)[0].cards.filter((c) => c.custom);
    expect(cards.map((c) => c.title)).toEqual(["Battery", "Second"]);
    expect(cards[0].widgets.map((w) => w.view?.src)).toEqual(["a/stored", "a/flow"]);
  });

  it("uses the language of the page", () => {
    const p = preset({ pages: parsePreset({ format: 1, name: "x", slots: preset().slots, pages: [{ title: { en: "Power", de: "Strom" }, cards: [{ w: 6, widgets: [{ slot: "cell", ids: ["stored"], label: { en: "Fill", de: "Füllstand" } }] }] }] }).pages });
    const { expanded } = matchPreset(p, uplinks(), "de");
    expect(expanded.pages[0].title).toBe("Strom");
    expect(Object.values(expanded.doc.widgets)[0].label).toBe("Füllstand");
  });

  it("never reuses a key of the layout it joins", () => {
    const first = matchPreset(preset(), uplinks(), "en").expanded;
    const second = matchPreset(preset(), uplinks(), "en", undefined, first.doc).expanded;
    expect(Object.keys(second.doc.cards).some((k) => k in first.doc.cards)).toBe(false);
    expect(Object.keys(second.doc.widgets).some((k) => k in first.doc.widgets)).toBe(false);
    expect(second.pages[0].id).not.toBe(first.pages[0].id);
  });

  it("leaves out pages and cards the base cannot fill", () => {
    const p = parsePreset({
      format: 1,
      name: "x",
      slots: [{ id: "cell", label: "x", roles: ["energy"], min: 1, max: 1 }, { id: "tank", label: "x", roles: ["fluid"], min: 0, max: 1 }],
      pages: [
        { title: "A", cards: [{ w: 6, widgets: [{ slot: "cell", ids: ["stored"] }] }, { w: 6, widgets: [{ slot: "tank", ids: ["fill"] }] }] },
        { title: "B", cards: [{ w: 6, widgets: [{ slot: "tank", ids: ["fill"] }] }] },
      ],
    });
    const m = matchPreset(p, uplinks(), "en");
    expect(m.state).toBe("partial");
    expect(m.expanded.pages).toHaveLength(1);
    expect(m.expanded.emptyPages).toBe(1);
    expect(m.expanded.cards).toBe(1);
  });

  it("places cards without overlaps, also when `each` grows them", () => {
    const p = preset();
    const { expanded } = matchPreset({ ...p, slots: [{ ...p.slots[0], max: 8 }] }, Array.from({ length: 8 }, (_, i) => battery(`b${i}`)), "en");
    const boxes = Object.values(expanded.doc.cards).map((c) => ({ x: c.x!, y: c.y!, w: c.w!, h: c.h! }));
    for (const a of boxes)
      for (const b of boxes) {
        if (a === b) continue;
        expect(a.x + a.w <= b.x || b.x + b.w <= a.x || a.y + a.h <= b.y || b.y + b.h <= a.y).toBe(true);
      }
  });

  it("clamps widgets to what the grid allows", () => {
    const p = parsePreset({ format: 1, name: "x", slots: preset().slots, pages: [{ title: "A", cards: [{ w: 6, widgets: [{ slot: "cell", ids: ["history"], x: 10, y: 0, w: 2, h: 40 }] }] }] });
    const w = Object.values(matchPreset(p, uplinks(), "en").expanded.doc.widgets)[0];
    expect(w.x! + w.w!).toBeLessThanOrEqual(12);
    expect(w.w).toBeGreaterThanOrEqual(3);
  });

  it("stops at the widget budget", () => {
    const many = Array.from({ length: 16 }, (_, i) => device(`m${i}`, "Bat", "energy", Array.from({ length: 64 }, (_, j) => widget(`w${j}`, "label", "Energy"))));
    const p = parsePreset({ format: 1, name: "x", slots: [{ id: "cell", label: "x", roles: ["energy"], min: 0, max: 16 }], pages: [{ title: "A", cards: [{ w: 6, each: "cell", widgets: [{ slot: "cell", types: ["label"], count: 32 }] }] }] });
    expect(matchPreset(p, many, "en").expanded.widgets).toBeLessThanOrEqual(PRESET_LIMITS.EXPANDED_WIDGETS);
  });
});

describe("the safety rules", () => {
  const relabel = (slot: string, ref: object) => parsePreset({
    format: 1,
    name: "Innocent",
    slots: [{ id: slot, label: "Reactor", roles: ["reactor"], min: 1, max: 1 }],
    pages: [{ title: "Lights", cards: [{ w: 6, title: "Lights", widgets: [{ slot, ...ref }] }] }],
  });

  it("an action widget keeps the label, colour and icon of its source", () => {
    const p = relabel("r", { ids: ["scram"], label: "Lights on", color: "green", icon: "bolt" });
    const { expanded } = matchPreset(p, [reactorProgram()], "en");
    const view = Object.values(expanded.doc.widgets)[0];
    expect(view).not.toHaveProperty("label");
    expect(view).not.toHaveProperty("color");
    expect(view).not.toHaveProperty("icon");
    const shown = resolveLayout([reactorProgram()], expanded.doc)[0].cards.find((c) => c.custom)!.widgets[0];
    expect(shown.label).toBe("SCRAM");
  });

  it("the same goes for a label taken from the device", () => {
    const p = relabel("r", { ids: ["power"], labelFrom: "device" });
    const view = Object.values(matchPreset(p, [reactorProgram()], "en").expanded.doc.widgets)[0];
    expect(view).not.toHaveProperty("label");
  });

  it("PIN and confirm of the source stay on the view", () => {
    const { expanded } = matchPreset(relabel("r", { ids: ["scram"] }), [reactorProgram()], "en");
    const shown = resolveLayout([reactorProgram()], expanded.doc)[0].cards.find((c) => c.custom)!.widgets[0];
    expect(shown.w.opts).toMatchObject({ confirm: true, pin: true });
    expect(shown.view?.src).toBe("r1/scram");
  });

  it("a read-only widget may be relabelled", () => {
    const { expanded } = matchPreset(relabel("r", { ids: ["temp"], label: "Heat" }), [reactorProgram()], "en");
    expect(Object.values(expanded.doc.widgets)[0].label).toBe("Heat");
  });

  it("cannot turn a widget into something it cannot be shown as", () => {
    const m = matchPreset(relabel("r", { ids: ["status"], as: "button" }), [reactorProgram()], "en");
    expect(m.widgets).toBe(0);
    expect(m.state).toBe("missing");
  });

  it("keeps a device's restricted flag out of nobody's reach: no slot sees what the session does not", () => {
    // a viewer's session has no restricted uplinks at all, so nothing can be matched to them
    const p = parsePreset({ format: 1, name: "x", slots: [{ id: "s", label: "x", roles: ["server"], min: 1, max: 1 }], pages: [{ title: "A", cards: [{ w: 6, widgets: [{ slot: "s", types: ["label"] }] }] }] });
    expect(matchPreset(p, [battery("a")], "en").state).toBe("missing");
  });

  it("source ids stay inside the limits of the validator", () => {
    const long = device("u".repeat(150), "Bat", "energy", [widget("x".repeat(30), "gauge", "Energy")]);
    const p = preset({ pages: parsePreset({ format: 1, name: "x", slots: preset().slots, pages: [{ title: "A", cards: [{ w: 6, widgets: [{ slot: "cell", types: ["gauge"] }] }] }] }).pages });
    expect(matchPreset(p, [long], "en").widgets).toBe(0);
  });
});

describe("merging into a layout", () => {
  const uplinks = [battery("a")];
  const add = matchPreset(preset(), uplinks, "en").expanded;

  it("adds pages and leaves every other one alone", () => {
    const base: LayoutDoc = { v: 2, pages: [{ id: "main", title: "Main" }], cards: { "a": { page: "main" } }, widgets: {} };
    const merged = mergeLayout(base, add);
    if (!("doc" in merged)) throw new Error("no room");
    expect(merged.doc.pages.map((p) => p.id)).toEqual(["main", add.pages[0].id]);
    expect(merged.doc.cards.a).toEqual({ page: "main" });
  });

  it("puts the pages first on an empty dashboard", () => {
    const base: LayoutDoc = { v: 2, pages: [{ id: "main", title: "Main" }], cards: {}, widgets: {} };
    const merged = mergeLayout(base, add, true);
    if (!("doc" in merged)) throw new Error("no room");
    expect(merged.doc.pages[0].id).toBe(add.pages[0].id);
  });

  it("says how many pages are left at the limit", () => {
    const base: LayoutDoc = { v: 2, pages: Array.from({ length: MAX_PAGES }, (_, i) => ({ id: `p${i}`, title: "x" })), cards: {}, widgets: {} };
    expect(mergeLayout(base, add)).toEqual({ problem: "too_many_pages", free: 0 });
  });

  it("refuses a preset that adds nothing", () => {
    expect(mergeLayout({ v: 2, pages: [], cards: {}, widgets: {} }, matchPreset(preset(), [chest("c")], "en").expanded)).toEqual({ problem: "nothing" });
  });

  it("the existing pages come through the snapshot untouched", () => {
    const u = [battery("a"), chest("b")];
    const base = snapshotLayout(resolveLayout(u, null, { includeHidden: true }), u);
    const before = JSON.stringify(base.cards);
    const merged = mergeLayout(base, matchPreset(preset(), u, "en", undefined, base).expanded);
    if (!("doc" in merged)) throw new Error("no room");
    expect(JSON.stringify(Object.fromEntries(Object.entries(merged.doc.cards).filter(([k]) => k in base.cards)))).toBe(before);
  });
});

describe("recommendation", () => {
  it("takes the preset that uses most of the base", () => {
    const any = parsePreset({ format: 1, name: "All", slots: [{ id: "x", label: "x", min: 0, max: 8 }], pages: [{ title: "A", cards: [{ w: 6, each: "x", widgets: [{ slot: "x", types: ["gauge"] }] }] }] });
    const power = preset();
    const u = [battery("a"), chest("b"), reactorProgram()];
    const best = recommend([matchPreset(any, u, "en"), matchPreset(power, u, "en")], u);
    expect(best?.preset.name).toBe("All");
  });
  it("prefers the preset made for a small base", () => {
    const any = parsePreset({ format: 1, name: "All", slots: [{ id: "x", label: "x", min: 0, max: 8 }], pages: [{ title: "A", cards: [{ w: 6, each: "x", widgets: [{ slot: "x", types: ["gauge"] }] }] }] });
    const u = [battery("a")];
    expect(recommend([matchPreset(any, u, "en"), matchPreset(preset(), u, "en")], u)?.preset.name).toBe("Test");
  });
  it("finds nothing when nothing fits", () => {
    expect(recommend([matchPreset(preset(), [], "en")], [])).toBeUndefined();
  });
});

describe("share codes", () => {
  const packed = (json: string) => {
    const payload = deflateRawSync(Buffer.from(json)).toString("base64url");
    return `ccp1.${payload}.${sha256Js(new TextEncoder().encode(payload)).slice(0, 8)}`;
  };
  const failure = async (text: string) => {
    try {
      await decodePreset(text);
    } catch (e) {
      return e as CodeError;
    }
    throw new Error("no error");
  };

  it("round-trips a preset", async () => {
    const p = preset({ name: { en: "Power", de: "Strömung" } });
    const code = await encodePreset(p);
    expect(code).toMatch(/^ccp1\.[A-Za-z0-9_-]+\.[0-9a-f]{8}$/);
    expect(await decodePreset(code)).toEqual(p);
  });

  it("survives line breaks and spaces from a chat", async () => {
    const code = await encodePreset(preset());
    const mid = Math.floor(code.length / 2);
    expect(await decodePreset(`  ${code.slice(0, mid)}\n ${code.slice(mid)}\n`)).toEqual(preset());
  });

  it("drops store identity: a pasted code cannot pose as a signed preset", async () => {
    const p = preset({ id: "official-thing", version: "1.0.0", author: { name: "Team" } as never, license: "MIT" });
    const back = await decodePreset(await encodePreset(p));
    expect(back.id).toBeUndefined();
    expect(back.version).toBeUndefined();
    expect(back.author).toBeUndefined();
  });

  it("strips store identity from a hand-made code too", async () => {
    const raw = { ...JSON.parse(JSON.stringify(preset())), id: "official-thing", version: "1.0.0" };
    const back = await decodePreset(packed(JSON.stringify(raw)));
    expect(back.id).toBeUndefined();
  });

  it("names every problem", async () => {
    const code = await encodePreset(preset());
    expect((await failure("   ")).code).toBe("empty");
    expect((await failure("hello world")).code).toBe("not_a_code");
    expect((await failure(code.replace("ccp1", "ccp2"))).code).toBe("newer_code");
    expect((await failure(code.slice(0, -12))).code).toBe("damaged");
    expect((await failure(code.replace(/\.(.)/, (_, c) => `.${c === "A" ? "B" : "A"}`))).code).toBe("damaged");
    expect((await failure(`ccp1.${"A".repeat(MAX_CODE_LENGTH + 100)}.00000000`)).code).toBe("too_large");
    expect((await failure(packed("not json"))).code).toBe("unreadable");
    expect((await failure(packed('{"format":1}'))).code).toBe("invalid_preset");
    expect((await failure(packed(JSON.stringify({ ...JSON.parse(JSON.stringify(preset())), format: 9 })))).code).toBe("newer_format");
  });

  it("stops a zip bomb", async () => {
    expect((await failure(packed(" ".repeat(MAX_JSON_BYTES * 20)))).code).toBe("too_large");
  });

  it("a code goes through the same validation as any preset", async () => {
    const raw = JSON.parse(JSON.stringify(preset()));
    raw.pages[0].cards[0].widgets[0].onClick = "steal()";
    const e = await failure(packed(JSON.stringify(raw)));
    expect(e.code).toBe("invalid_preset");
    expect(e.path).toContain("onClick");
  });

  it("garbage inside a good checksum is no crash", async () => {
    const payload = "AAAAAAAA";
    const e = await failure(`ccp1.${payload}.${sha256Js(new TextEncoder().encode(payload)).slice(0, 8)}`);
    expect(e.code).toBe("unreadable");
  });

  it("reads short codes", () => {
    expect(readInput(" power-grid-plus ")).toEqual({ kind: "store", id: "power-grid-plus" });
    expect(readInput("store:power-grid-plus@1.2.0")).toEqual({ kind: "store", id: "power-grid-plus", version: "1.2.0" });
    expect(readInput("ccp1.abc.12345678").kind).toBe("code");
    expect(readInput("not a short code").kind).toBe("code");
  });
});

describe("sharing a page", () => {
  const scratch = (uplinks: Uplink[], doc: LayoutDoc) => resolveLayout(uplinks, doc);

  it("leaves nothing of this world in the preset", async () => {
    const u = [{ ...reactorProgram("secret-uplink-id"), title: "Enricos Secret Bunker Reactor" }, { ...battery("another-secret"), title: "Vault under spawn" }];
    const doc: LayoutDoc = {
      v: 2,
      pages: [{ id: "p", title: "My page" }],
      cards: { "c:a": { page: "p", title: "Enricos Secret Bunker Reactor", x: 0, y: 0, w: 6, h: 8 } },
      widgets: { "v:1": { card: "c:a", src: "secret-uplink-id/temp", as: "gauge", x: 0, y: 0, w: 6, h: 3 }, "v:2": { card: "c:a", src: "another-secret/stored", as: "gauge", x: 0, y: 3, w: 6, h: 3 } },
    };
    const { preset: p } = pageToPreset(scratch(u, doc)[0], u, { name: "Mine" });
    const code = JSON.stringify(p);
    expect(code).not.toContain("secret");
    expect(code).not.toContain("Secret");
    expect(code).not.toContain("Vault");
  });

  it("comes back as the same page on another base", () => {
    const here = [reactorProgram("r1"), battery("b1")];
    const doc: LayoutDoc = {
      v: 2,
      pages: [{ id: "p", title: "Reactor" }],
      cards: { "c:a": { page: "p", title: "Core", color: "red", icon: "bolt", x: 0, y: 0, w: 6, h: 16 } },
      widgets: {
        "v:1": { card: "c:a", src: "r1/temp", as: "gauge", label: "Heat", x: 0, y: 0, w: 6, h: 6 },
        "v:2": { card: "c:a", src: "r1/scram", as: "button", x: 0, y: 6, w: 6, h: 2 },
        "v:3": { card: "c:a", src: "b1/stored", as: "gauge", x: 0, y: 8, w: 6, h: 6 },
      },
    };
    const { preset: p, skipped } = pageToPreset(scratch(here, doc)[0], here, { name: "Mine" });
    expect(skipped).toBe(0);
    const there = [reactorProgram("other"), battery("b9", "Their cell")];
    const m = matchPreset(p, there, "en");
    expect(m.state).toBe("ready");
    const card = resolveLayout(there, m.expanded.doc)[0].cards.find((c) => c.title === "Core")!;
    expect(card.color).toBe("red");
    expect(card.widgets.map((w) => w.view?.src)).toEqual(["other/temp", "other/scram", "b9/stored"]);
    expect(card.widgets[0].label).toBe("Heat");
    expect(card.widgets[1].label).toBe("SCRAM");
  });

  it("never exports the label of an action widget", () => {
    const u = [reactorProgram()];
    const doc: LayoutDoc = { v: 2, pages: [{ id: "p", title: "x" }], cards: { "c:a": { page: "p", x: 0, y: 0, w: 6, h: 6 } }, widgets: { "v:1": { card: "c:a", src: "r1/scram", as: "button", label: "Lights", x: 0, y: 0, w: 6, h: 3 } } };
    const { preset: p } = pageToPreset(scratch(u, doc)[0], u, { name: "x" });
    expect(JSON.stringify(p)).not.toContain("Lights\"");
    expect(p.pages[0].cards[0].widgets[0].label).toBeUndefined();
  });

  it("asks for world-bound widgets by type", () => {
    const u = [device("rs", "Redstone", "redstone", [widget("d_0123456789ab", "toggle", "Door")])];
    const doc: LayoutDoc = { v: 2, pages: [{ id: "p", title: "x" }], cards: { "c:a": { page: "p", x: 0, y: 0, w: 6, h: 6 } }, widgets: { "v:1": { card: "c:a", src: "rs/d_0123456789ab", as: "toggle", x: 0, y: 0, w: 6, h: 3 } } };
    const { preset: p } = pageToPreset(scratch(u, doc)[0], u, { name: "x" });
    expect(JSON.stringify(p)).not.toContain("d_0123456789ab");
    const there = [device("rs2", "Redstone", "redstone", [widget("d_ffffffffffff", "toggle", "Gate")])];
    expect(Object.values(matchPreset(p, there, "en").expanded.doc.widgets)[0].src).toBe("rs2/d_ffffffffffff");
  });

  it("counts what it had to leave out", () => {
    const u = [battery("a")];
    const doc: LayoutDoc = { v: 2, pages: [{ id: "p", title: "x" }], cards: { "c:a": { page: "p", x: 0, y: 0, w: 6, h: 6 } }, widgets: { "v:1": { card: "c:a", src: "a/stored", as: "gauge", x: 0, y: 0, w: 6, h: 3 } } };
    const page = scratch(u, doc)[0];
    const gone = { ...page, cards: page.cards.filter((c) => c.custom).map((c) => ({ ...c, widgets: [...c.widgets, { ...c.widgets[0], key: "v:9", view: { src: "gone/x", as: "gauge" as const } }] })) };
    expect(pageToPreset(gone, u, { name: "x" }).skipped).toBe(1);
  });

  it("exports an ordinary page, not only custom cards", () => {
    const u = [battery("a"), chest("b")];
    const resolved = resolveLayout(u, null);
    const { preset: p } = pageToPreset(resolved[0], u, { name: "All" });
    expect(p.slots.map((s) => s.kinds?.[0]).sort()).toEqual(["energy", "storage"]);
    const m = matchPreset(p, u, "en");
    expect(m.state).toBe("ready");
    expect(m.widgets).toBe(u.flatMap((x) => x.widgets).length);
  });
});

describe("official presets", () => {
  const texts = (v: unknown, path: string, out: [string, unknown][] = []) => {
    if (Array.isArray(v)) v.forEach((x, i) => texts(x, `${path}[${i}]`, out));
    else if (v && typeof v === "object") for (const [k, x] of Object.entries(v)) (["name", "description", "label", "title"].includes(k) ? out.push([`${path}.${k}`, x]) : texts(x, `${path}.${k}`, out));
    return out;
  };

  it("are eight and valid", () => {
    expect(officialPresets().map((p) => p.id)).toEqual(["base-overview", "power-grid", "me-command-centre", "reactor-control", "farm-production", "doors-redstone", "alerts-status", "phone-quick-panel"]);
  });

  it("speak English and German", () => {
    for (const p of officialPresets())
      for (const [path, t] of texts(p, p.id!)) {
        if (t === "{device}") continue;
        expect(typeof t === "object" && (t as { de?: string }).de, path).toBeTruthy();
      }
  });

  it("never name an action widget", () => {
    for (const p of officialPresets())
      for (const page of p.pages)
        for (const card of page.cards)
          for (const w of card.widgets) {
            if (w.as && ACTION_TYPES.has(w.as)) expect(w.label).toBeUndefined();
            if (w.types?.every((t) => ACTION_TYPES.has(t))) expect(w.label).toBeUndefined();
          }
  });

  it("work on the mock's bases, in both languages, inside the layout limits", () => {
    const report: Record<string, string> = {};
    for (const lang of ["en", "de"] as const)
      for (const [name, uplinks] of Object.entries(bases()))
        for (const p of officialPresets()) {
          const m = matchPreset(p, uplinks, lang);
          report[`${name}/${p.id}`] = `${m.state} ${m.widgets}`;
          for (const c of Object.values(m.expanded.doc.cards)) expect(c.h!, `${p.id} card height`).toBeLessThanOrEqual(60);
          const expected = normalizeLayout(m.expanded.doc);
          expect(expected.widgets).toEqual(m.expanded.doc.widgets);
          expect(expected.cards).toEqual(m.expanded.doc.cards);
        }
    for (const p of officialPresets()) expect(report[`main/${p.id}`], p.id).not.toMatch(/^missing/);
    expect(Object.entries(report).filter(([, r]) => r.startsWith("missing")).map(([k]) => k)).toEqual(["showcase/doors-redstone"]);
  });

  it("the overview fits every base that has a device", () => {
    const overview = officialPresets()[0];
    for (const uplinks of Object.values(bases())) expect(matchPreset(overview, uplinks, "en").state).not.toBe("missing");
    expect(matchPreset(overview, [device("x", "Odd", undefined, [widget("a", "label", "Hello")], { mode: "lua" })], "en").widgets).toBe(1);
  });

  it("recommends something sensible", () => {
    const pick = (uplinks: Uplink[]) => recommend(officialPresets().map((p) => matchPreset(p, uplinks, "en")), uplinks)?.preset.id;
    expect(pick(bases().main)).toBe("base-overview");
    expect(pick([reactorProgram()])).toBe("reactor-control");
    expect(pick([chest("c")])).toBe("me-command-centre");
    expect(pick([])).toBeUndefined();
  });

  it("keep restricted sections out unless a slot asks for them by name", () => {
    const server = device("srv", "Server", "server", [widget("tps", "gauge", "TPS", { warn: 15 })], { restricted: true });
    const m = matchPreset(officialPresets()[0], [server, battery("a")], "en");
    expect(JSON.stringify(m.expanded.doc)).not.toContain("srv/");
    expect(fits(server, { id: "s", label: "x", roles: ["any"], min: 0, max: 1 })).toBe(false);
    expect(fits(server, { id: "s", label: "x", roles: ["alarm"], min: 0, max: 1 })).toBe(false);
    expect(fits(server, { id: "s", label: "x", kinds: ["server"], min: 0, max: 1 })).toBe(true);
    expect(fits(server, { id: "s", label: "x", roles: ["server"], min: 0, max: 1 })).toBe(true);
  });
});
