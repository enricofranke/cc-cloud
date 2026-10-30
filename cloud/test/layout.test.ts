// Layout pages/cards/widgets: pure resolver + editor helpers, the layout_set
// protocol against the mock, and the v0.2 Lua widgets (chart/select/number/...).
import { afterEach, describe, expect, it } from "vitest";
import { createMockServer, CONTROL, ME, REACTOR, type MockServer } from "../dev/mock-server.ts";
import { layoutDoc, MAX_LAYOUT_BYTES, MAX_LAYOUT_CARDS } from "../dev/protocol.ts";
import {
  addPage,
  deletePage,
  layoutSize,
  moveCard,
  movePage,
  moveWidget,
  normalizeLayout,
  resolveLayout,
  snapshotLayout,
  resizeCard,
  resizeWidget,
  updateCard,
  updatePage,
  updateWidget,
} from "../web/src/lib/layout.ts";
import { MainframeConn } from "../web/src/lib/conn.ts";
import type { Uplink } from "../web/src/lib/types.ts";
import { TestSocket, sleep } from "./helpers.ts";

let mock: MockServer | undefined;
const sockets: TestSocket[] = [];
const conns: MainframeConn[] = [];
afterEach(async () => {
  sockets.splice(0).forEach((s) => s.close());
  conns.splice(0).forEach((c) => c.stop());
  await mock?.close();
  mock = undefined;
});
const base = () => `http://127.0.0.1:${mock!.port}`;
async function start() {
  mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, actionDelayMs: 5, log: () => {} });
}
async function token(body: object = {}) {
  return (await (await fetch(`${base()}/dev/token`, { method: "POST", body: JSON.stringify({ name: "Vitest", ...body }) })).json()).token as string;
}
async function client(body: object = {}) {
  const c = new TestSocket(`ws://127.0.0.1:${mock!.port}/ws/client`);
  sockets.push(c);
  await c.opened();
  c.send({ t: "auth", token: await token(body) });
  return { c, snap: await c.next("snapshot") };
}
async function waitFor(pred: () => boolean, ms = 2000) {
  const end = Date.now() + ms;
  while (!pred()) {
    if (Date.now() > end) throw new Error("timeout");
    await sleep(10);
  }
}

// a small fixture: one plain uplink, one Lua v2 uplink with pages + groups
const UPLINKS: Uplink[] = [
  {
    uplinkId: "r",
    title: "Reactor",
    online: true,
    widgets: [
      { id: "scram", type: "button", label: "SCRAM" },
      { id: "temp", type: "gauge", label: "Temp" },
      { id: "power", type: "toggle", label: "Power" },
    ],
  },
  {
    uplinkId: "c",
    title: "Control",
    online: true,
    pages: [{ id: "power", title: "Power", icon: "bolt" }],
    groups: [{ id: "grid", title: "Grid", page: "power", cols: 2 }],
    widgets: [
      { id: "load", type: "chart", label: "Load", opts: { group: "grid" } },
      { id: "mode", type: "select", label: "Mode", opts: { group: "grid", options: ["a", "b"] } },
      { id: "misc", type: "label", label: "Misc" },
      { id: "solo", type: "indicator", label: "Solo", opts: { page: "power", order: 5 } },
    ],
  },
];

describe("resolveLayout (defaults)", () => {
  it("builds Overview + script pages, one card per uplink/group/page", () => {
    const pages = resolveLayout(UPLINKS, null);
    expect(pages.map((p) => p.id)).toEqual(["main", "power"]);
    expect(pages[0].cards.map((c) => c.key)).toEqual(["r", "c"]);
    expect(pages[1].cards.map((c) => c.key)).toEqual(["c/grid", "c~power"]);
    const grid = pages[1].cards[0];
    expect(grid).toMatchObject({ title: "Grid", color: "gold" });
    expect(grid.box.w).toBe(6); // cols = 2 → half the 12-column page
    // default widget order: readouts first, buttons last
    expect(pages[0].cards[0].widgets.map((w) => w.w.id)).toEqual(["temp", "power", "scram"]);
  });

  it("applies a layout document and skips unknown ids", () => {
    const doc = normalizeLayout({
      v: 1,
      pages: [
        { id: "power", title: "Energy", icon: "bolt" },
        { id: "main", title: "Home", icon: "home" },
      ],
      cards: { r: { page: "power", order: 1, span: 3, color: "red", icon: "flame", title: "Core" }, ghost: { page: "x" } },
      widgets: { "r/scram": { order: 0, label: "Emergency stop" }, "r/power": { hidden: true }, "nope/x": { order: 1 } },
    });
    const pages = resolveLayout(UPLINKS, doc);
    expect(pages.map((p) => p.title)).toEqual(["Energy", "Home"]);
    const core = pages[0].cards.find((c) => c.key === "r")!;
    expect(core).toMatchObject({ title: "Core", color: "red", icon: "flame" });
    expect(core.box.w).toBe(9); // v1 span 3 of 4 → 9 of 12
    expect(core.widgets.map((w) => w.label)).toEqual(["Emergency stop", "Temp"]); // power hidden
    expect(resolveLayout(UPLINKS, doc, { includeHidden: true })[0].cards.find((c) => c.key === "r")!.widgets).toHaveLength(3);
  });

  it("sanitises hostile layout input", () => {
    const doc = normalizeLayout({
      v: 1,
      pages: [{ id: "bad id!", title: "x" }, { id: "ok", title: "y".repeat(500), icon: "<script>" }, { id: "ok", title: "dup" }],
      cards: { a: { span: 99, color: "hotpink", hidden: "yes" } },
      widgets: "nope",
    });
    expect(doc.pages).toEqual([{ id: "ok", title: "y".repeat(48) }]);
    expect(doc.cards.a).toEqual({ span: 4 });
    expect(doc.widgets).toEqual({});
  });
});

describe("layout editor helpers", () => {
  it("moves, edits and round-trips through the saved document", () => {
    let pages = resolveLayout(UPLINKS, null, { includeHidden: true });
    pages = moveCard(pages, "c", "power", 0);
    pages = updateCard(pages, "c/grid", { title: "Grid ⚡", color: "cyan", icon: "chart", hidden: false });
    pages = resizeCard(pages, "c/grid", 12, 6);
    pages = moveWidget(pages, "r", "r/scram", 0);
    pages = resizeWidget(pages, "r/temp", 6, 5);
    pages = updateWidget(pages, "r/temp", { label: "Core temp", hidden: true });
    const added = addPage(pages, "Farm stuff", "leaf");
    pages = added.pages;
    expect(added.id).toBe("farm_stuff");
    expect(addPage(pages, "Farm stuff").id).toBe("farm_stuff_2");
    pages = updatePage(pages, "power", { title: "Power!" });
    pages = movePage(pages, "farm_stuff", -1);
    expect(pages.map((p) => p.id)).toEqual(["main", "farm_stuff", "power"]);

    const doc = snapshotLayout(pages);
    // what the browser sends must pass the server-side schema
    expect(layoutDoc.safeParse(doc).success).toBe(true);
    expect(layoutSize(doc)).toBeLessThan(64 * 1024);

    const again = resolveLayout(UPLINKS, normalizeLayout(JSON.parse(JSON.stringify(doc))), { includeHidden: true });
    expect(again.map((p) => [p.id, p.title, p.cards.map((c) => c.key)])).toEqual(pages.map((p) => [p.id, p.title, p.cards.map((c) => c.key)]));
    const grid = again[2].cards.find((c) => c.key === "c/grid")!;
    expect(grid).toMatchObject({ title: "Grid ⚡", color: "cyan", icon: "chart" });
    expect(grid.box.w).toBe(12);
    const r = again[0].cards.find((c) => c.key === "r")!;
    expect(r.widgets.map((w) => [w.w.id, w.label, w.hidden])).toEqual([
      ["scram", "SCRAM", false],
      ["power", "Power", false],
      ["temp", "Core temp", true],
    ]);
  });

  it("deleting a page moves its cards to the first page; the last page stays", () => {
    let pages = resolveLayout(UPLINKS, null);
    pages = deletePage(pages, "power");
    expect(pages.map((p) => p.id)).toEqual(["main"]);
    expect(pages[0].cards.map((c) => c.key).sort()).toEqual(["c", "c/grid", "c~power", "r"]);
    expect(deletePage(pages, "main")).toBe(pages);
  });
});

describe("layout protocol", () => {
  it("saves a layout, confirms it and pushes it to every client", async () => {
    await start();
    const a = await client();
    const b = await client();
    expect(a.snap.layout).toBeNull();
    const doc = { v: 1, pages: [{ id: "main", title: "Home", icon: "home" }], cards: { [REACTOR]: { page: "main", order: 0, span: 2, color: "red" } }, widgets: {} };
    a.c.send({ t: "layout_set", reqId: "l1", layout: doc });
    expect(await a.c.next("layout_result")).toEqual({ t: "layout_result", reqId: "l1", ok: true });
    expect((await b.c.next("layout")).layout).toEqual(doc);
    // new connections get it in the snapshot
    expect((await client()).snap.layout).toEqual(doc);
  });

  it("rejects read-only devices, invalid and oversized layouts", async () => {
    await start();
    const ro = await client({ canControl: false });
    ro.c.send({ t: "layout_set", reqId: "r", layout: { v: 1, pages: [], cards: {}, widgets: {} } });
    expect(await ro.c.next("layout_result")).toMatchObject({ ok: false, error: "read_only" });
    const { c } = await client();
    c.send({ t: "layout_set", reqId: "i", layout: { v: 1, pages: [{ id: "x", title: "x", icon: "nope" }], cards: {}, widgets: {} } });
    expect(await c.next("layout_result")).toMatchObject({ reqId: "i", ok: false, error: "invalid_layout" });
    c.send({ t: "layout_set", reqId: "e", layout: { v: 1, pages: [], cards: { a: { evil: true } }, widgets: {} } });
    expect(await c.next("layout_result")).toMatchObject({ reqId: "e", ok: false, error: "invalid_layout" });
    // just over the 256 KB layout limit (the frame limit for layout_set is 256 KB + 1 KB envelope; big bases, docs/SPEC.md "Scaling")
    const cards: Record<string, object> = {};
    for (let i = 0, size = 0; size < MAX_LAYOUT_BYTES + 200; i++) {
      const key = `card_${i}_${"x".repeat(80)}`;
      cards[key] = { title: "y".repeat(48) };
      size += key.length + JSON.stringify(cards[key]).length + 4;
    }
    // bigger layouts are refused with too_large, the socket stays open
    c.send({ t: "layout_set", reqId: "big", layout: { v: 1, pages: [], cards, widgets: {} } });
    expect(await c.next((m) => m.reqId === "big")).toMatchObject({ ok: false, error: "too_large" });
    expect(c.ws.readyState).toBe(1);
    const mid: Record<string, object> = {};
    for (let i = 0; i < MAX_LAYOUT_CARDS + 1; i++) mid[`c${i}`] = { order: i };
    c.send({ t: "layout_set", reqId: "mid", layout: { v: 1, pages: [], cards: mid, widgets: {} } });
    expect(await c.next((m) => m.reqId === "mid")).toMatchObject({ ok: false, error: "invalid_layout" }); // > 2048 cards
  });

  it("MainframeConn.saveLayout resolves and updates the layout signal", async () => {
    await start();
    const conn = new MainframeConn({ baseUrl: base(), token: await token() });
    conns.push(conn);
    conn.start();
    await waitFor(() => conn.status.value === "open");
    let pages = resolveLayout(conn.uplinks.value, conn.layout.value, { includeHidden: true });
    expect(pages.map((p) => p.id)).toEqual(["main", "power", "farm"]);
    pages = updateCard(pages, ME, { title: "Storage", color: "purple" });
    await conn.saveLayout(snapshotLayout(pages));
    expect(conn.layout.value?.cards[ME]).toMatchObject({ title: "Storage", color: "purple" });
    const ro = new MainframeConn({ baseUrl: base(), token: await token({ canControl: false }) });
    conns.push(ro);
    ro.start();
    await waitFor(() => ro.status.value === "open");
    expect(ro.layout.value?.cards[ME]).toMatchObject({ title: "Storage" });
    await expect(ro.saveLayout(snapshotLayout(pages))).rejects.toThrow("read_only");
  });
});

describe("v0.2 Lua widgets", () => {
  it("validates select, buttons, number and text actions", async () => {
    await start();
    const { c } = await client();
    const act = (reqId: string, id: string, value: unknown) => c.send({ t: "action", reqId, uplinkId: CONTROL, id, value });
    act("s1", "mode", "boost");
    expect(await c.next((m) => m.reqId === "s1")).toMatchObject({ ok: true });
    expect(await c.next((m) => m.t === "value" && m.id === "mode")).toMatchObject({ value: "boost" });
    act("s2", "mode", "turbo");
    expect(await c.next((m) => m.reqId === "s2")).toMatchObject({ ok: false, error: "invalid_value" });
    act("b1", "priority", "High");
    expect(await c.next((m) => m.reqId === "b1")).toMatchObject({ ok: true });
    act("n1", "limit", 123);
    expect(await c.next((m) => m.t === "value" && m.id === "limit")).toMatchObject({ value: 120 });
    act("n2", "limit", 9999);
    expect(await c.next((m) => m.reqId === "n2")).toMatchObject({ ok: false, error: "invalid_value" });
    act("t1", "sign", "Hello\u0007 <b>world</b> " + "x".repeat(40));
    const v = await c.next((m) => m.t === "value" && m.id === "sign");
    expect(v.value).toBe(("Hello <b>world</b> " + "x".repeat(40)).slice(0, 32));
    act("i1", "gens", false);
    expect(await c.next((m) => m.reqId === "i1")).toMatchObject({ ok: false, error: "invalid_value" });
  });

  it("sends chart history in the snapshot and appends value deltas on the client", async () => {
    await start();
    const conn = new MainframeConn({ baseUrl: base(), token: await token() });
    conns.push(conn);
    conn.start();
    await waitFor(() => conn.status.value === "open");
    const chart = () => conn.uplinks.value.find((u) => u.uplinkId === CONTROL)!.widgets.find((w) => w.id === "load")!;
    expect(chart().history).toHaveLength(60);
    const before = chart().history!.slice();
    mock!.base.tick?.();
    await waitFor(() => chart().history!.at(-2) === before.at(-1) && chart().history![0] === before[1]);
    expect(chart().history).toHaveLength(60); // capped at opts.points
  });

  it("publishes pages and groups on the uplink", async () => {
    await start();
    const { snap } = await client();
    const cr = snap.uplinks.find((u: any) => u.uplinkId === CONTROL);
    expect(cr.pages.map((p: any) => p.id)).toEqual(["power", "farm"]);
    expect(cr.groups.find((g: any) => g.id === "grid")).toMatchObject({ page: "power", cols: 2 });
    const pages = resolveLayout(snap.uplinks, null);
    expect(pages.find((p) => p.id === "power")!.cards.map((c) => c.title)).toEqual(["Power Grid", "Induction Matrix", "Server"]);
  });
});
