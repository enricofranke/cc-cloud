// Web Worker (one of a small pool): decodes voxel sections and greedy-meshes the
// 32³ regions it owns off the main thread. Every worker gets every section
// (meshing a region needs the blocks next to it); region (rx, ry, rz) belongs to
// worker `regionOwner(rx, ry, rz, count)`. Meshes go back as transferables.
import { VoxelWorld, decodeRunBytes, meshRegion, regionOwner, type PaletteEntry } from "./voxels.ts";

const REGION = 32;
const world = new VoxelWorld();
const dirty = new Set<string>();
/** Regions this worker has sent a mesh for (an emptied region must be sent once more, empty). */
const meshed = new Set<string>();
let gen = 0;
let index = 0;
let count = 1;
let timer: ReturnType<typeof setTimeout> | undefined;
let firstDirty = 0;

type Section = { cx: number; cy: number; cz: number; palette: PaletteEntry[]; runs: Uint8Array };
type In =
  | { type: "init"; index: number; count: number }
  | { type: "sections"; list: Section[] }
  | { type: "drop"; list: [number, number, number][] }
  | { type: "reset"; gen: number };

const post = (msg: unknown, transfer: Transferable[] = []) => (self as unknown as Worker).postMessage(msg, transfer);

self.onmessage = (ev: MessageEvent<In>) => {
  const m = ev.data;
  if (m.type === "init") {
    index = m.index;
    count = Math.max(1, m.count);
    return;
  }
  if (m.type === "reset") {
    gen = m.gen;
    world.sections.clear();
    dirty.clear();
    meshed.clear();
    clearTimeout(timer);
    timer = undefined;
    return;
  }
  if (m.type === "drop") {
    for (const [cx, cy, cz] of m.list) {
      if (world.sections.delete(VoxelWorld.key(cx, cy, cz))) touch(cx, cy, cz);
    }
  } else {
    for (const s of m.list) {
      let cells: Uint16Array;
      try {
        if (!Array.isArray(s.palette) || s.palette.length > 4095 || !(s.runs instanceof Uint8Array)) continue;
        cells = decodeRunBytes(s.runs, s.palette.length);
      } catch {
        continue;
      }
      world.put({ cx: s.cx, cy: s.cy, cz: s.cz, palette: s.palette.map((p) => ({ id: String(p.id), color: Number(p.color) | 0, flags: Number(p.flags) | 0 })), cells });
      touch(s.cx, s.cy, s.cz);
    }
  }
  schedule();
};

/** Marks the section's region and the neighbouring regions whose border faces may change. */
function touch(cx: number, cy: number, cz: number) {
  for (const [dx, dy, dz] of [
    [0, 0, 0],
    [-1, 0, 0],
    [1, 0, 0],
    [0, -1, 0],
    [0, 1, 0],
    [0, 0, -1],
    [0, 0, 1],
  ]) {
    const rx = Math.floor((cx * 16 + dx * 16) / REGION);
    const ry = Math.floor((cy * 16 + dy * 16) / REGION);
    const rz = Math.floor((cz * 16 + dz * 16) / REGION);
    if (regionOwner(rx, ry, rz, count) === index) dirty.add(`${rx},${ry},${rz}`);
  }
}

/** Meshes 40 ms after the last message, but at least every 200 ms while sections keep coming. */
function schedule() {
  if (!dirty.size) return post({ type: "idle" });
  const now = performance.now();
  if (!timer) firstDirty = now;
  clearTimeout(timer);
  timer = setTimeout(flush, now - firstDirty > 200 ? 0 : 40);
}

function flush() {
  timer = undefined;
  for (const key of dirty) {
    const [rx, ry, rz] = key.split(",").map(Number);
    let any = false;
    for (let i = 0; i < 2 && !any; i++) for (let j = 0; j < 2 && !any; j++) for (let k = 0; k < 2 && !any; k++) any = world.sections.has(VoxelWorld.key(rx * 2 + i, ry * 2 + j, rz * 2 + k));
    if (!any && !meshed.has(key)) continue;
    if (any) meshed.add(key);
    else meshed.delete(key);
    const t0 = performance.now();
    const mesh = meshRegion(world, rx * REGION, ry * REGION, rz * REGION, REGION);
    const transfer = [mesh.opaque, mesh.light, mesh.water, mesh.glass].flatMap((d) => [d.pos.buffer, d.col.buffer, d.idx.buffer]).concat([mesh.glow.pos.buffer, mesh.glow.col.buffer]);
    post({ type: "region", gen, key, mesh, ms: performance.now() - t0 }, transfer);
  }
  dirty.clear();
  post({ type: "idle" });
}
