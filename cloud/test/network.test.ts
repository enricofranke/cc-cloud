// v0.4 network: routing in the mock, protocol (positions, unlink, breakdown), client normalisation and layout helpers.
import { afterEach, describe, expect, it } from "vitest";
import { createMockServer, ME, STORAGE, type MockServer } from "../dev/mock-server.ts";
import { computeNetwork, exampleNodes } from "../dev/network-sim.ts";
import { MainframeConn, normalizeNetwork } from "../web/src/lib/conn.ts";
import { dimsOf, gridStep, hopsOf, layoutTree, mapBounds, signalBars, signalClass, uplinkOf } from "../web/src/lib/network.ts";
import { TestSocket, sleep } from "./helpers.ts";

const ids = { reactor: "reactor", me: "me", storage: "storage", turbine: "turbine", control: "control", computer: "computer" };

describe("routing (dev simulation of the mod's rules)", () => {
  it("routes over fewest hops and flags weak, unloaded, legacy and no-route nodes", () => {
    const nodes = exampleNodes(ids);
    const { routes, status, links } = computeNetwork(nodes);
    expect(routes.storage).toEqual(["mf", "relay-north", "storage"]);
    expect(links.find((l) => l.a === "mf" && l.b === "relay-north")!.signal).toBeLessThan(0.15); // weak
    expect(status["relay-east"]).toBe("no_power");
    expect(status["dev-quarry"]).toBe("no_route");
    expect(status.turbine).toBe("unloaded");
    expect(status["dev-old"]).toBe("legacy_out_of_range");
    expect(routes["dev-old"]).toEqual(["mf", "dev-old"]);
    expect(routes["dev-nether"]).toEqual(["mf", "ender-home", "ender-nether", "dev-nether"]);
    expect(links.find((l) => l.kind === "ender")).toMatchObject({ a: "ender-home", b: "ender-nether", signal: 1 });
  });

  it("re-routes when a relay gets power back (and loses it again)", () => {
    const nodes = exampleNodes(ids);
    expect(computeNetwork(nodes).routes["dev-farm"]).toEqual(["mf", "relay-west", "dev-farm"]);
    nodes.find((n) => n.id === "relay-east")!.powered = true;
    const r = computeNetwork(nodes);
    expect(r.routes["dev-farm"]).toEqual(["mf", "relay-east", "dev-farm"]); // better weakest link
    expect(r.status["dev-quarry"]).toBe("online");
  });

  it("never routes through devices and caps at 8 hops", () => {
    const { routes } = computeNetwork(exampleNodes(ids));
    for (const r of Object.values(routes)) {
      expect(r.length).toBeLessThanOrEqual(9);
      for (const hop of r.slice(1, -1)) expect(hop.startsWith("relay") || hop.startsWith("ender") || hop === "mf").toBe(true);
    }
  });
});

let mock: MockServer | undefined;
const sockets: TestSocket[] = [];
const conns: MainframeConn[] = [];
afterEach(async () => {
  sockets.splice(0).forEach((s) => s.close());
  conns.splice(0).forEach((c) => c.stop());
  await mock?.close();
  mock = undefined;
});
async function start() {
  mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, actionDelayMs: 5, log: () => {} });
}
const base = () => `http://127.0.0.1:${mock!.port}`;
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

describe("network protocol", () => {
  it("sends the graph in the snapshot; positions only to devices that can control", async () => {
    await start();
    const { snap } = await client();
    const n = snap.network.nodes.find((x: any) => x.id === STORAGE);
    // the mod's format: parent/hops on the node, device routes are the nodes before the device
    expect(n).toMatchObject({ type: "uplink", name: "Storage", dim: "minecraft:overworld", status: "online", range: 48, parent: "relay-north", hops: 2, pos: { x: 40, y: 64, z: -270 } });
    expect(snap.network.routes[STORAGE]).toEqual(["mf", "relay-north"]);
    expect(snap.network.routes["relay-north"]).toEqual(["mf", "relay-north"]);
    expect(snap.network.links.find((l: any) => l.a === "mf" && l.b === "relay-north")).toMatchObject({ weak: true, kind: "radio" });
    expect(snap.network.nodes.find((x: any) => x.id === "dev-old")).toMatchObject({ status: "online", flags: ["legacy_out_of_range"], parent: "mf" });
    expect(snap.network.nodes.find((x: any) => x.id === "relay-east")).toMatchObject({ status: "no_power", type: "advanced_relay" });
    expect(snap.network.nodes.find((x: any) => x.name === "" && x.type === "device")).toMatchObject({ status: "unloaded" });
    const ro = await client({ canControl: false });
    expect(ro.snap.network.nodes.every((x: any) => x.pos === undefined)).toBe(true);
    expect(ro.snap.network.links.every((x: any) => x.dist === undefined)).toBe(true);
    expect(ro.snap.network.routes[STORAGE]).toEqual(["mf", "relay-north"]);
  });

  it("sends Cloud Probes as their own type, with the machine they read only with positions", async () => {
    await start();
    const { snap } = await client();
    expect(snap.network.nodes.find((x: any) => x.id === "dev-farm")).toMatchObject({ type: "probe", name: "Tree Farm", target: { x: 124, y: 63, z: -106 } });
    const ro = await client({ canControl: false });
    const farm = ro.snap.network.nodes.find((x: any) => x.id === "dev-farm");
    expect(farm.type).toBe("probe");
    expect(farm.target).toBeUndefined();
  });

  it("pushes network deltas when a relay changes and unlinks nodes (control only)", async () => {
    await start();
    const { c } = await client();
    const ro = await client({ canControl: false });
    await fetch(`${base()}/dev/control`, { method: "POST", body: JSON.stringify({ relay: { id: "relay-east", powered: true } }) });
    const d = await c.next("network");
    expect(d.network.routes["dev-quarry"]).toEqual(["mf", "relay-east"]);
    expect(d.network.nodes.find((x: any) => x.id === "dev-farm")).toMatchObject({ parent: "relay-east", hops: 2 });
    expect((await ro.c.next("network")).network.nodes[0].pos).toBeUndefined();

    ro.c.send({ t: "net_unlink", reqId: "x", nodeId: "dev-quarry" });
    expect(await ro.c.next("net_result")).toMatchObject({ reqId: "x", ok: false, error: "read_only" });
    c.send({ t: "net_unlink", reqId: "m", nodeId: "mf" });
    expect(await c.next((m) => m.reqId === "m")).toMatchObject({ ok: false, error: "not_allowed" });
    c.send({ t: "net_unlink", reqId: "u", nodeId: "dev-quarry" });
    expect(await c.next((m) => m.reqId === "u")).toMatchObject({ ok: true });
    const after = await c.next("network");
    expect(after.network.nodes.some((n: any) => n.id === "dev-quarry")).toBe(false);
  });

  it("explains the mainframe's power with a breakdown", async () => {
    await start();
    const { c } = await client();
    mock!.tickPower();
    const st = await c.next((m) => m.t === "state" && m.power?.breakdown);
    const b = Object.fromEntries(st.power.breakdown.map((x: any) => [x.key, x]));
    expect(b.base.fe).toBe(40);
    // the ME bridge is on fiber: half its FE
    expect(b.bridges).toMatchObject({ count: 1, fe: 5, fiber: 1 });
    // an Efficiency card shows the saved FE as a negative item
    expect(b.efficiency.fe).toBeLessThan(0);
    expect(b.clients).toMatchObject({ count: 1, fe: 2 });
    expect(b.hops.count).toBeGreaterThan(0);
    expect(st.power.usage).toBe(st.power.breakdown.reduce((s: number, x: any) => s + x.fe, 0));
  });

  it("MainframeConn keeps the graph and unlinkNode resolves", async () => {
    await start();
    const conn = new MainframeConn({ baseUrl: base(), token: await token() });
    conns.push(conn);
    conn.start();
    while (conn.status.value !== "open") await sleep(10);
    const net = conn.network.value!;
    expect(net.nodes.length).toBe(17);
    expect(hopsOf(net, ME)).toBe(1);
    expect(uplinkOf(net, STORAGE)!.signal).toBeGreaterThan(0.15);
    await conn.unlinkNode("dev-old");
    while (conn.network.value!.nodes.some((n) => n.id === "dev-old")) await sleep(10);
  });
});

describe("client helpers", () => {
  const net = () => normalizeNetwork({ nodes: exampleNodes(ids).map((n) => ({ ...n, status: computeNetwork(exampleNodes(ids)).status[n.id], range: 48 })), ...computeNetwork(exampleNodes(ids)) })!;

  it("sanitises hostile graphs", () => {
    const n = normalizeNetwork({
      nodes: [{ id: "a", type: "mainframe", status: "online" }, { id: "a", type: "relay" }, { id: "b", type: "evil" }, { id: "c", type: "uplink", status: "weird", name: "x".repeat(99) }],
      links: [{ a: "a", b: "c", signal: 7 }, { a: "a", b: "zzz" }, { a: "a", b: "a" }],
      routes: { c: ["a", "c", 5], zzz: ["a"] },
    })!;
    expect(n.nodes.map((x) => x.id)).toEqual(["a", "c"]);
    expect(n.nodes[1]).toMatchObject({ status: "offline", name: "x".repeat(48) });
    expect(n.links).toEqual([{ a: "a", b: "c", signal: 1, dist: 0, kind: "radio" }]);
    expect(n.routes).toEqual({ c: ["a", "c"] });
    expect(normalizeNetwork(null)).toBeNull();
  });

  it("lays out a tree with the mainframe on top and orphans apart", () => {
    const t = layoutTree(net());
    const root = t.nodes.find((x) => x.node.type === "mainframe")!;
    expect(root.depth).toBe(0);
    expect(t.nodes.filter((x) => x.orphan).map((x) => x.node.id).sort()).toEqual(["dev-quarry", "relay-east"]);
    const farm = t.nodes.find((x) => x.node.id === "dev-nether")!;
    expect(farm.depth).toBe(3);
    // portrait: depth runs left to right
    const lr = layoutTree(net(), "lr");
    expect(lr.nodes.find((x) => x.node.id === "dev-nether")!.x).toBeGreaterThan(lr.nodes.find((x) => x.node.type === "mainframe")!.x);
    expect(new Set(t.nodes.map((x) => `${x.x},${x.y}`)).size).toBe(t.nodes.length); // no overlaps
  });

  it("classifies signals and builds map helpers", () => {
    expect(signalClass({ signal: 0.1, kind: "radio" })).toBe("sig-weak");
    expect(signalClass({ signal: 0.3, kind: "radio" })).toBe("sig-ok");
    expect(signalClass({ signal: 0.9, kind: "radio" })).toBe("sig-good");
    expect(signalClass({ signal: 1, kind: "ender" })).toBe("sig-ender");
    expect(signalClass({ signal: 0, kind: "radio", legacy: true })).toBe("sig-legacy");
    expect(signalBars(0.8)).toBe(4);
    expect(signalBars(0.1)).toBe(1);
    expect(dimsOf(net())).toEqual(["minecraft:overworld", "minecraft:the_nether"]);
    const b = mapBounds(net().nodes.filter((n) => n.dim === "minecraft:overworld"), false);
    expect(b.minX).toBeLessThan(-80);
    expect(b.maxY).toBeGreaterThan(70);
    expect(gridStep(400)).toBe(64);
    expect(gridStep(60)).toBe(8);
  });
});

describe("large networks (~500 nodes)", () => {
  function big(n = 500) {
    const nodes: any[] = [{ id: "mf", type: "mainframe", name: "Mainframe", status: "online", pos: { x: 0, y: 64, z: 0 }, dim: "minecraft:overworld", range: 96, deviceRange: 96 }];
    const links: any[] = [];
    const routes: Record<string, string[]> = {};
    for (let r = 0; r < 20; r++) {
      const id = `relay-${r}`;
      nodes.push({ id, type: "advanced_relay", name: `Relay ${r}`, status: "online", parent: "mf", hops: 1, pos: { x: Math.cos(r) * 200, y: 70, z: Math.sin(r) * 200 }, dim: "minecraft:overworld", range: 256, deviceRange: 128 });
      links.push({ a: "mf", b: id, signal: 0.4, dist: 200, kind: "radio" });
      routes[id] = ["mf", id];
    }
    for (let d = 0; nodes.length < n; d++) {
      const parent = `relay-${d % 20}`;
      const id = `dev-${d}`;
      nodes.push({ id, type: "uplink", name: `Device ${d}`, status: "online", parent, hops: 2, pos: { x: d, y: 64, z: -d }, dim: "minecraft:overworld", range: 48 });
      links.push({ a: parent, b: id, signal: 0.7, dist: 30, kind: "radio" });
      routes[id] = ["mf", parent];
    }
    return { nodes, links, routes };
  }

  it("normalises and lays out 500 nodes quickly", () => {
    const raw = big();
    const t0 = performance.now();
    const net = normalizeNetwork(raw)!;
    const tree = layoutTree(net);
    const lr = layoutTree(net, "lr");
    const ms = performance.now() - t0;
    expect(net.nodes).toHaveLength(500);
    expect(tree.nodes).toHaveLength(500);
    expect(tree.nodes.filter((t) => t.orphan)).toHaveLength(0);
    expect(lr.height).toBeGreaterThan(lr.width); // portrait-friendly
    expect(ms).toBeLessThan(150);
  });

  it("does not assume small messages: a ~200 KB snapshot is parsed in one piece", () => {
    const raw = big(500);
    const json = JSON.stringify({ t: "network", network: raw });
    expect(json.length).toBeGreaterThan(80_000);
    expect(normalizeNetwork(JSON.parse(json).network)!.links).toHaveLength(raw.links.length);
  });
});
