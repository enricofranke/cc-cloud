// Layout v2 + the WYSIWYG editor's pure parts: grid engine, layout operations,
// v1 → v2 migration, undo/redo, keyboard drag & drop, springs.
import { describe, expect, it } from "vitest";
import { collides, compact, flowPack, freeSpot, moveItem, resizeItem, insertItem, type GridItem } from "../web/src/lib/grid.ts";
import {
  COLS,
  findWidget,
  migrateLayout,
  moveCardTo,
  moveWidgetsTo,
  newCardWithWidgets,
  normalizeLayout,
  removeCustomCard,
  resizeCard,
  resizeWidget,
  resolveLayout,
  snapshotLayout,
  updateCard,
  updateWidget,
  type LayoutDoc,
} from "../web/src/lib/layout.ts";
import { createHistory, current, isDirty, push, redo, undo, canRedo } from "../web/src/edit/history.ts";
import { kbdStep } from "../web/src/edit/keyboard.ts";
import { springPath } from "../web/src/edit/motion.ts";
import type { Uplink } from "../web/src/lib/types.ts";

const noOverlap = (items: GridItem[]) => items.every((a) => items.every((b) => !collides(a, b)));

describe("grid engine", () => {
  const items: GridItem[] = [
    { id: "a", x: 0, y: 0, w: 6, h: 2 },
    { id: "b", x: 6, y: 0, w: 6, h: 2 },
    { id: "c", x: 0, y: 2, w: 12, h: 3 },
  ];
  it("moves an item onto another and pushes it down without overlaps", () => {
    const r = moveItem(items, "c", 0, 0, 12);
    expect(noOverlap(r)).toBe(true);
    expect(r.find((i) => i.id === "c")).toMatchObject({ x: 0, y: 0 });
    expect(r.find((i) => i.id === "a")!.y).toBe(3);
  });
  it("swaps when dragging down past a neighbour and compacts holes", () => {
    const r = moveItem(items, "a", 0, 5, 12);
    expect(noOverlap(r)).toBe(true);
    expect(r.find((i) => i.id === "c")!.y).toBe(2);
    expect(r.find((i) => i.id === "a")!.y).toBe(5);
    const rows = [
      { id: "p", x: 0, y: 0, w: 12, h: 2 },
      { id: "q", x: 0, y: 2, w: 12, h: 2 },
    ];
    expect(moveItem(rows, "p", 0, 2, 12).map((i) => [i.id, i.y])).toEqual([
      ["q", 0],
      ["p", 2],
    ]);
    expect(compact([{ id: "x", x: 0, y: 7, w: 2, h: 1 }])[0].y).toBe(0);
  });
  it("resizes with push-down, clamps to the grid and finds free spots", () => {
    const r = resizeItem(items, "a", 20, 4, 12);
    expect(r.find((i) => i.id === "a")).toMatchObject({ w: 12, h: 4 });
    expect(noOverlap(r)).toBe(true);
    expect(freeSpot(items, 6, 1, 12)).toEqual({ x: 0, y: 5 });
    expect(freeSpot([{ id: "a", x: 0, y: 0, w: 6, h: 2 }], 6, 2, 12)).toEqual({ x: 6, y: 0 });
    expect(noOverlap(insertItem(items, { id: "n", x: 3, y: 1, w: 4, h: 2 }, 12))).toBe(true);
  });
  it("flow-packs in reading order", () => {
    const r = flowPack(
      [
        { id: "1", x: 0, y: 0, w: 3, h: 2 },
        { id: "2", x: 0, y: 0, w: 6, h: 3 },
        { id: "3", x: 0, y: 0, w: 6, h: 1 },
      ],
      12,
    );
    expect(r.map((i) => [i.id, i.x, i.y])).toEqual([
      ["1", 0, 0],
      ["2", 3, 0],
      ["3", 0, 3],
    ]);
  });
});

const uplinks: Uplink[] = [
  {
    uplinkId: "r",
    title: "Reactor",
    online: true,
    widgets: [
      { id: "temp", type: "gauge", label: "Core temp", value: 900, opts: { min: 0, max: 1200 } },
      { id: "cool", type: "gauge", label: "Coolant", value: 80 },
      { id: "power", type: "toggle", label: "Power", value: true },
      { id: "scram", type: "button", label: "SCRAM" },
    ],
  },
  { uplinkId: "s", title: "Storage", online: true, widgets: [{ id: "items", type: "label", label: "Items", value: "5" }, { id: "load", type: "chart", label: "Load", history: [1, 2], value: 2 }] },
  { uplinkId: "f", title: "Farm", online: false, widgets: [{ id: "on", type: "indicator", label: "Running", value: true }] },
];

describe("layout v2", () => {
  it("places cards 4 per row by default and widgets inside a 12-column card grid", () => {
    const [page] = resolveLayout(uplinks, null);
    expect(page.cards.map((c) => [c.key, c.box.x, c.box.y, c.box.w])).toEqual([
      ["r", 0, 0, 3],
      ["s", 3, 0, 6],
      ["f", 9, 0, 3],
    ]);
    const r = page.cards[0];
    expect(r.widgets.map((w) => [w.w.id, w.box.x, w.box.y, w.box.w])).toEqual([
      ["temp", 0, 0, 6],
      ["cool", 6, 0, 6],
      ["power", 0, 5, 12],
      ["scram", 0, 6, 4], // three buttons per row
    ]);
    expect(r.box.h).toBe(2 + 8);
    expect(r.icon).toBe("flame"); // guessed from the title
  });

  it("round-trips through the saved document", () => {
    let pages = resolveLayout(uplinks, null, { includeHidden: true });
    pages = moveCardTo(pages, "f", "main", 0, 0);
    pages = updateWidget(pages, "r/temp", { display: "bar", color: "red", label: "Temp" });
    const doc = snapshotLayout(pages);
    expect(doc.v).toBe(2);
    const again = resolveLayout(uplinks, normalizeLayout(JSON.parse(JSON.stringify(doc))), { includeHidden: true });
    expect(snapshotLayout(again)).toEqual(doc);
    const w = findWidget(again, "r/temp")!.widget;
    expect(w).toMatchObject({ display: "bar", color: "red", label: "Temp" });
    expect(w.box.h).toBe(2); // the bar style is shorter
  });

  it("migrates v1 documents (order + span) to boxes", () => {
    const v1: LayoutDoc = {
      v: 1,
      pages: [{ id: "main", title: "Home" }],
      cards: { s: { order: 0, span: 2 }, r: { order: 1, span: 1, color: "red" } },
      widgets: { "r/scram": { order: 0, span: 2 }, "r/temp": { hidden: true } },
    };
    const doc = migrateLayout(uplinks, normalizeLayout(v1));
    expect(doc.v).toBe(2);
    expect(doc.cards.s).toMatchObject({ x: 0, y: 0, w: 6 });
    expect(doc.cards.r).toMatchObject({ x: 6, y: 0, w: 3, color: "red" });
    expect(doc.widgets["r/scram"]).toMatchObject({ x: 0, y: 0, w: 12 }); // span 2 of a narrow card = full width
    expect(doc.widgets["r/temp"]).toMatchObject({ hidden: true });
  });

  it("moves widgets between cards, into new cards, and back home", () => {
    let pages = resolveLayout(uplinks, null, { includeHidden: true });
    pages = moveWidgetsTo(pages, ["r/scram", "r/power"], "s", 0, 0);
    const s = pages[0].cards.find((c) => c.key === "s")!;
    expect(s.widgets.slice(0, 2).map((w) => w.key).sort()).toEqual(["r/power", "r/scram"]);
    expect(noOverlap(s.widgets.map((w) => ({ id: w.key, ...w.box })))).toBe(true);
    // actions still go to the reactor uplink
    expect(findWidget(pages, "r/scram")!.widget.uplink.uplinkId).toBe("r");
    const made = newCardWithWidgets(pages, ["s/items"], "main", 0, 20, "Mine");
    const custom = made.pages[0].cards.find((c) => c.key === made.key)!;
    expect(custom).toMatchObject({ custom: true, title: "Mine" });
    expect(custom.widgets.map((w) => w.key)).toEqual(["s/items"]);
    // saved + restored, the custom card survives
    const doc = snapshotLayout(made.pages);
    expect(doc.widgets["s/items"].card).toBe(made.key);
    const restored = resolveLayout(uplinks, normalizeLayout(doc), { includeHidden: true });
    expect(restored[0].cards.find((c) => c.key === made.key)!.widgets.map((w) => w.key)).toEqual(["s/items"]);
    // removing the custom card sends its widgets home
    const back = removeCustomCard(made.pages, made.key);
    expect(back[0].cards.some((c) => c.key === made.key)).toBe(false);
    expect(findWidget(back, "s/items")!.card.key).toBe("s");
    // a custom card that loses its last widget disappears
    const emptied = moveWidgetsTo(made.pages, ["s/items"], "s", 0, 0);
    expect(emptied[0].cards.some((c) => c.key === made.key)).toBe(false);
  });

  it("resizes cards and widgets without overlaps; cards grow to fit their widgets", () => {
    let pages = resolveLayout(uplinks, null, { includeHidden: true });
    pages = resizeCard(pages, "r", 12, 4);
    const r = pages[0].cards.find((c) => c.key === "r")!;
    expect(r.box.w).toBe(12);
    expect(noOverlap(pages[0].cards.map((c) => ({ id: c.key, ...c.box })))).toBe(true);
    pages = resizeWidget(pages, "r/temp", 12, 10);
    expect(findWidget(pages, "r/temp")!.widget.box).toMatchObject({ w: 12, h: 10 });
    expect(pages[0].cards.find((c) => c.key === "r")!.box.h).toBeGreaterThanOrEqual(2 + 10);
  });

  it("hides and shows cards and widgets at free spots", () => {
    let pages = resolveLayout(uplinks, null, { includeHidden: true });
    pages = updateCard(pages, "s", { hidden: true });
    expect(pages[0].cards.find((c) => c.key === "s")!.hidden).toBe(true);
    pages = updateCard(pages, "s", { hidden: false, style: "compact" });
    expect(pages[0].cards.find((c) => c.key === "s")).toMatchObject({ hidden: false, style: "compact" });
    pages = updateWidget(pages, "r/cool", { hidden: true });
    expect(findWidget(pages, "r/cool")!.widget.hidden).toBe(true);
    expect(resolveLayout(uplinks, normalizeLayout(snapshotLayout(pages)))[0].cards.find((c) => c.key === "r")!.widgets.some((w) => w.key === "r/cool")).toBe(false);
  });

  it("never goes below readable minimum sizes (resize, saved docs, migration)", () => {
    let pages = resolveLayout(uplinks, null, { includeHidden: true });
    pages = resizeCard(pages, "r", 1, 4);
    expect(pages[0].cards.find((c) => c.key === "r")!.box.w).toBe(3);
    pages = resizeWidget(pages, "r/scram", 1, 2);
    expect(findWidget(pages, "r/scram")!.widget.box.w).toBe(3);
    pages = resizeWidget(pages, "r/temp", 1, 5);
    expect(findWidget(pages, "r/temp")!.widget.box.w).toBe(2);
    const tiny = normalizeLayout({ v: 2, pages: [], cards: { s: { x: 0, y: 0, w: 1, h: 4 } }, widgets: { "s/load": { x: 0, y: 0, w: 1, h: 3 } } });
    const r = resolveLayout(uplinks, tiny);
    expect(r[0].cards.find((c) => c.key === "s")!.box.w).toBe(3);
    expect(findWidget(r, "s/load")!.widget.box.w).toBe(4);
    const mig = migrateLayout(uplinks, normalizeLayout({ v: 1, pages: [], cards: { r: { order: 0, span: 1 } }, widgets: { "r/scram": { span: 1 } } }));
    expect(Object.values(mig.cards).every((c) => (c.w ?? 3) >= 3)).toBe(true);
    expect(mig.widgets["r/scram"].w).toBeGreaterThanOrEqual(3);
  });

  it("rejects hostile documents field by field", () => {
    const doc = normalizeLayout({ v: 2, pages: [], cards: { "c:../x": { title: "x" }, a: { x: 99, w: 0, h: 999, color: "neon", style: "fancy" } }, widgets: { "a/b": { display: "3d", card: 5 } } });
    expect(doc.cards["c:../x"]).toBeUndefined();
    expect(doc.cards.a).toEqual({ x: COLS - 1, w: 1, h: 60 });
    expect(doc.widgets["a/b"]).toEqual({});
  });
});

describe("undo / redo", () => {
  it("steps back and forth, drops the redo branch and coalesces typing", () => {
    let h = createHistory(0);
    h = push(h, 1);
    h = push(h, 2);
    expect(isDirty(h)).toBe(true);
    h = undo(undo(h));
    expect(current(h)).toBe(0);
    expect(isDirty(h)).toBe(false);
    h = redo(h);
    expect(current(h)).toBe(1);
    h = push(h, 5);
    expect(canRedo(h)).toBe(false);
    h = push(h, 6, "label:x", 1000);
    h = push(h, 7, "label:x", 1300);
    h = push(h, 8, "label:x", 1500);
    expect(current(undo(h))).toBe(5);
  });
});

describe("keyboard drag & drop", () => {
  it("moves, resizes and moves into the next card with announcements", () => {
    let pages = resolveLayout(uplinks, null, { includeHidden: true });
    let r = kbdStep(pages, "widget", "r/cool", { key: "ArrowLeft", shiftKey: false })!;
    expect(findWidget(r.pages, "r/cool")!.widget.box.x).toBe(5);
    expect(r.message).toMatch(/Coolant in Reactor: column 6/);
    r = kbdStep(r.pages, "widget", "r/cool", { key: "ArrowRight", shiftKey: true })!;
    expect(findWidget(r.pages, "r/cool")!.widget.box.w).toBe(7);
    r = kbdStep(r.pages, "widget", "r/cool", { key: "PageDown", shiftKey: false })!;
    expect(findWidget(r.pages, "r/cool")!.card.key).toBe("s");
    // cards: down swaps past the neighbour despite compaction
    pages = moveCardTo(pages, "s", "main", 0, 10);
    const c = kbdStep(pages, "card", "r", { key: "ArrowDown", shiftKey: false })!;
    const rb = c.pages[0].cards.find((x) => x.key === "r")!.box;
    expect(rb.y).toBeGreaterThan(0);
    expect(kbdStep(pages, "card", "r", { key: "x", shiftKey: false })).toBeNull();
  });
});

describe("spring", () => {
  it("overshoots a little and settles on the target", () => {
    const p = springPath(0, 100);
    expect(p.at(-1)).toBe(100);
    expect(Math.max(...p)).toBeGreaterThan(100);
    expect(Math.max(...p)).toBeLessThan(115);
    expect(p.length).toBeLessThan(60);
  });
});
