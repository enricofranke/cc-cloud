// Turns a preset and its filled slots into an ordinary v2 layout of views (`v:<id>`) in custom cards (`c:<id>`), on pages
// of their own. Nothing here writes: the result goes through the editor's history or conn.saveLayout, and through
// LayoutValidator.java like every other layout. Pure.
//
// The safety rules live here, because this is the one place every preset passes:
//  - An action widget (button, toggle, slider, …) keeps its source's label, colour and icon, whatever the preset says.
//    A button labelled "Lights" could be an emergency stop. The source's confirm and PIN stay: a view is the same widget.
//  - A widget is only shown as a type its source can really be shown as (VIEW_SOURCES). A view of a restricted section
//    only exists when this session sees that section, and it is still a view: the server sends viewers nothing for it.
import { CARD_CHROME_H, COLS, CUSTOM_PREFIX, DISPLAYS_FOR, MAX_CARD_H, MAX_LAYOUT_BYTES, MAX_PAGES, MAX_WIDGET_H, VIEW_PREFIX, defaultWidgetH, defaultWidgetW, layoutSize, minWidgetH, minWidgetW, type CardPatch, type Display, type LayoutDoc, type LayoutPage, type WidgetPatch } from "../layout.ts";
import { bottom, compact, flowPack, placeAll, type GridItem } from "../grid.ts";
import type { Lang } from "../lang.ts";
import type { Uplink } from "../types.ts";
import { PRESET_LIMITS, textOf, type Preset, type PresetCard, type PresetPage, type PresetWidget } from "./format.ts";
import { matchSlots, pickWidgets, type MatchState, type PresetMatch, type SlotMatch, type SlotPicks } from "./match.ts";
import { ACTION_TYPES } from "./roles.ts";

export interface Expanded {
  /** only what is new: the preset's pages, cards and views */
  doc: LayoutDoc;
  pages: LayoutPage[];
  cards: number;
  widgets: number;
  /** preset pages that stayed empty on this base */
  emptyPages: number;
}

const MAX_SRC = 160;
const clamp = (v: number, min: number, max: number) => Math.max(min, Math.min(max, v));
const cap = (s: string, max: number) => (s.length > max ? `${s.slice(0, max - 1)}…` : s);

/** The next free key `<prefix><n>`; the numbers go on across a document, so two presets never meet. */
function keys(prefix: string, taken: Iterable<string>) {
  const used = new Set(taken);
  let n = 1;
  return () => {
    while (used.has(`${prefix}p${n}`)) n++;
    const key = `${prefix}p${n}`;
    used.add(key);
    return key;
  };
}

function pageId(title: string, taken: Set<string>) {
  const base = title.toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_|_$/g, "").slice(0, 24) || "preset";
  let id = base;
  for (let n = 2; taken.has(id); n++) id = `${base}_${n}`;
  taken.add(id);
  return id;
}

interface Draft {
  key: string;
  patch: CardPatch;
  box: GridItem & { placed: boolean };
  widgets: { key: string; patch: WidgetPatch; box: GridItem & { placed: boolean } }[];
}

function buildWidget(ref: PresetWidget, u: Uplink, source: Uplink["widgets"][number], card: string, lang: Lang, nextKey: () => string, explicit: boolean) {
  const as = ref.as ?? source.type;
  const src = `${u.uplinkId}/${source.id}`;
  if (src.length > MAX_SRC) return null;
  const offered = DISPLAYS_FOR[as];
  const display: Display | undefined = ref.display && offered?.includes(ref.display) ? ref.display : undefined;
  const action = ACTION_TYPES.has(as);
  const label = action ? "" : ref.labelFrom === "device" ? u.title : textOf(ref.label, lang);
  const w = clamp(ref.w ?? defaultWidgetW(as), minWidgetW(as), COLS);
  const h = clamp(ref.h ?? defaultWidgetH(as, display), minWidgetH(as, display), MAX_WIDGET_H);
  const placed = explicit && ref.x !== undefined && ref.y !== undefined;
  const key = nextKey();
  const patch: WidgetPatch = {
    card,
    src,
    as,
    ...(label ? { label: cap(label, PRESET_LIMITS.LABEL) } : {}),
    ...(display && display !== offered?.[0] ? { display } : {}),
    ...(!action && ref.color ? { color: ref.color } : {}),
    ...(!action && ref.icon ? { icon: ref.icon } : {}),
  };
  return { key, patch, box: { id: key, x: placed ? Math.min(ref.x!, COLS - w) : 0, y: placed ? ref.y! : 0, w, h, placed } };
}

function buildCard(card: PresetCard, device: Uplink | undefined, filled: Map<string, SlotMatch>, lang: Lang, cardKey: () => string, widgetKey: () => string, budget: { widgets: number }): Draft | null {
  const key = cardKey();
  const widgets: Draft["widgets"] = [];
  const used = new Set<string>();
  let first: Uplink | undefined;
  let single = true;
  for (const ref of card.widgets) {
    const pool = ref.slot === card.each && device ? [device] : filled.get(ref.slot)?.chosen ?? [];
    const before = widgets.length;
    for (const u of pool)
      for (const source of pickWidgets(u, ref, used)) {
        if (budget.widgets >= PRESET_LIMITS.EXPANDED_WIDGETS) break;
        const w = buildWidget(ref, u, source, key, lang, widgetKey, true);
        if (!w) continue;
        budget.widgets++;
        widgets.push(w);
        first ??= u;
      }
    if (widgets.length - before !== 1) single = false;
  }
  if (!widgets.length) return null;
  // an author's grid only fits when every reference became exactly one widget; otherwise the widgets flow
  const exact = single && widgets.every((w) => w.box.placed);
  const boxes = exact ? placeAll(widgets.map((w) => w.box), COLS) : flowPack(widgets.map((w) => ({ ...w.box, placed: false })), COLS);
  const at = new Map(boxes.map((b) => [b.id, b]));
  for (const w of widgets) {
    const b = at.get(w.key)!;
    w.box = { ...w.box, x: b.x, y: b.y, w: b.w, h: b.h };
    Object.assign(w.patch, { x: b.x, y: b.y, w: b.w, h: b.h });
  }
  const title = textOf(card.title, lang).replaceAll("{device}", device?.title ?? first?.title ?? "") || device?.title || first?.title || "Card";
  const h = clamp(Math.max(card.h ?? 0, CARD_CHROME_H + bottom(boxes)), 3, MAX_CARD_H);
  const placed = !device && card.x !== undefined && card.y !== undefined;
  return {
    key,
    patch: { title: cap(title, PRESET_LIMITS.TITLE), ...(card.color && card.color !== "gold" ? { color: card.color } : {}), ...(card.icon ? { icon: card.icon } : {}), ...(card.style === "compact" ? { style: "compact" } : {}) },
    box: { id: key, x: placed ? card.x! : 0, y: placed ? card.y! : 0, w: card.w, h, placed },
    widgets,
  };
}

function buildPage(page: PresetPage, filled: Map<string, SlotMatch>, lang: Lang, cardKey: () => string, widgetKey: () => string, budget: { widgets: number }) {
  const cards: Draft[] = [];
  for (const card of page.cards) {
    const devices = card.each ? filled.get(card.each)?.chosen ?? [] : [undefined];
    for (const device of devices) {
      if (cards.length >= PRESET_LIMITS.CARDS) break;
      const c = buildCard(card, device, filled, lang, cardKey, widgetKey, budget);
      if (c) cards.push(c);
    }
  }
  // cards fall out on a small base and grow on a big one: the grid closes the gaps and pushes overlaps apart
  const anyPlaced = cards.some((c) => c.box.placed);
  const boxes = compact(anyPlaced ? placeAll(cards.map((c) => c.box), COLS) : flowPack(cards.map((c) => c.box), COLS));
  const at = new Map(boxes.map((b) => [b.id, b]));
  for (const c of cards) {
    const b = at.get(c.key)!;
    Object.assign(c.patch, { x: b.x, y: b.y, w: b.w, h: b.h });
  }
  return cards;
}

/** The preset as layout entries, with keys and page ids that do not exist in `existing`. */
export function expandPreset(preset: Preset, slots: SlotMatch[], lang: Lang, existing?: LayoutDoc | null): Expanded {
  const filled = new Map(slots.map((s) => [s.slot.id, s]));
  const doc: LayoutDoc = { v: 2, pages: [], cards: {}, widgets: {} };
  const cardKey = keys(CUSTOM_PREFIX, Object.keys(existing?.cards ?? {}));
  const widgetKey = keys(VIEW_PREFIX, Object.keys(existing?.widgets ?? {}));
  const pageIds = new Set((existing?.pages ?? []).map((p) => p.id));
  const budget = { widgets: 0 };
  let emptyPages = 0;
  for (const page of preset.pages) {
    const cards = buildPage(page, filled, lang, cardKey, widgetKey, budget);
    if (!cards.length) {
      emptyPages++;
      continue;
    }
    const title = textOf(page.title, lang);
    const id = pageId(title, pageIds);
    doc.pages.push({ id, title: cap(title, PRESET_LIMITS.TITLE), ...(page.icon ? { icon: page.icon } : {}) });
    for (const c of cards) {
      doc.cards[c.key] = { page: id, ...c.patch };
      for (const w of c.widgets) doc.widgets[w.key] = w.patch;
    }
  }
  return { doc, pages: doc.pages, cards: Object.keys(doc.cards).length, widgets: Object.keys(doc.widgets).length, emptyPages };
}

/** What a preset does on this base: the slots, the state, and the layout it would add. */
export function matchPreset(preset: Preset, uplinks: Uplink[], lang: Lang, picks?: SlotPicks, existing?: LayoutDoc | null): PresetMatch & { expanded: Expanded } {
  const slots = matchSlots(preset, uplinks, picks);
  const expanded = expandPreset(preset, slots, lang, existing);
  const needed = slots.some((s) => s.missing > 0);
  const state: MatchState = needed || !expanded.widgets ? "missing" : slots.some((s) => !s.chosen.length) || expanded.emptyPages > 0 ? "partial" : "ready";
  const devices = new Set(Object.values(expanded.doc.widgets).map((w) => w.src!.slice(0, w.src!.indexOf("/"))));
  return { preset, state, slots, widgets: expanded.widgets, devices: devices.size, expanded };
}

export type RoomProblem = "too_many_pages" | "too_large" | "nothing";

/** Why this cannot be added to a layout, or null. `front`: the preset's pages become the first tabs (the empty dashboard). */
export function mergeLayout(base: LayoutDoc, add: Expanded, front = false): { doc: LayoutDoc } | { problem: RoomProblem; free?: number } {
  if (!add.pages.length) return { problem: "nothing" };
  if (base.pages.length + add.pages.length > MAX_PAGES) return { problem: "too_many_pages", free: Math.max(0, MAX_PAGES - base.pages.length) };
  const doc: LayoutDoc = {
    v: 2,
    pages: front ? [...add.pages, ...base.pages] : [...base.pages, ...add.pages],
    cards: { ...base.cards, ...add.doc.cards },
    widgets: { ...base.widgets, ...add.doc.widgets },
  };
  return layoutSize(doc) > MAX_LAYOUT_BYTES ? { problem: "too_large" } : { doc };
}

/**
 * The preset that suits this base best. How much of the base it puts to use counts most; a preset with required slots
 * gets a bonus for being made for what is there, so a single reactor gets Reactor control and a mixed base the overview.
 */
export function recommend<T extends { state: MatchState; devices: number; preset: Preset }>(matches: T[], uplinks: Uplink[]): T | undefined {
  const total = Math.max(1, uplinks.filter((u) => !u.blocked).length);
  const score = (m: T) => (m.devices / total) * 0.7 + (m.preset.slots.some((s) => s.min > 0) ? 0.3 : 0) + (m.state === "ready" ? 0.05 : 0);
  return matches.filter((m) => m.state !== "missing").reduce<T | undefined>((best, m) => (!best || score(m) > score(best) ? m : best), undefined);
}
