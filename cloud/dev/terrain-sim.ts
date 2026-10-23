// DEV-ONLY fake coarse terrain for the 3D view (docs/SPEC.md "v0.7 web protocol").
// Smooth value-noise hills with water, sand, grass, stone and snow in the
// Overworld; netherrack, soul sand and lava lakes in the Nether. Chunks around
// unloaded nodes are "unknown" (-32768), like the mod which never loads chunks.
import type { SimNode } from "./network-sim.ts";

export const UNKNOWN = -32768;
export const MAX_BYTES = 200 * 1024;
const RADIUS = 96;

function hash(x: number, z: number, seed: number) {
  let h = (x * 374761393 + z * 668265263 + seed * 2147483647) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967295;
}
function noise(x: number, z: number, scale: number, seed: number) {
  const fx = x / scale;
  const fz = z / scale;
  const x0 = Math.floor(fx);
  const z0 = Math.floor(fz);
  const tx = fx - x0;
  const tz = fz - z0;
  const s = (t: number) => t * t * (3 - 2 * t);
  const a = hash(x0, z0, seed);
  const b = hash(x0 + 1, z0, seed);
  const c = hash(x0, z0 + 1, seed);
  const d = hash(x0 + 1, z0 + 1, seed);
  return a + (b - a) * s(tx) + (c - a) * s(tz) + (a - b - c + d) * s(tx) * s(tz);
}

/** Surface height and RGB map colour at a block position. */
export function sample(dim: string, x: number, z: number): { y: number; rgb: [number, number, number] } {
  if (dim === "minecraft:the_nether") {
    const n = noise(x, z, 40, 7) * 0.7 + noise(x, z, 12, 9) * 0.3;
    const y = Math.round(40 + n * 40);
    if (y < 52) return { y: 31, rgb: [216, 127, 51] }; // lava lake (MapColor.COLOR_ORANGE)
    if (noise(x, z, 20, 3) > 0.72) return { y, rgb: [102, 76, 51] }; // soul sand (COLOR_BROWN)
    return { y, rgb: [112, 2, 0] }; // netherrack (NETHER)
  }
  const n = noise(x, z, 90, 1) * 0.6 + noise(x, z, 28, 2) * 0.3 + noise(x, z, 8, 3) * 0.1;
  const y = Math.round(52 + n * 38);
  if (y < 62) return { y: 62, rgb: [64, 64, 255] }; // water (WATER)
  if (y < 64) return { y, rgb: [247, 233, 163] }; // sand
  if (y > 84) return { y, rgb: [255, 255, 255] }; // snow
  if (y > 78) return { y, rgb: [112, 112, 112] }; // stone
  // grass, a little darker in forests
  return noise(x, z, 14, 5) > 0.62 ? { y, rgb: [0, 124, 0] } : { y, rgb: [127, 178, 56] };
}

interface Box {
  x0: number;
  z0: number;
  x1: number;
  z1: number;
}

/** Squares of RADIUS around each positioned node; overlapping squares merged into their bounding box. */
export function clusters(nodes: SimNode[], root?: SimNode): Box[] {
  const sorted = root ? [...nodes].sort((a, b) => Math.hypot(a.pos.x - root.pos.x, a.pos.z - root.pos.z) - Math.hypot(b.pos.x - root.pos.x, b.pos.z - root.pos.z)) : nodes;
  let boxes: Box[] = sorted.map((n) => ({ x0: n.pos.x - RADIUS, z0: n.pos.z - RADIUS, x1: n.pos.x + RADIUS, z1: n.pos.z + RADIUS }));
  for (let merged = true; merged; ) {
    merged = false;
    outer: for (let i = 0; i < boxes.length; i++)
      for (let j = i + 1; j < boxes.length; j++) {
        const a = boxes[i];
        const b = boxes[j];
        if (a.x0 <= b.x1 && b.x0 <= a.x1 && a.z0 <= b.z1 && b.z0 <= a.z1) {
          boxes[i] = { x0: Math.min(a.x0, b.x0), z0: Math.min(a.z0, b.z0), x1: Math.max(a.x1, b.x1), z1: Math.max(a.z1, b.z1) };
          boxes.splice(j, 1);
          merged = true;
          break outer;
        }
      }
  }
  return boxes;
}

export function terrain(nodes: SimNode[], dim: string, step = 2) {
  const inDim = nodes.filter((n) => n.dim === dim);
  const root = inDim.find((n) => n.type === "mainframe") ?? inDim[0];
  const unloaded = inDim.filter((n) => n.loaded === false).map((n) => ({ cx: n.pos.x >> 4, cz: n.pos.z >> 4 }));
  const boxes = clusters(inDim, root);
  const build = (st: number) => {
    const patches = [];
    let bytes = 64;
    let truncated = false;
    for (const b of boxes) {
      const x0 = Math.floor(b.x0 / st) * st;
      const z0 = Math.floor(b.z0 / st) * st;
      const w = Math.floor((b.x1 - x0) / st) + 1;
      const h = Math.floor((b.z1 - z0) / st) + 1;
      // base64 of 2 + 3 bytes per cell, plus a little JSON
      const size = Math.ceil((w * h * 2) / 3) * 4 + Math.ceil((w * h * 3) / 3) * 4 + 80;
      if (bytes + size > MAX_BYTES) {
        truncated = true;
        break;
      }
      bytes += size;
      const heights = Buffer.alloc(w * h * 2);
      const colors = Buffer.alloc(w * h * 3);
      for (let j = 0; j < h; j++)
        for (let i = 0; i < w; i++) {
          const x = x0 + i * st;
          const z = z0 + j * st;
          const k = j * w + i;
          if (unloaded.some((u) => Math.abs((x >> 4) - u.cx) <= 1 && Math.abs((z >> 4) - u.cz) <= 1)) {
            heights.writeInt16LE(UNKNOWN, k * 2);
            continue;
          }
          const s = sample(dim, x, z);
          // levelled ground around the blocks, so nodes stand on the terrain
          let y = s.y;
          for (const n of inDim) {
            const d = Math.hypot(n.pos.x - x, n.pos.z - z);
            if (d < 14) y = Math.round(y + (n.pos.y - 1 - y) * Math.min(1, (14 - d) / 8));
          }
          // levelled ground is dry land
          const rgb = y > 62 && s.rgb[2] === 255 && s.rgb[0] === 64 ? ([127, 178, 56] as [number, number, number]) : s.rgb;
          heights.writeInt16LE(y, k * 2);
          colors.set(rgb, k * 3);
        }
      patches.push({ x0, z0, w, h, heights: heights.toString("base64"), colors: colors.toString("base64") });
    }
    return { dim, step: st, patches, truncated };
  };
  let out = build(step);
  if (out.truncated && step < 4) out = build(4);
  return out;
}
