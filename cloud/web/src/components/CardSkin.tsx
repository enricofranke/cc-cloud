// A card's design in the browser (lib/cardstyle.ts has the rules): the background option and the custom CSS.
//
// A designed card gets a skin: a layer behind its content, inside a clip box at the card's border edge that clips all its
// painting (overflow: clip, contain: paint) and never takes the pointer. Surface properties go on the skin; text
// properties and the corner radius go on the card element itself. Every declaration is applied with
// style.setProperty() and removed again on the next run; nothing is written as stylesheet text or a style attribute.
// The custom CSS is sanitised each time it changes, here in the browser with CSS.supports(), whatever the layout came
// from. A solid background (the custom colour, or one the CSS sets) picks light or dark text and, when needed, a veil
// over the colour; a text colour that would be unreadable on the card is dropped.
//
// Parts (title bar, gauge fill, buttons …) need no skin: their kept declarations go onto the card element as custom
// properties (--cp-<part>-<property>), and its data-cp attribute lists them for the fixed rules in styles.css. Both are
// made from whitelisted names only. A part's text colour that is unreadable where the part's text actually sits is
// dropped too.
import { signal } from "@preact/signals";
import type { RefObject } from "preact";
import { useLayoutEffect, useRef } from "preact/hooks";
import type { ResolvedCard } from "../lib/layout.ts";
import {
  browserCssEnv,
  CARD_PARTS,
  colorsIn,
  contrast,
  customColorLook,
  mix,
  parseCssColor,
  parseHex,
  readableLook,
  sanitizeCardCss,
  sanitizePartCss,
  scrimColor,
  TONES,
  type CardPart,
  type CardParts,
  type CssDecl,
  type DroppedDecl,
  type ReadableLook,
  type Rgb,
  type SanitizedCss,
} from "../lib/cardstyle.ts";
import { theme } from "../lib/ui.ts";

const EMPTY: SanitizedCss = { decls: [], dropped: [] };
const cache = new Map<string, SanitizedCss>();

/** The card's CSS, sanitised in this browser (the same text gives the same result, so it is kept for a while). */
export function sanitizedCss(css: string | undefined): SanitizedCss {
  if (!css) return EMPTY;
  let s = cache.get(css);
  if (!s) {
    s = sanitizeCardCss(css, browserCssEnv());
    if (cache.size >= 256) cache.clear();
    cache.set(css, s);
  }
  return s;
}

const partCache = new Map<string, SanitizedCss>();
/** One part's CSS, sanitised in this browser (cached like {@link sanitizedCss}). */
export function sanitizedPart(part: CardPart, css: string | undefined): SanitizedCss {
  if (!css) return EMPTY;
  const key = `${part}\n${css}`;
  let s = partCache.get(key);
  if (!s) {
    s = sanitizePartCss(part, css, browserCssEnv());
    if (partCache.size >= 256) partCache.clear();
    partCache.set(key, s);
  }
  return s;
}

/** The kept declarations of a card's parts, in the order of CARD_PARTS. */
function partDecls(parts: CardParts | undefined): [CardPart, CssDecl[]][] {
  if (!parts) return [];
  const out: [CardPart, CssDecl[]][] = [];
  for (const part of CARD_PARTS) {
    const decls = sanitizedPart(part, parts[part]).decls;
    if (decls.length) out.push([part, decls]);
  }
  return out;
}

/** Where the text of a part sits, for the readability check of its `color` (the rules in styles.css style the same). */
const TEXT_OF: Partial<Record<CardPart, string>> = {
  title: ":scope > .card-head .card-title",
  label: ".kv-key, .w2-label, .gauge-label, .bignum-label, .row-label",
  value: ".kv-val, .kv-text, .w2-value, .chart-now, .gauge-value, .bignum-value, .row-value, .ind-state",
  button: ".wcell .pxbtn",
};

/** Declarations a card dropped while it was drawn (unreadable text colour), by card key: the card editor shows them. */
export const cardStyleNotes = signal<Record<string, DroppedDecl[]>>({});
function note(key: string, drops: DroppedDecl[]) {
  const before = cardStyleNotes.value[key];
  if ((before?.length ?? 0) === 0 && drops.length === 0) return;
  if (before && JSON.stringify(before) === JSON.stringify(drops)) return;
  const next = { ...cardStyleNotes.value };
  if (drops.length) next[key] = drops;
  else delete next[key];
  cardStyleNotes.value = next;
}

const SURFACE = new Set(["background", "background-color", "background-image", "opacity"]);

function rootHex(name: string): Rgb | null {
  return parseHex(getComputedStyle(document.documentElement).getPropertyValue(name).trim().toLowerCase());
}

/** The colours the skin paints, flattened over what lies below them (for the contrast checks). */
function skinColors(skin: HTMLElement): Rgb[] {
  const cs = getComputedStyle(skin);
  const panel = rootHex("--panel") ?? TONES.dark.panel;
  const page = rootHex("--bg") ?? panel;
  const fill = parseCssColor(cs.backgroundColor);
  const base: Rgb = fill && fill[3] > 0 ? mix(panel, fill, fill[3]) : panel;
  const layers = colorsIn(cs.backgroundImage)
    .filter((c) => c[3] >= 0.15)
    .map((c) => mix(base, c, c[3]));
  const opacity = Number(cs.opacity);
  return [base, ...layers].map((c) => (opacity < 1 ? mix(page, c, Math.max(0, opacity)) : c));
}

/** The colours behind an element inside the card: the first solid background up the tree, with the layers above it. */
function colorsBehind(node: Element, card: HTMLElement, cardColors: Rgb[]): Rgb[] {
  const over: Rgb[] = [];
  const layer = (base: Rgb[]) => (over.length ? base.flatMap((b) => [b, ...over.map((c) => mix(b, c, 0.5))]) : base);
  for (let n: Element | null = node; n && n !== card; n = n.parentElement) {
    const cs = getComputedStyle(n);
    for (const c of colorsIn(cs.backgroundImage)) if (c[3] >= 0.15) over.push([c[0], c[1], c[2]]);
    const fill = parseCssColor(cs.backgroundColor);
    if (fill && fill[3] >= 0.6) return layer([mix(cardColors[0], fill, fill[3])]);
    if (fill && fill[3] >= 0.15) over.push([fill[0], fill[1], fill[2]]);
  }
  return layer(cardColors);
}

/** The alpha of a computed colour in any colour space (`… / 0.4)`), 1 when it has none. */
function alphaOf(s: string): number {
  const rgba = parseCssColor(s);
  if (rgba) return rgba[3];
  const m = /\/\s*([\d.]+)(%?)\s*\)$/.exec(s.trim());
  return m ? Number(m[1]) / (m[2] ? 100 : 1) : 1;
}

/**
 * Applies a card's design to its element and skin, and undoes it when the design changes. `on`: the card has a skin
 * (a background option or custom CSS). `content`: changes when the card's widgets are drawn (for the parts' check).
 */
export function useCardSkin(card: ResolvedCard, cardRef: RefObject<HTMLElement>, content: unknown = null) {
  const css = sanitizedCss(card.css);
  const parts = partDecls(card.parts);
  const on = !!card.bg || css.decls.length > 0;
  const skinRef = useRef<HTMLDivElement>(null);
  const applied = useRef<{ el: HTMLElement; prop: string }[]>([]);
  const mode = theme.value;
  const partsKey = parts.length ? JSON.stringify(card.parts) : "";
  useLayoutEffect(() => {
    const el = cardRef.current;
    const skin = skinRef.current;
    for (const a of applied.current) a.el.style.removeProperty(a.prop);
    applied.current = [];
    if (el) {
      delete el.dataset.tone;
      delete el.dataset.cp;
    }
    if (!el) return note(card.key, []);
    const set = (target: HTMLElement, prop: string, value: string) => {
      target.style.setProperty(prop, value);
      applied.current.push({ el: target, prop });
    };
    const drops: DroppedDecl[] = [];
    const skinText = on && skin ? skinLook(el, skin, set, drops) : null;
    if (parts.length) applyParts(el, parts, set, drops, skinText);
    note(card.key, drops);
  }, [card.key, card.css, card.bg, card.bgColor, card.color, partsKey, mode, on, content]);
  return { on, ref: skinRef };

  /** The skin and the card's own CSS; returns the colours the skin paints as text sees them (worked out when asked). */
  function skinLook(el: HTMLElement, skin: HTMLElement, set: (target: HTMLElement, prop: string, value: string) => void, drops: DroppedDecl[]): () => Rgb[] {
    for (const d of css.decls) set(d.layer === "skin" ? skin : el, d.prop, d.apply);
    let look: ReadableLook | null = null;
    if (card.bg === "custom" && card.bgColor) {
      set(skin, "--skin-c", card.bgColor);
      look = customColorLook(card.bgColor);
    }
    const ownSurface = css.decls.some((d) => SURFACE.has(d.prop));
    const ownColor = css.decls.find((d) => d.prop === "color");
    let colors: Rgb[] | null = null;
    if (ownSurface || ownColor) {
      colors = skinColors(skin);
      if (ownSurface) look = readableLook(colors);
    }
    if (look) {
      el.dataset.tone = look.tone;
      if (look.scrim > 0) set(skin, "--skin-scrim", scrimColor(look));
    }
    const under = () => {
      colors ??= skinColors(skin);
      return look ? colors.map((c) => mix(c, TONES[look!.tone].panel, look!.scrim)) : colors;
    };
    if (ownColor) {
      const fg = getComputedStyle(el).color;
      const rgb = parseCssColor(fg);
      const low = alphaOf(fg) < 0.6 || (rgb && Math.min(...under().map((c) => contrast(rgb, c))) < 3);
      if (low) {
        el.style.removeProperty("color");
        drops.push({ text: `color: ${ownColor.value}`, reason: "contrast" });
      }
    }
    return under;
  }
}

/**
 * Sets the parts' declarations as custom properties of the card and lists them in data-cp. A text colour of a part
 * that is too faint or too close to the background where that text sits is taken out again.
 */
function applyParts(el: HTMLElement, parts: [CardPart, CssDecl[]][], set: (target: HTMLElement, prop: string, value: string) => void, drops: DroppedDecl[], skin: (() => Rgb[]) | null) {
  const tokens: string[] = [];
  for (const [part, decls] of parts)
    for (const d of decls) {
      set(el, `--cp-${part}-${d.prop}`, d.apply);
      tokens.push(`${part}:${d.prop}`);
    }
  el.dataset.cp = tokens.join(" ");
  let cardColors: Rgb[] | null = null;
  for (const [part, decls] of parts) {
    const color = decls.find((d) => d.prop === "color");
    const where = TEXT_OF[part];
    if (!color || !where) continue;
    cardColors ??= skin ? skin() : [parseCssColor(getComputedStyle(el).backgroundColor) ?? TONES.dark.panel].map((c) => [c[0], c[1], c[2]] as Rgb);
    const texts = [...el.querySelectorAll(where)].filter((t) => !t.closest(".backdrop, .zone-warn, .zone-crit")).slice(0, 16);
    const low = texts.some((t) => {
      const fg = getComputedStyle(t).color;
      const rgb = parseCssColor(fg);
      return alphaOf(fg) < 0.6 || (!!rgb && Math.min(...colorsBehind(t, el, cardColors!).map((c) => contrast(rgb, c))) < 3);
    });
    if (low) {
      el.style.removeProperty(`--cp-${part}-color`);
      el.dataset.cp = (el.dataset.cp ?? "").split(" ").filter((t) => t !== `${part}:color`).join(" ");
      drops.push({ text: `color: ${color.value}`, reason: "contrast", part });
    }
  }
}
