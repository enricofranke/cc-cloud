// v0.2 storage widgets (item_search / watchlist / jobs) against the fake AE2 network.
import { afterEach, describe, expect, it } from "vitest";
import { createMockServer, ME, type MockServer } from "../dev/mock-server.ts";
import { buildItems, search, MAX_CRAFT } from "../dev/ae2-sim.ts";
import { MainframeConn } from "../web/src/lib/conn.ts";
import { fmtCount } from "../web/src/lib/format.ts";
import { TestSocket, sleep } from "./helpers.ts";

let mock: MockServer;
const sockets: TestSocket[] = [];
const conns: MainframeConn[] = [];
const base = () => `http://127.0.0.1:${mock.port}`;

afterEach(async () => {
  sockets.splice(0).forEach((s) => s.close());
  conns.splice(0).forEach((c) => c.stop());
  await mock?.close();
});

async function start() {
  mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, actionDelayMs: 5, controlRoom: false, log: () => {} });
}
async function token(body: object = {}) {
  return (await (await fetch(`${base()}/dev/token`, { method: "POST", body: JSON.stringify({ name: "Vitest", ...body }) })).json()).token as string;
}
async function client(body: object = {}) {
  const c = new TestSocket(`ws://127.0.0.1:${mock.port}/ws/client`);
  sockets.push(c);
  await c.opened();
  c.send({ t: "auth", token: await token(body) });
  const snap = await c.next("snapshot");
  return { c, snap };
}
const meWidget = (snap: any, id: string) => snap.uplinks.find((u: any) => u.uplinkId === ME).widgets.find((w: any) => w.id === id);
async function waitFor(pred: () => boolean, ms = 2000) {
  const end = Date.now() + ms;
  while (!pred()) {
    if (Date.now() > end) throw new Error("timeout");
    await sleep(10);
  }
}

describe("fake AE2 network", () => {
  it("has a few hundred items, some craftable", () => {
    const items = [...buildItems().values()];
    expect(items.length).toBeGreaterThan(200);
    expect(items.filter((i) => i.craftable).length).toBeGreaterThan(10);
    expect(items.every((i) => /^[a-z0-9_]+:[a-z0-9_]+$/.test(i.name))).toBe(true);
  });

  it("searches ids and display names case-insensitively, max 50 rows", () => {
    const items = buildItems();
    expect(search(items, "IRON INGOT").rows[0].name).toBe("minecraft:iron_ingot");
    expect(search(items, "ae2:").rows.every((r) => r.name.startsWith("ae2:"))).toBe(true);
    const many = search(items, "a");
    expect(many.rows).toHaveLength(50);
    expect(many.truncated).toBe(true);
  });
});

describe("storage protocol", () => {
  it("publishes the ME Network uplink first with all v0.2 widget types", async () => {
    await start();
    const { snap } = await client();
    expect(snap.uplinks[0].title).toBe("ME Network");
    const types = snap.uplinks[0].widgets.map((w: any) => w.type);
    expect(types).toEqual(expect.arrayContaining(["gauge", "label", "item_search", "watchlist", "jobs"]));
    expect(meWidget(snap, "watch").value[0]).toEqual({ name: expect.any(String), displayName: expect.any(String), count: expect.any(Number), min: expect.any(Number) });
    expect(meWidget(snap, "jobs").value[0]).toMatchObject({ jobId: expect.any(String), status: expect.stringMatching(/queued|running|done|failed/), progress: expect.any(Number) });
  });

  it("answers query with query_result rows", async () => {
    await start();
    const { c } = await client();
    c.send({ t: "query", reqId: "q1", uplinkId: ME, id: "search", q: "processor" });
    const r = await c.next("query_result");
    expect(r.reqId).toBe("q1");
    expect(r.rows.length).toBeGreaterThan(0);
    expect(r.rows[0]).toEqual({ name: expect.any(String), displayName: expect.stringMatching(/Processor/), count: expect.any(Number), craftable: true });
    expect(r.truncated).toBe(false);
  });

  it("limits queries to 5 per second and rejects queries on other widgets", async () => {
    await start();
    const { c } = await client();
    for (let i = 0; i < 7; i++) c.send({ t: "query", reqId: `q${i}`, uplinkId: ME, id: "search", q: "iron" });
    await sleep(100);
    const res = c.messages.filter((m) => m.t === "query_result");
    expect(res.filter((m) => !m.error)).toHaveLength(5);
    expect(res.filter((m) => m.error === "rate_limited")).toHaveLength(2);
    await sleep(1000);
    c.send({ t: "query", reqId: "bad", uplinkId: ME, id: "watch", q: "iron" });
    expect(await c.next((m) => m.reqId === "bad")).toMatchObject({ error: "unknown_widget" });
  });

  it("starts a craft and returns a jobId; jobs progress and finish", async () => {
    await start();
    const { c } = await client();
    c.send({ t: "action", reqId: "c1", uplinkId: ME, id: "search", value: { op: "craft", name: "ae2:fluix_crystal", count: 64 } });
    const r = await c.next("action_result");
    expect(r).toMatchObject({ reqId: "c1", ok: true, jobId: expect.any(String) });
    const jobs = await c.next((m) => m.t === "value" && m.id === "jobs");
    expect(jobs.value[0]).toMatchObject({ jobId: r.jobId, name: "ae2:fluix_crystal", count: 64, status: "running", progress: 0 });
    const before = mock.ae2!.items.get("ae2:fluix_crystal")!.count;
    for (let i = 0; i < 20; i++) mock.base.tick?.();
    const job = mock.ae2!.jobs.find((j) => j.jobId === r.jobId)!;
    expect(job.status).toBe("done");
    expect(mock.ae2!.items.get("ae2:fluix_crystal")!.count).toBe(before + 64);
    expect(await c.next((m) => m.t === "alert" && /Crafted 64/.test(m.text))).toBeTruthy();
  });

  it("reports craft errors", async () => {
    await start();
    const { c } = await client();
    const craft = (reqId: string, name: string, count: number) => c.send({ t: "action", reqId, uplinkId: ME, id: "search", value: { op: "craft", name, count } });
    craft("a", "minecraft:cobblestone", 1);
    expect(await c.next((m) => m.reqId === "a")).toMatchObject({ ok: false, error: "not_craftable" });
    craft("b", "ae2:fluix_crystal", MAX_CRAFT + 1);
    expect(await c.next((m) => m.reqId === "b")).toMatchObject({ ok: false, error: "too_many" });
    craft("c", "ae2:singularity", 1);
    expect(await c.next((m) => m.reqId === "c")).toMatchObject({ ok: false, error: "missing_resources" });
    craft("d1", "ae2:fluix_crystal", 1);
    craft("d2", "ae2:fluix_crystal", 1);
    craft("d3", "ae2:fluix_crystal", 1);
    expect(await c.next((m) => m.reqId === "d3")).toMatchObject({ ok: false, error: "no_cpu" });
    c.send({ t: "action", reqId: "e", uplinkId: ME, id: "search", value: { op: "remove", name: "minecraft:dirt" } });
    expect(await c.next((m) => m.reqId === "e")).toMatchObject({ ok: false, error: "invalid_value" });
    c.send({ t: "action", reqId: "f", uplinkId: ME, id: "jobs", value: true });
    expect(await c.next((m) => m.reqId === "f")).toMatchObject({ ok: false, error: "not_interactive" });
  });

  it("edits the watchlist and warns once when an item drops below its minimum", async () => {
    await start();
    const { c } = await client();
    const op = (reqId: string, value: object) => c.send({ t: "action", reqId, uplinkId: ME, id: "watch", value });
    op("add", { op: "add", name: "minecraft:emerald", min: 500 });
    expect(await c.next((m) => m.reqId === "add")).toMatchObject({ ok: true });
    const v = await c.next((m) => m.t === "value" && m.id === "watch");
    expect(v.value.find((r: any) => r.name === "minecraft:emerald")).toMatchObject({ displayName: "Emerald", count: 812, min: 500 });
    op("dup", { op: "add", name: "minecraft:emerald", min: 1 });
    expect(await c.next((m) => m.reqId === "dup")).toMatchObject({ ok: false, error: "already_watched" });
    op("set", { op: "set_min", name: "minecraft:emerald", min: 1000 });
    expect(await c.next((m) => m.reqId === "set")).toMatchObject({ ok: true });
    // the next tick sees emerald below 1000 -> one warn alert
    mock.base.tick?.();
    expect(await c.next((m) => m.t === "alert" && /Emerald below minimum/.test(m.text))).toMatchObject({ level: "warn" });
    mock.base.tick?.();
    await sleep(30);
    expect(c.messages.filter((m) => m.t === "alert" && /Emerald below/.test(m.text))).toHaveLength(0);
    op("rm", { op: "remove", name: "minecraft:emerald" });
    expect(await c.next((m) => m.reqId === "rm")).toMatchObject({ ok: true });
    op("bad", { op: "set_min", name: "not an id", min: 1 });
    expect(await c.next((m) => m.reqId === "bad")).toMatchObject({ ok: false, error: "invalid_value" });
  });

  it("does not let read-only devices craft or edit", async () => {
    await start();
    const { c } = await client({ canControl: false });
    c.send({ t: "action", reqId: "x", uplinkId: ME, id: "search", value: { op: "craft", name: "ae2:fluix_crystal", count: 1 } });
    expect(await c.next("action_result")).toMatchObject({ ok: false, error: "read_only" });
    // searching is fine for read-only devices
    c.send({ t: "query", reqId: "q", uplinkId: ME, id: "search", q: "fluix" });
    expect((await c.next("query_result")).rows.length).toBeGreaterThan(0);
  });
});

describe("client: query + structured values", () => {
  it("resolves conn.query and keeps watchlist/jobs rows as arrays", async () => {
    await start();
    const conn = new MainframeConn({ baseUrl: base(), token: await token() });
    conns.push(conn);
    conn.start();
    await waitFor(() => conn.status.value === "open");
    const me = conn.uplinks.value[0];
    expect(Array.isArray(me.widgets.find((w) => w.id === "watch")!.value)).toBe(true);
    const r = await conn.query(ME, "search", "diamond");
    expect(r.rows[0]).toMatchObject({ name: "minecraft:diamond", displayName: "Diamond", craftable: false });

    let outcome: any;
    conn.action(ME, me.widgets.find((w) => w.id === "search")!, { op: "craft", name: "ae2:logic_processor", count: 16 }, false, { onResult: (o) => ((outcome = o), true) });
    await waitFor(() => !!outcome);
    expect(outcome).toMatchObject({ ok: true, jobId: expect.any(String) });
    await waitFor(() => (conn.uplinks.value[0].widgets.find((w) => w.id === "jobs")!.value as any[]).some((j) => j.jobId === outcome.jobId));
  });

  it("rejects queries with the server error code", async () => {
    await start();
    const conn = new MainframeConn({ baseUrl: base(), token: await token() });
    conns.push(conn);
    conn.start();
    await waitFor(() => conn.status.value === "open");
    await expect(conn.query(ME, "watch", "x")).rejects.toThrow("unknown_widget");
    await fetch(`${base()}/dev/control`, { method: "POST", body: JSON.stringify({ online: false }) });
    await waitFor(() => conn.info.value?.online === false);
    await expect(conn.query(ME, "search", "x")).rejects.toThrow("offline");
  });

  it("formats counts with separators, then compact", () => {
    expect(fmtCount(0)).toBe("0");
    expect(fmtCount(1234)).toBe("1,234");
    expect(fmtCount(99_999)).toBe("99,999");
    expect(fmtCount(312_400)).toBe("312K");
    expect(fmtCount(14_250_000)).toBe("14.3M");
    expect(fmtCount(1_234_567)).toBe("1.2M");
    expect(fmtCount(4_500_000_000)).toBe("4.5B");
  });
});
