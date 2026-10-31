// The canvas widget in the dashboard: frames are checked again here, whatever the server sent (docs/SPEC.md "Canvas widget").
import { describe, expect, it } from "vitest";
import { backingScale, canvasPoint, canvasSize, parseFrame, renderFrame, themePalette, type CanvasPaint } from "../web/src/lib/canvas.ts";
import { minWidgetH, minWidgetW, resolveLayout, VIEW_SOURCES } from "../web/src/lib/layout.ts";
import type { Uplink } from "../web/src/lib/types.ts";

const frame = (...c: unknown[]) => ({ canvas: 1, c });

function fakeContext() {
  const calls: unknown[][] = [];
  const styles: unknown[] = [];
  const gradient = { addColorStop: (o: number, c: string) => calls.push(["stop", o, c]) };
  const ctx = new Proxy({} as Record<string, unknown>, {
    get: (_t, name: string) => (name === "createLinearGradient" ? () => gradient : (...a: unknown[]) => calls.push([name, ...a])),
    set: (_t, name: string, value) => {
      if (name === "fillStyle" || name === "strokeStyle" || name === "font") styles.push(value);
      return true;
    },
  });
  return { ctx: ctx as unknown as CanvasPaint, calls, styles };
}
const palette = themePalette((v) => ({ "--red": "#d34f4f", "--text": "#e7ecf1" })[v] ?? "#123456");
const env = { palette, iconMask: () => ["#......."], itemImage: () => null, font: "monospace" };

describe("frame validation", () => {
  it("keeps every valid command", () => {
    const f = parseFrame(frame(["c", "panel"], ["r", 0, 0, 1, 1, "red"], ["R", 0, 0, 1, 1, "#336699", 2], ["l", 0, 0, 1, 1, "cyan"], ["o", 1, 1, 1, "green"], ["O", 1, 1, 1, "gold", 2],
      ["p", "gold", 1, 1], ["t", 0, 0, "<b>hi</b>", "text", 8, "c"], ["g", 0, 0, 1, 1, "red", "blue", "v"], ["i", 0, 0, "flame"], ["s", 0, 0, "minecraft:iron_ingot"], ["b", 0, 0, 2, "0f f"]));
    expect(f).toHaveLength(12);
  });

  it("drops everything that could be markup, CSS or a URL", () => {
    expect(parseFrame(frame(
      ["r", 0, 0, 1, 1, "url(http://evil/x)"],
      ["r", 0, 0, 1, 1, "red;background:url(x)"],
      ["g", 0, 0, 1, 1, "red", "linear-gradient(red,blue)"],
      ["t", 0, 0, "x", "expression(alert(1))"],
      ["t", 0, 0, "a\nb", "text"],
      ["i", 0, 0, "__proto__"],
      ["i", 0, 0, "constructor"],
      ["s", 0, 0, "http://evil/x.png"],
      ["s", 0, 0, "minecraft:../../api/pair"],
      ["s", 0, 0, "minecraft:a/../b"],
      ["b", 0, 0, 2, "<>"],
      ["<img src=x onerror=alert(1)>", 0, 0],
      ["r", 0, 0, 1, 1, "constructor"],
      ["r", 0, 0, 1, 1, "__proto__"],
    ))).toEqual([]);
  });

  it("drops bad numbers and shapes without throwing", () => {
    expect(parseFrame(frame(["r", NaN, 0, 1, 1, "red"], ["r", 1e9, 0, 1, 1, "red"], ["r", 0, 0, -1, 1, "red"], ["r", "0", 0, 1, 1, "red"], ["r", 0, 0], ["t", 0, 0, "x", "red", 900], ["t", 0, 0, "y".repeat(129), "red"],
      ["p", "red", 1], ["b", 0, 0, 3, "0000"], null, 5, "r", {}, [], [[]]))).toEqual([]);
    for (const v of [null, undefined, 1, "x", [], {}, { canvas: 2, c: [] }, { canvas: 1, c: "x" }]) expect(parseFrame(v)).toEqual([]);
  });

  it("caps a frame even if the server let too much through", () => {
    expect(parseFrame(frame(...Array.from({ length: 5000 }, () => ["r", 0, 0, 1, 1, "red"])))).toHaveLength(1024);
    expect(parseFrame(frame(...Array.from({ length: 100 }, () => ["s", 0, 0, "minecraft:stone"])))).toHaveLength(64);
    expect(canvasSize({ width: 100000, height: "x" })).toEqual({ w: 160, h: 90 });
    expect(canvasSize({ width: 320, height: 200 })).toEqual({ w: 320, h: 200 });
  });
});

describe("rendering", () => {
  it("only ever hands the context palette colours and strict hex", () => {
    const { ctx, styles } = fakeContext();
    renderFrame(ctx, parseFrame(frame(["r", 0, 0, 1, 1, "red"], ["r", 0, 0, 1, 1, "#112233"], ["r", 0, 0, 1, 1, "blue"], ["r", 0, 0, 1, 1, "url(x)"])), 10, 10, env);
    expect(styles).toEqual(["#d34f4f", "#112233", "#3366cc"]);
  });

  it("draws text as text and bitmaps as runs", () => {
    const { ctx, calls } = fakeContext();
    renderFrame(ctx, parseFrame(frame(["t", 1, 2, "<script>", "text"], ["b", 0, 0, 3, "000 f ", 2])), 10, 10, env);
    expect(calls).toContainEqual(["fillText", "<script>", 1, 2]);
    expect(calls.filter((c) => c[0] === "fillRect")).toEqual([["fillRect", 0, 0, 6, 2], ["fillRect", 2, 2, 2, 2]]);
  });

  it("draws an item that is not loaded as a tile", () => {
    const { ctx, calls } = fakeContext();
    renderFrame(ctx, parseFrame(frame(["s", 2, 2, "minecraft:stone", 16])), 10, 10, env);
    expect(calls).toEqual([["fillRect", 2, 2, 16, 16]]);
  });
});

describe("pointer and scale", () => {
  it("maps a pointer to a logical pixel on the surface", () => {
    expect(canvasPoint(100, 50, { width: 200, height: 100 }, 400, 200)).toEqual({ x: 200, y: 100 });
    expect(canvasPoint(-5, 999, { width: 200, height: 100 }, 400, 200)).toEqual({ x: 0, y: 199 });
  });

  it("keeps the backing store a bounded whole multiple", () => {
    expect(backingScale(800, 160, 90, 2)).toBe(10);
    expect(backingScale(8000, 480, 320, 3)).toBe(4);
    expect(backingScale(10, 160, 90, 1)).toBe(1);
  });
});

describe("layout", () => {
  const u = (extra: Partial<Uplink>): Uplink => ({ uplinkId: "u", title: "Base", online: true, widgets: [], ...extra });

  it("gives a canvas a wide card in its proportions", () => {
    const [page] = resolveLayout([u({ widgets: [{ id: "m", type: "canvas", opts: { width: 160, height: 90 } }] })], null);
    expect(page.cards[0].box.w).toBe(6);
    expect(page.cards[0].widgets[0].box).toMatchObject({ w: 12, h: 11 });
    expect(minWidgetW("canvas")).toBe(4);
    expect(minWidgetH("canvas")).toBe(5);
    expect(VIEW_SOURCES.canvas).toEqual(["canvas"]);
  });

  it("places groups where the script asks, with their colour and icon", () => {
    const uplink = u({
      groups: [{ id: "a", title: "Map", w: 8, h: 12, x: 0, y: 0, color: "purple", icon: "star" }, { id: "b", title: "Log", w: 4, h: 12, x: 8, y: 0 }],
      widgets: [{ id: "x", type: "canvas", opts: { group: "a" } }, { id: "y", type: "label", value: "hi", opts: { group: "b" } }],
    });
    const [page] = resolveLayout([uplink], null);
    const map = page.cards.find((c) => c.title === "Map")!;
    const log = page.cards.find((c) => c.title === "Log")!;
    expect(map.box).toEqual({ x: 0, y: 0, w: 8, h: 12 });
    expect(log.box).toEqual({ x: 8, y: 0, w: 4, h: 12 });
    expect(map.color).toBe("purple");
    expect(map.icon).toBe("star");
  });

  it("lets the editor win over the script", () => {
    const uplink = u({ groups: [{ id: "a", title: "Map", w: 8, h: 12, x: 0, y: 0, color: "purple" }], widgets: [{ id: "x", type: "label", value: "hi", opts: { group: "a" } }] });
    const [page] = resolveLayout([uplink], { v: 2, version: 2, pages: [], cards: { "u/a": { x: 3, y: 2, w: 6, h: 5, color: "cyan" } }, widgets: {} } as never);
    expect(page.cards[0].box).toEqual({ x: 3, y: 0, w: 6, h: 5 });
    expect(page.cards[0].color).toBe("cyan");
  });

  it("composes widgets on the card's grid from Lua", () => {
    const uplink = u({
      widgets: [
        { id: "a", type: "gauge", value: 1, opts: { x: 0, y: 0, w: 4, h: 6 } },
        { id: "b", type: "canvas", opts: { x: 4, y: 0, w: 8, h: 12 } },
      ],
    });
    const [page] = resolveLayout([uplink], null);
    const [a, b] = page.cards[0].widgets;
    expect(a.box).toEqual({ x: 0, y: 0, w: 4, h: 6 });
    expect(b.box).toEqual({ x: 4, y: 0, w: 8, h: 12 });
  });

  it("ignores boxes that are out of range", () => {
    const [page] = resolveLayout([u({ widgets: [{ id: "a", type: "label", value: "x", opts: { x: 99, w: 500 } as never }] })], null);
    expect(page.cards[0].widgets[0].box.w).toBeLessThanOrEqual(12);
  });
});
