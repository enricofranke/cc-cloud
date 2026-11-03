// Card parts: the per-part whitelists against the attack corpus, glows that stay inside the card, the fixed rules in
// styles.css that hand each part's custom properties to its elements (and never to a dialog), and a script's design
// (cloud.ui dash:style, a group's style) next to the layout's.
import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import {
  CARD_PARTS,
  GLOW,
  MAX_PART_CSS,
  MAX_PART_DECLARATIONS,
  PART_PROPERTIES,
  PART_TEXT,
  STRUCTURAL_ENV,
  cleanDesign,
  cleanParts,
  sanitizePartCss,
  serializeCss,
  serverPartProblem,
  serverPartsProblem,
  splitTop,
  type CardPart,
  type Reason,
} from "../web/src/lib/cardstyle.ts";
import { normalizeLayout, resetCardDesign, resolveLayout, snapshotLayout, updateCard } from "../web/src/lib/layout.ts";
import type { Uplink, Widget } from "../web/src/lib/types.ts";
import { PRESET_FORMAT, PresetError, parsePreset } from "../web/src/lib/presets/format.ts";
import { decodePreset, encodePreset } from "../web/src/lib/presets/code.ts";
import { pageToPreset } from "../web/src/lib/presets/export.ts";

const run = (part: CardPart, css: unknown) => sanitizePartCss(part, css, STRUCTURAL_ENV);
const kept = (part: CardPart, css: unknown) => run(part, css).decls.map((d) => `${d.prop}: ${d.value}`);
const reasons = (part: CardPart, css: unknown) => run(part, css).dropped.map((d) => d.reason);

describe("card parts: what is kept", () => {
  it("keeps each part's own properties, normalised, for the part", () => {
    expect(kept("title", "COLOR: Var(--Accent); font-size: 15px; letter-spacing: 0.12em")).toEqual(["color: var(--accent)", "font-size: 15px", "letter-spacing: 0.12em"]);
    expect(run("gauge_fill", "color: #5ef0c4").decls[0].layer).toBe("part");
    expect(kept("gauge_fill", "filter: drop-shadow(0 0 3px rgb(94 240 196 / 80%)) saturate(1.2)")).toHaveLength(1);
    expect(kept("button", "background-image: linear-gradient(180deg, rgb(255 255 255 / 20%), transparent); border-radius: 6px")).toHaveLength(2);
    expect(run("value", "font-family: 'monospace'").decls[0].apply).toContain("ui-monospace");
  });

  it("refuses what a part does not have", () => {
    expect(reasons("title", "background: red")).toEqual(["property"]);
    expect(reasons("chart_line", "background: red")).toEqual(["property"]);
    expect(reasons("label", "font-size: 20px")).toEqual(["property"]);
    expect(reasons("title_bar", "color: red")).toEqual(["property"]);
    expect(reasons("button", "background: red"), "only background-color and -image: the image lies over the button's own colour").toEqual(["property"]);
    for (const part of CARD_PARTS) for (const css of ["position: fixed", "display: none", "width: 999px", "content: none"]) expect(run(part, css).decls, `${part}: ${css}`).toEqual([]);
  });

  it("lets glows reach at most the card body's padding", () => {
    expect(kept("gauge_fill", `box-shadow: 0 0 ${GLOW.blur}px ${GLOW.spread}px red`)).toHaveLength(1);
    expect(kept("screen", "box-shadow: inset 0 0 40px rgb(0 0 0 / 60%), 0 0 6px var(--accent)")).toHaveLength(1);
    expect(kept("toggle_on", "box-shadow: 0 0 6px var(--green)")).toHaveLength(1);
    for (const css of [
      "box-shadow: 0 0 7px red",
      "box-shadow: 3px 0 2px red",
      "box-shadow: 0 0 6px 3px red",
      "box-shadow: 2px 2px 6px 2px red",
      "box-shadow: 0 0 0.5em red",
      "box-shadow: 0 0 6px red, 0 0 6px blue, 0 0 6px green, 0 0 6px white",
      "box-shadow: 0 0 1e1px red",
      "box-shadow: red",
    ]) {
      expect(run("button", css).decls, css).toEqual([]);
    }
    expect(reasons("button", "box-shadow: 0 0 20px red")).toEqual(["glow"]);
    // parts outside the card body glow inward only
    expect(reasons("title_bar", "box-shadow: 0 0 4px red")).toEqual(["shadow"]);
    expect(reasons("toggle", "box-shadow: 0 0 4px red")).toEqual(["shadow"]);
    expect(reasons("gauge_track", "box-shadow: 0 0 4px red")).toEqual(["shadow"]);
  });

  it("knows drop-shadow() only in a part's filter, with nested colours, within the glow limits", () => {
    expect(kept("chart_line", "filter: drop-shadow(0 0 2px color-mix(in srgb, var(--cyan) 60%, transparent)) brightness(1.1)")).toHaveLength(1);
    expect(reasons("gauge_fill", "filter: drop-shadow(0 0 12px red)")).toEqual(["glow"]);
    expect(reasons("gauge_fill", "filter: drop-shadow(4px 0 0 red)")).toEqual(["glow"]);
    expect(reasons("gauge_fill", "filter: drop-shadow(0 0 2px red) drop-shadow(0 0 2px red) drop-shadow(0 0 2px red)")).toEqual(["range"]);
    expect(reasons("gauge_fill", "filter: invert(1)")).toEqual(["function"]);
    expect(reasons("gauge_fill", "filter: drop-shadow(0 0 2px red) url(#x)")).toEqual(["network"]);
    expect(reasons("gauge_fill", "filter: drop-shadow(0 0 2px red")).toEqual(["syntax"]);
    expect(reasons("gauge_fill", "filter: red drop-shadow(0 0 2px red)")).toEqual(["function"]);
    // a function in the wrong property is left to the browser (CSS.supports), like the card's own CSS
    expect(sanitizePartCss("gauge_fill", "color: drop-shadow(0 0 2px red)", { supports: (p, v) => !(p === "color" && v.includes("drop-shadow")) }).dropped.map((d) => d.reason)).toEqual(["unsupported"]);
  });

  it("keeps a title's size readable", () => {
    expect(kept("title", "font-size: 1.2em")).toHaveLength(1);
    for (const css of ["font-size: 40px", "font-size: 6px", "font-size: 3em", "font-size: large", "font-size: 12px 14px"]) expect(run("title", css).decls, css).toEqual([]);
  });

  it("caps length and count, and never throws", () => {
    expect(reasons("title", "color: red;".repeat(60))).toEqual(["too_long"]);
    const many = run("button", "color: red;".repeat(MAX_PART_DECLARATIONS + 3));
    expect(many.decls).toHaveLength(MAX_PART_DECLARATIONS);
    expect(many.dropped.map((d) => d.reason)).toEqual(Array(3).fill("too_many"));
    for (const v of [undefined, null, 42, {}, [], ["color: red"], "", ";;;", "(((", "))))"]) for (const part of CARD_PARTS) expect(run(part, v).decls).toEqual([]);
  });

  it("cleans a card's parts: known parts with strings only, in a fixed order", () => {
    expect(cleanParts({ value: "color: red; position: fixed", title: "color: var(--accent)", nope: "color: red", label: 42, button: ["color: red"] })).toEqual({ title: "color: var(--accent);", value: "color: red;" });
    expect(Object.keys(cleanParts({ value: "color: red", title: "color: red" })!)).toEqual(["title", "value"]);
    expect(cleanParts({ title: "position: fixed" })).toBeUndefined();
    for (const v of [undefined, null, "title", [], 42]) expect(cleanParts(v)).toBeUndefined();
    const proto = JSON.parse('{"__proto__": {"title": "color: red"}, "constructor": "color: red"}');
    expect(cleanParts(proto)).toBeUndefined();
    expect(({} as Record<string, unknown>).title).toBeUndefined();
    // what survives passes the server
    const clean = cleanParts({ gauge_fill: "COLOR: #5EF0C4; filter: drop-shadow(0 0 3px #5ef0c4)", toggle_on: "background: var(--green)" })!;
    expect(serverPartsProblem(clean)).toBeNull();
    for (const [part, css] of Object.entries(clean)) expect(serializeCss(run(part as CardPart, css).decls)).toBe(css);
  });

  it("explains every part", () => {
    for (const part of CARD_PARTS) {
      expect(PART_TEXT[part].name.length).toBeGreaterThan(2);
      expect(PART_PROPERTIES[part].length).toBeGreaterThan(0);
    }
  });
});

describe("card parts: the attack corpus, in every property of every part", () => {
  /** A hostile value for any property, and why it must fail. */
  const VALUES: [string, Reason][] = [
    ["url(https://x.test/a.png)", "network"],
    ["URL (x)", "network"],
    ["image-set(a 1x)", "network"],
    ["-webkit-image-set(a 1x)", "network"],
    ["element(#pin)", "network"],
    ["cross-fade(red, blue)", "network"],
    ["paint(worklet)", "network"],
    ["var(--panel, url(x))", "network"],
    ["drop-shadow(0 0 1px url(x))", "network"],
    ["attr(data-x)", "script"],
    ["expression(alert(1))", "script"],
    ["javascript:alert(1)", "script"],
    ["\\75 rl(x)", "escape"],
    ["red /* x */", "comment"],
    ["red } .pin-dialog { display: none", "block"],
    ["@import x", "block"],
    ["red !important", "important"],
    ["rеd", "unicode"],
    ["red‮", "unicode"],
    ["ｒｅｄ", "unicode"],
    ["red</style><script>alert(1)</script>", "chars"],
  ];
  /** Reasons that only depend on the value (checked after the property). */
  const LATE: [string, Reason][] = [
    ["var(--evil)", "token"],
    ["var(--cp-title-color)", "token"],
    ["--accent", "token"],
    ["calc(1px + 2px)", "function"],
    ["env(safe-area-inset-top)", "function"],
    ["'red'", "strings"],
    ['"red"', "strings"],
    ["red_x", "chars"],
  ];

  it("applies nothing hostile, whatever the property", () => {
    let checked = 0;
    for (const part of CARD_PARTS)
      for (const prop of PART_PROPERTIES[part]) {
        for (const [value, reason] of VALUES) {
          const css = `${prop}: ${value}`;
          const r = run(part, css);
          expect(r.decls, `${part} ${css}`).toEqual([]);
          expect(r.dropped.map((d) => d.reason), `${part} ${css}`).toContain(reason);
          expect(serverPartProblem(part, `${css};`), `${part} ${css}`).not.toBeNull();
          checked++;
        }
        for (const [value, reason] of LATE) {
          const css = `${prop}: ${value}`;
          const r = run(part, css);
          expect(r.decls, `${part} ${css}`).toEqual([]);
          // a font name is checked against the list of fonts first
          expect(r.dropped.map((d) => d.reason), `${part} ${css}`).toContain(prop === "font-family" ? "font" : reason);
          checked++;
        }
      }
    expect(checked).toBeGreaterThan(1500);
  });

  it("cannot reach a theme token or another part's property, nor define one", () => {
    for (const part of CARD_PARTS) {
      expect(reasons(part, "--cp-title-color: red")).toEqual(["property"]);
      expect(reasons(part, "--accent: red")).toEqual(["property"]);
      expect(serverPartProblem(part, "--accent: red")).toBe("property");
      expect(serverPartProblem(part, "--cp-title-color: red")).toBe("token");
    }
  });

  it("only knows its parts on the server, and only strings", () => {
    for (const p of ["body", "card", "TITLE", "__proto__", "constructor", "toString", ""]) expect(serverPartProblem(p, "color: red"), p).toBe("part");
    expect(serverPartsProblem({ title: "color: red" })).toBeNull();
    expect(serverPartsProblem({})).toBeNull();
    for (const v of [null, "x", [], 42, { title: 1 }, { title: null }, { title: ["color: red"] }]) expect(serverPartsProblem(v), JSON.stringify(v)).toBe("parts");
    expect(serverPartsProblem({ title: "x".repeat(MAX_PART_CSS + 1) })).toBe("too_long");
  });
});

describe("the fixed rules in styles.css", () => {
  const css = readFileSync(fileURLToPath(new URL("../web/src/styles.css", import.meta.url)), "utf8").replace(/\/\*[\s\S]*?\*\//g, "");
  /** Every rule that names a part: its selectors and its body. */
  const rules = [...css.matchAll(/([^{}]*data-cp~=[^{}]*)\{([^{}]*)\}/g)].map((m) => ({ selectors: splitTop(m[1]), body: m[2] }));
  const pairs = new Set(CARD_PARTS.flatMap((p) => PART_PROPERTIES[p].map((x) => `${p}:${x}`)));

  it("has a rule for every property of every part, and none for anything else", () => {
    const seen = new Set<string>();
    for (const r of rules)
      for (const s of r.selectors) {
        const tokens = [...s.matchAll(/data-cp~="([^"]+)"/g)].map((m) => m[1]);
        expect(tokens, s).toHaveLength(1);
        expect(pairs.has(tokens[0]), tokens[0]).toBe(true);
        seen.add(tokens[0]);
      }
    for (const pair of pairs) {
      const [part, prop] = pair.split(":");
      const reads = rules.some((r) => r.selectors.some((s) => s.includes(`data-cp~="${pair}"`)) && r.body.includes(`var(--cp-${part}-${prop})`));
      expect(reads, pair).toBe(true);
    }
    expect(seen).toEqual(pairs);
  });

  it("each rule reads only its own part and property", () => {
    for (const r of rules) {
      const own = [...r.selectors[0].matchAll(/data-cp~="([^":]+):([^"]+)"/g)][0];
      for (const m of r.body.matchAll(/--cp-([a-z_]+)-([a-z-]+)/g)) expect(`${m[1]}:${m[2]}`, r.selectors[0]).toBe(`${own[1]}:${own[2]}`);
      for (const s of r.selectors) expect(s, "one part per rule").toContain(`data-cp~="${own[1]}:${own[2]}"`);
    }
  });

  it("never styles a dialog a widget opens", () => {
    for (const r of rules) for (const s of r.selectors) if (!/^\.card\[data-cp~="[^"]+"\] > \.card-head/.test(s)) expect(s, s).toMatch(/:not\(\.backdrop \*\)/);
  });

  it("keeps the warning colours: the fill, the chart line and the values only in the normal zone", () => {
    for (const r of rules)
      for (const s of r.selectors) {
        const pair = /data-cp~="([^"]+)"/.exec(s)![1];
        if (/^value:(color|text-shadow)$/.test(pair)) expect(s, s).toContain(":not(:is(.zone-warn, .zone-crit) *)");
        if (/^gauge_fill:(color|background|background-color|background-image|box-shadow|filter)$/.test(pair)) expect(s, s).toMatch(/zone-ok|\.seg-ok/);
        if (/^chart_line:/.test(pair)) expect(s, s).toContain(".chart.zone-ok");
        if (/^gauge_track:color$/.test(pair)) expect(s, s).toMatch(/\.seg-ok:not\(\.on\)|\.pbar/);
      }
  });
});

const widget = (id: string, type: Widget["type"], label?: string, opts?: Widget["opts"]): Widget => ({ id, type, label, value: 0, opts, online: true }) as Widget;
const STYLE = { bg: "custom", bgColor: "#0B1A2A", css: "border-radius: 12px; position: fixed", parts: { title: "color: #5ef0c4; background: url(x)", gauge_fill: "color: #5ef0c4", bogus: "color: red" } };
const reactor = (style?: unknown, groups?: Uplink["groups"]): Uplink => ({
  uplinkId: "r1",
  title: "Fission Reactor",
  online: true,
  mode: "lua",
  widgets: [widget("temp", "gauge", "Temperature", { unit: "K" }), widget("scram", "button", "SCRAM"), widget("fuel", "gauge", "Fuel", { group: "fuel" })],
  ...(style ? { style } : {}),
  ...(groups ? { groups } : {}),
});
const doc = (card: Record<string, unknown> = {}) => normalizeLayout({ v: 2, pages: [{ id: "p", title: "P" }], cards: { r1: { page: "p", x: 0, y: 0, w: 6, h: 10, ...card } }, widgets: {} });
const cardOf = (pages: ReturnType<typeof resolveLayout>, key = "r1") => pages.flatMap((p) => p.cards).find((c) => c.key === key)!;

describe("parts in layouts and presets", () => {
  it("keeps sanitised parts through a save", () => {
    const d = doc({ parts: { title: "color: var(--accent); background: red", gauge_fill: "filter: drop-shadow(0 0 3px var(--accent)); filter: url(#x)", nope: "color: red" } });
    expect(d.cards.r1.parts).toEqual({ title: "color: var(--accent);", gauge_fill: "filter: drop-shadow(0 0 3px var(--accent));" });
    const saved = snapshotLayout(resolveLayout([reactor()], d));
    expect(saved.cards.r1.parts).toEqual(d.cards.r1.parts);
    expect(serverPartsProblem(saved.cards.r1.parts)).toBeNull();
    expect(normalizeLayout(JSON.parse(JSON.stringify(saved))).cards.r1).toEqual(saved.cards.r1);
    expect(doc({ parts: { title: "position: fixed" } }).cards.r1.parts).toBeUndefined();
    expect(doc({ parts: "color: red" }).cards.r1.parts).toBeUndefined();
  });

  it("the editor's patch goes into the snapshot", () => {
    const pages = resolveLayout([reactor()], doc());
    const next = updateCard(pages, "r1", { parts: { button: "border-radius: 6px;" } });
    expect(snapshotLayout(next).cards.r1.parts).toEqual({ button: "border-radius: 6px;" });
    expect(snapshotLayout(updateCard(next, "r1", { parts: undefined })).cards.r1).not.toHaveProperty("parts");
  });

  it("parses parts in presets, refuses the wrong shape and carries them through a share code", async () => {
    const raw = (card: Record<string, unknown>) => ({
      format: PRESET_FORMAT,
      name: "Styled",
      slots: [{ id: "r", label: "Reactor", types: ["gauge"], min: 1, max: 1 }],
      pages: [{ title: "Reactor", cards: [{ w: 6, title: "Core", widgets: [{ slot: "r", ids: ["temp"] }], ...card }] }],
    });
    const p = parsePreset(raw({ parts: { title: "color: var(--accent); z-index: 9", value: "background: url(x)" } }));
    expect(p.pages[0].cards[0].parts).toEqual({ title: "color: var(--accent);" });
    for (const parts of [[], "x", { nope: "color: red" }, { title: 1 }, { title: "color: red;".repeat(60) }]) expect(() => parsePreset(raw({ parts })), JSON.stringify(parts)).toThrow(PresetError);
    const back = await decodePreset(await encodePreset(p));
    expect(back.pages[0].cards[0].parts).toEqual({ title: "color: var(--accent);" });
    const evil = JSON.parse(JSON.stringify(p));
    evil.pages[0].cards[0].parts = { button: "background-image: \\75 rl(x); color: red} .pin{display:none" };
    expect((await decodePreset(await encodePreset(parsePreset(evil)))).pages[0].cards[0].parts).toBeUndefined();
  });

  it("exports the parts of a page", () => {
    const here = [reactor()];
    const pages = resolveLayout(here, doc({ parts: { title: "color: var(--accent);" } }));
    const { preset } = pageToPreset(pages[0], here, { name: "Mine" });
    expect(preset.pages[0].cards[0].parts).toEqual({ title: "color: var(--accent);" });
  });
});

describe("a script's design", () => {
  it("shows the uplink's style, sanitised, without saving it into the layout", () => {
    const card = cardOf(resolveLayout([reactor(STYLE)], doc()));
    expect(card).toMatchObject({ bg: "custom", bgColor: "#0b1a2a", css: "border-radius: 12px;", parts: { title: "color: #5ef0c4;", gauge_fill: "color: #5ef0c4;" }, designFrom: "script" });
    expect(card.parts).not.toHaveProperty("bogus");
    const saved = snapshotLayout(resolveLayout([reactor(STYLE)], doc()));
    for (const k of ["bg", "bgColor", "css", "parts"]) expect(saved.cards.r1, k).not.toHaveProperty(k);
    expect(cleanDesign("x")).toBeUndefined();
    expect(cleanDesign({ css: "position: fixed", bgColor: "#123456" }), "nothing that changes anything").toBeUndefined();
  });

  it("a group's own style goes to the group's card; without one it takes the section's", () => {
    const groups = [{ id: "fuel", title: "Fuel", style: { parts: { gauge_fill: "color: var(--green)" } } }];
    const pages = resolveLayout([reactor(STYLE, groups)], null);
    expect(cardOf(pages, "r1/fuel").parts).toEqual({ gauge_fill: "color: var(--green);" });
    expect(cardOf(pages, "r1").parts?.title).toBe("color: #5ef0c4;");
    expect(cardOf(resolveLayout([reactor(STYLE, [{ id: "fuel", title: "Fuel" }])], null), "r1/fuel").parts?.title).toBe("color: #5ef0c4;");
    expect(cardOf(resolveLayout([reactor(STYLE, [{ id: "fuel", title: "Fuel", style: { css: "position: fixed" } }])], null), "r1/fuel").parts?.title, "a style with nothing left counts as none").toBe("color: #5ef0c4;");
    expect(cardOf(resolveLayout([reactor(undefined, [{ id: "fuel", title: "Fuel" }])], null), "r1/fuel").designFrom).toBeUndefined();
  });

  it("the layout's design wins as a whole", () => {
    const card = cardOf(resolveLayout([reactor(STYLE)], doc({ css: "letter-spacing: 0.1em" })));
    expect(card.css).toBe("letter-spacing: 0.1em;");
    expect(card.bg).toBeUndefined();
    expect(card.parts).toBeUndefined();
    expect(card.designFrom).toBeUndefined();
    expect(card.scriptDesign?.parts?.title).toBe("color: #5ef0c4;");
  });

  it("a change in the editor keeps the rest of the script's design, and a reset goes back to it", () => {
    const pages = resolveLayout([reactor(STYLE)], doc());
    const next = updateCard(pages, "r1", { parts: { ...cardOf(pages).parts, button: "border-radius: 6px;" } });
    const saved = snapshotLayout(next).cards.r1;
    expect(saved).toMatchObject({ bg: "custom", bgColor: "#0b1a2a", css: "border-radius: 12px;" });
    expect(saved.parts).toEqual({ title: "color: #5ef0c4;", gauge_fill: "color: #5ef0c4;", button: "border-radius: 6px;" });
    const reset = resetCardDesign(next, "r1");
    expect(cardOf(reset)).toMatchObject({ designFrom: "script", parts: { title: "color: #5ef0c4;", gauge_fill: "color: #5ef0c4;" } });
    for (const k of ["bg", "bgColor", "css", "parts"]) expect(snapshotLayout(reset).cards.r1, k).not.toHaveProperty(k);
    // without a script design a reset clears the card's
    const plain = resolveLayout([reactor()], doc({ css: "border-radius: 4px", parts: { title: "color: red" } }));
    const cleared = cardOf(resetCardDesign(plain, "r1"));
    expect(cleared.css).toBeUndefined();
    expect(cleared.parts).toBeUndefined();
  });
});
