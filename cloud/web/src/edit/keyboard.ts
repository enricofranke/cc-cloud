// Keyboard drag & drop for the layout editor (pure, unit-tested):
// Space picks up / drops, arrows move one cell, Shift+arrows resize,
// PageUp/PageDown move a widget into the previous/next card, Esc cancels.
import { COLS, findWidget, moveCardTo, moveWidgetsTo, resizeCard, resizeWidget, type ResolvedPage } from "../lib/layout.ts";

export type Kind = "card" | "widget";
export interface KbdResult {
  pages: ResolvedPage[];
  message: string;
}

const DIRS: Record<string, [number, number]> = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] };

export function describe(pages: ResolvedPage[], kind: Kind, key: string): string {
  if (kind === "widget") {
    const f = findWidget(pages, key);
    if (!f) return "";
    const b = f.widget.box;
    return `${f.widget.label} in ${f.card.title}: column ${b.x + 1}, row ${b.y + 1}, size ${b.w} by ${b.h}.`;
  }
  for (const p of pages) {
    const c = p.cards.find((x) => x.key === key);
    if (c) return `${c.title} on ${p.title}: column ${c.box.x + 1}, row ${c.box.y + 1}, size ${c.box.w} by ${c.box.h}.`;
  }
  return "";
}

export const PICKUP_HELP = "Arrow keys move, Shift and arrow keys resize, Page Up and Page Down move to the previous or next card, Space drops, Escape cancels.";

/** Applies one key to a picked-up item; null when the key does nothing. */
export function kbdStep(pages: ResolvedPage[], kind: Kind, key: string, e: { key: string; shiftKey: boolean }): KbdResult | null {
  const d = DIRS[e.key];
  if (kind === "widget") {
    const f = findWidget(pages, key);
    if (!f) return null;
    const b = f.widget.box;
    if (d && e.shiftKey) {
      const next = resizeWidget(pages, key, Math.max(1, Math.min(COLS - b.x, b.w + d[0])), Math.max(1, b.h + d[1]));
      return { pages: next, message: describe(next, kind, key) };
    }
    if (d) {
      // with compaction a 1-row step may land where it started: keep stepping until something changes
      for (let k = 1; k <= 60; k++) {
        const next = moveWidgetsTo(pages, [key], f.card.key, Math.max(0, Math.min(COLS - b.w, b.x + d[0] * k)), Math.max(0, b.y + d[1] * k));
        const nb = findWidget(next, key)!.widget.box;
        if (nb.x !== b.x || nb.y !== b.y) return { pages: next, message: describe(next, kind, key) };
        if (d[0] !== 0) break;
      }
      return { pages, message: describe(pages, kind, key) };
    }
    if (e.key === "PageUp" || e.key === "PageDown") {
      const cards = f.page.cards.filter((c) => !c.hidden);
      const i = cards.findIndex((c) => c.key === f.card.key) + (e.key === "PageDown" ? 1 : -1);
      const target = cards[i];
      if (!target) return null;
      const y = target.widgets.filter((w) => !w.hidden).reduce((m, w) => Math.max(m, w.box.y + w.box.h), 0);
      const next = moveWidgetsTo(pages, [key], target.key, 0, y);
      return { pages: next, message: describe(next, kind, key) };
    }
    return null;
  }
  const page = pages.find((p) => p.cards.some((c) => c.key === key));
  const card = page?.cards.find((c) => c.key === key);
  if (!page || !card || !d) return null;
  const b = card.box;
  if (e.shiftKey) {
    const next = resizeCard(pages, key, Math.max(1, Math.min(COLS - b.x, b.w + d[0])), Math.max(2, b.h + d[1]));
    return { pages: next, message: describe(next, kind, key) };
  }
  for (let k = 1; k <= 100; k++) {
    const next = moveCardTo(pages, key, page.id, Math.max(0, Math.min(COLS - b.w, b.x + d[0] * k)), Math.max(0, b.y + d[1] * k));
    const nb = next.flatMap((p) => p.cards).find((c) => c.key === key)!.box;
    if (nb.x !== b.x || nb.y !== b.y) return { pages: next, message: describe(next, kind, key) };
    if (d[0] !== 0) break;
  }
  return { pages, message: describe(pages, kind, key) };
}
