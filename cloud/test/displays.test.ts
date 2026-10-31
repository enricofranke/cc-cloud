// Cloud Displays on the dashboard: list rows, what can be picked, preview cards and frame, and the display message
// in the mock (rights, positions only with view.positions, restricted choice, share deny).
import { afterEach, describe, expect, it } from "vitest";
import { createMockServer, REACTOR, type MockServer } from "../dev/mock-server.ts";
import { normalizeDisplays, previewCards, previewFrame, targetOptions } from "../web/src/lib/displays.ts";
import { resolveLayout, type LayoutDoc } from "../web/src/lib/layout.ts";
import type { Uplink } from "../web/src/lib/types.ts";
import { TestSocket } from "./helpers.ts";

const uplinks: Uplink[] = [
  { uplinkId: "a", title: "Reactor", online: true, widgets: [{ id: "t", type: "gauge", label: "Temp", value: 3 }, { id: "c", type: "button", label: "Go", opts: { group: "g" } }] },
  { uplinkId: "s", title: "Server", online: true, restricted: true, widgets: [{ id: "tps", type: "label", label: "TPS", value: "20" }] },
];
const layout: LayoutDoc = {
  v: 2,
  pages: [{ id: "main", title: "Base" }],
  cards: { "c:wall": { title: "Wall", page: "main", x: 0, y: 0, w: 6, h: 4 } },
  widgets: { "a/t": { card: "c:wall", x: 0, y: 0, w: 6, h: 2 }, "s/tps": { card: "c:wall", x: 6, y: 0, w: 6, h: 2 } },
};

describe("display lists", () => {
  it("sanitises rows", () => {
    expect(normalizeDisplays("x")).toEqual([]);
    const [row] = normalizeDisplays([{ id: "d", w: 3, h: 2, status: "weird", target: "c:x", interactive: true }, { w: 1 }]);
    expect(row).toEqual({ id: "d", w: 3, h: 2, status: "missing", target: "c:x", interactive: true, allowRestricted: false });
  });

  it("offers restricted sections only once the display allows them", () => {
    const off = targetOptions(uplinks, layout, false);
    expect(off.map((o) => o.value)).toEqual(["a", "c:wall", "p:main"]);
    expect(off.find((o) => o.value === "c:wall")?.restricted).toBe(true);
    expect(targetOptions(uplinks, layout, true).map((o) => o.value)).toContain("s");
  });

  it("previews a section as published, one card with all its widgets", () => {
    const pages = resolveLayout(uplinks, layout);
    const [card] = previewCards("a", uplinks, pages, false)!;
    expect(card.title).toBe("Reactor");
    expect(card.widgets.map((w) => w.key).sort()).toEqual(["a/c", "a/t"]);
  });

  it("keeps restricted widgets out of a composed preview unless allowed", () => {
    const pages = resolveLayout(uplinks, layout);
    expect(previewCards("c:wall", uplinks, pages, false)![0].widgets.map((w) => w.key)).toEqual(["a/t"]);
    expect(previewCards("c:wall", uplinks, pages, true)![0].widgets.map((w) => w.key)).toEqual(["a/t", "s/tps"]);
    expect(previewCards("s", uplinks, pages, false)).toBeNull();
    expect(previewCards("", uplinks, pages, true)).toBeNull();
    expect(previewCards("c:gone", uplinks, pages, true)).toBeNull();
    expect(previewCards("p:main", uplinks, pages, false)!.length).toBeGreaterThan(0);
  });

  it("frames keep the display's aspect ratio", () => {
    for (const [w, h] of [[1, 1], [8, 6], [8, 1], [1, 6], [4, 3]]) {
      const f = previewFrame(w, h);
      expect(f.frameW / f.frameH).toBeCloseTo(w / h, 0);
      expect(f.frameW).toBeLessThanOrEqual(340);
      expect(f.frameH).toBeLessThanOrEqual(240);
      expect(f.canvasW * f.scale).toBeCloseTo(f.frameW, 0);
    }
  });
});

const sockets: TestSocket[] = [];
let mock: MockServer | undefined;
afterEach(async () => {
  sockets.splice(0).forEach((s) => s.close());
  await mock?.close();
  mock = undefined;
});

describe("display messages in the mock", () => {
  const open = async (body: object) => {
    const base = `http://127.0.0.1:${mock!.port}`;
    const token = (await (await fetch(`${base}/dev/token`, { method: "POST", body: JSON.stringify({ name: "Vitest", ...body }) })).json()).token;
    const c = new TestSocket(`ws://127.0.0.1:${mock!.port}/ws/client`);
    sockets.push(c);
    await c.opened();
    c.send({ t: "auth", token });
    await c.next("snapshot");
    return c;
  };
  const ask = async (c: TestSocket, msg: object) => {
    const reqId = `r${Math.random()}`.slice(0, 12);
    c.send({ t: "display", reqId, ...msg });
    const res = await c.next("display_result");
    expect(res.reqId).toBe(reqId);
    return res;
  };

  it("lists and changes displays; viewers get nothing", async () => {
    mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, extras: true, log: () => {} });
    const admin = await open({});
    const list = (await ask(admin, { op: "list" })).data.displays;
    expect(list.find((d: any) => d.target === REACTOR)).toMatchObject({ w: 4, h: 3, status: "ok", x: 118, dim: "minecraft:overworld" });
    expect(list.some((d: any) => d.status === "unloaded")).toBe(true);
    const id = list.find((d: any) => d.target === REACTOR).id;

    expect(await ask(admin, { op: "set", id, target: "mf-card-claims" })).toMatchObject({ ok: false, error: "not_allowed" });
    expect((await ask(admin, { op: "set", id, allowRestricted: true })).ok).toBe(true);
    expect((await ask(admin, { op: "set", id, target: "mf-card-claims" })).ok).toBe(true);
    expect(await ask(admin, { op: "set", id })).toMatchObject({ ok: false, error: "invalid_message" });
    expect(await ask(admin, { op: "set", id, target: "c:gone" })).toMatchObject({ ok: false, error: "unknown_target" });
    const unloaded = list.find((d: any) => d.status === "unloaded").id;
    expect(await ask(admin, { op: "set", id: unloaded, interactive: true })).toMatchObject({ ok: false, error: "unloaded" });
    expect(await ask(admin, { op: "unlink", id: "nope" })).toMatchObject({ ok: false, error: "unknown_display" });
    expect((await ask(admin, { op: "unlink", id })).ok).toBe(true);
    expect((await ask(admin, { op: "list" })).data.displays.some((d: any) => d.id === id)).toBe(false);

    const viewer = await open({ canControl: false });
    const denied = await ask(viewer, { op: "list" });
    expect(denied.ok).toBe(false);
    expect(denied.data).toBeUndefined();
  }, 30_000);

  it("says offline when the mainframe is", async () => {
    mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, log: () => {} });
    const admin = await open({});
    mock.base.online = false;
    const list = (await ask(admin, { op: "list" })).data.displays;
    expect(list.find((d: any) => d.target === REACTOR).status).toBe("offline");
  });
});
