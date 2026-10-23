// v0.7: wired links, terrain endpoint + decoding, 3D scene helpers and the bundled block models.
import { afterEach, describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { createMockServer, ME, COMPUTER, type MockServer } from "../dev/mock-server.ts";
import { computeNetwork, exampleNodes } from "../dev/network-sim.ts";
import { MAX_BYTES, terrain } from "../dev/terrain-sim.ts";
import { KINDS, decodePng, generate } from "../dev/build-models.ts";
import { normalizeNetwork } from "../web/src/lib/conn.ts";
import { signalClass } from "../web/src/lib/network.ts";
import { decodeTerrain, fetchTerrain } from "../web/src/lib/api.ts";
import { MODEL_OF, frameNodes, instanceGroups, linkSegments, pickNearest, routedThrough, searchNodes, terrainMesh, textureStatus } from "../web/src/lib/net3d.ts";
import type { NetworkInfo } from "../web/src/lib/types.ts";

const ids = { reactor: "reactor", me: "me", storage: "storage", turbine: "turbine", control: "control", computer: "computer" };

/** The mock's network in the dashboard's normalised form. */
function exampleNet(): NetworkInfo {
  const nodes = exampleNodes(ids);
  const { links, routes, status } = computeNetwork(nodes);
  return normalizeNetwork({
    nodes: nodes.map((n) => {
      const r = routes[n.id];
      return { id: n.id, type: n.type, name: n.name, pos: n.pos, dim: n.dim, status: status[n.id] === "legacy_out_of_range" ? "online" : status[n.id], range: 48, ...(r && r.length > 1 ? { parent: r[r.length - 2], hops: r.length - 1 } : {}) };
    }),
    links,
    routes,
  })!;
}

describe("wired links (v0.7)", () => {
  it("cables have no range limit, 100 % signal and win over radio", () => {
    const nodes = exampleNodes(ids);
    // move the cabled computer far out of radio range: still online over the cable
    nodes.find((n) => n.id === "computer")!.pos = { x: 900, y: 64, z: 900 };
    const { links, routes, status } = computeNetwork(nodes);
    const cable = links.find((l) => l.b === "computer")!;
    expect(cable).toMatchObject({ a: "mf", kind: "wired", signal: 1 });
    expect(routes.computer).toEqual(["mf", "computer"]);
    expect(status.computer).toBe("online");
    // no radio duplicate for a cabled pair
    expect(links.filter((l) => [l.a, l.b].sort().join() === "computer,mf")).toHaveLength(1);
  });

  it("is kept by the client and drawn as a cable", () => {
    const net = normalizeNetwork({ nodes: [{ id: "a", type: "mainframe", dim: "d", status: "online" }, { id: "b", type: "uplink", dim: "d", status: "online", parent: "a" }], links: [{ a: "a", b: "b", kind: "wired", signal: 1 }] })!;
    expect(net.links[0].kind).toBe("wired");
    expect(signalClass(net.links[0])).toBe("sig-wired");
    expect(normalizeNetwork({ nodes: net.nodes, links: [{ a: "a", b: "b", kind: "laser", signal: 1 }] })!.links[0].kind).toBe("radio");
  });
});

let mock: MockServer | undefined;
afterEach(async () => {
  await mock?.close();
  mock = undefined;
});
const base = () => `http://127.0.0.1:${mock!.port}`;
async function token(body: object = {}) {
  return (await (await fetch(`${base()}/dev/token`, { method: "POST", body: JSON.stringify({ name: "Vitest", ...body }) })).json()).token as string;
}

describe("terrain endpoint (mock)", () => {
  it("the example base cables the ME bridge and the computer", () => {
    const { links } = computeNetwork(exampleNodes({ ...ids, me: ME, computer: COMPUTER }));
    expect(links.filter((l) => l.kind === "wired").map((l) => l.b).sort()).toEqual([COMPUTER, ME].sort());
  });

  it("needs operator, is rate-limited and stays under 200 KB", async () => {
    mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, terrainRateMs: 300, log: () => {} });
    const mf = mock.base.id;
    const url = `${base()}/api/mainframes/${mf}/terrain?dim=minecraft:overworld`;
    expect((await fetch(url)).status).toBe(401);
    const viewer = await token({ role: "viewer" });
    expect((await fetch(url, { headers: { authorization: `Bearer ${viewer}` } })).status).toBe(403);
    const t = await token();
    const res = await fetch(url, { headers: { authorization: `Bearer ${t}` } });
    expect(res.status).toBe(200);
    expect(res.headers.get("cache-control")).toContain("private");
    const text = await res.text();
    expect(text.length).toBeLessThanOrEqual(MAX_BYTES);
    const doc = decodeTerrain(JSON.parse(text), "minecraft:overworld");
    expect(doc.patches.length).toBeGreaterThan(0);
    const again = await fetch(url, { headers: { authorization: `Bearer ${t}` } });
    expect(again.status).toBe(429);
    expect((await again.json()).error).toBe("rate_limited");
    // the client turns 429 into a retryable error and 403 into "no terrain"
    await expect(fetchTerrain(t, mf, "minecraft:overworld", base())).rejects.toThrow(/rate_limited:\d+/);
    expect(await fetchTerrain(viewer, mf, "minecraft:overworld", base())).toBeNull();
  });
});

describe("terrain data", () => {
  it("merges node clusters, marks unloaded chunks and levels the ground under blocks", () => {
    const nodes = exampleNodes(ids);
    const t = terrain(nodes, "minecraft:overworld");
    expect(JSON.stringify(t).length).toBeLessThanOrEqual(MAX_BYTES);
    const d = decodeTerrain(t, "minecraft:overworld");
    expect(d.patches.length).toBeGreaterThan(0);
    const p = d.patches[0];
    expect(p.heights.some((h) => Number.isNaN(h))).toBe(true); // around the unloaded turbine hall
    const mf = nodes[0].pos;
    const k = Math.round((mf.z - p.z0) / d.step) * p.w + Math.round((mf.x - p.x0) / d.step);
    expect(p.heights[k]).toBe(mf.y - 1);
    expect(decodeTerrain(terrain(nodes, "minecraft:the_nether"), "minecraft:the_nether").step).toBe(2);
  });

  it("rejects malformed patches", () => {
    const good = { x0: 0, z0: 0, w: 2, h: 2, heights: Buffer.alloc(8).toString("base64"), colors: Buffer.alloc(12).toString("base64") };
    const d = decodeTerrain({ step: 2, patches: [good, { ...good, w: 3 }, { ...good, heights: "%%%" }, { ...good, w: 1e9 }, null] }, "x");
    expect(d.patches).toHaveLength(1);
    expect(decodeTerrain(null, "x")).toMatchObject({ dim: "x", step: 2, patches: [] });
  });

  it("builds a mesh that skips unknown squares", () => {
    const heights = new Float32Array([64, 64, 64, 64, NaN, 64, 64, 64, 64]);
    const m = terrainMesh({ x0: 0, z0: 0, w: 3, h: 3, heights, colors: new Uint8Array(27).fill(200) }, 2);
    expect(m.idx.length).toBe(0); // every square touches the unknown centre
    const m2 = terrainMesh({ x0: 0, z0: 0, w: 3, h: 3, heights: heights.map((h) => (Number.isNaN(h) ? 70 : h)), colors: new Uint8Array(27).fill(200) }, 2);
    expect(m2.idx.length).toBe(4 * 6);
    expect(Math.max(...m2.pos.filter((_, i) => i % 3 === 1))).toBe(71);
  });
});

describe("3D scene helpers", () => {
  it("groups instances into few draw calls for any network size", () => {
    const g = generate();
    const net = exampleNet();
    const small = instanceGroups(net.nodes, g.models);
    // 500 nodes of every type and status still cost about the same number of groups
    const types = Object.keys(MODEL_OF) as (keyof typeof MODEL_OF)[];
    const statuses = ["online", "offline", "connecting", "no_power", "no_route", "unloaded"] as const;
    const big = Array.from({ length: 500 }, (_, i) => ({ ...net.nodes[0], id: `n${i}`, type: types[i % types.length], status: statuses[i % statuses.length], pos: { x: i, y: 64, z: i } }));
    const groups = instanceGroups(big, g.models);
    expect(groups.length).toBeLessThanOrEqual(50);
    expect(groups.reduce((n, x) => n + x.nodes.length, 0)).toBeGreaterThan(500);
    expect(small.length).toBeLessThanOrEqual(groups.length);
  });

  it("maps statuses onto the textures a block has", () => {
    expect(textureStatus("relay", "no_power", ["online", "connecting", "offline", "no_power"])).toBe("no_power");
    expect(textureStatus("uplink", "no_power", ["online", "connecting", "offline"])).toBe("offline");
    expect(textureStatus("uplink", "unloaded", ["online", "connecting", "offline"])).toBe("offline");
  });

  it("splits links into cable, radio and ender per dimension", () => {
    const net = exampleNet();
    const segs = linkSegments(net, "minecraft:overworld");
    expect(segs.filter((s) => s.link.kind === "wired")).toHaveLength(2);
    expect(segs.some((s) => s.link.kind === "radio" && s.used)).toBe(true);
    expect(segs.some((s) => s.link.kind === "radio" && !s.used)).toBe(true);
    expect(segs.every((s) => s.a.dim === "minecraft:overworld" && s.b.dim === "minecraft:overworld")).toBe(true);
  });

  it("highlights what a relay carries and the path to it", () => {
    const net = exampleNet();
    const lit = routedThrough(net, "relay-north");
    expect([...lit].sort()).toEqual(["mf", "relay-north", "storage"]);
    expect(routedThrough(net, "ender-home").has("dev-nether")).toBe(true);
  });

  it("picks the nearest block on screen, the front one on ties", () => {
    const pts = [
      { i: 0, x: 100, y: 100, depth: 0.9 },
      { i: 1, x: 102, y: 100, depth: 0.2 },
      { i: 2, x: 300, y: 300, depth: 0.1 },
      { i: 3, x: 100, y: 100, depth: 1.5 }, // behind the camera
    ];
    expect(pickNearest(pts, 101, 100, 20)).toBe(1);
    expect(pickNearest(pts, 200, 200, 20)).toBeUndefined();
  });

  it("frames nodes and searches for fly-to", () => {
    const net = exampleNet();
    const f = frameNodes(net.nodes.filter((n) => n.dim === "minecraft:overworld"));
    expect(f.r).toBeGreaterThan(100);
    expect(searchNodes(net, "rel").map((n) => n.name)).toContain("North Relay");
    expect(searchNodes(net, "nether")[0].name).toBe("Nether Ender Relay");
    expect(searchNodes(net, "  ")).toEqual([]);
  });
});

describe("bundled block models", () => {
  it("are up to date with the mod's assets", () => {
    const file = fileURLToPath(new URL("../web/src/network/models.gen.json", import.meta.url));
    expect(JSON.parse(readFileSync(file, "utf8"))).toEqual(JSON.parse(JSON.stringify(generate())));
  });

  it("have every node type and status, with textures for every part", () => {
    const g = generate();
    for (const [kind, def] of Object.entries(KINDS)) {
      const m = g.models[kind];
      expect(m.parts.length, kind).toBeGreaterThan(0);
      expect(Object.keys(m.status).sort()).toEqual([...def.statuses].sort());
      for (const p of m.parts) expect(g.textures[p.tex], p.tex).toMatch(/^data:image\/png;base64,/);
      for (const swap of Object.values(m.status)) for (const t of Object.values(swap)) expect(g.textures[t]).toBeDefined();
    }
    expect(g.models.mainframe.tall).toBe(2);
    expect(JSON.stringify(g).length).toBeLessThan(200 * 1024);
  });

  it("decodes PNG textures", () => {
    const tex = fileURLToPath(new URL("../../src/main/resources/assets/cccloud/textures/block/relay.png", import.meta.url));
    const png = decodePng(readFileSync(tex));
    expect(png.w).toBeGreaterThan(0);
    expect(png.rgba.length).toBe(png.w * png.h * 4);
  });
});
