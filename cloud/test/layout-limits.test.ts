// Security review 2026-10-30: a saved layout must not freeze the dashboards that open it. placeAll pushed overlapping
// boxes one item at a time (cubic): 2048 cards saved on the same spot took minutes in every viewer's browser, the editor
// included, so the owner could not even repair it from the web.
import { describe, expect, it } from "vitest";
import { anyOverlap, clampItem, collides, compact, freeSpot, placeAll, PLACE_LIMIT, type GridItem } from "../web/src/lib/grid.ts";
import { resolveLayout } from "../web/src/lib/layout.ts";

type Item = GridItem & { placed?: boolean };

// the previous placeAll (push item by item, first free spot), the reference for small and overlap-free layouts
const byPos = (a: GridItem, b: GridItem) => a.y - b.y || a.x - b.x || a.id.localeCompare(b.id);
function pushDownRef<T extends GridItem>(items: T[], moved: T): T[] {
  const map = new Map(items.map((i) => [i.id, { ...i }]));
  map.set(moved.id, { ...moved });
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
function placeAllRef<T extends Item>(items: T[], cols: number): T[] {
  let out: T[] = [];
  for (const it of items.filter((i) => i.placed !== false)) out = pushDownRef(out, clampItem(it, cols));
  for (const it of items.filter((i) => i.placed === false)) {
    const c = clampItem(it, cols);
    out.push({ ...c, ...freeSpot(out, c.w, c.h, cols) });
  }
  return compact(out);
}
function random(seed: number) {
  let s = seed;
  return () => ((s = (s * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff);
}

describe("layout placement stays fast for any saved layout", () => {
  it("2048 cards on the same spot: placed without overlaps in well under a second", () => {
    const items: Item[] = Array.from({ length: 2048 }, (_, i) => ({ id: `c${i}`, x: 0, y: 0, w: 12, h: 60, placed: true }));
    const t = performance.now();
    const out = placeAll(items, 12);
    expect(performance.now() - t).toBeLessThan(1_000);
    expect(out).toHaveLength(2048);
    expect(anyOverlap(out, 12)).toBe(false);
  });

  it("one card with a box and 2048 without: bounded too", () => {
    const items: Item[] = [{ id: "a", x: 0, y: 0, w: 4, h: 4, placed: true }, ...Array.from({ length: 2048 }, (_, i) => ({ id: `c${i}`, x: 0, y: 0, w: 4, h: 6, placed: false }))];
    const t = performance.now();
    const out = placeAll(items, 12);
    expect(performance.now() - t).toBeLessThan(1_000);
    expect(out).toHaveLength(2049);
    expect(anyOverlap(out, 12)).toBe(false);
  });

  it("the same results as before for small layouts and for every layout without overlaps", () => {
    const rnd = random(11);
    for (let round = 0; round < 200; round++) {
      // small, with overlaps and items without a box: the old algorithm unchanged
      const small: Item[] = [];
      const n = 1 + Math.floor(rnd() * PLACE_LIMIT);
      for (let i = 0; i < n; i++) {
        const w = 1 + Math.floor(rnd() * 12);
        small.push({ id: `s${i}`, x: Math.floor(rnd() * (13 - w)), y: Math.floor(rnd() * 30), w, h: 1 + Math.floor(rnd() * 6), placed: rnd() > 0.2 });
      }
      expect(placeAll(small, 12)).toEqual(placeAllRef(small, 12));
      // big and overlap-free (a saved editor layout of a big base): nothing moves
      const big: Item[] = compact(
        Array.from({ length: PLACE_LIMIT + 1 + Math.floor(rnd() * 200) }, (_, i) => {
          const w = 3 + Math.floor(rnd() * 10);
          return { id: `b${i}`, x: Math.floor(rnd() * (13 - w)), y: i * 3, w, h: 1 + Math.floor(rnd() * 8) };
        }),
      ).map((b) => ({ ...b, placed: true }));
      expect(anyOverlap(big, 12)).toBe(false);
      expect(placeAll(big, 12)).toEqual(placeAllRef(big, 12));
    }
  });

  it("a malicious layout document resolves quickly in the dashboard", () => {
    const uplinks = Array.from({ length: 1500 }, (_, i) => ({
      uplinkId: `00000000-0000-0000-0000-${String(i).padStart(12, "0")}`,
      title: `M${i}`,
      online: true,
      widgets: [{ id: "v", type: "label", value: "x" }],
    }));
    const cards: Record<string, unknown> = {};
    for (const u of uplinks) cards[u.uplinkId] = { page: "main", x: 0, y: 0, w: 12, h: 60 };
    const doc = { v: 2, pages: [{ id: "main", title: "Main" }], cards, widgets: {} };
    const t = performance.now();
    const pages = resolveLayout(uplinks as never, doc as never);
    expect(performance.now() - t).toBeLessThan(2_000);
    expect(pages[0].cards).toHaveLength(1500);
  });
});

// Voxel cache (IndexedDB): a role without "See coordinates" drops the cached blocks of that mainframe.
import { voxelsForbidden } from "../web/src/lib/voxelcache.ts";
describe("voxel cache and access", () => {
  it("forgets the blocks when view.positions is gone, keeps them otherwise", () => {
    expect(voxelsForbidden(new Set(["view", "storage.search"]))).toBe(true);
    expect(voxelsForbidden(new Set(["view", "view.positions"]))).toBe(false);
    expect(voxelsForbidden(null)).toBe(false);
  });
});
