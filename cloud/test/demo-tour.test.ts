// The live demo's tour card: where it goes next to what a step highlights (demo/src/place.ts).
import { describe, expect, it } from "vitest";
import { hit, placeCard, type Rect } from "../demo/src/place.ts";

const card = { w: 368, h: 228 };
/** a 1000 × 530 Chrome window: the free area below the sticky header */
const laptop: Rect = { x: 16, y: 108, w: 968, h: 406 };
const phone: Rect = { x: 8, y: 108, w: 374, h: 660 };
const rectOf = (p: { x: number; y: number }, c = card): Rect => ({ ...p, ...c });
const shift = (r: Rect, scroll: number): Rect => ({ ...r, y: r.y - scroll });

describe("tour card placement", () => {
  it("never lands on the search field of a short laptop window (the reported bug)", () => {
    const search: Rect = { x: 31, y: 380, w: 938, h: 52 };
    const p = placeCard({ card, view: laptop, target: search, sides: ["above", "right", "left"], room: 130 });
    expect(p.clear).toBe(true);
    expect(hit(rectOf(p), search)).toBe(false);
    expect(p.side).toBe("above");
    // and when the page may scroll, the search field moves to where some results fit below it
    const s = placeCard({ card, view: laptop, target: search, sides: ["above", "right", "left"], room: 130, scrollable: true });
    const after = shift(search, s.scroll);
    expect(hit(rectOf(s), after)).toBe(false);
    expect(after.y + after.h).toBeLessThanOrEqual(laptop.y + laptop.h - 50);
    expect(s.y).toBeGreaterThanOrEqual(laptop.y);
  });

  it("puts the card beside a small target and keeps a gap", () => {
    const pencil: Rect = { x: 850, y: 48, w: 44, h: 44 };
    const p = placeCard({ card, view: { x: 16, y: 108, w: 1248, h: 596 }, target: pencil, sides: ["below", "left"] });
    expect(p.side).toBe("left");
    expect(p.x + card.w).toBeLessThanOrEqual(pencil.x - 10);
    const wide = placeCard({ card, view: { x: 16, y: 40, w: 1248, h: 664 }, target: pencil, sides: ["below", "left"] });
    expect(wide.side).toBe("below");
    expect(wide.y).toBeGreaterThanOrEqual(pencil.y + pencil.h + 10);
  });

  it("keeps clear of the other areas a step needs, also with nothing highlighted", () => {
    const legend: Rect = { x: 27, y: 650, w: 540, h: 26 };
    const view: Rect = { x: 16, y: 108, w: 1248, h: 596 };
    const end = placeCard({ card, view, target: null, avoid: [legend] });
    expect(end.clear).toBe(true);
    expect(hit(rectOf(end), legend)).toBe(false);
    const three: Rect = { x: 160, y: 182, w: 59, h: 44 };
    const p = placeCard({ card, view, target: three, avoid: [legend], sides: ["below", "right", "above", "left"] });
    expect(hit(rectOf(p), legend) || hit(rectOf(p), three)).toBe(false);
  });

  it("docks to the edge away from the target on a phone and scrolls the target into the rest", () => {
    // a target as tall as the space a card beside it would need
    const tall: Rect = { x: 20, y: 500, w: 350, h: 420 };
    const p = placeCard({ card: { w: 374, h: 175 }, view: phone, target: tall, sides: ["above", "below"], scrollable: true });
    expect(p.clear).toBe(true);
    expect(["top", "bottom", "above", "below"]).toContain(p.side);
    expect(hit(rectOf(p, { w: 374, h: 175 }), shift(tall, p.scroll))).toBe(false);
    // a target in the tab bar cannot scroll: the card sits above it
    const tab: Rect = { x: 227, y: 780, w: 83, h: 62 };
    const q = placeCard({ card: { w: 374, h: 175 }, view: phone, target: tab, sides: ["below", "right", "above", "left"] });
    expect(q.side).toBe("above");
    expect(q.y + 175).toBeLessThanOrEqual(tab.y);
  });

  it("stays on its last side while that still works", () => {
    const row: Rect = { x: 28, y: 300, w: 590, h: 70 };
    const view: Rect = { x: 16, y: 108, w: 1248, h: 596 };
    expect(placeCard({ card, view, target: row, sides: ["right", "left", "below", "above"] }).side).toBe("right");
    expect(placeCard({ card, view, target: row, sides: ["right", "left", "below", "above"], last: "below" }).side).toBe("below");
  });

  it("never covers the target when a placement is clear (random screens)", () => {
    let seed = 7;
    const rnd = () => ((seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff);
    for (let i = 0; i < 3000; i++) {
      const vw = 360 + rnd() * 1600;
      const vh = 300 + rnd() * 900;
      const view: Rect = { x: 8, y: 100, w: vw - 16, h: vh - 116 };
      const c = { w: Math.min(368, view.w), h: 150 + rnd() * 100 };
      const t: Rect = { x: view.x + rnd() * view.w * 0.9, y: 40 + rnd() * vh * 1.5, w: 20 + rnd() * view.w * 0.8, h: 20 + rnd() * 200 };
      t.w = Math.min(t.w, view.x + view.w - t.x);
      const avoid = rnd() > 0.5 ? [{ x: view.x + rnd() * view.w * 0.5, y: view.y + rnd() * view.h * 0.8, w: 200, h: 40 }] : [];
      const scrollable = rnd() > 0.3;
      const p = placeCard({ card: c, view, target: t, avoid, scrollable, room: rnd() > 0.5 ? 120 : 0 });
      const placed = rectOf(p, c);
      if (!p.clear) continue;
      expect(hit(placed, shift(t, p.scroll)), JSON.stringify({ view, c, t, p })).toBe(false);
      for (const a of avoid) expect(hit(placed, shift(a, p.scroll))).toBe(false);
    }
  });
});
