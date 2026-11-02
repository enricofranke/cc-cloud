// A card's design in the browser (lib/cardstyle.ts has the rules): the background option and the custom CSS.
//
// A designed card gets a skin: a layer behind its content, inside a clip box at the card's border edge that clips all its
// painting (overflow: clip, contain: paint) and never takes the pointer. Surface properties go on the skin; text
// properties and the corner radius go on the card element itself. Every declaration is applied with
// style.setProperty() and removed again on the next run; nothing is written as stylesheet text or a style attribute.
// The custom CSS is sanitised each time it changes, here in the browser with CSS.supports(), whatever the layout came
// from. A solid background (the custom colour, or one the CSS sets) picks light or dark text and, when needed, a veil
// over the colour; a text colour that would be unreadable on the card is dropped.
import { signal } from "@preact/signals";
import type { RefObject } from "preact";
import { useLayoutEffect, useRef } from "preact/hooks";
import type { ResolvedCard } from "../lib/layout.ts";
import {
  browserCssEnv,
  colorsIn,
  contrast,
  customColorLook,
  mix,
  parseCssColor,
  parseHex,
  readableLook,
  sanitizeCardCss,
  scrimColor,
  TONES,
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

/** The alpha of a computed colour in any colour space (`… / 0.4)`), 1 when it has none. */
function alphaOf(s: string): number {
  const rgba = parseCssColor(s);
  if (rgba) return rgba[3];
  const m = /\/\s*([\d.]+)(%?)\s*\)$/.exec(s.trim());
  return m ? Number(m[1]) / (m[2] ? 100 : 1) : 1;
}

/**
 * Applies a card's design to its element and skin, and undoes it when the design changes. `on`: the card has a skin
 * (a background option or custom CSS).
 */
export function useCardSkin(card: ResolvedCard, cardRef: RefObject<HTMLElement>) {
  const css = sanitizedCss(card.css);
  const on = !!card.bg || css.decls.length > 0;
  const skinRef = useRef<HTMLDivElement>(null);
  const applied = useRef<{ el: HTMLElement; prop: string }[]>([]);
  const mode = theme.value;
  useLayoutEffect(() => {
    const el = cardRef.current;
    const skin = skinRef.current;
    for (const a of applied.current) a.el.style.removeProperty(a.prop);
    applied.current = [];
    if (el) delete el.dataset.tone;
    if (!el || !skin || !on) return note(card.key, []);
    const set = (target: HTMLElement, prop: string, value: string) => {
      target.style.setProperty(prop, value);
      applied.current.push({ el: target, prop });
    };
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
    const drops: DroppedDecl[] = [];
    if (ownColor && colors) {
      const fg = getComputedStyle(el).color;
      const rgb = parseCssColor(fg);
      const under = look ? colors.map((c) => mix(c, TONES[look!.tone].panel, look!.scrim)) : colors;
      const low = alphaOf(fg) < 0.6 || (rgb && Math.min(...under.map((c) => contrast(rgb, c))) < 3);
      if (low) {
        el.style.removeProperty("color");
        drops.push({ text: `color: ${ownColor.value}`, reason: "contrast" });
      }
    }
    note(card.key, drops);
  }, [card.key, card.css, card.bg, card.bgColor, card.color, mode, on]);
  return { on, ref: skinRef };
}
