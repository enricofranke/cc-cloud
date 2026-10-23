// Map tiles (surface/slice) in the mock, the client's tile selection and label placement.
import { afterEach, describe, expect, it } from "vitest";
import { createMockServer, type MockServer } from "../dev/mock-server.ts";
import { DEEP_BASE, blockAt, mapTile, voxelSection } from "../dev/world-sim.ts";
import { medianY, tileKey, tilePath, tilesFor } from "../web/src/lib/maptiles.ts";
import { placeLabels } from "../web/src/lib/network.ts";

let mock: MockServer | undefined;
afterEach(async () => {
  await mock?.close();
  mock = undefined;
});
const base = () => `http://127.0.0.1:${mock!.port}`;
async function token(body: object = {}) {
  return (await (await fetch(`${base()}/dev/token`, { method: "POST", body: JSON.stringify({ name: "Vitest", ...body }) })).json()).token as string;
}
const deepOf = (m: MockServer) => [...m.mainframes.values()].find((x) => x.name.startsWith("Deep"))!;

describe("map tiles (mock)", () => {
  it("serves surface and slice PNGs near the network only, with ETag and rate limit", async () => {
    mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, deepBase: true, tileRate: 3, log: () => {} });
    const mf = deepOf(mock).id;
    const t = await token({ mainframeId: mf });
    const get = (path: string, h: Record<string, string> = {}) => fetch(`${base()}/api/mainframes/${mf}/map/minecraft:overworld/${path}`, { headers: { authorization: `Bearer ${t}`, ...h } });
    const res = await get("surface/0/0.png");
    expect(res.status).toBe(200);
    expect(res.headers.get("content-type")).toBe("image/png");
    expect(res.headers.get("cache-control")).toContain("private");
    const etag = res.headers.get("etag")!;
    expect((await get("surface/0/0.png", { "if-none-match": etag })).status).toBe(304);
    expect((await get("slice/-59/-1/-1.png")).status).toBe(200);
    expect((await get("surface/40/40.png")).status).toBe(403); // far away from every node
    expect((await get("surface/0/-1.png")).status).toBe(429); // 3 per minute in this test
    const viewer = await token({ mainframeId: mf, role: "viewer" });
    expect((await fetch(`${base()}/api/mainframes/${mf}/map/minecraft:overworld/surface/0/0.png`, { headers: { authorization: `Bearer ${viewer}` } })).status).toBe(403);
  });
});

describe("world simulation", () => {
  it("has the underground base, a shaft and exposed-only voxels", () => {
    expect(blockAt("minecraft:overworld", DEEP_BASE.mainframe.x, DEEP_BASE.mainframe.y, DEEP_BASE.mainframe.z)).toBe("mainframe");
    expect(blockAt("minecraft:overworld", 0, -59, 3)).toBe("air"); // inside the room
    // the slice at the room height shows the room interior, rock around is dark
    const tile = mapTile("minecraft:overworld", "slice", 0, 0, -59);
    const px = (x: number, z: number) => tile[(z * 128 + x) * 4 + 3];
    expect(px(0, 3)).toBe(255);
    // voxels: solid rock deep inside is never sent (anti x-ray)
    const sec = voxelSection("minecraft:overworld", 6, -2, 6);
    const room = voxelSection("minecraft:overworld", 0, -4, 0);
    expect(room.exposed).toBeGreaterThan(50);
    expect(sec.exposed).toBeLessThan(4096);
    expect(room.palette.some((p) => p.id === "cccloud:cloud_mainframe")).toBe(true);
  });
});

describe("client tiles", () => {
  it("picks tiles in view near nodes, centre first", () => {
    const t = tilesFor({ x: -200, y: -200, w: 400, h: 400 }, [{ x: 0, z: 0 }]);
    expect(t[0]).toEqual({ tx: -1, tz: -1 });
    expect(t.length).toBe(16);
    expect(tilesFor({ x: 5000, y: 5000, w: 100, h: 100 }, [{ x: 0, z: 0 }])).toEqual([]);
    expect(medianY([-61, -60, -61, 87, 80])).toBe(-60);
    expect(tilePath("m1", "minecraft:overworld", "slice", -59.4, { tx: -1, tz: 2 })).toBe("/api/mainframes/m1/map/minecraft:overworld/slice/-59/-1/2.png");
    expect(tileKey("d", "surface", 7, { tx: 1, tz: 2 })).toBe("d|surface||1|2");
  });
});

describe("label placement", () => {
  it("never overlaps labels of close nodes and drops the least important", () => {
    const placed = placeLabels([
      { id: "pc", x: 100, y: 100, text: "Computer #0", priority: 10, r: 9 },
      { id: "rs", x: 108, y: 100, text: "RS Network", priority: 10, r: 9 },
      { id: "mf", x: 104, y: 104, text: "Mainframe", priority: 50, r: 9 },
    ]);
    const shown = [...placed.entries()].filter(([, v]) => v);
    expect(placed.get("mf")).toBeTruthy();
    expect(shown.length).toBeGreaterThanOrEqual(2);
    // rough overlap check of the chosen boxes
    const box = (id: string, x: number, y: number, len: number) => {
      const p = placed.get(id)!;
      const w = len * 6.6 + 4;
      const cx = x + p.x;
      const x0 = p.anchor === "middle" ? cx - w / 2 : p.anchor === "start" ? cx : cx - w;
      return { x0, x1: x0 + w, y0: y + p.y - 11, y1: y + p.y + 2 };
    };
    const items = [["pc", 100, 100, 11], ["rs", 108, 100, 10], ["mf", 104, 104, 9]] as const;
    const boxes = items.filter(([id]) => placed.get(id)).map(([id, x, y, l]) => box(id, x, y, l));
    for (let i = 0; i < boxes.length; i++)
      for (let j = i + 1; j < boxes.length; j++) {
        const a = boxes[i];
        const b = boxes[j];
        expect(a.x0 < b.x1 && b.x0 < a.x1 && a.y0 < b.y1 && b.y0 < a.y1, `${i}/${j}`).toBe(false);
      }
  });
});
