// Big bases (docs/SPEC.md "Scaling"): a dashboard with hundreds of sections. The layout is resolved once per structure
// change, value deltas swap in new widget objects only for the cards they touch, layouts stay under the limits, and a
// batched "values" message updates many sections in one signal write.
import { describe, expect, it } from "vitest";
import { MAX_LAYOUT_BYTES, layoutSize, resolveLayout, snapshotLayout, moveCardTo } from "../web/src/lib/layout.ts";
import { layoutDoc } from "../dev/protocol.ts";
import { Hydrator } from "../web/src/lib/hydrate.ts";
import { adaptComputerCard } from "../web/src/lib/redstone.ts";
import { MainframeConn } from "../web/src/lib/conn.ts";
import type { Uplink, Widget } from "../web/src/lib/types.ts";

/** A base like the scale benchmark's: Lua cards with 25 widgets, machine cards with a 300-point chart. */
function base(sections: number): Uplink[] {
  const out: Uplink[] = [];
  for (let i = 0; i < sections; i++) {
    const id = `00000000-0000-4000-8000-${String(i).padStart(12, "0")}`;
    const widgets: Widget[] = [];
    if (i % 2 === 0) {
      for (let g = 0; g < 6; g++) widgets.push({ id: `g${g}`, type: "gauge", label: `Gauge ${g}`, opts: { min: 0, max: 1000 }, value: g });
      for (let l = 0; l < 4; l++) widgets.push({ id: `l${l}`, type: "label", label: `Label ${l}`, value: "x" });
      for (let t = 0; t < 3; t++) widgets.push({ id: `t${t}`, type: "toggle", label: `Toggle ${t}`, value: false });
      for (let b = 0; b < 3; b++) widgets.push({ id: `b${b}`, type: "button", label: `Button ${b}` });
      for (let c = 0; c < 2; c++) widgets.push({ id: `c${c}`, type: "chart", label: `Chart ${c}`, opts: { points: 60 }, history: Array.from({ length: 60 }, (_, k) => k), value: 59 });
      for (let p = 0; p < 2; p++) widgets.push({ id: `p${p}`, type: "progress", label: `Progress ${p}`, opts: { min: 0, max: 100 }, value: 50 });
      for (let n = 0; n < 2; n++) widgets.push({ id: `i${n}`, type: "indicator", label: `Indicator ${n}`, value: true });
      widgets.push({ id: "s0", type: "select", label: "Mode", opts: { options: ["Eco", "Normal"] }, value: "Eco" });
      widgets.push({ id: "n0", type: "number", label: "Target", opts: { min: 0, max: 100 }, value: 1 });
      widgets.push({ id: "x0", type: "text", label: "Note", value: "" });
    } else {
      widgets.push({ id: "stored", type: "gauge", label: "Stored", opts: { min: 0, max: 100, unit: "%" }, value: 40 });
      widgets.push({ id: "energy", type: "label", label: "Energy", value: "1 MFE" });
      widgets.push({ id: "hist", type: "chart", label: "Stored (60 min)", opts: { points: 300 }, history: Array.from({ length: 300 }, (_, k) => k % 100), value: 1 });
    }
    out.push({ uplinkId: id, title: `Section ${i}`, online: true, widgets });
  }
  return out;
}

describe("big bases", () => {
  it("resolves a 500-section page in a reasonable time", () => {
    const uplinks = base(500);
    const t0 = performance.now();
    const pages = resolveLayout(uplinks, null);
    const ms = performance.now() - t0;
    expect(pages[0].cards.length).toBe(500);
    // generous for CI machines; it runs once per structure change, never per value delta
    expect(ms).toBeLessThan(2000);
  });

  it("saves a sparse layout that fits and resolves to the same boxes", () => {
    const uplinks = base(500);
    let pages = resolveLayout(uplinks, null, { includeHidden: true });
    // the user moved one card; the widgets were never touched
    pages = moveCardTo(pages, pages[0].cards[3].key, pages[0].id, 6, 0);
    const full = snapshotLayout(pages);
    const sparse = snapshotLayout(pages, uplinks);
    expect(Object.keys(full.widgets).length).toBeGreaterThan(5000);
    expect(Object.keys(sparse.widgets).length).toBe(0);
    expect(layoutSize(sparse)).toBeLessThan(MAX_LAYOUT_BYTES);
    const parsed = layoutDoc.safeParse(sparse);
    expect(parsed.success, parsed.success ? "" : JSON.stringify(parsed.error.issues.slice(0, 3))).toBe(true);
    const again = resolveLayout(uplinks, sparse, { includeHidden: true });
    const boxes = (p: typeof pages) => p.flatMap((x) => x.cards.flatMap((c) => [`${c.key}:${JSON.stringify(c.box)}`, ...c.widgets.map((w) => `${w.key}:${JSON.stringify(w.box)}`)])).sort();
    expect(boxes(again)).toEqual(boxes(pages));
  });

  it("keeps the widget entries of a card whose widgets were rearranged", () => {
    const uplinks = base(4);
    const pages = resolveLayout(uplinks, null, { includeHidden: true });
    const card = pages[0].cards.find((c) => c.widgets.length > 3)!;
    const moved = card.widgets[0];
    moved.box = { ...moved.box, y: moved.box.y + 20 };
    const sparse = snapshotLayout(pages, uplinks);
    expect(sparse.widgets[moved.key]).toBeDefined();
    for (const w of card.widgets) expect(sparse.widgets[w.key]).toBeDefined();
  });

  it("re-renders only the cards whose widgets changed", () => {
    const uplinks = base(50);
    const hydrator = new Hydrator(adaptComputerCard);
    const pages = resolveLayout(uplinks.map((u) => hydrator.adapted(u)), null);
    const byId = (list: Uplink[]) => new Map(list.map((u) => [u.uplinkId, u]));
    const first = pages[0].cards.map((c) => hydrator.card(c, byId(uplinks)));
    // one value of section 0 changes (the connection copies only that uplink and widget)
    const next = uplinks.map((u, i) => (i === 0 ? { ...u, widgets: u.widgets.map((w) => (w.id === "g0" ? { ...w, value: 999 } : w)) } : u));
    const second = pages[0].cards.map((c) => hydrator.card(c, byId(next)));
    const changed = second.filter((c, i) => c !== first[i]);
    expect(changed.length).toBe(1);
    expect(changed[0].widgets.find((w) => w.w.id === "g0")!.w.value).toBe(999);
  });

  it("applies a batched values message in one update", () => {
    const conn = new MainframeConn({ baseUrl: "http://x", token: "t" });
    const uplinks = base(20);
    (conn as unknown as { handle(m: unknown): void }).handle({ t: "snapshot", mainframe: { id: "m", name: "M", online: true, enabled: true }, uplinks });
    let writes = 0;
    const stop = conn.uplinks.subscribe(() => writes++);
    writes = 0;
    const s: Record<string, Record<string, unknown>> = {};
    for (const u of uplinks.slice(0, 10)) s[u.uplinkId] = u.widgets.some((w) => w.id === "g0") ? { g0: 7, c0: 60 } : { stored: 77, hist: 5 };
    const structure = conn.structure.value;
    (conn as unknown as { handle(m: unknown): void }).handle({ t: "values", s });
    expect(writes).toBe(1);
    expect(conn.structure.value).toBe(structure);
    const u0 = conn.uplinks.value[0];
    expect(u0.widgets.find((w) => w.id === "g0")!.value).toBe(7);
    expect(u0.widgets.find((w) => w.id === "c0")!.history!.at(-1)).toBe(60);
    expect(conn.uplinks.value[15]).toBe(conn.uplinks.value[15]);
    stop();
    conn.stop();
  });
});

// ---- the grid engine: the fast compact/freeSpot give exactly the results of the row-by-row originals ------------------
import { compact, freeSpot, collides, bottom, type GridItem } from "../web/src/lib/grid.ts";

const byPosRef = (a: GridItem, b: GridItem) => a.y - b.y || a.x - b.x || a.id.localeCompare(b.id);
function compactRef<T extends GridItem>(items: T[], pinned?: string): T[] {
  const out: T[] = [];
  const fixed = items.filter((i) => i.id === pinned);
  for (const it of [...items].filter((i) => i.id !== pinned).sort(byPosRef)) {
    const c = { ...it };
    while (c.y > 0 && ![...out, ...fixed].some((o) => collides({ ...c, y: c.y - 1 }, o))) c.y--;
    for (let guard = 0; [...out, ...fixed].some((o) => collides(c, o)) && guard < 1000; guard++) c.y++;
    out.push(c);
  }
  return [...out, ...fixed].sort(byPosRef);
}
function freeSpotRef(items: GridItem[], w: number, h: number, cols: number) {
  const ww = Math.min(cols, w);
  for (let y = 0; y < 10_000; y++) for (let x = 0; x + ww <= cols; x++) if (!items.some((o) => collides({ id: "\u0000", x, y, w: ww, h }, o))) return { x, y };
  return { x: 0, y: bottom(items) };
}
function random(seed: number) {
  let s = seed;
  return () => ((s = (s * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff);
}

describe("grid engine at scale", () => {
  it("compact and freeSpot match the originals on random layouts", () => {
    const rnd = random(7);
    for (let round = 0; round < 300; round++) {
      const n = 1 + Math.floor(rnd() * 25);
      const items: GridItem[] = [];
      for (let i = 0; i < n; i++) {
        const w = 1 + Math.floor(rnd() * 12);
        items.push({ id: `i${i}`, x: Math.floor(rnd() * (13 - w)), y: Math.floor(rnd() * 40), w, h: 1 + Math.floor(rnd() * 8) });
      }
      const pinned = rnd() < 0.3 ? items[Math.floor(rnd() * n)].id : undefined;
      expect(compact(items, pinned)).toEqual(compactRef(items, pinned));
      const packed = compactRef(items);
      const w = 1 + Math.floor(rnd() * 12), h = 1 + Math.floor(rnd() * 6);
      expect(freeSpot(packed, w, h, 12)).toEqual(freeSpotRef(packed, w, h, 12));
      expect(freeSpot(items, w, h, 12)).toEqual(freeSpotRef(items, w, h, 12));
    }
  });
});
