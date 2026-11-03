// Card design: the custom CSS sanitiser against an attack corpus, the server's check against the fixture it shares with
// CardStyle.java, readable text on every background colour, and the design surviving layouts, presets and share codes.
import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import {
  CARD_BACKGROUNDS,
  CARD_PARTS,
  CSS_FUNCTIONS,
  CSS_PROPERTIES,
  MAX_CARD_CSS,
  MAX_DECLARATIONS,
  MAX_PART_CSS,
  PART_FUNCTIONS,
  PART_PROPERTIES,
  REASON_TEXT,
  STRUCTURAL_ENV,
  THEME_TOKENS,
  TONES,
  browserCssEnv,
  cleanCardCss,
  contrast,
  customColorLook,
  mix,
  parseHex,
  readableLook,
  sanitizeCardCss,
  sanitizePartCss,
  scrimColor,
  serializeCss,
  serverCssProblem,
  serverPartProblem,
  type CssEnv,
  type Reason,
  type Rgb,
} from "../web/src/lib/cardstyle.ts";
import { normalizeLayout, resolveLayout, snapshotLayout, updateCard, type LayoutDoc } from "../web/src/lib/layout.ts";
import type { Uplink, Widget } from "../web/src/lib/types.ts";
import { PRESET_FORMAT, PresetError, parsePreset } from "../web/src/lib/presets/format.ts";
import { matchPreset } from "../web/src/lib/presets/expand.ts";
import { decodePreset, encodePreset } from "../web/src/lib/presets/code.ts";
import { pageToPreset } from "../web/src/lib/presets/export.ts";

const ok: CssEnv = STRUCTURAL_ENV;
const run = (css: unknown, env: CssEnv = ok) => sanitizeCardCss(css, env);
/** The reasons of the dropped declarations. */
const reasons = (css: unknown, env: CssEnv = ok) => run(css, env).dropped.map((d) => d.reason);
const kept = (css: unknown, env: CssEnv = ok) => run(css, env).decls.map((d) => `${d.prop}: ${d.value}`);
/** Reasons found before the property is even looked at: the same in a card's CSS and in every part's. */
const EARLY = new Set<Reason>(["escape", "comment", "block", "important", "network", "script", "unicode"]);
/** Nothing of it is applied, and this is why; neither in any part of the card (CardStyle parts). */
const refused = (css: string, reason: Reason) => {
  const r = run(css);
  expect(r.decls, css).toEqual([]);
  expect(r.dropped.map((d) => d.reason), css).toContain(reason);
  for (const part of CARD_PARTS) {
    const p = sanitizePartCss(part, css, ok);
    expect(p.decls, `${part}: ${css}`).toEqual([]);
    if (EARLY.has(reason)) expect(p.dropped.map((d) => d.reason), `${part}: ${css}`).toContain(reason);
  }
};

describe("card CSS: what is kept", () => {
  it("keeps whitelisted declarations, normalised", () => {
    expect(kept("COLOR:  Var(--Text) ;\n\tbackground: linear-gradient(135deg,  #1E3A5F, var(--panel));")).toEqual([
      "color: var(--text)",
      "background: linear-gradient(135deg, #1e3a5f, var(--panel))",
    ]);
    expect(kept("border: 2px solid var(--accent); border-radius: 12px; letter-spacing: 0.05em; text-transform: uppercase")).toHaveLength(4);
    expect(kept("box-shadow: inset 0 0 12px rgb(0 0 0 / 40%), inset 0 1px 0 var(--line)")).toHaveLength(1);
    expect(kept("filter: saturate(1.4) hue-rotate(20deg) blur(2px)")).toHaveLength(1);
    expect(kept("text-shadow: 0 1px 0 #000; font-weight: 700; font-style: italic; opacity: 0.85")).toHaveLength(4);
    expect(kept("background: repeating-linear-gradient(45deg, color-mix(in srgb, var(--accent) 20%, transparent) 0 6px, transparent 6px 12px)")).toHaveLength(1);
  });

  it("puts surface properties on the skin and text properties on the card", () => {
    const layers = Object.fromEntries(run("background: red; opacity: 0.9; color: red; border-radius: 4px; font-family: serif").decls.map((d) => [d.prop, d.layer]));
    expect(layers).toEqual({ background: "skin", opacity: "skin", color: "card", "border-radius": "card", "font-family": "card" });
  });

  it("knows a short list of fonts and applies the dashboard's stacks", () => {
    const d = run('font-family: "monospace"').decls[0];
    expect(d.value).toBe("monospace");
    expect(d.apply).toContain("ui-monospace");
    expect(run("font-family: Silkscreen").decls[0].apply).toContain('"Silkscreen"');
    expect(reasons("font-family: Comic Sans MS")).toEqual(["font"]);
    expect(reasons("font-family: monospace, serif")).toEqual(["font"]);
    expect(reasons("font-family: monospace, url(x)")).toEqual(["network"]);
  });

  it("serialises to text that sanitises to itself", () => {
    const input = "Color: RED;;; background:linear-gradient(red,blue); font-family: 'serif'; position: fixed";
    const once = serializeCss(run(input).decls);
    expect(once).toBe("color: red;\nbackground: linear-gradient(red,blue);\nfont-family: serif;");
    expect(serializeCss(run(once).decls)).toBe(once);
    expect(serverCssProblem(once)).toBeNull();
    expect(cleanCardCss(input)).toBe(once);
    expect(cleanCardCss("position: fixed")).toBeUndefined();
  });

  it("never throws, whatever it gets", () => {
    for (const v of [undefined, null, 42, {}, [], ["color: red"], { toString: () => "color: red" }, "", "   ", ";;;", ":", "::::", "(((", ")))"]) {
      expect(() => run(v)).not.toThrow();
      expect(run(v).decls).toEqual([]);
    }
  });

  it("caps the number of declarations", () => {
    const r = run("color: red;".repeat(MAX_DECLARATIONS + 8));
    expect(r.decls).toHaveLength(MAX_DECLARATIONS);
    expect(r.dropped.map((d) => d.reason)).toEqual(Array(8).fill("too_many"));
  });
});

describe("card CSS: the attack corpus", () => {
  it("refuses escapes", () => {
    refused("background: \\75 rl(https://x.test/a.png)", "escape");
    refused("background: u\\72 l(https://x.test/a.png)", "escape");
    refused("background: \\000075rl(x)", "escape");
    refused("color: red\\;background: url(x)", "escape");
    refused("posi\\tion: fixed", "escape");
  });

  it("refuses comments, also ones that hide a token", () => {
    refused("color: red /* x */", "comment");
    refused("background: ur/**/l(x)", "comment");
    refused("color: red */", "comment");
  });

  it("cannot break out of the declaration list", () => {
    const r = run("color: red; } .pin-dialog { display: none } .x { color: blue");
    expect(r.decls.map((d) => d.prop)).toEqual(["color"]);
    expect(r.dropped.map((d) => d.reason)).toEqual(["block"]);
    refused("color: red }", "block");
    refused("@import 'x.css'", "block");
    refused("@media all { color: red", "block");
    refused("color: red</style><script>alert(1)</script>", "chars");
    refused("color: red<!--", "important");
  });

  it("a `;` only ends a declaration: an injected one is checked on its own", () => {
    const r = run("color: red; background: url(https://x.test/a.png); position: fixed; opacity: 0.5");
    expect(r.decls.map((d) => d.prop)).toEqual(["color", "opacity"]);
    expect(r.dropped.map((d) => d.reason)).toEqual(["network", "escape_card"]);
    // a quoted `;` cannot hide anything: quotes are refused
    expect(reasons("background: 'a;b' red")).toEqual(["strings", "syntax"]);
  });

  it("refuses !important", () => {
    refused("color: red !important", "important");
    refused("color: red!IMPORTANT", "important");
    refused("color: red ! important", "important");
  });

  it("refuses everything that loads something, in any case and spacing", () => {
    for (const css of [
      "background: url(https://x.test/a.png)",
      "background: URL(https://x.test/a.png)",
      "background: Url (https://x.test/a.png)",
      "background: url\t(x)",
      "background-image: image-set(a 1x)",
      "background-image: -webkit-image-set(a 1x)",
      "background-image: image(red)",
      "background-image: src(x)",
      "background: element(#pin)",
      "background: -moz-element(#pin)",
      "background: cross-fade(red, blue)",
      "background: paint(worklet)",
    ]) {
      refused(css, "network");
    }
  });

  it("refuses everything that runs or reads something", () => {
    for (const css of ["color: attr(data-x)", "width: expression(alert(1))", "background: EXPRESSION(alert(1))", "-moz-binding: none", "behavior: url(x.htc)", "behaviour: none", "background: javascript:alert(1)", "background: JavaScript :alert(1)"]) {
      expect(run(css).decls, css).toEqual([]);
      expect(["script", "network"], css).toContain(reasons(css)[0]);
    }
  });

  it("checks var() fallbacks and allows only theme tokens", () => {
    refused("background: var(--x, url(https://x.test/a.png))", "network");
    refused("background: var(--panel, url(x))", "network");
    refused("background: var(--evil)", "token");
    refused("background: var(--panel, var(--evil))", "token");
    refused("background: var(--panel, var(--muted, var(--x)))", "token");
    refused("background: var(panel)", "token");
    refused("background: var(--panel", "syntax");
    refused("background: --panel", "token");
    refused("--accent: red", "property");
    refused("--x: url(x)", "network");
    expect(kept("background: var(--panel, var(--muted, red))")).toHaveLength(1);
    expect(kept("color: var( --text )")).toHaveLength(1);
  });

  it("refuses unicode tricks", () => {
    for (const css of [
      "color: \uff52\uff45\uff44", // fullwidth letters
      "background: \uff55\uff52\uff4c(x)",
      "background: ur\u200bl(x)", // zero-width space
      "background: u\u00adrl(x)", // soft hyphen
      "color: \u202ered", // right-to-left override
      "color: red\u202e;",
      "\u0441olor: red", // Cyrillic s
      "color: r\u0435d", // Cyrillic e
      "color:\u00a0red", // no-break space
      "color: red\u0000",
      "color: red\u2028",
      "position\ufe0f: fixed",
    ]) {
      refused(css, "unicode");
    }
  });

  it("refuses properties that could leave the card, cover other UI or take the pointer", () => {
    for (const css of [
      "position: fixed",
      "inset: 0",
      "inset-inline: 0",
      "top: -100px",
      "left: 0",
      "z-index: 9999",
      "transform: scale(10)",
      "transform-origin: 0 0",
      "translate: 0 -100px",
      "rotate: 10deg",
      "scale: 3",
      "margin: -100px",
      "margin-top: -50px",
      "content: none",
      "pointer-events: none",
      "cursor: pointer",
      "outline: 9999px solid red",
      "outline-offset: 100px",
      "overflow: visible",
      "width: 100vw",
      "height: 9999px",
      "min-height: 0",
      "max-width: none",
      "zoom: 5",
      "float: left",
      "contain: none",
      "isolation: isolate",
      "will-change: transform",
    ]) {
      refused(css, "escape_card");
    }
  });

  it("refuses properties that are not on the list", () => {
    for (const css of ["display: none", "visibility: hidden", "font-size: 100px", "clip-path: none", "mask: none", "backdrop-filter: blur(4px)", "animation: spin 1s", "transition: all 1s", "mix-blend-mode: difference", "line-height: 9"]) {
      refused(css, "property");
    }
  });

  it("refuses outer and huge shadows", () => {
    refused("box-shadow: 0 0 20px red", "shadow");
    refused("box-shadow: inset 0 0 4px red, 0 0 20px red", "shadow");
    refused("box-shadow: 0 0 0 9999px red", "range");
    refused("box-shadow: inset 0 0 200px red", "range");
    refused("box-shadow: inset 0 0 1px red, inset 0 0 1px red, inset 0 0 1px red, inset 0 0 1px red, inset 0 0 1px red", "range");
    refused("text-shadow: 0 0 50px red", "range");
    refused("text-shadow: 0 0 1px red, 0 0 1px red, 0 0 1px red", "range");
  });

  it("keeps numbers small, without exponents or viewport units", () => {
    refused("border: 50px solid red", "range");
    refused("border-width: 1em", "range");
    refused("border-radius: 9999px", "range");
    refused("border-radius: 50%", "range");
    refused("letter-spacing: 50px", "range");
    refused("letter-spacing: 10%", "unit");
    refused("background-size: 1e9px", "range");
    refused("background-size: 100vw", "unit");
    refused("background-size: 10cm", "unit");
    refused("background-position: 99999px 0", "range");
    refused("opacity: 2", "range");
    refused("opacity: -1", "range");
  });

  it("allows only gentle filters", () => {
    refused("filter: invert(1)", "function");
    refused("filter: drop-shadow(0 0 40px red)", "function");
    refused("filter: blur(50px)", "range");
    refused("filter: brightness(0)", "range");
    refused("filter: contrast(10)", "range");
    refused("filter: url(#f)", "network");
    refused("filter: blur(1px) blur(1px) blur(1px) blur(1px) blur(1px) blur(1px) blur(1px)", "range");
  });

  it("refuses unknown functions, deep nesting and broken syntax", () => {
    refused("color: calc(1px + 2px)", "function");
    refused("color: env(safe-area-inset-top)", "function");
    refused("color: rgb(rgb(rgb(rgb(rgb(1)))))", "function");
    refused("background: linear-gradient(red, blue", "syntax");
    refused("background: red)", "syntax");
    refused("color red", "syntax");
    refused(": red", "syntax");
    refused("color:", "syntax");
    refused("color: red background: blue", "syntax");
    refused("font-weight: heavy", "keyword");
    refused("text-transform: full-width", "keyword");
    refused("color: 'red'", "strings");
    refused('color: "red"', "strings");
    refused("background: red_x", "chars");
    refused("background: red*2", "chars");
  });

  it("drops very long input whole", () => {
    const long = "color: red;".repeat(400);
    expect(long.length).toBeGreaterThan(MAX_CARD_CSS);
    expect(run(long)).toEqual({ decls: [], dropped: [{ text: `${long.length} characters`, reason: "too_long" }] });
    expect(run("a".repeat(1_000_000)).dropped[0].reason).toBe("too_long");
    refused(`background: linear-gradient(${"red, ".repeat(100)}blue)`, "too_long");
  });

  it("asks the browser last, and fails closed without one", () => {
    const no: CssEnv = { supports: () => false };
    expect(reasons("color: red", no)).toEqual(["unsupported"]);
    // in Node there is no CSS.supports: nothing is applied
    expect(run("color: red", browserCssEnv()).decls).toEqual([]);
    const asked: string[] = [];
    run("color: red; position: fixed; background: url(x)", { supports: (p, v) => (asked.push(`${p}: ${v}`), true) });
    expect(asked).toEqual(["color: red"]);
  });

  it("explains every reason", () => {
    for (const r of Object.values(REASON_TEXT)) expect(r.length).toBeGreaterThan(5);
  });
});

describe("the server's check (shared with CardStyle.java)", () => {
  const file = fileURLToPath(new URL("../../src/test/resources/layout/card_style_fixture.json", import.meta.url));
  const fixture = JSON.parse(readFileSync(file, "utf8")) as {
    backgrounds: string[];
    properties: string[];
    functions: string[];
    tokens: string[];
    maxCss: number;
    accept: string[];
    reject: { css: string; reason: string }[];
    parts: Record<string, string[]>;
    partFunctions: string[];
    maxPartCss: number;
    partAccept: { part: string; css: string }[];
    partReject: { part: string; css: string; reason: string }[];
  };

  it("has the same lists", () => {
    expect(fixture.backgrounds).toEqual([...CARD_BACKGROUNDS]);
    expect(fixture.properties).toEqual(CSS_PROPERTIES);
    expect(fixture.functions).toEqual([...CSS_FUNCTIONS]);
    expect(fixture.tokens).toEqual([...THEME_TOKENS]);
    expect(fixture.maxCss).toBe(MAX_CARD_CSS);
  });
  it.each(fixture.accept)("accepts %j", (css) => expect(serverCssProblem(css)).toBeNull());
  it.each(fixture.reject)("refuses $css ($reason)", ({ css, reason }) => expect(serverCssProblem(css)).toBe(reason));

  it("has the same parts", () => {
    expect(Object.keys(fixture.parts)).toEqual([...CARD_PARTS]);
    for (const part of CARD_PARTS) expect(fixture.parts[part], part).toEqual(PART_PROPERTIES[part]);
    expect(fixture.partFunctions).toEqual([...PART_FUNCTIONS]);
    expect(fixture.maxPartCss).toBe(MAX_PART_CSS);
  });
  it.each(fixture.partAccept)("accepts $part: $css", ({ part, css }) => expect(serverPartProblem(part, css)).toBeNull());
  it.each(fixture.partReject)("refuses $part: $css ($reason)", ({ part, css, reason }) => expect(serverPartProblem(part, css)).toBe(reason));
  it("refuses every attack of the card in every part, for the same reason", () => {
    for (const { css, reason } of fixture.reject) for (const part of CARD_PARTS) expect(serverPartProblem(part, css), `${part}: ${css}`).toBe(reason);
  });
  it("keeps every accepted part whole in the sanitiser", () => {
    for (const { part, css } of fixture.partAccept) {
      const r = sanitizePartCss(part as (typeof CARD_PARTS)[number], css, ok);
      expect(r.dropped, `${part}: ${css}`).toEqual([]);
      expect(serverPartProblem(part, serializeCss(r.decls))).toBeNull();
    }
  });

  it("accepts everything the sanitiser keeps", () => {
    const samples = [
      "COLOR: RED; background: linear-gradient(red, blue); font-family: 'serif'",
      "box-shadow: inset 0 0 12px rgb(0 0 0 / 40%); filter: saturate(1.4) hue-rotate(20deg)",
      "background: var(--panel, var(--muted, #123456)); text-transform: uppercase; letter-spacing: -0.02em",
    ];
    for (const s of samples) expect(serverCssProblem(cleanCardCss(s)!)).toBeNull();
  });
});

describe("readable text on a card's own colour", () => {
  const MIN_TEXT = 7;
  const MIN_MUTED = 4.5;
  const readable = (bg: Rgb) => {
    const look = readableLook([bg]);
    const t = TONES[look.tone];
    const under = mix(bg, t.panel, look.scrim);
    return { look, text: contrast(t.text, under), muted: contrast(t.muted, under) };
  };

  it("keeps text and labels readable on every colour", () => {
    for (let r = 0; r <= 255; r += 51)
      for (let g = 0; g <= 255; g += 51)
        for (let b = 0; b <= 255; b += 51) {
          const { text, muted } = readable([r, g, b]);
          expect(text, `${r},${g},${b}`).toBeGreaterThanOrEqual(MIN_TEXT);
          expect(muted, `${r},${g},${b}`).toBeGreaterThanOrEqual(MIN_MUTED);
        }
  });

  it("uses no veil where none is needed, and the matching text", () => {
    expect(readableLook([[0, 0, 0]])).toEqual({ tone: "dark", scrim: 0 });
    expect(readableLook([[255, 255, 255]])).toEqual({ tone: "light", scrim: 0 });
    expect(customColorLook("#0b1a2a")).toEqual({ tone: "dark", scrim: 0 });
    expect(customColorLook("#fff3c4")!.tone).toBe("light");
    const mid = customColorLook("#3f7fbf")!;
    expect(mid.scrim).toBeGreaterThan(0);
    expect(mid.scrim).toBeLessThan(1);
  });

  it("a gradient is readable at both ends", () => {
    const look = readableLook([parseHex("#000000")!, parseHex("#ffffff")!]);
    const t = TONES[look.tone];
    for (const c of [[0, 0, 0], [255, 255, 255]] as Rgb[]) expect(contrast(t.text, mix(c, t.panel, look.scrim))).toBeGreaterThanOrEqual(MIN_TEXT);
  });

  it("builds the veil from numbers only", () => {
    expect(scrimColor({ tone: "dark", scrim: 0.35 })).toBe("rgb(22 27 33 / 35%)");
    expect(customColorLook("url(x)")).toBeNull();
    expect(customColorLook("#12345")).toBeNull();
  });
});

const widget = (id: string, type: Widget["type"], label?: string, opts?: Widget["opts"]): Widget => ({ id, type, label, value: 0, opts, online: true }) as Widget;
const reactor = (uplinkId = "r1"): Uplink => ({
  uplinkId,
  title: "Fission Reactor",
  online: true,
  mode: "lua",
  widgets: [widget("temp", "gauge", "Temperature", { unit: "K" }), widget("status", "label", "Status"), widget("scram", "button", "SCRAM")],
});

describe("the design in layouts", () => {
  const doc = (card: Record<string, unknown>, widgets: Record<string, unknown> = {}) => normalizeLayout({ v: 2, pages: [{ id: "p", title: "P" }], cards: { r1: { page: "p", x: 0, y: 0, w: 6, h: 10, ...card } }, widgets });

  it("keeps background, colour, sanitised CSS and widget icons through a save", () => {
    const d = doc({ bg: "custom", bgColor: "#1E3A5F", css: "color: var(--text); position: fixed; background: url(x)" }, { "r1/status": { icon: "flame" } });
    expect(d.cards.r1).toMatchObject({ bg: "custom", bgColor: "#1e3a5f", css: "color: var(--text);" });
    const pages = resolveLayout([reactor()], d);
    const card = pages[0].cards.find((c) => c.key === "r1")!;
    expect(card.bg).toBe("custom");
    expect(card.widgets.find((w) => w.key === "r1/status")!.icon).toBe("flame");
    const saved = snapshotLayout(pages);
    expect(saved.cards.r1).toMatchObject({ bg: "custom", bgColor: "#1e3a5f", css: "color: var(--text);" });
    expect(saved.widgets["r1/status"]).toMatchObject({ icon: "flame" });
    expect(serverCssProblem(saved.cards.r1.css!)).toBeNull();
    // and reads back the same
    expect(normalizeLayout(JSON.parse(JSON.stringify(saved))).cards.r1).toEqual(saved.cards.r1);
  });

  it("drops what it does not know", () => {
    const d = doc({ bg: "neon", bgColor: "red", css: 42 });
    expect(d.cards.r1.bg).toBeUndefined();
    expect(d.cards.r1.bgColor).toBeUndefined();
    expect(d.cards.r1.css).toBeUndefined();
    expect(doc({ bg: "tint-red", bgColor: "#123456" }).cards.r1.bgColor, "only with the custom option").toBeUndefined();
    expect(doc({ css: "position: fixed; z-index: 9" }).cards.r1.css).toBeUndefined();
  });

  it("an old layout without a design still reads the same", () => {
    const pages = resolveLayout([reactor()], doc({ title: "Core", color: "red" }));
    const card = pages[0].cards.find((c) => c.key === "r1")!;
    expect(card.bg).toBeUndefined();
    expect(card.css).toBeUndefined();
    expect(snapshotLayout(pages).cards.r1).not.toHaveProperty("bg");
  });

  it("the editor's patch goes into the snapshot", () => {
    const pages = resolveLayout([reactor()], doc({}));
    const next = updateCard(pages, "r1", { bg: "pat-circuit", css: "border-radius: 4px;" });
    expect(snapshotLayout(next).cards.r1).toMatchObject({ bg: "pat-circuit", css: "border-radius: 4px;" });
    const cleared = updateCard(next, "r1", { bg: undefined, css: undefined });
    expect(snapshotLayout(cleared).cards.r1).not.toHaveProperty("bg");
    expect(snapshotLayout(cleared).cards.r1).not.toHaveProperty("css");
  });
});

describe("the design in presets and share codes", () => {
  const raw = (card: Record<string, unknown>) => ({
    format: PRESET_FORMAT,
    name: "Styled",
    slots: [{ id: "r", label: "Reactor", types: ["gauge"], min: 1, max: 1 }],
    pages: [{ title: "Reactor", cards: [{ w: 6, title: "Core", widgets: [{ slot: "r", ids: ["temp"] }, { slot: "r", ids: ["status"], icon: "flame" }], ...card }] }],
  });

  it("parses and sanitises the design", () => {
    const p = parsePreset(raw({ bg: "custom", bgColor: "#ABCDEF", css: "color: var(--text); background: url(https://x.test/a.png); z-index: 9" }));
    expect(p.pages[0].cards[0]).toMatchObject({ bg: "custom", bgColor: "#abcdef", css: "color: var(--text);" });
    expect(parsePreset(raw({ bg: "grad-ember", bgColor: "#abcdef" })).pages[0].cards[0].bgColor).toBeUndefined();
    expect(parsePreset(raw({ css: "position: fixed" })).pages[0].cards[0].css).toBeUndefined();
  });

  it("refuses a design of the wrong shape", () => {
    for (const card of [{ bg: "neon" }, { bg: 1 }, { bgColor: "red" }, { bgColor: "#fff" }, { css: 42 }, { css: ["color: red"] }, { css: "color: red;".repeat(300) }, { styles: "x" }]) {
      expect(() => parsePreset(raw(card)), JSON.stringify(card)).toThrow(PresetError);
    }
  });

  it("goes through a share code and onto another base", async () => {
    const p = parsePreset(raw({ bg: "pat-grid", css: "border-radius: 12px; letter-spacing: 0.04em" }));
    const back = await decodePreset(await encodePreset(p));
    expect(back.pages[0].cards[0]).toMatchObject({ bg: "pat-grid", css: "border-radius: 12px;\nletter-spacing: 0.04em;" });
    const m = matchPreset(back, [reactor("other")], "en");
    expect(m.state).toBe("ready");
    const card = Object.values(m.expanded.doc.cards)[0];
    expect(card).toMatchObject({ bg: "pat-grid", css: "border-radius: 12px;\nletter-spacing: 0.04em;" });
    const resolved = resolveLayout([reactor("other")], m.expanded.doc);
    const styled = resolved[0].cards.find((c) => c.title === "Core")!;
    expect(styled.widgets.find((w) => w.view?.src === "other/status")!.icon).toBe("flame");
  });

  it("a hand-made code with hostile CSS loses it on import", async () => {
    const p = parsePreset(raw({}));
    const evil = { ...JSON.parse(JSON.stringify(p)) };
    evil.pages[0].cards[0].css = "background: \\75 rl(https://x.test/a.png); color: red} .pin{display:none";
    const back = await decodePreset(await encodePreset(parsePreset(evil)));
    expect(back.pages[0].cards[0].css).toBeUndefined();
  });

  it("exports the design of a page", () => {
    const here = [reactor("r1")];
    const doc: LayoutDoc = {
      v: 2,
      pages: [{ id: "p", title: "Reactor" }],
      cards: { "c:a": { page: "p", title: "Core", x: 0, y: 0, w: 6, h: 10, bg: "custom", bgColor: "#203040", css: "color: var(--text);" } },
      widgets: {
        "v:1": { card: "c:a", src: "r1/temp", as: "gauge", x: 0, y: 0, w: 6, h: 5 },
        "v:2": { card: "c:a", src: "r1/status", as: "label", icon: "flame", x: 0, y: 5, w: 6, h: 1 },
      },
    };
    const { preset: p } = pageToPreset(resolveLayout(here, doc)[0], here, { name: "Mine" });
    expect(p.pages[0].cards[0]).toMatchObject({ bg: "custom", bgColor: "#203040", css: "color: var(--text);" });
    expect(p.pages[0].cards[0].widgets.find((w) => w.icon === "flame")).toBeTruthy();
  });
});
