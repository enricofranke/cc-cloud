// v0.7 on the web side: cable media, capacity, power items, wireless redstone, map overlays, restricted cards.
import { afterEach, describe, expect, it } from "vitest";
import { createMockServer, type MockServer } from "../dev/mock-server.ts";
import { MainframeConn, normalizeNetwork, normalizePower, normalizeRedstone } from "../web/src/lib/conn.ts";
import { linkLabel, signalClass } from "../web/src/lib/network.ts";
import { overlaysOf } from "../web/src/lib/network.ts";
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
const accountToken = async (player: string) =>
  (await (await fetch(`${base()}/dev/token`, { method: "POST", body: JSON.stringify({ account: player }) })).json()).token as string;
async function ws(player: string) {
  const c = new TestSocket(`ws://127.0.0.1:${mock!.port}/ws/client`);
  sockets.push(c);
  await c.opened();
  c.send({ t: "auth", token: await accountToken(player), mainframeId: mock!.base.id });
  return c;
}

describe("v0.7 parsing", () => {
  it("keeps cable media, via, capacity and over_capacity", () => {
    const net = normalizeNetwork({
      nodes: [
        { id: "mf", type: "mainframe", name: "MF", dim: "minecraft:overworld", status: "online", range: 96, capacity: { devices: 20, used: 18.5, limit: 16, over: 3, wired: 4, fiber: 3 } },
        { id: "a", type: "uplink", name: "A", dim: "minecraft:overworld", status: "over_capacity", range: 48, via: "fiber" },
        { id: "b", type: "uplink", name: "B", dim: "minecraft:overworld", status: "online", range: 48, via: "laser" },
      ],
      links: [
        { a: "mf", b: "a", signal: 1, kind: "wired", medium: "fiber" },
        { a: "mf", b: "b", signal: 1, kind: "wired" },
      ],
    })!;
    expect(net.nodes[0].capacity).toEqual({ devices: 20, used: 18.5, limit: 16, over: 3, wired: 4, fiber: 3 });
    expect(net.nodes[1]).toMatchObject({ status: "over_capacity", via: "fiber" });
    expect(net.nodes[2].via).toBeUndefined();
    expect(net.links.map((l) => l.medium)).toEqual(["fiber", "cc"]);
    expect(signalClass(net.links[0])).toBe("sig-fiber");
    expect(signalClass(net.links[1])).toBe("sig-wired");
    expect(net.links.map(linkLabel)).toEqual(["fiber", "cable"]);
    // unlimited (Quantum Core)
    expect(normalizeNetwork({ nodes: [{ id: "mf", type: "mainframe", dim: "x", status: "online", range: 1, capacity: { devices: 2, used: 2, limit: null } }] })!.nodes[0].capacity!.limit).toBeNull();
  });

  it("allows a negative efficiency item only", () => {
    const p = normalizePower({ stored: 1, capacity: 2, usage: 40, breakdown: [{ key: "base", fe: 50 }, { key: "efficiency", fe: -10 }, { key: "hops", fe: -5 }, { key: "bridges", count: 2, fe: 15, fiber: 1 }] })!;
    expect(p.breakdown).toEqual([{ key: "base", fe: 50 }, { key: "efficiency", fe: -10 }, { key: "hops", fe: 0 }, { key: "bridges", count: 2, fe: 15, fiber: 1 }]);
  });

  it("validates redstone channels", () => {
    const r = normalizeRedstone({
      channels: [
        { name: "z", level: 99, signal: -3, transmitters: 1, receivers: 2 },
        { name: "bad name", level: 1 },
        { name: "a.b", level: 4.4, signal: 4, transmitters: 0, receivers: 0, override: { level: 7, until: null } },
      ],
      limit: 64,
    })!;
    expect(r.channels.map((c) => c.name)).toEqual(["a.b", "z"]);
    expect(r.channels[1]).toMatchObject({ level: 15, signal: 0 });
    expect(r.channels[0].override).toEqual({ level: 7, until: null });
    expect(normalizeRedstone(null)).toBeNull();
  });

  it("collects claims and waystones overlays from the cards", () => {
    const o = overlaysOf([
      { uplinkId: "c", title: "Claims", online: true, widgets: [], claims: { team: "B", color: 0xff0000, chunks: [{ dim: "o", x: 1, z: 2, forced: true }], truncated: false } },
      { uplinkId: "w", title: "Waystones", online: true, widgets: [], waystones: { items: [{ name: "Spawn", dim: "o", x: 1, y: 2, z: 3, kind: "waystone" }], truncated: false } },
    ]);
    expect(o.claims).toHaveLength(1);
    expect(o.waystones[0].name).toBe("Spawn");
  });
});

describe("v0.7 mock protocol", () => {
  it("sends redstone, lets operators override and refuses viewers", async () => {
    mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, extras: true, log: () => {} });
    const tom = await ws("Tom"); // team member = operator (act.redstone)
    const snap = await tom.next("snapshot");
    expect(snap.redstone.channels.find((c: any) => c.name === "alarm")).toMatchObject({ level: 15, signal: 0, override: { level: 15, until: null } });
    tom.send({ t: "rs_override", reqId: "r1", channel: "base_door", level: 0, seconds: 30 });
    expect(await tom.next((m) => m.reqId === "r1")).toMatchObject({ ok: true });
    const delta = await tom.next("redstone");
    const door = delta.channels.find((c: any) => c.name === "base_door");
    expect(door.level).toBe(0);
    expect(door.signal).toBe(15);
    expect(door.override.until).toBeGreaterThan(Date.now());
    tom.send({ t: "rs_override", reqId: "r2", channel: "no spaces", level: 1, seconds: null });
    expect(await tom.next((m) => m.reqId === "r2")).toMatchObject({ ok: false, error: "invalid_channel" });
    tom.send({ t: "rs_override", reqId: "r3", channel: "x", level: 16, seconds: null });
    expect(await tom.next((m) => m.reqId === "r3")).toMatchObject({ ok: false, error: "invalid_argument" });

    const sam = await ws("Sam"); // ally = viewer
    const ss = await sam.next("snapshot");
    sam.send({ t: "rs_override", reqId: "r4", channel: "alarm", level: null, seconds: null });
    expect(await sam.next((m) => m.reqId === "r4")).toMatchObject({ ok: false, error: "read_only" });
    // restricted mainframe cards (claims, waystones) only reach view.positions
    expect(ss.uplinks.some((u: any) => u.waystones)).toBe(false);
    expect(snap.uplinks.find((u: any) => u.waystones).waystones.items.length).toBeGreaterThan(0);
    expect(snap.uplinks.find((u: any) => u.claims).claims.chunks.length).toBeGreaterThan(0);
    expect(snap.uplinks.find((u: any) => u.claims).claims.color).toMatch(/^#[0-9a-f]{6}$/);
    // capacity on the mainframe node, fiber on the ME bridge
    const mf = snap.network.nodes.find((n: any) => n.type === "mainframe");
    expect(mf.capacity).toMatchObject({ limit: 48, fiber: 1 });
    expect(snap.network.links.some((l: any) => l.kind === "wired" && l.medium === "fiber")).toBe(true);
  });

  it("MainframeConn.rsOverride resolves and keeps the channels", async () => {
    mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, log: () => {} });
    const conn = new MainframeConn({ baseUrl: base(), token: await accountToken("Enrico"), mainframeId: mock.base.id } as never);
    conns.push(conn);
    conn.start();
    while (conn.status.value !== "open") await sleep(10);
    expect(conn.redstone.value?.channels.length).toBeGreaterThan(0);
    await conn.rsOverride("alarm", null, null);
    await sleep(30);
    expect(conn.redstone.value?.channels.find((c) => c.name === "alarm")?.override).toBeUndefined();
    await expect(conn.rsOverride("bad name", 3, null)).rejects.toThrow("invalid_channel");
  });
});
