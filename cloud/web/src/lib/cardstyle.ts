// Card design: a card's background option and its custom CSS (docs: /docs/dashboard#card-design).
//
// Custom CSS is untrusted. It comes from the layout (anyone with layout.edit), from share codes written by strangers and
// from store presets, so every string is treated as hostile and checked again each time a card renders, wherever the
// layout came from. Only declarations (`property: value;`) are accepted: no selectors, no blocks, no at-rules. A
// declaration is kept when its property is on a short whitelist and its value passes the checks below and the browser's
// CSS.supports(). Card.tsx applies the kept ones one by one with style.setProperty() to two elements the card owns: the
// skin (a layer behind the content, clipped to the card, never hit by the pointer) and the card itself (text properties
// and the corner radius). Nothing is ever written as stylesheet text, a style attribute or innerHTML.
//
// The server checks the same forbidden tokens, characters, properties, functions and theme tokens (CardStyle.java,
// used by LayoutValidator.java; serverCssProblem() here is the same check), and the shared fixture
// src/test/resources/layout/card_style_fixture.json keeps both in step. Pure functions only (no DOM), so the attack corpus runs in Vitest.

/** Background options of a card. `custom` uses the card's `bgColor`. */
export const CARD_BACKGROUNDS = [
  "tint-gold",
  "tint-cyan",
  "tint-green",
  "tint-yellow",
  "tint-red",
  "tint-purple",
  "tint-orange",
  "tint-gray",
  "grad-accent",
  "grad-aurora",
  "grad-ember",
  "grad-lagoon",
  "pat-grid",
  "pat-stripes",
  "pat-dots",
  "pat-circuit",
  "custom",
] as const;
export type CardBackground = (typeof CARD_BACKGROUNDS)[number];
export const isCardBackground = (v: unknown): v is CardBackground => typeof v === "string" && (CARD_BACKGROUNDS as readonly string[]).includes(v);
/** `bgColor` of a card with the `custom` background. */
export const HEX_COLOR = /^#[0-9a-f]{6}$/i;
export const isHexColor = (v: unknown): v is string => typeof v === "string" && HEX_COLOR.test(v);

/** Longest custom CSS of one card (characters), and most declarations kept. */
export const MAX_CARD_CSS = 2048;
export const MAX_DECLARATIONS = 32;

/** The dashboard's theme tokens a value may read with var(): they follow the light and the dark theme. */
export const THEME_TOKENS = [
  "--accent",
  "--bg",
  "--panel",
  "--panel-2",
  "--panel-3",
  "--screen",
  "--line",
  "--line-2",
  "--text",
  "--muted",
  "--faint",
  "--gold",
  "--gold-hi",
  "--gold-dk",
  "--gold-text",
  "--cyan",
  "--cyan-hi",
  "--cyan-dk",
  "--green",
  "--yellow",
  "--red",
  "--red-text",
  "--link",
  "--gray",
  "--purple",
  "--orange",
  "--on-color",
] as const;

/** Functions a value may call: colours, gradients, var() and the filter functions. Nothing that loads or reads anything. */
export const CSS_FUNCTIONS = [
  "rgb",
  "rgba",
  "hsl",
  "hsla",
  "hwb",
  "lab",
  "lch",
  "oklab",
  "oklch",
  "color",
  "color-mix",
  "linear-gradient",
  "radial-gradient",
  "conic-gradient",
  "repeating-linear-gradient",
  "repeating-radial-gradient",
  "repeating-conic-gradient",
  "var",
  "blur",
  "brightness",
  "contrast",
  "grayscale",
  "hue-rotate",
  "saturate",
  "sepia",
  "opacity",
] as const;

/** Fonts a card may use, by the name you write; the stacks are the dashboard's own (no web fonts, nothing loaded). */
export const CARD_FONTS: Record<string, string> = {
  silkscreen: '"Silkscreen", monospace',
  monospace: 'ui-monospace, "SF Mono", Menlo, Consolas, monospace',
  "sans-serif": 'system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", sans-serif',
  serif: 'ui-serif, Georgia, "Times New Roman", serif',
  "system-ui": "system-ui, sans-serif",
};

/** Where a property goes: the skin behind the content (clipped to the card) or the card element (text and corners). */
export type CssLayer = "skin" | "card";

type Limits = { px?: [number, number]; em?: [number, number]; pct?: [number, number]; num?: [number, number] };

interface PropRule {
  layer: CssLayer;
  /** extra limits for the numbers of this property */
  limits?: Limits;
  /** keywords only (lower case) */
  keywords?: readonly string[];
  check?: (value: string) => Reason | null;
}

export type Reason =
  | "too_long"
  | "too_many"
  | "syntax"
  | "unicode"
  | "chars"
  | "strings"
  | "escape"
  | "comment"
  | "block"
  | "important"
  | "network"
  | "script"
  | "escape_card"
  | "property"
  | "function"
  | "token"
  | "unit"
  | "range"
  | "font"
  | "keyword"
  | "shadow"
  | "unsupported"
  | "contrast";

export interface CssDecl {
  prop: string;
  /** the value as stored (lower case, single spaces) */
  value: string;
  /** what goes into style.setProperty (a font name becomes its stack) */
  apply: string;
  layer: CssLayer;
}
export interface DroppedDecl {
  /** the declaration as written (shortened) */
  text: string;
  reason: Reason;
  /** e.g. the property, function or token at fault */
  detail?: string;
}
export interface SanitizedCss {
  decls: CssDecl[];
  dropped: DroppedDecl[];
}

/** What the sanitiser asks the browser. Without CSS.supports (not a browser) nothing is kept: it fails closed. */
export interface CssEnv {
  supports: (prop: string, value: string) => boolean;
}
export const browserCssEnv = (): CssEnv => {
  const css = (globalThis as { CSS?: { supports?: (p: string, v: string) => boolean } }).CSS;
  return typeof css?.supports === "function" ? { supports: (p, v) => css.supports!(p, v) } : { supports: () => false };
};
/** Structure only, for code without a browser (layout and preset import): the full check runs again when a card renders. */
export const STRUCTURAL_ENV: CssEnv = { supports: () => true };

// ----------------------------------------------------------------- forbidden
/**
 * Tokens that are never allowed anywhere in a card's CSS, checked before anything else. The same list is in
 * CardStyle.java. Most of them cannot get past the character whitelist anyway; they are listed so the user
 * learns why, and so the server rejects them even from a client that skips the sanitiser.
 */
export const FORBIDDEN: readonly { re: RegExp; reason: Reason }[] = [
  { re: /\\/, reason: "escape" },
  { re: /\/\*|\*\//, reason: "comment" },
  { re: /[{}@]/, reason: "block" },
  { re: /!/, reason: "important" },
  { re: /url\s*\(|image-set|image\s*\(|src\s*\(|element\s*\(|cross-fade\s*\(|paint\s*\(/i, reason: "network" },
  { re: /attr\s*\(|expression|-moz-binding|behaviou?r|javascript\s*:/i, reason: "script" },
];

/** The first forbidden token in a text, or null. */
export function forbiddenIn(text: string): Reason | null {
  for (const f of FORBIDDEN) if (f.re.test(text)) return f.reason;
  return null;
}

/** Plain printable ASCII and line breaks: no look-alike letters, no invisible or direction-changing characters. */
const ASCII = /^[\x20-\x7e\t\n\r]*$/;
/** Characters a value may contain. Quotes, `;`, `:`, `!`, `*`, `\`, `<`, `>`, `_` and the like cannot appear. */
const VALUE_CHARS = /^[a-z0-9 #%.,()+\-/]*$/;
const PROP_NAME = /^[a-z][a-z-]{0,31}$/;

/** Properties that would move a card's content out of the card, cover other UI or take the pointer: never allowed. */
const ESCAPES = new Set(["position", "top", "right", "bottom", "left", "z-index", "translate", "rotate", "scale", "content", "pointer-events", "cursor", "width", "height", "zoom", "float", "contain", "will-change", "isolation"]);
const ESCAPE_PREFIX = /^(inset|margin|transform|outline|overflow|min-|max-)/;

const lengths = (px: number, em: number): Limits => ({ px: [-px, px], em: [-em, em] });
const BORDER: PropRule = { layer: "skin", limits: { px: [0, 8], em: [0, 0.5] } };

/** The whitelist. Surface properties paint the skin; text properties and the radius go on the card element. */
const PROPS: Record<string, PropRule> = {
  background: { layer: "skin" },
  "background-color": { layer: "skin" },
  "background-image": { layer: "skin" },
  "background-size": { layer: "skin" },
  "background-position": { layer: "skin" },
  "background-repeat": { layer: "skin" },
  "background-blend-mode": { layer: "skin" },
  border: BORDER,
  "border-top": BORDER,
  "border-right": BORDER,
  "border-bottom": BORDER,
  "border-left": BORDER,
  "border-width": BORDER,
  "border-color": { layer: "skin" },
  "border-style": { layer: "skin" },
  "box-shadow": { layer: "skin", limits: lengths(64, 4), check: insetOnly },
  opacity: { layer: "skin", limits: { num: [0, 1], pct: [0, 100] } },
  filter: { layer: "skin", check: filterCheck },
  "border-radius": { layer: "card", limits: { px: [0, 32], em: [0, 2], pct: [0, 12] } },
  color: { layer: "card" },
  "font-family": { layer: "card" },
  "font-weight": { layer: "card", keywords: ["normal", "bold", "bolder", "lighter", "100", "200", "300", "400", "500", "600", "700", "800", "900"] },
  "font-style": { layer: "card", keywords: ["normal", "italic"] },
  "letter-spacing": { layer: "card", limits: { px: [-1, 6], em: [-0.06, 0.4] }, check: (v) => (/%/.test(v) ? "unit" : null) },
  "text-transform": { layer: "card", keywords: ["none", "uppercase", "lowercase", "capitalize"] },
  "text-shadow": { layer: "card", limits: lengths(6, 0.4), check: (v) => (splitTop(v).length > 2 ? "range" : null) },
};
/** Property names on the whitelist (CardStyle.java has the same list). */
export const CSS_PROPERTIES = Object.keys(PROPS);

const UNITS = new Set(["", "px", "em", "rem", "%", "deg", "turn", "rad", "grad"]);
/** Limits for every number, whatever the property. */
const GENERAL: Limits = { px: [-256, 256], em: [-16, 16], pct: [-1000, 1000], num: [-10000, 10000] };
/** A number with its unit, at a token boundary (not inside `display-p3`, `#1e3a5f` or `--panel-2`). */
const NUMBER = /(?<![a-z0-9#._-])([+-]?(?:\d+(?:\.\d+)?|\.\d+))(e[+-]?\d+)?([a-z%]*)/g;

/** Splits at commas outside parentheses (layers of a shadow or background). */
export function splitTop(value: string, sep = ","): string[] {
  const out: string[] = [];
  let depth = 0;
  let start = 0;
  for (let i = 0; i < value.length; i++) {
    const c = value[i];
    if (c === "(") depth++;
    else if (c === ")") depth--;
    else if (c === sep && depth === 0) {
      out.push(value.slice(start, i).trim());
      start = i + 1;
    }
  }
  out.push(value.slice(start).trim());
  return out;
}

function insetOnly(value: string): Reason | null {
  if (value === "none") return null;
  const layers = splitTop(value);
  if (layers.length > 4) return "range";
  return layers.every((l) => /(^|\s)inset(\s|$)/.test(l)) ? null : "shadow";
}

/** Filters of the skin: only gentle ones, so the background keeps its brightness (no invert, no drop-shadow). */
const FILTERS: Record<string, Limits> = {
  blur: { px: [0, 12], em: [0, 0.75] },
  brightness: { num: [0.7, 1.3], pct: [70, 130] },
  contrast: { num: [0.7, 1.3], pct: [70, 130] },
  grayscale: { num: [0, 1], pct: [0, 100] },
  "hue-rotate": {},
  saturate: { num: [0, 3], pct: [0, 300] },
  sepia: { num: [0, 1], pct: [0, 100] },
  opacity: { num: [0, 1], pct: [0, 100] },
};
function filterCheck(value: string): Reason | null {
  if (value === "none") return null;
  const parts = value.match(/[a-z-]+\([^()]*\)|\S+/g) ?? [];
  if (parts.length > 6) return "range";
  for (const p of parts) {
    const m = /^([a-z-]+)\(([^()]*)\)$/.exec(p);
    if (!m || !Object.hasOwn(FILTERS, m[1])) return "function";
    if (numbersProblem(m[2], FILTERS[m[1]])) return "range";
  }
  return null;
}

function inRange(n: number, r: [number, number] | undefined) {
  return !r || (n >= r[0] && n <= r[1]);
}

/** Checks every number of a value: allowed units, no exponents, within the limits. */
function numbersProblem(value: string, limits?: Limits): Reason | null {
  for (const m of value.matchAll(NUMBER)) {
    if (m[2]) return "range";
    const n = Number(m[1]);
    const unit = m[3];
    if (!UNITS.has(unit)) return "unit";
    const key = unit === "px" ? "px" : unit === "em" || unit === "rem" ? "em" : unit === "%" ? "pct" : unit === "" ? "num" : null;
    if (!key) continue;
    if (!Number.isFinite(n) || !inRange(n, GENERAL[key]) || !inRange(n, limits?.[key])) return "range";
  }
  return null;
}

/** Functions and parentheses of a value: only whitelisted functions, balanced, not nested deeper than 4. */
function functionsProblem(value: string): { reason: Reason; detail?: string } | null {
  let depth = 0;
  for (let i = 0; i < value.length; i++) {
    const c = value[i];
    if (c === "(") {
      const name = /[a-z-]*$/.exec(value.slice(0, i))![0];
      if (!(CSS_FUNCTIONS as readonly string[]).includes(name)) return { reason: "function", detail: name ? `${name}()` : "(" };
      if (++depth > 4) return { reason: "function", detail: "nesting" };
    } else if (c === ")" && --depth < 0) return { reason: "syntax" };
  }
  return depth === 0 ? null : { reason: "syntax" };
}

/** var(--token) only for the theme tokens; a fallback is checked like the rest of the value. */
function tokensProblem(value: string): { reason: Reason; detail?: string } | null {
  const calls = value.split("var(").length - 1;
  const named = [...value.matchAll(/var\(\s*(--[a-z0-9-]+)\s*[,)]/g)];
  if (named.length !== calls) return { reason: "token", detail: "var()" };
  for (const m of named) if (!(THEME_TOKENS as readonly string[]).includes(m[1])) return { reason: "token", detail: m[1] };
  return null;
}

const shorten = (s: string) => (s.length > 80 ? `${s.slice(0, 79)}…` : s);

function checkDecl(raw: string, env: CssEnv): CssDecl | DroppedDecl {
  const text = shorten(raw.replace(/\s+/g, " ").trim());
  const fail = (reason: Reason, detail?: string): DroppedDecl => (detail ? { text, reason, detail } : { text, reason });
  if (!ASCII.test(raw)) return fail("unicode");
  const bad = forbiddenIn(raw);
  if (bad) return fail(bad);
  if (/[<>]/.test(raw)) return fail("chars");
  const colon = raw.indexOf(":");
  if (colon < 0) return fail("syntax");
  const prop = raw.slice(0, colon).trim().toLowerCase();
  const value = raw.slice(colon + 1).replace(/\s+/g, " ").trim().toLowerCase();
  // custom properties would redefine the theme for the whole card
  if (prop.startsWith("--")) return fail("property", prop);
  if (!PROP_NAME.test(prop) || !value) return fail("syntax");
  if (ESCAPES.has(prop) || ESCAPE_PREFIX.test(prop)) return fail("escape_card", prop);
  const rule = Object.hasOwn(PROPS, prop) ? PROPS[prop] : undefined;
  if (!rule) return fail("property", prop);
  if (prop === "font-family") {
    const name = value.replace(/^(["'])([a-z -]+)\1$/, "$2").trim();
    return Object.hasOwn(CARD_FONTS, name) ? { prop, value: name, apply: CARD_FONTS[name], layer: rule.layer } : fail("font", name);
  }
  if (/["']/.test(value)) return fail("strings");
  // two declarations without the `;` between them
  if (value.includes(":")) return fail("syntax", "missing ;");
  if (!VALUE_CHARS.test(value)) return fail("chars");
  if (value.length > 400) return fail("too_long");
  const fn = functionsProblem(value);
  if (fn) return fail(fn.reason, fn.detail);
  const tok = tokensProblem(value);
  if (tok) return fail(tok.reason, tok.detail);
  // a theme token outside var() is not a value
  if (/--/.test(value.replace(/var\(\s*--[a-z0-9-]+/g, "var("))) return fail("token");
  if (rule.keywords && !rule.keywords.includes(value)) return fail("keyword", value);
  const num = numbersProblem(value, rule.limits);
  if (num) return fail(num);
  const own = rule.check?.(value);
  if (own) return fail(own);
  if (!env.supports(prop, value)) return fail("unsupported");
  return { prop, value, apply: value, layer: rule.layer };
}

/**
 * The declarations of a card's CSS that may be applied, and the ones dropped with the reason. Never throws. An input
 * longer than {@link MAX_CARD_CSS} is dropped whole: cutting it could change what it means.
 */
export function sanitizeCardCss(input: unknown, env: CssEnv = browserCssEnv()): SanitizedCss {
  const out: SanitizedCss = { decls: [], dropped: [] };
  if (typeof input !== "string" || !input.trim()) return out;
  if (input.length > MAX_CARD_CSS) {
    out.dropped.push({ text: `${input.length} characters`, reason: "too_long" });
    return out;
  }
  // without quotes, escapes and comments (all refused), a `;` can only end a declaration
  for (const raw of input.split(";")) {
    if (!raw.trim()) continue;
    const r = checkDecl(raw, env);
    if ("prop" in r) {
      if (out.decls.length >= MAX_DECLARATIONS) out.dropped.push({ text: shorten(raw.trim()), reason: "too_many" });
      else out.decls.push(r);
    } else out.dropped.push(r);
  }
  return out;
}

/** The kept declarations as text, one per line: what a layout, a preset and a share code store. */
export const serializeCss = (decls: readonly CssDecl[]) => decls.map((d) => `${d.prop}: ${d.value};`).join("\n");

/** Sanitised CSS as text (structure only, see STRUCTURAL_ENV), or undefined when nothing is left. */
export function cleanCardCss(input: unknown, env: CssEnv = STRUCTURAL_ENV): string | undefined {
  const s = serializeCss(sanitizeCardCss(input, env).decls);
  return s || undefined;
}

/**
 * The server's check (CardStyle.java, used by LayoutValidator.java): the reason a card's CSS is refused, or null. No
 * forbidden token, plain ASCII of the value characters, only `property: value` declarations of whitelisted properties,
 * whitelisted functions and theme tokens. Number ranges and CSS.supports stay with the browser.
 */
export function serverCssProblem(css: string): string | null {
  if (css.length > MAX_CARD_CSS) return "too_long";
  const bad = forbiddenIn(css);
  if (bad) return bad;
  if (!/^[A-Za-z0-9 \t\n\r#%.,()+\-/:;]*$/.test(css)) return "chars";
  const lower = css.toLowerCase();
  for (const m of lower.matchAll(/([a-z-]*)\s*\(/g)) if (!(CSS_FUNCTIONS as readonly string[]).includes(m[1])) return "function";
  for (const m of lower.matchAll(/--[a-z0-9-]+/g)) if (!(THEME_TOKENS as readonly string[]).includes(m[0])) return "token";
  for (const decl of lower.split(";")) {
    if (!decl.trim()) continue;
    const m = /^\s*([a-z-]+)\s*:\s*\S/.exec(decl);
    if (!m || !CSS_PROPERTIES.includes(m[1]) || decl.indexOf(":") !== decl.lastIndexOf(":")) return "property";
  }
  return null;
}

// --------------------------------------------------------------- readability
// A card with its own colour keeps its text readable in both themes: it takes light or dark text (the tokens of the
// dark or the light theme) and, for colours in the middle, a veil of the panel colour that is just strong enough.
export type Rgb = [number, number, number];
export type Rgba = [number, number, number, number];
export type Tone = "dark" | "light";

/** The text tokens of each tone (styles.css, :root and [data-theme="light"]). */
export const TONES: Record<Tone, { text: Rgb; muted: Rgb; panel: Rgb }> = {
  dark: { text: [0xe7, 0xec, 0xf1], muted: [0x8d, 0x99, 0xa6], panel: [0x16, 0x1b, 0x21] },
  light: { text: [0x15, 0x1a, 0x20], muted: [0x58, 0x64, 0x70], panel: [0xff, 0xff, 0xff] },
};
/** Body text at least 7:1 (AAA), muted labels at least 4.5:1 (AA). */
const MIN_TEXT = 7;
const MIN_MUTED = 4.5;

export function parseHex(hex: string): Rgb | null {
  if (!isHexColor(hex)) return null;
  const n = parseInt(hex.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

/** A computed colour from getComputedStyle: rgb(), rgba() or color(srgb …); other spaces are not read (null). */
export function parseCssColor(s: string): Rgba | null {
  const t = s.trim().toLowerCase();
  let m = /^rgba?\(\s*([\d.]+)[\s,]+([\d.]+)[\s,]+([\d.]+)\s*(?:[,/]\s*([\d.]+)(%?))?\s*\)$/.exec(t);
  if (m) return [Number(m[1]), Number(m[2]), Number(m[3]), m[4] === undefined ? 1 : Number(m[4]) / (m[5] ? 100 : 1)];
  m = /^color\(srgb\s+([\d.e-]+)\s+([\d.e-]+)\s+([\d.e-]+)\s*(?:\/\s*([\d.]+)(%?))?\s*\)$/.exec(t);
  if (m) return [Number(m[1]) * 255, Number(m[2]) * 255, Number(m[3]) * 255, m[4] === undefined ? 1 : Number(m[4]) / (m[5] ? 100 : 1)];
  if (t === "transparent") return [0, 0, 0, 0];
  return null;
}

/** All colours named in a computed value (gradients list theirs as rgb()/color()). */
export function colorsIn(s: string): Rgba[] {
  return [...s.matchAll(/rgba?\([^()]*\)|color\(srgb[^()]*\)/g)].map((m) => parseCssColor(m[0])).filter((c): c is Rgba => !!c);
}

const channel = (v: number) => {
  const c = Math.max(0, Math.min(255, v)) / 255;
  return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
};
export const luminance = (c: Rgb | Rgba) => 0.2126 * channel(c[0]) + 0.7152 * channel(c[1]) + 0.0722 * channel(c[2]);
export function contrast(a: Rgb | Rgba, b: Rgb | Rgba) {
  const la = luminance(a);
  const lb = luminance(b);
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}
/** `a` with `t` (0–1) of `b` over it. */
export const mix = (a: Rgb | Rgba, b: Rgb | Rgba, t: number): Rgb => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];

export interface ReadableLook {
  tone: Tone;
  /** strength of the panel-coloured veil over the background, 0–1 */
  scrim: number;
}

/** The tone and the weakest veil that keep the tone's text and labels readable on every one of these colours. */
export function readableLook(colors: readonly (Rgb | Rgba)[]): ReadableLook {
  const need = (tone: Tone) => {
    const t = TONES[tone];
    for (let s = 0; s <= 20; s++) {
      const a = s / 20;
      if (colors.every((c) => contrast(t.text, mix(c, t.panel, a)) >= MIN_TEXT && contrast(t.muted, mix(c, t.panel, a)) >= MIN_MUTED)) return a;
    }
    return 1;
  };
  const dark = need("dark");
  const light = need("light");
  if (dark !== light) return dark < light ? { tone: "dark", scrim: dark } : { tone: "light", scrim: light };
  const avg = (tone: Tone) => colors.reduce((n, c) => n + contrast(TONES[tone].text, c), 0);
  return { tone: avg("dark") >= avg("light") ? "dark" : "light", scrim: dark };
}

/** The look of a card with its own background colour (`bg: "custom"`). */
export function customColorLook(hex: string | undefined): ReadableLook | null {
  const rgb = hex ? parseHex(hex) : null;
  return rgb ? readableLook([rgb]) : null;
}

/** The veil as a CSS colour for the skin (numbers only, made here). */
export const scrimColor = (look: ReadableLook) => {
  const [r, g, b] = TONES[look.tone].panel;
  return `rgb(${r} ${g} ${b} / ${Math.round(look.scrim * 100)}%)`;
};

/** What the editor says about a dropped declaration. */
export const REASON_TEXT: Record<Reason, string> = {
  too_long: `Longer than ${MAX_CARD_CSS} characters: nothing is applied.`,
  too_many: `More than ${MAX_DECLARATIONS} declarations.`,
  syntax: "Not a declaration: write property: value;",
  unicode: "Only plain ASCII characters.",
  chars: "Character not allowed in a value.",
  strings: "No quotes or strings.",
  escape: "No backslash escapes.",
  comment: "No comments.",
  block: "Only declarations: no selectors, blocks or @-rules.",
  important: "No !important (not needed: your styles already win).",
  network: "Nothing that loads anything: no url(), image-set() or similar.",
  script: "Nothing that runs or reads anything (attr(), expression, behavior, javascript:).",
  escape_card: "Could move or cover things outside the card.",
  property: "This property is not on the list.",
  function: "Function not allowed.",
  token: "Only the dashboard's theme tokens, e.g. var(--accent).",
  unit: "Unit not allowed: use px, em, rem, % or deg.",
  range: "Value out of range.",
  font: "Font not on the list: silkscreen, monospace, sans-serif, serif, system-ui.",
  keyword: "Value not allowed for this property.",
  shadow: "Only inset shadows: a shadow may not paint outside the card.",
  unsupported: "Not valid CSS for this property.",
  contrast: "Text colour too faint or too close to the background.",
};
