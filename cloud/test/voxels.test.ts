// Voxel view: section decoding (the mod's LEB128 runs), the batch format, greedy meshing with AO, section
// selection and order, worker ownership, the loading line, and the mock's batch endpoint.
import { afterEach, describe, expect, it } from "vitest";
import { gunzipSync } from "node:zlib";
import { voxelSection } from "../dev/world-sim.ts";
import { encodeBatch, sectionHash } from "../dev/voxel-batch.ts";
import { createMockServer, type MockServer } from "../dev/mock-server.ts";
import { VoxelWorld, decodeBatch, decodeRunBytes, decodeRuns, meshRegion, regionOwner, sectionsAround, type PaletteEntry } from "../web/src/network/voxels.ts";
import { voxelProgress, type VoxelStatus } from "../web/src/lib/net3d.ts";

const load = (world: VoxelWorld, cx: number, cy: number, cz: number) => {
  const s = voxelSection("minecraft:overworld", cx, cy, cz);
  world.put({ cx, cy, cz, palette: s.palette, cells: decodeRuns(s.runs, s.palette.length) });
  return s;
};

describe("voxel sections", () => {
  it("decodes runs and rejects malformed data", () => {
    const s = voxelSection("minecraft:overworld", 0, -4, 0);
    const cells = decodeRuns(s.runs, s.palette.length);
    expect(cells.length).toBe(4096);
    expect([...cells].filter((c) => c).length).toBe(s.exposed);
    expect(() => decodeRuns(Buffer.from([5, 0]).toString("base64"), 1)).toThrow(); // too short
    expect(() => decodeRuns(Buffer.from([0x80, 0x20, 9]).toString("base64"), 1)).toThrow(); // index out of palette
  });

  it("meshes the Deep Base room: greedy quads, front faces outward, emissive glow", () => {
    const world = new VoxelWorld();
    for (let cx = -1; cx <= 0; cx++) for (let cy = -4; cy <= -3; cy++) for (let cz = -1; cz <= 0; cz++) load(world, cx, cy, cz);
    const m = meshRegion(world, -32 + 16, -64, -32 + 16, 32);
    expect(m.triangles).toBeGreaterThan(100);
    expect(m.triangles).toBeLessThan(40_000);
    expect(m.glow.pos.length).toBeGreaterThan(0); // sea lanterns in the ceiling
    // winding: every quad's front side is the empty side (the block is behind it)
    const { pos, idx } = m.opaque;
    let checked = 0;
    for (let t = 0; t < idx.length; t += 6) {
      const [a, b, c] = [idx[t], idx[t + 1], idx[t + 2]].map((i) => [pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2]]);
      const e1 = [b[0] - a[0], b[1] - a[1], b[2] - a[2]];
      const e2 = [c[0] - a[0], c[1] - a[1], c[2] - a[2]];
      const nrm = [e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]].map(Math.sign);
      const mid = [0, 1, 2].map((k) => (a[k] + b[k] + c[k]) / 3);
      const front = world.at(...(mid.map((v, k) => Math.floor(v + nrm[k] * 0.5)) as [number, number, number]));
      const back = world.at(...(mid.map((v, k) => Math.floor(v - nrm[k] * 0.5)) as [number, number, number]));
      expect(back, `quad ${t / 6}`).not.toBeNull();
      if (front) expect(front.flags & 1).toBe(1);
      checked++;
    }
    expect(checked).toBeGreaterThan(0);
  });

  it("selects sections around nodes within the radius, nearest first", () => {
    const s = sectionsAround([{ x: 0, y: -61, z: 0 }], 32, { x: 8, y: -56, z: 8 });
    expect(s[0]).toEqual([0, -4, 0]);
    expect(s.length).toBeGreaterThan(20);
    expect(s.every(([, cy]) => cy >= -4)).toBe(true);
  });
});

const section = (cx: number, cy: number, cz: number) => {
  const v = voxelSection("minecraft:overworld", cx, cy, cz);
  return { palette: v.palette as PaletteEntry[], runs: new Uint8Array(Buffer.from(v.runs, "base64")), exposed: v.exposed };
};

describe("voxel batch format", () => {
  it("round-trips sections with a shared palette and every status", () => {
    const a = section(0, -4, 0);
    const b = section(-1, -4, 0);
    const buf = encodeBatch("minecraft:overworld", [
      { cx: 0, cy: -4, cz: 0, status: "ok", data: a },
      { cx: -1, cy: -4, cz: 0, status: "same", data: b },
      { cx: 5, cy: -4, cz: 0, status: "empty" },
      { cx: 9, cy: -4, cz: 0, status: "out_of_range" },
      { cx: 6, cy: -4, cz: 0, status: "claimed" },
      { cx: -1, cy: -4, cz: 0, status: "ok", data: b },
    ]);
    expect([...buf.slice(0, 5)]).toEqual([0x43, 0x43, 0x56, 0x42, 1]);
    const d = decodeBatch(buf);
    expect(d.dim).toBe("minecraft:overworld");
    expect(d.sections.map((x) => x.status)).toEqual(["ok", "same", "empty", "out_of_range", "claimed", "ok"]);
    const first = d.sections[0];
    expect(first.hash).toBe(sectionHash(a));
    expect(first.exposed).toBe(a.exposed);
    expect([...decodeRunBytes(first.runs!, first.palette!.length)]).toEqual([...decodeRunBytes(a.runs, a.palette.length)]);
    expect(first.palette!.map((p) => p.id)).toEqual(a.palette.map((p) => p.id));
    expect(d.sections[1].runs).toBeUndefined(); // "same": the client keeps its cached runs
    expect(d.sections[1].palette!.length).toBe(b.palette.length);
    // the runs are copies: changing the buffer afterwards does not change them
    buf.fill(0, 9);
    expect(first.runs![0]).not.toBe(0);
  });

  it("refuses malformed batches", () => {
    const good = encodeBatch("minecraft:overworld", [{ cx: 0, cy: -4, cz: 0, status: "ok", data: section(0, -4, 0) }]);
    expect(() => decodeBatch(good.slice(0, good.length - 1))).toThrow(); // runs cut short
    expect(() => decodeBatch(new Uint8Array([...good, 0]))).toThrow(); // trailing bytes
    const v2 = good.slice();
    v2[4] = 2;
    expect(() => decodeBatch(v2)).toThrow(/version/);
    const evil = new TextEncoder().encode(JSON.stringify({ dim: "x", palette: [], sections: [{ c: [0, 0, 0], s: "ok", h: "0123456789abcdef", p: [5], n: 1, l: 0 }] }));
    const bad = new Uint8Array(9 + evil.length);
    bad.set([0x43, 0x43, 0x56, 0x42, 1, 0, 0, evil.length >> 8, evil.length & 255]);
    bad.set(evil, 9);
    expect(() => decodeBatch(bad)).toThrow(/palette/); // index beyond the batch palette
    expect(() => decodeRunBytes(new Uint8Array([0x80, 0x20, 9]), 1)).toThrow(); // index out of palette
    expect(() => decodeRunBytes(new Uint8Array([0x80, 0x20, 0, 7]), 1)).toThrow(); // trailing bytes
  });
});

describe("voxel meshing across sections", () => {
  it("merges equal blocks of different sections into one kind (fewer quads)", () => {
    const world = new VoxelWorld();
    // two sections whose palettes hold equal stone entries as different objects
    const stone = () => ({ id: "minecraft:stone", color: 0xff707070 | 0, flags: 0 });
    for (const cx of [0, 1]) {
      const cells = new Uint16Array(4096);
      for (let x = 0; x < 16; x++) for (let z = 0; z < 16; z++) cells[x + 16 * z] = 1; // floor at y = 0
      world.put({ cx, cy: 0, cz: 0, palette: [stone()], cells });
    }
    const m = meshRegion(world, 0, 0, 0, 32);
    // one quad on top and one below the 32×16 floor, plus the sides: far fewer than one per section and face
    const tops = m.opaque.idx.length / 6;
    expect(tops).toBeLessThanOrEqual(6);
  });
});

describe("voxel loading order and workers", () => {
  it("spreads regions over the worker pool evenly and stably", () => {
    const counts = [0, 0, 0, 0];
    for (let x = -8; x < 8; x++) for (let y = -2; y < 2; y++) for (let z = -8; z < 8; z++) counts[regionOwner(x, y, z, 4)]++;
    for (const c of counts) expect(c).toBeGreaterThan(1024 * 0.2);
    expect(regionOwner(3, -2, 7, 4)).toBe(regionOwner(3, -2, 7, 4));
    expect(regionOwner(3, -2, 7, 1)).toBe(0);
  });

  it("asks for the sections nearest to the mainframe first", () => {
    const s = sectionsAround([{ x: 0, y: -60, z: 0 }, { x: 200, y: -60, z: 0 }], 40, { x: 0, y: -60, z: 0 });
    const d = s.map(([cx, cy, cz]) => Math.hypot(cx * 16 + 8, cy * 16 + 8 + 60, cz * 16 + 8));
    for (let i = 1; i < d.length; i++) expect(d[i]).toBeGreaterThanOrEqual(d[i - 1] - 1e-9);
    expect(new Set(s.map((c) => c.join())).size).toBe(s.length);
  });

  it("tells the truth while loading", () => {
    const st = (o: Partial<VoxelStatus>): VoxelStatus => ({ loaded: 0, total: 100, cached: 0, triangles: 0, building: false, waiting: false, unavailable: false, ...o });
    expect(voxelProgress(st({ loaded: 37 }))).toBe("Loading blocks 37 %");
    expect(voxelProgress(st({ loaded: 37, cached: 90 }))).toBe("Updating blocks 37 %");
    expect(voxelProgress(st({ loaded: 37, waiting: true }))).toBe("Server busy – waiting (37 %)");
    expect(voxelProgress(st({ loaded: 100, building: true }))).toBe("Building blocks…");
    expect(voxelProgress(st({ loaded: 100 }))).toBe("");
    expect(voxelProgress(st({ unavailable: true }))).toBe("");
  });
});

describe("voxel batch endpoint (mock)", () => {
  let mock: MockServer | undefined;
  afterEach(async () => {
    await mock?.close();
    mock = undefined;
  });

  it("answers many sections at once, gzip, same for known hashes, budget in sections", async () => {
    mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, deepBase: true, voxelRate: 2, log: () => {} });
    const base = `http://127.0.0.1:${mock.port}`;
    const mf = [...mock.mainframes.values()].find((x) => x.name.startsWith("Deep"))!.id;
    const t = (await (await fetch(`${base}/dev/token`, { method: "POST", body: JSON.stringify({ name: "Vitest", mainframeId: mf }) })).json()).token as string;
    const post = (sections: unknown[]) =>
      fetch(`${base}/api/mainframes/${mf}/voxels/minecraft:overworld`, { method: "POST", headers: { authorization: `Bearer ${t}`, "content-type": "application/json" }, body: JSON.stringify({ sections }) });
    const res = await post([[0, -4, 0], [-1, -4, 0], [40, -4, 40]]);
    expect(res.status).toBe(200);
    expect(res.headers.get("content-type")).toBe("application/octet-stream");
    const d = decodeBatch(new Uint8Array(await res.arrayBuffer()));
    expect(d.sections.map((s) => s.status)).toEqual(["ok", "ok", "out_of_range"]);
    // the hash the client has → "same", no runs
    const again = decodeBatch(new Uint8Array(await (await post([[0, -4, 0, d.sections[0].hash]])).arrayBuffer()));
    expect(again.sections[0].status).toBe("same");
    expect(again.sections[0].runs).toBeUndefined();
    // gzip when asked for (raw http, so the body is not decompressed for us)
    const http = await import("node:http");
    const gz = await new Promise<Buffer>((resolve, reject) => {
      const r = http.request(`${base}/api/mainframes/${mf}/voxels/minecraft:overworld`, { method: "POST", headers: { authorization: `Bearer ${t}`, "accept-encoding": "gzip" } }, (res2) => {
        expect(res2.headers["content-encoding"]).toBe("gzip");
        const parts: Buffer[] = [];
        res2.on("data", (c) => parts.push(c));
        res2.on("end", () => resolve(Buffer.concat(parts)));
      });
      r.on("error", reject);
      r.end(JSON.stringify({ sections: [[0, -3, 0]] }));
    });
    expect(decodeBatch(new Uint8Array(gunzipSync(gz))).sections[0].status).toBe("ok");
    // strict body
    expect((await post([])).status).toBe(400);
    expect((await post([[1, 2]])).status).toBe(400);
    // the budget counts sections, not requests: 2/s × 8 = 16 here; 5 are used, 8 more fit, 8 after that do not
    const list = (x0: number) => Array.from({ length: 8 }, (_, i) => [x0 + i, -4, 0]);
    expect((await post(list(100))).status).toBe(200);
    const limited = await post(list(200));
    expect(limited.status).toBe(429);
    expect((await limited.json()).retryAfter).toBeGreaterThanOrEqual(1);
    // viewers get nothing
    const viewer = (await (await fetch(`${base}/dev/token`, { method: "POST", body: JSON.stringify({ name: "V", mainframeId: mf, role: "viewer" }) })).json()).token as string;
    expect((await fetch(`${base}/api/mainframes/${mf}/voxels/minecraft:overworld`, { method: "POST", headers: { authorization: `Bearer ${viewer}` }, body: JSON.stringify({ sections: [[0, -4, 0]] }) })).status).toBe(403);
  });
});
