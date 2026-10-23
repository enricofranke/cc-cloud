// Web Worker: decodes voxel sections and greedy-meshes 32³ regions off the main thread.
import { VoxelWorld, decodeRuns, meshRegion, type PaletteEntry } from "./voxels.ts";

const REGION = 32;
const world = new VoxelWorld();
const dirty = new Set<string>();
let timer: ReturnType<typeof setTimeout> | undefined;

type In = { type: "section"; cx: number; cy: number; cz: number; palette: PaletteEntry[]; runs: string } | { type: "reset" };

self.onmessage = (ev: MessageEvent<In>) => {
  const m = ev.data;
  if (m.type === "reset") {
    world.sections.clear();
    dirty.clear();
    return;
  }
  let cells: Uint16Array;
  try {
    if (!Array.isArray(m.palette) || m.palette.length > 4095) throw new Error("bad palette");
    cells = decodeRuns(m.runs, m.palette.length);
  } catch {
    return;
  }
  world.put({ cx: m.cx, cy: m.cy, cz: m.cz, palette: m.palette.map((p) => ({ id: String(p.id), color: Number(p.color) | 0, flags: Number(p.flags) | 0 })), cells });
  // this region and the neighbours whose border faces may change
  for (const [dx, dy, dz] of [
    [0, 0, 0],
    [-1, 0, 0],
    [1, 0, 0],
    [0, -1, 0],
    [0, 1, 0],
    [0, 0, -1],
    [0, 0, 1],
  ]) {
    const rx = Math.floor((m.cx * 16 + dx * 16) / REGION);
    const ry = Math.floor((m.cy * 16 + dy * 16) / REGION);
    const rz = Math.floor((m.cz * 16 + dz * 16) / REGION);
    dirty.add(`${rx},${ry},${rz}`);
  }
  clearTimeout(timer);
  timer = setTimeout(flush, 40);
};

function flush() {
  for (const key of dirty) {
    const [rx, ry, rz] = key.split(",").map(Number);
    // skip regions without any loaded section
    let any = false;
    for (let i = 0; i < 2 && !any; i++) for (let j = 0; j < 2 && !any; j++) for (let k = 0; k < 2 && !any; k++) any = world.sections.has(VoxelWorld.key(rx * 2 + i, ry * 2 + j, rz * 2 + k));
    if (!any) continue;
    const mesh = meshRegion(world, rx * REGION, ry * REGION, rz * REGION, REGION);
    const transfer = [mesh.opaque.pos.buffer, mesh.opaque.col.buffer, mesh.opaque.idx.buffer, mesh.light.pos.buffer, mesh.light.col.buffer, mesh.light.idx.buffer, mesh.water.pos.buffer, mesh.water.col.buffer, mesh.water.idx.buffer, mesh.glass.pos.buffer, mesh.glass.col.buffer, mesh.glass.idx.buffer, mesh.glow.pos.buffer, mesh.glow.col.buffer];
    (self as unknown as Worker).postMessage({ type: "region", key, mesh }, transfer);
  }
  dirty.clear();
}
