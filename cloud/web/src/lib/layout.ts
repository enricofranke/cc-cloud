// Dashboard layout: pages → cards → widgets. Merges what the Lua scripts
// declare (pages, groups, opts.page/group/span/order/icon/color) with the
// per-mainframe layout document edited in the browser (docs/SPEC.md "Layout schema").
// Pure functions only, so the editor logic is unit-testable.
import type { Uplink, Widget } from "./types.ts";

export const LAYOUT_VERSION = 1;
export const MAX_LAYOUT_BYTES = 64 * 1024;
export const MAX_PAGES = 12;
export const DEFAULT_PAGE = "main";
export const ACCENTS = ["gold", "cyan", "green", "yellow", "red", "purple", "orange", "gray"] as const;
export type Accent = (typeof ACCENTS)[number];
export const ICON_NAMES = [
  "home", "bolt", "flame", "drop", "box", "fan", "lock", "warning", "gear", "chart", "star", "leaf", "heart", "cpu", "shield", "pick",
] as const;
export type IconName = (typeof ICON_NAMES)[number];

export interface LayoutPage {
  id: string;
  title: string;
  icon?: IconName;
}
export interface CardPatch {
  page?: string;
  order?: number;
  span?: number;
  title?: string;
  hidden?: boolean;
  color?: Accent;
  icon?: IconName;
}
export interface WidgetPatch {
  order?: number;
  span?: number;
  label?: string;
  hidden?: boolean;
}
export interface LayoutDoc {
  v: 1;
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
  label: string;
  span: number;
  hidden: boolean;
  order: number;
}
export interface ResolvedCard {
  key: string; // layout.cards key
  uplink: Uplink;
  defaultTitle: string;
  title: string;
  page: string;
  span: number;
  hidden: boolean;
  color: Accent;
  icon?: IconName;
  order: number;
  widgets: ResolvedWidget[];
}
export interface ResolvedPage extends LayoutPage {
  cards: ResolvedCard[];
}

export const emptyLayout = (): LayoutDoc => ({ v: 1, pages: [], cards: {}, widgets: {} });
const clampSpan = (n: unknown, d: number) => (typeof n === "number" && Number.isFinite(n) ? Math.min(4, Math.max(1, Math.round(n))) : d);
const isIcon = (s: unknown): s is IconName => typeof s === "string" && (ICON_NAMES as readonly string[]).includes(s);
const isAccent = (s: unknown): s is Accent => typeof s === "string" && (ACCENTS as readonly string[]).includes(s);
const str = (v: unknown) => (typeof v === "string" ? v : undefined);

/** Cards are: the uplink's ungrouped widgets, one card per Lua group, and ungrouped widgets that name a page. */
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
  toggle: 8, slider: 8, select: 8, number: 8, text: 8, buttons: 9, button: 10,
};
/** Default span inside the card body grid (2 columns on phones, 2×card span on wide screens, max 4). */
export function defaultWidgetSpan(type: string) {
  if (type === "gauge" || type === "indicator" || type === "button") return 1;
  if (type === "label" || type === "watchlist" || type === "jobs") return 2;
  return 4;
}

/** Sanitises an untrusted layout document (from the server or the editor). */
export function normalizeLayout(raw: unknown): LayoutDoc {
  const out = emptyLayout();
  if (!raw || typeof raw !== "object") return out;
  const r = raw as Record<string, any>;
  const seen = new Set<string>();
  for (const p of Array.isArray(r.pages) ? r.pages.slice(0, MAX_PAGES) : []) {
    const id = str(p?.id);
    if (!id || !/^[A-Za-z0-9_-]{1,32}$/.test(id) || seen.has(id)) continue;
    seen.add(id);
    out.pages.push({ id, title: (str(p.title) ?? id).slice(0, 48), ...(isIcon(p.icon) ? { icon: p.icon } : {}) });
  }
  if (r.cards && typeof r.cards === "object")
    for (const [k, c] of Object.entries(r.cards as Record<string, any>).slice(0, 256)) {
      if (!c || typeof c !== "object" || k.length > 160) continue;
      const patch: CardPatch = {};
      if (str(c.page)) patch.page = c.page;
      if (typeof c.order === "number" && Number.isFinite(c.order)) patch.order = c.order;
      if (c.span !== undefined) patch.span = clampSpan(c.span, 1);
      if (str(c.title) !== undefined) patch.title = c.title.slice(0, 48);
      if (c.hidden === true) patch.hidden = true;
      if (isAccent(c.color)) patch.color = c.color;
      if (isIcon(c.icon)) patch.icon = c.icon;
      out.cards[k] = patch;
    }
  if (r.widgets && typeof r.widgets === "object")
    for (const [k, w] of Object.entries(r.widgets as Record<string, any>).slice(0, 2048)) {
      if (!w || typeof w !== "object" || k.length > 160) continue;
      const patch: WidgetPatch = {};
      if (typeof w.order === "number" && Number.isFinite(w.order)) patch.order = w.order;
      if (w.span !== undefined) patch.span = clampSpan(w.span, 1);
      if (str(w.label) !== undefined) patch.label = w.label.slice(0, 48);
      if (w.hidden === true) patch.hidden = true;
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

export function resolveLayout(uplinks: Uplink[], layout: LayoutDoc | null, opts: { includeHidden?: boolean } = {}): ResolvedPage[] {
  const doc = layout ?? emptyLayout();
  // pages: the edited list wins; otherwise Overview + the pages scripts declared
  const declared = luaPages(uplinks);
  let pages: LayoutPage[] = doc.pages.length ? doc.pages.map((p) => ({ ...p })) : [];
  if (!pages.length) {
    pages = [{ id: DEFAULT_PAGE, title: "Overview", icon: "home" }, ...declared.filter((p) => p.id !== DEFAULT_PAGE)];
  }
  const pageIds = new Set(pages.map((p) => p.id));

  const cards = new Map<string, ResolvedCard>();
  let natural = 0;
  uplinks.forEach((u) => {
    const groups = new Map(((u as Uplink & { groups?: LuaGroup[] }).groups ?? []).filter((g) => g && typeof g.id === "string").map((g) => [g.id, g]));
    u.widgets.forEach((w, wi) => {
      const key = cardKeyOf(u.uplinkId, w);
      let card = cards.get(key);
      if (!card) {
        const o = (w.opts ?? {}) as Record<string, unknown>;
        const group = str(o.group) ? groups.get(o.group as string) : undefined;
        const patch = doc.cards[key] ?? {};
        const luaPage = group?.page ?? str(o.page);
        const defaultTitle = group?.title ?? u.title ?? "Uplink";
        const hasWide = u.widgets.some((x) => cardKeyOf(u.uplinkId, x) === key && (x.type === "item_search" || x.type === "chart"));
        let page = patch.page ?? luaPage ?? pages[0].id;
        if (!pageIds.has(page)) page = luaPage && pageIds.has(luaPage) ? luaPage : pages[0].id;
        card = {
          key,
          uplink: u,
          defaultTitle,
          title: patch.title || defaultTitle,
          page,
          span: clampSpan(patch.span ?? group?.cols, hasWide ? 2 : 1),
          hidden: patch.hidden === true,
          color: patch.color ?? (isAccent(o.color) && o.color !== "gray" ? (o.color as Accent) : "gold"),
          icon: patch.icon ?? (isIcon(o.icon) ? (o.icon as IconName) : undefined),
          order: patch.order ?? 1000 + natural++,
          widgets: [],
        };
        cards.set(key, card);
      }
      const wkey = `${u.uplinkId}/${w.id}`;
      const wp = doc.widgets[wkey] ?? {};
      const o = (w.opts ?? {}) as Record<string, unknown>;
      card.widgets.push({
        key: wkey,
        w,
        label: wp.label || w.label || w.id,
        span: clampSpan(wp.span ?? o.span, defaultWidgetSpan(w.type)),
        hidden: wp.hidden === true,
        order: wp.order ?? (typeof o.order === "number" ? o.order : (TYPE_RANK[w.type] ?? 5) * 100 + wi),
      });
    });
    // an uplink without widgets still gets its (empty) card
    if (!u.widgets.length && !cards.has(u.uplinkId)) {
      const patch = doc.cards[u.uplinkId] ?? {};
      cards.set(u.uplinkId, {
        key: u.uplinkId,
        uplink: u,
        defaultTitle: u.title || "Uplink",
        title: patch.title || u.title || "Uplink",
        page: patch.page && pageIds.has(patch.page) ? patch.page : pages[0].id,
        span: clampSpan(patch.span, 1),
        hidden: patch.hidden === true,
        color: patch.color ?? "gold",
        icon: patch.icon,
        order: patch.order ?? 1000 + natural++,
        widgets: [],
      });
    }
  });

  for (const c of cards.values()) {
    c.widgets.sort((a, b) => a.order - b.order);
    if (!opts.includeHidden) c.widgets = c.widgets.filter((w) => !w.hidden);
  }
  return pages.map((p) => ({
    ...p,
    cards: [...cards.values()].filter((c) => c.page === p.id && (opts.includeHidden || !c.hidden)).sort((a, b) => a.order - b.order),
  }));
}

// ------------------------------------------------------------------ editing
// The editor works on a resolved view (includeHidden) and writes explicit
// order numbers back, so the saved document is self-contained.

/** Turns the current resolved view into a full layout document. */
export function snapshotLayout(pages: ResolvedPage[]): LayoutDoc {
  const doc = emptyLayout();
  doc.pages = pages.map(({ id, title, icon }) => ({ id, title, ...(icon ? { icon } : {}) }));
  for (const p of pages)
    p.cards.forEach((c, ci) => {
      doc.cards[c.key] = {
        page: p.id,
        order: ci,
        span: c.span,
        ...(c.title !== c.defaultTitle ? { title: c.title } : {}),
        ...(c.hidden ? { hidden: true } : {}),
        ...(c.color !== "gold" ? { color: c.color } : {}),
        ...(c.icon ? { icon: c.icon } : {}),
      };
      c.widgets.forEach((w, wi) => {
        doc.widgets[w.key] = {
          order: wi,
          span: w.span,
          ...(w.label !== (w.w.label || w.w.id) ? { label: w.label } : {}),
          ...(w.hidden ? { hidden: true } : {}),
        };
      });
    });
  return doc;
}

const clone = (pages: ResolvedPage[]) => pages.map((p) => ({ ...p, cards: p.cards.map((c) => ({ ...c, widgets: [...c.widgets] })) }));

export function moveCard(pages: ResolvedPage[], cardKey: string, toPage: string, toIndex: number): ResolvedPage[] {
  const next = clone(pages);
  let card: ResolvedCard | undefined;
  for (const p of next) {
    const i = p.cards.findIndex((c) => c.key === cardKey);
    if (i >= 0) [card] = p.cards.splice(i, 1);
  }
  const target = next.find((p) => p.id === toPage);
  if (!card || !target) return pages;
  card.page = toPage;
  target.cards.splice(Math.max(0, Math.min(toIndex, target.cards.length)), 0, card);
  return next;
}

export function updateCard(pages: ResolvedPage[], cardKey: string, patch: Partial<Pick<ResolvedCard, "title" | "span" | "hidden" | "color" | "icon">>): ResolvedPage[] {
  return clone(pages).map((p) => ({ ...p, cards: p.cards.map((c) => (c.key === cardKey ? { ...c, ...patch } : c)) }));
}

export function moveWidget(pages: ResolvedPage[], cardKey: string, widgetKey: string, toIndex: number): ResolvedPage[] {
  const next = clone(pages);
  for (const p of next)
    for (const c of p.cards) {
      if (c.key !== cardKey) continue;
      const i = c.widgets.findIndex((w) => w.key === widgetKey);
      if (i < 0) return pages;
      const [w] = c.widgets.splice(i, 1);
      c.widgets.splice(Math.max(0, Math.min(toIndex, c.widgets.length)), 0, w);
    }
  return next;
}

export function updateWidget(pages: ResolvedPage[], widgetKey: string, patch: Partial<Pick<ResolvedWidget, "label" | "span" | "hidden">>): ResolvedPage[] {
  return clone(pages).map((p) => ({ ...p, cards: p.cards.map((c) => ({ ...c, widgets: c.widgets.map((w) => (w.key === widgetKey ? { ...w, ...patch } : w)) })) }));
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

/** Deletes a page; its cards move to the first remaining page. The last page cannot be deleted. */
export function deletePage(pages: ResolvedPage[], id: string): ResolvedPage[] {
  if (pages.length <= 1) return pages;
  const next = clone(pages);
  const i = next.findIndex((p) => p.id === id);
  if (i < 0) return pages;
  const [gone] = next.splice(i, 1);
  const target = next[0];
  target.cards.push(...gone.cards.map((c) => ({ ...c, page: target.id })));
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

export function layoutSize(doc: LayoutDoc) {
  return new TextEncoder().encode(JSON.stringify(doc)).length;
}
