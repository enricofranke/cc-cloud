// Voxel view: section decoding (the mod's LEB128 runs), greedy meshing with AO, section selection.
import { describe, expect, it } from "vitest";
import { voxelSection } from "../dev/world-sim.ts";
import { VoxelWorld, decodeRuns, meshRegion, sectionsAround } from "../web/src/network/voxels.ts";

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
