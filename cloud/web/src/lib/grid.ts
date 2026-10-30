// Grid layout engine for the dashboard editor (pure functions, unit-tested).
// Items live on a grid of `cols` columns and unbounded rows; x/y/w/h are in
// grid units. Same algorithm family as react-grid-layout: an item that is
// moved or resized pushes colliding items down, then everything floats up
// ("vertical compaction") so there are no holes.

export interface GridItem {
  id: string;
  x: number;
  y: number;
  w: number;
  h: number;
}

export const collides = (a: GridItem, b: GridItem) => a.id !== b.id && a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;

const byPos = (a: GridItem, b: GridItem) => a.y - b.y || a.x - b.x || a.id.localeCompare(b.id);

/** Clamps an item into the grid (width first, then x). */
export function clampItem<T extends GridItem>(it: T, cols: number, maxH = 200): T {
  const w = Math.max(1, Math.min(cols, Math.round(it.w)));
  const h = Math.max(1, Math.min(maxH, Math.round(it.h)));
  const x = Math.max(0, Math.min(cols - w, Math.round(it.x)));
  const y = Math.max(0, Math.round(it.y));
  return { ...it, x, y, w, h };
}

/**
 * Floats every item up as far as it goes (top to bottom, left to right). `pinned` stays where it is.
 * O(n²) instead of a row-by-row walk (a 500-card page is thousands of rows tall; docs/SPEC.md "Scaling"); the result is
 * exactly that of moving each item up one row at a time until the row above is blocked, then down past overlaps.
 */
export function compact<T extends GridItem>(items: T[], pinned?: string): T[] {
  const out: T[] = [];
  const fixed = items.filter((i) => i.id === pinned);
  const obstacles: GridItem[] = [...fixed];
  for (const it of [...items].filter((i) => i.id !== pinned).sort(byPos)) {
    const c = { ...it };
    // up: the highest row above the start where the item would collide stops it just below
    let top = 0;
    for (const o of obstacles) {
      if (o.id === c.id || !(c.x < o.x + o.w && o.x < c.x + c.w)) continue;
      const hi = Math.min(c.y - 1, o.y + o.h - 1);
      if (hi >= Math.max(o.y - c.h + 1, 0)) top = Math.max(top, hi + 1);
    }
    c.y = Math.min(c.y, top);
    // pushed below anything it still overlaps (only possible around the pinned item), at most 1000 rows
    const start = c.y;
    for (;;) {
      let next = Infinity;
      for (const o of obstacles) if (collides(c, o)) next = Math.min(next, o.y + o.h);
      if (next === Infinity) break;
      c.y = Math.min(next, start + 1000);
      if (c.y === start + 1000) break;
    }
    out.push(c);
    obstacles.push(c);
  }
  return [...out, ...fixed].sort(byPos);
}

/**
 * Pushes items that overlap `moved` (recursively) below it. When the user drags
 * downwards, a directly hit item first tries to hop above the moved item (swap).
 */
function pushDown<T extends GridItem>(items: T[], moved: T, fromY?: number): T[] {
  const map = new Map(items.map((i) => [i.id, { ...i }]));
  map.set(moved.id, { ...moved });
  if (fromY !== undefined && moved.y > fromY)
    for (const o of [...map.values()].sort(byPos)) {
      if (o.id === moved.id || !collides(moved, o)) continue;
      const up = { ...o, y: moved.y - o.h };
      if (up.y >= 0 && ![...map.values()].some((x) => x.id !== o.id && collides(up, x))) o.y = up.y;
    }
  const queue = [moved.id];
  for (let guard = 0; queue.length && guard < 10_000; guard++) {
    const cur = map.get(queue.shift()!)!;
    for (const o of [...map.values()].sort(byPos)) {
      if (o.id === cur.id || !collides(cur, o)) continue;
      o.y = cur.y + cur.h;
      queue.push(o.id);
    }
  }
  return [...map.values()];
}

/**
 * Makes room to the right: items hit by `moved` slide right (in a chain) when
 * the row has space – like inserting into a row. Null when it does not fit.
 */
function shiftRight<T extends GridItem>(items: T[], moved: T, cols: number): T[] | null {
  const map = new Map(items.map((i) => [i.id, { ...i }]));
  map.set(moved.id, { ...moved });
  const fixed = new Set([moved.id]);
  const queue = [moved.id];
  for (let guard = 0; queue.length && guard < 1000; guard++) {
    const cur = map.get(queue.shift()!)!;
    for (const o of [...map.values()].sort(byPos)) {
      if (o.id === cur.id || !collides(cur, o)) continue;
      if (fixed.has(o.id)) return null;
      const nx = cur.x + cur.w;
      if (nx + o.w > cols) return null;
      o.x = nx;
      fixed.add(o.id);
      queue.push(o.id);
    }
  }
  return [...map.values()];
}

/** Moves one item to (x, y): hit items slide right if the row has room, else they are pushed down; then the grid is compacted. */
export function moveItem<T extends GridItem>(items: T[], id: string, x: number, y: number, cols: number): T[] {
  const it = items.find((i) => i.id === id);
  if (!it) return items;
  const moved = clampItem({ ...it, x, y }, cols);
  const others = items.filter((i) => i.id !== id);
  if (moved.y <= it.y && others.some((o) => collides(moved, o))) {
    const shifted = shiftRight(others, moved, cols);
    if (shifted) return compact(compact(shifted, id));
  }
  // the others make room around the moved item, then it floats up as well
  return compact(compact(pushDown(items, moved, it.y), id));
}

/** Resizes one item; neighbours are pushed down and the grid compacted. */
export function resizeItem<T extends GridItem>(items: T[], id: string, w: number, h: number, cols: number, maxH = 200): T[] {
  const it = items.find((i) => i.id === id);
  if (!it) return items;
  const sized = clampItem({ ...it, w, h }, cols, maxH);
  return compact(compact(pushDown(items, sized), id));
}

/**
 * The first free spot (top-left first) for an item of w × h. Only rows where something ends (and row 0) and columns
 * where something ends (and column 0) can be the first free spot, so only those are tried (same result as trying every
 * cell; docs/SPEC.md "Scaling").
 */
export function freeSpot(items: GridItem[], w: number, h: number, cols: number): { x: number; y: number } {
  const ww = Math.min(cols, w);
  const ys = [...new Set([0, ...items.map((o) => o.y + o.h)])].filter((y) => y < 10_000).sort((a, b) => a - b);
  for (const y of ys) {
    const band = items.filter((o) => o.y < y + h && y < o.y + o.h);
    const xs = [...new Set([0, ...band.map((o) => o.x + o.w)])].filter((x) => x + ww <= cols).sort((a, b) => a - b);
    for (const x of xs) if (!band.some((o) => collides({ id: "\u0000", x, y, w: ww, h }, o))) return { x, y };
  }
  return { x: 0, y: bottom(items) };
}

/** Adds an item at its own x/y (pushing others) or, without a position, at the first free spot. */
export function insertItem<T extends GridItem>(items: T[], item: T, cols: number, atPosition = true): T[] {
  const placed = atPosition ? clampItem(item, cols) : clampItem({ ...item, ...freeSpot(items, item.w, item.h, cols) }, cols);
  return compact(compact(pushDown([...items.filter((i) => i.id !== item.id), placed], placed), placed.id));
}

export function removeItem<T extends GridItem>(items: T[], id: string): T[] {
  return compact(items.filter((i) => i.id !== id));
}

export const bottom = (items: GridItem[]) => items.reduce((m, i) => Math.max(m, i.y + i.h), 0);

/**
 * Up to this many items, overlapping saved boxes are resolved by pushing (item by item, like the editor) and items
 * without a box go to the first free spot. Both are cubic in the worst case: a saved layout with 2048 cards on the same
 * spot froze every dashboard that opened it for minutes (security review 2026-10-30). Beyond it, overlaps are resolved
 * in one pass (boxes without overlaps never move), and items without a box beyond a work budget are packed below.
 */
export const PLACE_LIMIT = 64;
/** Work (items², the worst case of one freeSpot) for placing items without a box: a few hundred items. */
const FREE_SPOT_BUDGET = 20_000_000;

/** True if two of the items overlap (sweep in reading order with the lowest filled row per column). */
export function anyOverlap(items: GridItem[], cols: number): boolean {
  const bottoms = new Array<number>(cols).fill(0);
  for (const it of [...items].sort(byPos)) {
    for (let c = it.x; c < it.x + it.w; c++) if (bottoms[c] > it.y) return true;
    for (let c = it.x; c < it.x + it.w; c++) bottoms[c] = Math.max(bottoms[c], it.y + it.h);
  }
  return false;
}

/** Moves items down (in reading order) until nothing overlaps: linear in the number of items. Keeps the order given. */
function settle<T extends GridItem>(items: T[], cols: number): T[] {
  const bottoms = new Array<number>(cols).fill(0);
  const at = new Map<string, number>();
  for (const it of [...items].sort(byPos)) {
    let y = it.y;
    for (let c = it.x; c < it.x + it.w; c++) y = Math.max(y, bottoms[c]);
    for (let c = it.x; c < it.x + it.w; c++) bottoms[c] = y + it.h;
    at.set(it.id, y);
  }
  return items.map((it) => ({ ...it, y: at.get(it.id) ?? it.y }));
}

/** Places items without a position (keeps the order given) after/around the positioned ones. */
export function placeAll<T extends GridItem & { placed?: boolean }>(items: T[], cols: number): T[] {
  const boxed = items.filter((i) => i.placed !== false).map((it) => clampItem(it, cols));
  let out: T[] = [];
  if (boxed.length <= PLACE_LIMIT) for (const it of boxed) out = pushDown(out, it);
  else out = anyOverlap(boxed, cols) ? settle(boxed, cols) : boxed; // without overlaps pushing changes nothing
  // freeSpot costs up to n² per item: within the budget the first free spot, then packed below in the order given
  let spent = 0;
  let flow: { x: number; y: number; rowH: number } | null = null;
  for (const it of items.filter((i) => i.placed === false)) {
    const c = clampItem(it, cols);
    if (!flow) {
      spent += Math.max(1, out.length) ** 2;
      if (spent <= FREE_SPOT_BUDGET) {
        out.push({ ...c, ...freeSpot(out, c.w, c.h, cols) });
        continue;
      }
      flow = { x: 0, y: bottom(out), rowH: 0 };
    }
    if (flow.x + c.w > cols) {
      flow.x = 0;
      flow.y += flow.rowH;
      flow.rowH = 0;
    }
    out.push({ ...c, x: flow.x, y: flow.y });
    flow.x += c.w;
    flow.rowH = Math.max(flow.rowH, c.h);
  }
  return compact(out);
}

/**
 * Packs items in reading order into rows (left to right, wrapping) – used to
 * migrate v1 layouts (order + span) and for "reset to default".
 */
export function flowPack<T extends GridItem>(items: T[], cols: number): T[] {
  const out: T[] = [];
  let x = 0;
  let y = 0;
  let rowH = 0;
  for (const it of items) {
    const w = Math.min(cols, it.w);
    if (x + w > cols) {
      x = 0;
      y += rowH;
      rowH = 0;
    }
    out.push({ ...it, x, y, w });
    x += w;
    rowH = Math.max(rowH, it.h);
  }
  return compact(out);
}

/** Reading order for narrow screens (rows top to bottom, then left to right). */
export const readingOrder = <T extends GridItem>(items: T[]) => [...items].sort(byPos);

/**
 * Keyboard/phone reordering: moves `id` to just before `beforeId` in reading
 * order (or to the end) and re-flows the positions it passes.
 */
export function moveBefore<T extends GridItem>(items: T[], id: string, beforeId: string | null, cols: number): T[] {
  const it = items.find((i) => i.id === id);
  if (!it || id === beforeId) return items;
  const target = beforeId ? items.find((i) => i.id === beforeId) : null;
  if (!target) return moveItem(items, id, 0, bottom(items.filter((i) => i.id !== id)), cols);
  return moveItem(items, id, target.x + it.w <= cols ? target.x : cols - it.w, target.y, cols);
}

/** Grid cell under a point, given the grid's box. Rows include the gap. */
export function cellAt(px: number, py: number, box: { left: number; top: number; width: number }, cols: number, colGap: number, rowH: number, rowGap: number) {
  const colW = (box.width - colGap * (cols - 1)) / cols;
  const x = Math.floor((px - box.left + colGap / 2) / (colW + colGap));
  const y = Math.floor((py - box.top + rowGap / 2) / (rowH + rowGap));
  return { x: Math.max(0, Math.min(cols - 1, x)), y: Math.max(0, y), colW };
}

export const sameLayout = (a: GridItem[], b: GridItem[]) => {
  if (a.length !== b.length) return false;
  const m = new Map(b.map((i) => [i.id, i]));
  return a.every((i) => {
    const o = m.get(i.id);
    return o && o.x === i.x && o.y === i.y && o.w === i.w && o.h === i.h;
  });
};
