// DEV-ONLY: offline benchmark of the voxel decoder and mesher with real sections
// captured by tools/showcase/loadbench.mjs (LOADBENCH_SAVE=1 → voxel-bodies.json).
//   node dev/voxel-bench.ts <voxel-bodies.json>
// Prints decode and meshing time (per region and total), triangles and quads.
import { readFileSync } from "node:fs";
import { gunzipSync } from "node:zlib";
import { VoxelWorld, decodeBatch, decodeRunBytes, decodeRuns, meshRegion, type SectionData } from "../web/src/network/voxels.ts";

const file = process.argv[2];
if (!file) throw new Error("usage: node dev/voxel-bench.ts <voxel-bodies.json>");
const bodies = JSON.parse(readFileSync(file, "utf8")) as { url: string; type?: string; body: string }[];

const t0 = performance.now();
const sections: SectionData[] = [];
for (const b of bodies) {
  let buf = Buffer.from(b.body, "base64");
  if (buf[0] === 0x1f && buf[1] === 0x8b) buf = gunzipSync(buf); // in case the body was captured still compressed
  if (!b.type || b.type.includes("json")) {
    const j = JSON.parse(buf.toString("utf8"));
    if (!j.exposed) continue;
    sections.push({ cx: j.cx, cy: j.cy, cz: j.cz, palette: j.palette, cells: decodeRuns(j.runs, j.palette.length) });
  } else {
    for (const s of decodeBatch(new Uint8Array(buf)).sections) {
      if (s.status !== "ok" || !s.exposed || !s.runs || !s.palette) continue;
      sections.push({ cx: s.cx, cy: s.cy, cz: s.cz, palette: s.palette, cells: decodeRunBytes(s.runs, s.palette.length) });
    }
  }
}
const decodeMs = performance.now() - t0;
const world = new VoxelWorld();
for (const s of sections) world.put(s);
const regions = new Set<string>();
for (const s of sections) regions.add(`${Math.floor(s.cx / 2)},${Math.floor(s.cy / 2)},${Math.floor(s.cz / 2)}`);
// warm up the JIT like a worker that has meshed a few regions
for (const k of [...regions].slice(0, 5)) {
  const [rx, ry, rz] = k.split(",").map(Number);
  meshRegion(world, rx * 32, ry * 32, rz * 32, 32);
}
let tris = 0;
let quads = 0;
let draws = 0;
const times: number[] = [];
const t1 = performance.now();
for (const k of regions) {
  const [rx, ry, rz] = k.split(",").map(Number);
  const s = performance.now();
  const m = meshRegion(world, rx * 32, ry * 32, rz * 32, 32);
  times.push(performance.now() - s);
  tris += m.triangles;
  quads += m.triangles / 2;
  draws += [m.opaque, m.light, m.water, m.glass].filter((d) => d.idx.length).length + (m.glow.pos.length ? 1 : 0);
}
const meshMs = performance.now() - t1;
times.sort((a, b) => a - b);
console.log(
  JSON.stringify({
    sections: sections.length,
    regions: regions.size,
    decodeMs: Math.round(decodeMs),
    meshMs: Math.round(meshMs),
    perRegionMs: { p50: +times[Math.floor(times.length / 2)].toFixed(1), p95: +times[Math.floor(times.length * 0.95)].toFixed(1), max: +times[times.length - 1].toFixed(1) },
    triangles: tris,
    quads,
    drawCallsIfOneMeshPerRegionMaterial: draws,
  }),
);
