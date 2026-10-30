// Dashboard layout: pages → cards → widgets. Merges what the Lua scripts
// declare (pages, groups, opts.page/group/span/order/icon/color) with the
// per-mainframe layout document edited in the browser (docs/SPEC.md "Layout schema").
//
// v2: cards sit on a 12-column page grid and widgets on a 12-column grid
// inside their card, both with x/y/w/h in grid units (rows of 32 px incl. gap;
// rows grow when the content is taller). Widgets can move into other cards and
// into custom cards ("c:<id>"). v1 documents (order + span) are migrated on read.
// Pure functions only, so the editor logic is unit-testable.
import type { Uplink, Widget } from "./types.ts";
import { bottom, clampItem, compact, flowPack, freeSpot, insertItem, moveItem, placeAll, resizeItem, type GridItem } from "./grid.ts";

export const LAYOUT_VERSION = 2;
export const MAX_LAYOUT_BYTES = 256 * 1024;
/** Limits of a layout document (LayoutValidator.java): raised for big bases (docs/SPEC.md "Scaling"). */
export const MAX_LAYOUT_CARDS = 2048;
export const MAX_LAYOUT_WIDGETS = 8192;
export const MAX_LAYOUT_Y = 99_999;
export const MAX_PAGES = 12;
export const DEFAULT_PAGE = "main";
/** columns of the page grid and of every card's widget grid */
export const COLS = 12;
export const MAX_CARD_H = 60;
export const MAX_WIDGET_H = 40;
export const ACCENTS = ["gold", "cyan", "green", "yellow", "red", "purple", "orange", "gray"] as const;
export type Accent = (typeof ACCENTS)[number];
export const ICON_NAMES = [
  "home", "bolt", "flame", "drop", "box", "fan", "lock", "warning", "gear", "chart", "star", "leaf", "heart", "cpu", "shield", "pick",
  "lever", "button", "lamp", "sensor", "eye",
] as const;
export type IconName = (typeof ICON_NAMES)[number];
export const CARD_STYLES = ["comfortable", "compact"] as const;
export type CardStyle = (typeof CARD_STYLES)[number];
export const DISPLAYS = ["auto", "dial", "bar", "number", "row", "big", "line", "sparkline", "chip", "lamp"] as const;
export type Display = (typeof DISPLAYS)[number];
/** Display styles a widget type offers (first = default). */
export const DISPLAYS_FOR: Record<string, Display[]> = {
  gauge: ["dial", "bar", "number"],
  progress: ["bar", "number"],
  label: ["row", "big"],
  chart: ["line", "sparkline"],
  indicator: ["chip", "lamp"],
};
export const CUSTOM_PREFIX = "c:";

export interface Box {
  x: number;
  y: number;
  w: number;
  h: number;
}
export interface LayoutPage {
  id: string;
  title: string;
  icon?: IconName;
}
export interface CardPatch extends Partial<Box> {
  page?: string;
  title?: string;
  hidden?: boolean;
  color?: Accent;
  icon?: IconName;
  style?: CardStyle;
  /** v1 only (migrated on read) */
  order?: number;
  span?: number;
}
export interface WidgetPatch extends Partial<Box> {
  /** moved into another card (natural or custom key) */
  card?: string;
  label?: string;
  hidden?: boolean;
  color?: Accent;
  icon?: IconName;
  display?: Display;
  /** v1 only (migrated on read) */
  order?: number;
  span?: number;
}
export interface LayoutDoc {
  v: 1 | 2;
  pages: LayoutPage[];
  cards: Record<string, CardPatch>;
  widgets: Record<string, WidgetPatch>;
}

/** Lua v2 additions on the uplink snapshot (optional). */
export interface LuaPage {
  id: string;
  title?: string;
  icon?: string;
}
export interface LuaGroup {
  id: string;
  title?: string;
  page?: string;
  cols?: number;
}

export interface ResolvedWidget {
  key: string; // `${uplinkId}/${widgetId}` – the key in layout.widgets
  w: Widget;
  /** the uplink the widget belongs to (actions go there, also in custom cards) */
  uplink: Uplink;
  /** the card it came from in the scripts' structure */
  home: string;
  label: string;
  box: Box;
  hidden: boolean;
  color?: Accent;
  icon?: IconName;
  display: Display;
}
export interface ResolvedCard {
  key: string; // layout.cards key
  /** the card's uplink; for custom cards the uplink of its first widget (or a stub) */
  uplink: Uplink;
  custom: boolean;
  defaultTitle: string;
  title: string;
  page: string;
  box: Box;
  hidden: boolean;
  color: Accent;
  icon?: IconName;
  style: CardStyle;
  widgets: ResolvedWidget[];
}
export interface ResolvedPage extends LayoutPage {
  cards: ResolvedCard[];
}

export const emptyLayout = (): LayoutDoc => ({ v: LAYOUT_VERSION, pages: [], cards: {}, widgets: {} });
const isIcon = (s: unknown): s is IconName => typeof s === "string" && (ICON_NAMES as readonly string[]).includes(s);
const isAccent = (s: unknown): s is Accent => typeof s === "string" && (ACCENTS as readonly string[]).includes(s);
const str = (v: unknown) => (typeof v === "string" ? v : undefined);
const int = (v: unknown, min: number, max: number) => (typeof v === "number" && Number.isFinite(v) ? Math.max(min, Math.min(max, Math.round(v))) : undefined);
const STUB: Uplink = { uplinkId: "", title: "", online: true, widgets: [] };

/** Cards are: the uplink's ungrouped widgets, one card per Lua group, and ungrouped widgets that name a page. */
/**
 * The colour a button widget shows: a colour picked for the widget in the layout editor wins over the colour the
 * script or device sent (`opts.color`, e.g. a Cloud Button's cyan), so every button follows its widget colour in both
 * themes. Other widget types only take the colour as their accent (label, bars).
 */
export function withLayoutColor(w: Widget, color: Accent | undefined): Widget {
  if (!color || w.type !== "button" || w.opts?.color === color) return w;
  return { ...w, opts: { ...(w.opts ?? {}), color } };
}

export function cardKeyOf(uplinkId: string, w: Widget) {
  const o = (w.opts ?? {}) as Record<string, unknown>;
  const group = str(o.group);
  if (group) return `${uplinkId}/${group}`;
  const page = str(o.page);
  if (page) return `${uplinkId}~${page}`;
  return uplinkId;
}

/** Default widget order inside a card when nobody customised it: readouts first, buttons last. */
const TYPE_RANK: Record<string, number> = {
  gauge: 0, indicator: 1, chart: 2, progress: 3, label: 4, item_search: 5, watchlist: 6, jobs: 7,
  toggle: 8, slider: 8, select: 8, number: 8, text: 8, buttons: 9, button: 10, redstone_sides: 11, sensor_log: 2,
};
/** Smallest readable width (of 12): cards on the page, widgets inside their card. */
export const MIN_CARD_W = 3;
export function minWidgetW(type: string) {
  if (type === "item_search" || type === "watchlist" || type === "jobs" || type === "redstone_sides" || type === "sensor_log") return 6;
  if (type === "chart") return 4;
  if (type === "gauge") return 2;
  return 3;
}
/** Default width (of 12) inside a card. */
export function defaultWidgetW(type: string) {
  if (type === "gauge" || type === "indicator") return 6;
  if (type === "button") return 4;
  return 12;
}
/** Default height in grid rows (32 px each incl. gap) – roughly what the widget needs. */
export function defaultWidgetH(type: string, display?: Display) {
  if (type === "gauge") return display === "bar" ? 2 : display === "number" ? 3 : 5;
  if (type === "label") return display === "big" ? 3 : 1;
  if (type === "chart") return display === "sparkline" ? 3 : 5;
  if (type === "indicator") return display === "lamp" ? 3 : 2;
  // rows grow with their content, so these are minimums rather than exact heights
  const H: Record<string, number> = { progress: 2, item_search: 3, watchlist: 4, jobs: 3, toggle: 1, slider: 2, select: 1, number: 1, text: 1, buttons: 1, button: 2, redstone_sides: 6, sensor_log: 6 };
  return H[type] ?? 2;
}
/** Card rows for the header and padding around the widget grid. */
export const CARD_CHROME_H = 2;

/** Sanitises an untrusted layout document (from the server or the editor). v1 fields are kept for migration. */
export function normalizeLayout(raw: unknown): LayoutDoc {
  const out = emptyLayout();
  if (!raw || typeof raw !== "object") return out;
  const r = raw as Record<string, any>;
  if (r.v === 1) out.v = 1;
  const seen = new Set<string>();
  for (const p of Array.isArray(r.pages) ? r.pages.slice(0, MAX_PAGES) : []) {
    const id = str(p?.id);
    if (!id || !/^[A-Za-z0-9_-]{1,32}$/.test(id) || seen.has(id)) continue;
    seen.add(id);
    out.pages.push({ id, title: (str(p.title) ?? id).slice(0, 48), ...(isIcon(p.icon) ? { icon: p.icon } : {}) });
  }
  const box = (c: Record<string, any>, maxH: number) => ({
    ...(int(c.x, 0, COLS - 1) !== undefined ? { x: int(c.x, 0, COLS - 1) } : {}),
    ...(int(c.y, 0, MAX_LAYOUT_Y) !== undefined ? { y: int(c.y, 0, MAX_LAYOUT_Y) } : {}),
    ...(int(c.w, 1, COLS) !== undefined ? { w: int(c.w, 1, COLS) } : {}),
    ...(int(c.h, 1, maxH) !== undefined ? { h: int(c.h, 1, maxH) } : {}),
  });
  if (r.cards && typeof r.cards === "object")
    for (const [k, c] of Object.entries(r.cards as Record<string, any>).slice(0, MAX_LAYOUT_CARDS)) {
      if (!c || typeof c !== "object" || k.length > 160) continue;
      if (k.startsWith(CUSTOM_PREFIX) && !/^c:[A-Za-z0-9_-]{1,24}$/.test(k)) continue;
      const patch: CardPatch = box(c, MAX_CARD_H);
      if (str(c.page)) patch.page = c.page;
      if (str(c.title) !== undefined) patch.title = c.title.slice(0, 48);
      if (c.hidden === true) patch.hidden = true;
      if (isAccent(c.color)) patch.color = c.color;
      if (isIcon(c.icon)) patch.icon = c.icon;
      if (c.style === "compact") patch.style = "compact";
      if (int(c.order, 0, 10_000) !== undefined) patch.order = int(c.order, 0, 10_000);
      if (int(c.span, 1, 4) !== undefined) patch.span = int(c.span, 1, 4);
      out.cards[k] = patch;
    }
  if (r.widgets && typeof r.widgets === "object")
    for (const [k, w] of Object.entries(r.widgets as Record<string, any>).slice(0, MAX_LAYOUT_WIDGETS)) {
      if (!w || typeof w !== "object" || k.length > 160) continue;
      const patch: WidgetPatch = box(w, MAX_WIDGET_H);
      if (str(w.card) && w.card.length <= 160) patch.card = w.card;
      if (str(w.label) !== undefined) patch.label = w.label.slice(0, 48);
      if (w.hidden === true) patch.hidden = true;
      if (isAccent(w.color)) patch.color = w.color;
      if (isIcon(w.icon)) patch.icon = w.icon;
      if ((DISPLAYS as readonly string[]).includes(w.display) && w.display !== "auto") patch.display = w.display;
      if (int(w.order, 0, 10_000) !== undefined) patch.order = int(w.order, 0, 10_000);
      if (int(w.span, 1, 4) !== undefined) patch.span = int(w.span, 1, 4);
      out.widgets[k] = patch;
    }
  return out;
}

/** Pages the scripts declared (deduplicated, in uplink order). */
function luaPages(uplinks: Uplink[]): LayoutPage[] {
  const pages: LayoutPage[] = [];
  for (const u of uplinks)
    for (const p of (u as Uplink & { pages?: LuaPage[] }).pages ?? []) {
      if (!p || typeof p.id !== "string" || pages.some((x) => x.id === p.id)) continue;
      pages.push({ id: p.id, title: (p.title ?? p.id).slice(0, 48), ...(isIcon(p.icon) ? { icon: p.icon } : {}) });
    }
  return pages;
}

const hasBox = (p: Partial<Box>) => p.x !== undefined && p.y !== undefined && p.w !== undefined && p.h !== undefined;

const REDSTONE_ICONS = new Set(["lamp", "lever", "button", "sensor", "eye"]);
/** A guess for a card icon from its title when nobody chose one (view-mode polish). */
export function guessIcon(title: string, widgets: Widget[]): IconName | undefined {
  // Cloud Redstone cards: the icon of their devices (lamps, switches, buttons, sensors)
  const rs = widgets.find((w) => typeof w.opts?.icon === "string" && REDSTONE_ICONS.has(w.opts.icon as string));
  if (rs) return rs.opts!.icon as IconName;
  const t = title.toLowerCase();
  const rules: [RegExp, IconName][] = [
    [/reactor|fission|fusion|core|heat|boiler/, "flame"],
    [/power|energy|grid|matrix|battery|generator|turbine|solar/, "bolt"],
    [/storage|me network|rs network|chest|item|ae2|refined/, "box"],
    [/farm|tree|crop|garden|green|bee/, "leaf"],
    [/computer|server|cpu|terminal/, "cpu"],
    [/door|gate|lock|security|tür|tuer/, "lock"],
    [/water|fluid|tank|pump/, "drop"],
    [/mine|quarry|drill/, "pick"],
    [/control|kontroll|room|base|home/, "gear"],
    [/fan|cool|vent/, "fan"],
  ];
  for (const [re, icon] of rules) if (re.test(t)) return icon;
  if (widgets.some((w) => w.type === "chart")) return "chart";
  return undefined;
}

export function resolveLayout(uplinks: Uplink[], layout: LayoutDoc | null, opts: { includeHidden?: boolean } = {}): ResolvedPage[] {
  const doc = layout ?? emptyLayout();
  // pages: the edited list wins; otherwise Overview + the pages scripts declared
  const declared = luaPages(uplinks);
  let pages: LayoutPage[] = doc.pages.length ? doc.pages.map((p) => ({ ...p })) : [];
  if (!pages.length) pages = [{ id: DEFAULT_PAGE, title: "Overview", icon: "home" }, ...declared.filter((p) => p.id !== DEFAULT_PAGE)];
  const pageIds = new Set(pages.map((p) => p.id));
  const pageOf = (want: string | undefined, fallback?: string) => (want && pageIds.has(want) ? want : fallback && pageIds.has(fallback) ? fallback : pages[0].id);

  type Draft = ResolvedCard & { order: number; spanHint?: number; placed: boolean };
  type WDraft = ResolvedWidget & { order: number; placed: boolean; spanHint?: number };
  const cards = new Map<string, Draft>();
  const widgets: WDraft[] = [];
  let natural = 0;
  const makeCard = (key: string, u: Uplink, defaultTitle: string, luaPage: string | undefined, cols: number | undefined, wide: boolean, o: Record<string, unknown>): Draft => {
    const patch = doc.cards[key] ?? {};
    const spanHint = patch.span ?? cols;
    const w = patch.w ?? (spanHint ? spanHint * 3 : wide ? 6 : 3);
    return {
      key,
      uplink: u,
      custom: false,
      defaultTitle,
      title: patch.title || defaultTitle,
      page: pageOf(patch.page ?? luaPage, luaPage),
      box: { x: patch.x ?? 0, y: patch.y ?? 0, w, h: patch.h ?? 0 },
      placed: hasBox(patch),
      hidden: patch.hidden === true,
      color: patch.color ?? (isAccent(o.color) && o.color !== "gray" ? (o.color as Accent) : "gold"),
      icon: patch.icon ?? (isIcon(o.icon) ? (o.icon as IconName) : undefined),
      style: patch.style ?? "comfortable",
      order: patch.order ?? 1000 + natural++,
      widgets: [],
    };
  };

  uplinks.forEach((u) => {
    const groups = new Map(((u as Uplink & { groups?: LuaGroup[] }).groups ?? []).filter((g) => g && typeof g.id === "string").map((g) => [g.id, g]));
    u.widgets.forEach((w, wi) => {
      const key = cardKeyOf(u.uplinkId, w);
      if (!cards.has(key)) {
        const o = (w.opts ?? {}) as Record<string, unknown>;
        const group = str(o.group) ? groups.get(o.group as string) : undefined;
        const wide = u.widgets.some((x) => cardKeyOf(u.uplinkId, x) === key && (x.type === "item_search" || x.type === "chart"));
        cards.set(key, makeCard(key, u, group?.title ?? u.title ?? "Uplink", group?.page ?? str(o.page), group?.cols, wide, o));
      }
      const wkey = `${u.uplinkId}/${w.id}`;
      const wp = doc.widgets[wkey] ?? {};
      const o = (w.opts ?? {}) as Record<string, unknown>;
      const offered = DISPLAYS_FOR[w.type];
      const luaDisplay = w.type === "chart" && o.style === "sparkline" ? "sparkline" : undefined;
      const display: Display = wp.display && offered?.includes(wp.display) ? wp.display : luaDisplay ?? offered?.[0] ?? "auto";
      const spanHint = wp.span ?? (typeof o.span === "number" ? Math.max(1, Math.min(4, Math.round(o.span))) : undefined);
      widgets.push({
        key: wkey,
        w,
        uplink: u,
        home: key,
        label: wp.label || w.label || w.id,
        box: {
          x: wp.x ?? 0,
          y: wp.y ?? 0,
          w: wp.w ?? (spanHint ? spanHint * 3 : defaultWidgetW(w.type)),
          h: wp.h ?? defaultWidgetH(w.type, display),
        },
        placed: hasBox(wp),
        ...(spanHint && wp.w === undefined ? { spanHint } : {}),
        hidden: wp.hidden === true,
        ...(wp.color ? { color: wp.color } : {}),
        ...(wp.icon ? { icon: wp.icon } : {}),
        display,
        order: wp.order ?? (typeof o.order === "number" ? o.order : (TYPE_RANK[w.type] ?? 5) * 100 + wi),
      });
    });
    // an uplink without widgets still gets its (empty) card
    if (!u.widgets.length && !cards.has(u.uplinkId)) cards.set(u.uplinkId, makeCard(u.uplinkId, u, u.title || "Uplink", undefined, undefined, false, {}));
  });
  // custom cards from the editor
  for (const [key, patch] of Object.entries(doc.cards)) {
    if (!key.startsWith(CUSTOM_PREFIX) || cards.has(key)) continue;
    cards.set(key, { ...makeCard(key, STUB, "New card", undefined, undefined, false, {}), custom: true });
  }
  // widgets moved into another card
  for (const w of widgets) {
    const target = doc.widgets[w.key]?.card;
    const card = cards.get(target && cards.has(target) ? target : w.home)!;
    card.widgets.push(w);
  }

  const out: ResolvedPage[] = pages.map((p) => ({ ...p, cards: [] }));
  for (const c of cards.values()) {
    if (c.custom) {
      const first = c.widgets[0]?.uplink;
      c.uplink = first ? { ...STUB, uplinkId: first.uplinkId, title: c.title, online: c.widgets.every((w) => w.uplink.online) } : { ...STUB, title: c.title };
    }
    // widget grid inside the card: saved boxes first, then the rest in their natural order
    const visible = c.widgets.filter((w) => !w.hidden).sort((a, b) => (a as WDraft).order - (b as WDraft).order) as WDraft[];
    // v1 / Lua spans count body columns: 2 in a narrow card, 4 in a wide one
    for (const w of visible) if (w.spanHint && !w.placed) w.box.w = c.box.w <= 3 ? Math.min(COLS, w.spanHint * 6) : w.spanHint * 3;
    // never below the readable minimum (also for old and migrated layouts)
    for (const w of visible) w.box.w = Math.min(COLS, Math.max(minWidgetW(w.w.type), w.box.w));
    // indicators carry a label: full width in narrow cards
    for (const w of visible) if (!w.placed && !w.spanHint && w.w.type === "indicator" && c.box.w <= 3) w.box.w = 12;
    const anyPlaced = visible.some((w) => w.placed);
    const boxes = anyPlaced
      ? placeAll(visible.map((w) => ({ id: w.key, ...w.box, placed: w.placed })), COLS)
      : flowPack(visible.map((w) => ({ id: w.key, ...w.box })), COLS);
    const at = new Map(boxes.map((b) => [b.id, b]));
    for (const w of c.widgets) {
      const b = at.get(w.key);
      if (b) w.box = { x: b.x, y: b.y, w: b.w, h: b.h };
    }
    c.widgets = [...visible.map((w) => w as ResolvedWidget), ...(opts.includeHidden ? c.widgets.filter((w) => w.hidden) : [])].sort(
      (a, b) => Number(a.hidden) - Number(b.hidden) || a.box.y - b.box.y || a.box.x - b.box.x,
    );
    for (const w of c.widgets) {
      const d = w as Partial<WDraft>;
      delete d.order;
      delete d.placed;
      delete d.spanHint;
    }
    if (!c.box.h) c.box.h = Math.max(3, Math.min(MAX_CARD_H, CARD_CHROME_H + bottom(boxes)));
    if (!c.icon) c.icon = guessIcon(c.title, c.widgets.map((w) => w.w));
  }
  for (const c of cards.values()) c.box.w = Math.min(COLS, Math.max(MIN_CARD_W, c.box.w));
  for (const page of out) {
    const list = [...cards.values()].filter((c) => c.page === page.id && (opts.includeHidden || !c.hidden));
    const shown = list.filter((c) => !c.hidden && (!c.custom || c.widgets.length || opts.includeHidden)).sort((a, b) => a.order - b.order);
    const anyPlaced = shown.some((c) => c.placed);
    const boxes = anyPlaced ? placeAll(shown.map((c) => ({ id: c.key, ...c.box, placed: c.placed })), COLS) : flowPack(shown.map((c) => ({ id: c.key, ...c.box })), COLS);
    const at = new Map(boxes.map((b) => [b.id, b]));
    for (const c of shown) {
      const b = at.get(c.key)!;
      c.box = { x: b.x, y: b.y, w: b.w, h: b.h };
    }
    page.cards = [...shown, ...list.filter((c) => !shown.includes(c))]
      .map(({ order: _o, spanHint: _s, placed: _p, ...c }) => c as ResolvedCard)
      .sort((a, b) => Number(a.hidden) - Number(b.hidden) || a.box.y - b.box.y || a.box.x - b.box.x);
  }
  return out;
}

// ------------------------------------------------------------------ editing
// The editor works on a resolved view (includeHidden) and writes explicit
// boxes back, so the saved document is self-contained.

/**
 * Turns the current resolved view into a v2 layout document. With the uplinks it was resolved from, the widget entries
 * of cards whose widgets all sit where the automatic packing puts them are left out (a big base's full document would
 * be about 1 MB; docs/SPEC.md "Scaling"); the sparse document is resolved again to make sure nothing moves.
 */
export function snapshotLayout(pages: ResolvedPage[], uplinks?: Uplink[]): LayoutDoc {
  const doc = fullSnapshot(pages);
  return uplinks ? sparseWidgets(doc, pages, uplinks) : doc;
}

const BOX_KEYS = new Set(["x", "y", "w", "h"]);

function sparseWidgets(doc: LayoutDoc, pages: ResolvedPage[], uplinks: Uplink[]): LayoutDoc {
  const candidates: ResolvedCard[] = [];
  for (const p of pages)
    for (const c of p.cards)
      if (!c.custom && c.widgets.length && c.widgets.every((w) => !w.hidden && w.home === c.key && Object.keys(doc.widgets[w.key] ?? {}).every((k) => BOX_KEYS.has(k))))
        candidates.push(c);
  if (!candidates.length) return doc;
  const sparse: LayoutDoc = { ...doc, widgets: { ...doc.widgets } };
  for (const c of candidates) for (const w of c.widgets) delete sparse.widgets[w.key];
  const again = new Map<string, Box>();
  for (const p of resolveLayout(uplinks, sparse, { includeHidden: true })) for (const c of p.cards) for (const w of c.widgets) again.set(w.key, w.box);
  for (const c of candidates) {
    const same = c.widgets.every((w) => {
      const b = again.get(w.key);
      return !!b && b.x === w.box.x && b.y === w.box.y && b.w === w.box.w && b.h === w.box.h;
    });
    if (!same) for (const w of c.widgets) sparse.widgets[w.key] = doc.widgets[w.key];
  }
  return sparse;
}

function fullSnapshot(pages: ResolvedPage[]): LayoutDoc {
  const doc = emptyLayout();
  doc.pages = pages.map(({ id, title, icon }) => ({ id, title, ...(icon ? { icon } : {}) }));
  for (const p of pages)
    for (const c of p.cards) {
      doc.cards[c.key] = {
        page: p.id,
        ...c.box,
        ...(c.title !== c.defaultTitle || c.custom ? { title: c.title } : {}),
        ...(c.hidden ? { hidden: true } : {}),
        ...(c.color !== "gold" ? { color: c.color } : {}),
        ...(c.icon ? { icon: c.icon } : {}),
        ...(c.style !== "comfortable" ? { style: c.style } : {}),
      };
      for (const w of c.widgets) {
        doc.widgets[w.key] = {
          ...(c.key !== w.home ? { card: c.key } : {}),
          ...w.box,
          ...(w.label !== (w.w.label || w.w.id) ? { label: w.label } : {}),
          ...(w.hidden ? { hidden: true } : {}),
          ...(w.color ? { color: w.color } : {}),
          ...(w.icon ? { icon: w.icon } : {}),
          ...(w.display !== (DISPLAYS_FOR[w.w.type]?.[0] ?? "auto") ? { display: w.display } : {}),
        };
      }
    }
  return doc;
}

/**
 * v1 → v2 for a known set of uplinks (what the dashboard saves after the first
 * edit). Entries of sections that are not there right now are kept (minus the
 * v1-only order/span), like the server does, so they survive a restart.
 */
export function migrateLayout(uplinks: Uplink[], v1: LayoutDoc): LayoutDoc {
  const src = normalizeLayout(v1);
  const doc = snapshotLayout(resolveLayout(uplinks, src, { includeHidden: true }));
  for (const [k, c] of Object.entries(src.cards)) {
    if (doc.cards[k]) continue;
    const { order: _o, span: _s, ...rest } = c;
    doc.cards[k] = rest;
  }
  for (const [k, w] of Object.entries(src.widgets)) {
    if (doc.widgets[k]) continue;
    const { order: _o, span: _s, ...rest } = w;
    doc.widgets[k] = rest;
  }
  return doc;
}

const clone = (pages: ResolvedPage[]): ResolvedPage[] =>
  pages.map((p) => ({ ...p, cards: p.cards.map((c) => ({ ...c, box: { ...c.box }, widgets: c.widgets.map((w) => ({ ...w, box: { ...w.box } })) })) }));

const findCard = (pages: ResolvedPage[], key: string) => {
  for (const p of pages) {
    const c = p.cards.find((x) => x.key === key);
    if (c) return { page: p, card: c };
  }
  return undefined;
};
export const findWidget = (pages: ResolvedPage[], key: string) => {
  for (const p of pages)
    for (const c of p.cards) {
      const w = c.widgets.find((x) => x.key === key);
      if (w) return { page: p, card: c, widget: w };
    }
  return undefined;
};

const asItems = (cards: ResolvedCard[]) => cards.filter((c) => !c.hidden).map((c) => ({ id: c.key, ...c.box }));
const wItems = (ws: ResolvedWidget[]) => ws.filter((w) => !w.hidden).map((w) => ({ id: w.key, ...w.box }));
function applyCards(page: ResolvedPage, items: GridItem[]) {
  const m = new Map(items.map((i) => [i.id, i]));
  for (const c of page.cards) {
    const b = m.get(c.key);
    if (b) c.box = { x: b.x, y: b.y, w: b.w, h: b.h };
  }
  page.cards.sort((a, b) => Number(a.hidden) - Number(b.hidden) || a.box.y - b.box.y || a.box.x - b.box.x);
}
function applyWidgets(card: ResolvedCard, items: GridItem[]) {
  const m = new Map(items.map((i) => [i.id, i]));
  for (const w of card.widgets) {
    const b = m.get(w.key);
    if (b) w.box = { x: b.x, y: b.y, w: b.w, h: b.h };
  }
  card.widgets.sort((a, b) => Number(a.hidden) - Number(b.hidden) || a.box.y - b.box.y || a.box.x - b.box.x);
}
/**
 * A card at least fits its widgets (rows grow in the browser anyway, this keeps
 * neighbours honest). With `shrink` (widgets left the card) it also gets smaller.
 */
function fitCard(page: ResolvedPage, card: ResolvedCard, shrink = false) {
  const need = Math.max(3, CARD_CHROME_H + bottom(wItems(card.widgets)));
  if (card.box.h < need || (shrink && card.box.h > need)) applyCards(page, resizeItem(asItems(page.cards), card.key, card.box.w, Math.min(MAX_CARD_H, need), COLS, MAX_CARD_H));
}

/** Moves a card to (x, y) on a page (other page = moved there). */
export function moveCardTo(pages: ResolvedPage[], cardKey: string, pageId: string, x?: number, y?: number): ResolvedPage[] {
  const next = clone(pages);
  const found = findCard(next, cardKey);
  const target = next.find((p) => p.id === pageId);
  if (!found || !target) return pages;
  const card = found.card;
  if (found.page !== target) {
    found.page.cards = found.page.cards.filter((c) => c !== card);
    applyCards(found.page, compact(asItems(found.page.cards)));
    card.page = target.id;
    target.cards.push(card);
    const at = x === undefined || y === undefined ? freeSpot(asItems(target.cards.filter((c) => c !== card)), card.box.w, card.box.h, COLS) : { x, y };
    card.box = { ...card.box, ...at };
    applyCards(target, insertItem(asItems(target.cards.filter((c) => c !== card)), { id: card.key, ...card.box }, COLS));
    return next;
  }
  applyCards(target, moveItem(asItems(target.cards), cardKey, x ?? card.box.x, y ?? card.box.y, COLS));
  return next;
}

export function resizeCard(pages: ResolvedPage[], cardKey: string, w: number, h: number): ResolvedPage[] {
  const next = clone(pages);
  const f = findCard(next, cardKey);
  if (!f) return pages;
  applyCards(f.page, resizeItem(asItems(f.page.cards), cardKey, Math.max(MIN_CARD_W, w), h, COLS, MAX_CARD_H));
  return next;
}

export function updateCard(pages: ResolvedPage[], cardKey: string, patch: Partial<Pick<ResolvedCard, "title" | "hidden" | "color" | "icon" | "style">>): ResolvedPage[] {
  const next = clone(pages);
  const f = findCard(next, cardKey);
  if (!f) return pages;
  const wasHidden = f.card.hidden;
  Object.assign(f.card, patch);
  if (patch.hidden !== undefined && patch.hidden !== wasHidden) {
    const others = asItems(f.page.cards.filter((c) => c !== f.card));
    if (patch.hidden) applyCards(f.page, compact(others));
    else {
      // shown again: at the first free spot
      f.card.box = { ...f.card.box, ...freeSpot(others, f.card.box.w, f.card.box.h, COLS) };
      applyCards(f.page, [...others, { id: f.card.key, ...f.card.box }]);
    }
  }
  return next;
}

/**
 * Moves widgets into a card at (x, y) – the same card (re-arrange) or another
 * one. Several widgets (multi-select) are placed one after the other.
 */
export function moveWidgetsTo(pages: ResolvedPage[], widgetKeys: string[], cardKey: string, x: number, y: number): ResolvedPage[] {
  const next = clone(pages);
  const target = findCard(next, cardKey);
  if (!target) return pages;
  const moving = widgetKeys.map((k) => findWidget(next, k)).filter((f): f is NonNullable<typeof f> => !!f);
  if (!moving.length) return pages;
  // single widget inside its own card: plain grid move
  if (moving.length === 1 && moving[0].card === target.card && !moving[0].widget.hidden) {
    applyWidgets(target.card, moveItem(wItems(target.card.widgets), moving[0].widget.key, x, y, COLS));
    fitCard(target.page, target.card);
    return next;
  }
  const sources = new Set<ResolvedCard>();
  for (const m of moving) {
    m.card.widgets = m.card.widgets.filter((w) => w !== m.widget);
    if (m.card !== target.card) sources.add(m.card);
    m.widget.hidden = false;
  }
  for (const s of sources) {
    applyWidgets(s, compact(wItems(s.widgets)));
    const sp = next.find((pg) => pg.cards.includes(s));
    if (sp && !(s.custom && !s.widgets.length)) fitCard(sp, s, true);
  }
  let items = wItems(target.card.widgets);
  let cx = x;
  let cy = y;
  for (const m of moving) {
    const w = m.widget;
    w.box = { ...w.box, w: Math.min(COLS, w.box.w), x: Math.min(cx, COLS - Math.min(COLS, w.box.w)), y: cy };
    target.card.widgets.push(w);
    items = insertItem(items, { id: w.key, ...w.box }, COLS);
    const placed = items.find((i) => i.id === w.key)!;
    cx = placed.x + placed.w;
    cy = placed.y;
    if (cx >= COLS) {
      cx = 0;
      cy = placed.y + placed.h;
    }
  }
  applyWidgets(target.card, items);
  fitCard(target.page, target.card);
  // a custom card left empty disappears
  for (const s of sources) {
    if (s.custom && !s.widgets.length) {
      const p = next.find((pg) => pg.cards.includes(s))!;
      p.cards = p.cards.filter((c) => c !== s);
      applyCards(p, compact(asItems(p.cards)));
    }
  }
  return next;
}

export function resizeWidget(pages: ResolvedPage[], widgetKey: string, w: number, h: number): ResolvedPage[] {
  const next = clone(pages);
  const f = findWidget(next, widgetKey);
  if (!f) return pages;
  applyWidgets(f.card, resizeItem(wItems(f.card.widgets), widgetKey, Math.max(minWidgetW(f.widget.w.type), w), h, COLS, MAX_WIDGET_H));
  fitCard(f.page, f.card);
  return next;
}

export function updateWidget(pages: ResolvedPage[], widgetKey: string, patch: Partial<Pick<ResolvedWidget, "label" | "hidden" | "color" | "icon" | "display">>): ResolvedPage[] {
  const next = clone(pages);
  const f = findWidget(next, widgetKey);
  if (!f) return pages;
  const wasHidden = f.widget.hidden;
  if (patch.display && patch.display !== f.widget.display) f.widget.box = { ...f.widget.box, h: defaultWidgetH(f.widget.w.type, patch.display) };
  Object.assign(f.widget, patch);
  const others = wItems(f.card.widgets.filter((w) => w !== f.widget));
  if (patch.hidden === true && !wasHidden) {
    applyWidgets(f.card, compact(others));
    fitCard(f.page, f.card, true);
    return next;
  }
  else if (patch.hidden === false && wasHidden) {
    f.widget.box = { ...f.widget.box, ...freeSpot(others, f.widget.box.w, f.widget.box.h, COLS) };
    applyWidgets(f.card, [...others, { id: f.widget.key, ...f.widget.box }]);
  } else if (patch.display) applyWidgets(f.card, resizeItem(wItems(f.card.widgets), widgetKey, f.widget.box.w, f.widget.box.h, COLS, MAX_WIDGET_H));
  fitCard(f.page, f.card);
  return next;
}

/** Drags widgets out into empty space: a new custom card at (x, y) on a page holds them. */
export function newCardWithWidgets(pages: ResolvedPage[], widgetKeys: string[], pageId: string, x?: number, y?: number, title = "New card"): { pages: ResolvedPage[]; key: string } {
  const all = new Set(pages.flatMap((p) => p.cards.map((c) => c.key)));
  let n = 1;
  while (all.has(`${CUSTOM_PREFIX}card${n}`)) n++;
  const key = `${CUSTOM_PREFIX}card${n}`;
  let next = clone(pages);
  const page = next.find((p) => p.id === pageId);
  if (!page) return { pages, key };
  const ws = widgetKeys.map((k) => findWidget(next, k)?.widget).filter((w): w is ResolvedWidget => !!w);
  const width = Math.max(3, Math.min(COLS, ws.length > 1 ? 6 : 3));
  const card: ResolvedCard = {
    key,
    uplink: { ...STUB, uplinkId: ws[0]?.uplink.uplinkId ?? "", title, online: ws.every((w) => w.uplink.online) },
    custom: true,
    defaultTitle: title,
    title,
    page: pageId,
    box: { x: x ?? 0, y: y ?? 0, w: width, h: 4 },
    hidden: false,
    color: "gold",
    style: "comfortable",
    widgets: [],
  };
  const others = asItems(page.cards);
  if (x === undefined || y === undefined) card.box = { ...card.box, ...freeSpot(others, card.box.w, card.box.h, COLS) };
  page.cards.push(card);
  applyCards(page, insertItem(others, { id: key, ...card.box }, COLS));
  next = moveWidgetsTo(next, widgetKeys, key, 0, 0);
  return { pages: next, key };
}

/** Adds an empty custom card (from the Add gallery). */
export function addEmptyCard(pages: ResolvedPage[], pageId: string): { pages: ResolvedPage[]; key: string } {
  const r = newCardWithWidgets(pages, [], pageId, undefined, undefined, "New card");
  return r;
}

export function removeCustomCard(pages: ResolvedPage[], key: string): ResolvedPage[] {
  if (!key.startsWith(CUSTOM_PREFIX)) return pages;
  let next = clone(pages);
  const f = findCard(next, key);
  if (!f) return pages;
  // its widgets go home
  for (const w of [...f.card.widgets]) {
    const home = findCard(next, w.home);
    if (home) next = moveWidgetsTo(next, [w.key], w.home, 0, bottom(wItems(home.card.widgets)));
  }
  const g = findCard(next, key);
  if (g) {
    g.page.cards = g.page.cards.filter((c) => c.key !== key);
    applyCards(g.page, compact(asItems(g.page.cards)));
  }
  return next;
}

export function addPage(pages: ResolvedPage[], title: string, icon?: IconName): { pages: ResolvedPage[]; id: string } {
  const base = title.toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_|_$/g, "").slice(0, 24) || "page";
  let id = base;
  for (let n = 2; pages.some((p) => p.id === id); n++) id = `${base}_${n}`;
  if (pages.length >= MAX_PAGES) return { pages, id: pages[pages.length - 1].id };
  return { pages: [...clone(pages), { id, title: title.slice(0, 48) || "Page", ...(icon ? { icon } : {}), cards: [] }], id };
}

export function updatePage(pages: ResolvedPage[], id: string, patch: { title?: string; icon?: IconName }): ResolvedPage[] {
  return clone(pages).map((p) => (p.id === id ? { ...p, ...patch, title: (patch.title ?? p.title).slice(0, 48) || p.title } : p));
}

/** Deletes a page; its cards move below the cards of the first remaining page. The last page cannot be deleted. */
export function deletePage(pages: ResolvedPage[], id: string): ResolvedPage[] {
  if (pages.length <= 1) return pages;
  const next = clone(pages);
  const i = next.findIndex((p) => p.id === id);
  if (i < 0) return pages;
  const [gone] = next.splice(i, 1);
  const target = next[0];
  let y = bottom(asItems(target.cards));
  for (const c of gone.cards) {
    target.cards.push({ ...c, page: target.id, box: { ...c.box, y: y + c.box.y } });
  }
  y = 0;
  applyCards(target, compact(asItems(target.cards)));
  return next;
}

export function movePage(pages: ResolvedPage[], id: string, dir: -1 | 1): ResolvedPage[] {
  const i = pages.findIndex((p) => p.id === id);
  const j = i + dir;
  if (i < 0 || j < 0 || j >= pages.length) return pages;
  const next = clone(pages);
  [next[i], next[j]] = [next[j], next[i]];
  return next;
}

/** Legacy helper (v1 tests, keyboard): moves a card to an index in reading order on a page. */
export function moveCard(pages: ResolvedPage[], cardKey: string, toPage: string, toIndex: number): ResolvedPage[] {
  const target = pages.find((p) => p.id === toPage);
  if (!target) return pages;
  const others = target.cards.filter((c) => c.key !== cardKey && !c.hidden);
  const before = others[Math.max(0, Math.min(toIndex, others.length))];
  if (!before) return moveCardTo(pages, cardKey, toPage, 0, bottom(asItems(others)));
  return moveCardTo(pages, cardKey, toPage, before.box.x, before.box.y);
}

/** Legacy helper: moves a widget to an index (reading order) inside its card. */
export function moveWidget(pages: ResolvedPage[], cardKey: string, widgetKey: string, toIndex: number): ResolvedPage[] {
  const f = findCard(pages, cardKey);
  if (!f) return pages;
  const others = f.card.widgets.filter((w) => w.key !== widgetKey && !w.hidden);
  const before = others[Math.max(0, Math.min(toIndex, others.length))];
  return moveWidgetsTo(pages, [widgetKey], cardKey, before ? before.box.x : 0, before ? before.box.y : bottom(wItems(others)));
}

export function layoutSize(doc: LayoutDoc) {
  return new TextEncoder().encode(JSON.stringify(doc)).length;
}

/** The caption to show: the editor's label, else the script's label; none when the script set none (no raw ids). */
export function captionOf(rw: Pick<ResolvedWidget, "label" | "w">): string | undefined {
  if (rw.w.label == null && rw.label === rw.w.id) return undefined;
  return rw.label;
}

export { clampItem };
