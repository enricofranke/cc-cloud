// DEV-ONLY fake block world for the mock: map tiles (surface + slice), the
// coarse 3D terrain and the voxel endpoint read from the same deterministic
// world, so 2D map, 3D terrain and voxels agree. Around (0, 0): a grassy hill
// with a lake, caves, and the "Deep Base" – an underground room at y −60 with a
// mainframe, a computer, an RS bridge and a lit cave next to it, connected to
// the surface by a shaft. South of the hill the example base of the mock
// (dev/network-sim.ts) in its cleared yard. The land is built around every block
// of the example networks and every sample waystone: each stands on the ground,
// relays on hills, machines on stone-brick foundations. Elsewhere: the rolling
// hills of terrain-sim with trees.
import { WAYSTONES } from "./compat-cards.ts";
import { exampleNodes } from "./network-sim.ts";
import { sample as surfaceSample } from "./terrain-sim.ts";

export interface BlockInfo {
  id: string;
  /** ARGB map colour */
  color: number;
  /** 1 transparent, 2 fluid, 4 emissive */
  flags: number;
}
const rgb = (r: number, g: number, b: number) => (0xff << 24) | (r << 16) | (g << 8) | b;
export const BLOCKS: Record<string, BlockInfo> = {
  air: { id: "minecraft:air", color: 0, flags: 1 },
  grass: { id: "minecraft:grass_block", color: rgb(127, 178, 56), flags: 0 },
  dirt: { id: "minecraft:dirt", color: rgb(151, 109, 77), flags: 0 },
  stone: { id: "minecraft:stone", color: rgb(112, 112, 112), flags: 0 },
  deepslate: { id: "minecraft:deepslate", color: rgb(100, 100, 100) - 0x00141414, flags: 0 },
  sand: { id: "minecraft:sand", color: rgb(247, 233, 163), flags: 0 },
  snow: { id: "minecraft:snow_block", color: rgb(255, 255, 255), flags: 0 },
  water: { id: "minecraft:water", color: rgb(64, 64, 255), flags: 3 },
  lava: { id: "minecraft:lava", color: rgb(255, 90, 0), flags: 6 },
  leaves: { id: "minecraft:oak_leaves", color: rgb(0, 124, 0), flags: 1 },
  log: { id: "minecraft:oak_log", color: rgb(143, 119, 72), flags: 0 },
  bricks: { id: "minecraft:deepslate_bricks", color: rgb(80, 80, 86), flags: 0 },
  floor: { id: "minecraft:polished_deepslate", color: rgb(60, 60, 66), flags: 0 },
  lamp: { id: "minecraft:sea_lantern", color: rgb(220, 245, 255), flags: 4 },
  glass: { id: "minecraft:glass", color: rgb(200, 230, 240), flags: 1 },
  ladder: { id: "minecraft:ladder", color: rgb(143, 119, 72), flags: 1 },
  computer: { id: "computercraft:computer_advanced", color: rgb(229, 229, 51), flags: 0 },
  mainframe: { id: "cccloud:cloud_mainframe", color: rgb(221, 215, 113), flags: 4 },
  rs: { id: "cccloud:rs_cloud_bridge", color: rgb(170, 60, 60), flags: 0 },
  chest: { id: "minecraft:chest", color: rgb(143, 119, 72), flags: 0 },
  glow: { id: "minecraft:glow_lichen", color: rgb(120, 200, 170), flags: 5 },
  amethyst: { id: "minecraft:amethyst_block", color: rgb(127, 63, 178), flags: 0 },
  bedrock: { id: "minecraft:bedrock", color: rgb(50, 50, 50), flags: 0 },
  foundation: { id: "minecraft:stone_bricks", color: rgb(132, 132, 136), flags: 0 },
  netherrack: { id: "minecraft:netherrack", color: rgb(112, 2, 0), flags: 0 },
  machine: { id: "minecraft:barrel", color: rgb(143, 119, 72), flags: 0 },
};
type B = keyof typeof BLOCKS;

function hash3(x: number, y: number, z: number, seed: number) {
  let h = (x * 374761393 + y * 1103515245 + z * 668265263 + seed * 2147483647) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967295;
}
function noise3(x: number, y: number, z: number, s: number, seed: number) {
  const fx = x / s;
  const fy = y / s;
  const fz = z / s;
  const x0 = Math.floor(fx);
  const y0 = Math.floor(fy);
  const z0 = Math.floor(fz);
  const tx = fx - x0;
  const ty = fy - y0;
  const tz = fz - z0;
  const sm = (t: number) => t * t * (3 - 2 * t);
  const [a, b, c] = [sm(tx), sm(ty), sm(tz)];
  let out = 0;
  for (let i = 0; i < 2; i++)
    for (let j = 0; j < 2; j++)
      for (let k = 0; k < 2; k++) out += hash3(x0 + i, y0 + j, z0 + k, seed) * (i ? a : 1 - a) * (j ? b : 1 - b) * (k ? c : 1 - c);
  return out;
}

const O = "minecraft:overworld";
const smooth = (t: number) => (t <= 0 ? 0 : t >= 1 ? 1 : t * t * (3 - 2 * t));
/** the water surface of the Overworld's seas and lakes */
export const SEA = 62;

const LAKE = { x: 64, z: 6, r: 11 };

/** The land before anyone built on it: the rolling hills of terrain-sim, and around (10, 10) the hill above the Deep Base. */
export function wildY(x: number, z: number): number {
  const far = surfaceSample(O, x, z).y;
  const r = Math.hypot(x - 10, z - 10);
  if (r >= 140) return far;
  const hill = 64 + 22 * Math.exp(-((r / 46) ** 2)) + (noise3(x, 0, z, 12, 4) - 0.5) * 4;
  const t = smooth((r - 90) / 50);
  let y = hill * (1 - t) + far * t;
  // the lake east of the hill: a bowl that holds water, with a flat sandy shore around it
  const d = Math.hypot(x - LAKE.x, z - LAKE.z);
  if (d < LAKE.r) y = SEA - 5 + 5 * (d / LAKE.r) ** 2;
  else if (d < LAKE.r + 9) y = SEA + 0.4 + (y - SEA - 0.4) * smooth((d - LAKE.r) / 9);
  return y;
}

/**
 * A spot where a block of the example networks or a waystone stands. The ground there is one below the block: flat
 * for `flat` blocks around it, then blending into the land over `edge` more. A wide edge makes a relay's hill, a narrow
 * one a machine's levelled patch. `pad` blocks around it (a square) are a stone-brick foundation.
 */
interface Site {
  dim: string;
  x: number;
  y: number;
  z: number;
  flat: number;
  edge: number;
  pad: number;
}

/** The main base (dev/network-sim.ts): a cleared yard at y 63 for the mainframe and the machines next to it. */
export const YARD: Site = { dim: O, x: 14, y: 64, z: -30, flat: 26, edge: 14, pad: -1 };

/** The machines the example Cloud Probes read: a block each, on the probe's patch. */
const MACHINES: { dim: string; x: number; y: number; z: number }[] = [];

const SITES: Site[] = (() => {
  const ids = { reactor: "reactor", me: "me", storage: "storage", turbine: "turbine", control: "control", computer: "computer" };
  const out: Site[] = [YARD];
  for (const n of exampleNodes(ids)) {
    const relay = n.type === "relay" || n.type === "advanced_relay" || n.type === "ender_relay";
    // a Cloud Probe's patch takes in the machine it reads
    out.push({ dim: n.dim, ...n.pos, flat: n.target ? 7 : 3, edge: relay ? 26 : 10, pad: n.type === "mainframe" ? 2 : 1 });
    if (n.target) MACHINES.push({ dim: n.dim, ...n.target });
  }
  for (const w of WAYSTONES) out.push({ dim: w.dim, x: w.x, y: w.y, z: w.z, flat: 2, edge: 8, pad: -1 });
  // the widest first: a machine's patch sits on a relay's hill, not the other way round
  return out.sort((a, b) => b.flat + b.edge - (a.flat + a.edge));
})();

/** Ground height with every site levelled in. */
function built(dim: string, x: number, z: number, wild: number): number {
  let y = wild;
  for (const s of SITES) {
    if (s.dim !== dim) continue;
    const reach = s.flat + s.edge;
    if (Math.abs(x - s.x) > reach || Math.abs(z - s.z) > reach) continue;
    const d = Math.hypot(x - s.x, z - s.z);
    if (d < reach) y += (s.y - 1 - y) * (1 - smooth((d - s.flat) / s.edge));
  }
  return Math.round(y);
}

/** A foundation block under a site's block: stone bricks in a square around it. */
function padAt(dim: string, x: number, z: number): boolean {
  for (const s of SITES) if (s.dim === dim && s.pad >= 0 && Math.abs(x - s.x) <= s.pad && Math.abs(z - s.z) <= s.pad) return true;
  return false;
}
/** Keeps trees off the yard and the patches the machines stand on. */
function builtUp(x: number, z: number): boolean {
  for (const s of SITES) if (s.dim === O && Math.hypot(x - s.x, z - s.z) < s.flat + 4) return true;
  return false;
}

// every block of a column asks for the surface: one answer per column (bounded, the demo runs for hours)
const columns = new Map<number, number>();
const column = (dim: string, x: number, z: number, get: () => number) => {
  const key = ((dim === O ? 0 : 1) * 70_000 + x + 35_000) * 70_000 + z + 35_000;
  let y = columns.get(key);
  if (y === undefined) {
    if (columns.size > 400_000) columns.clear();
    columns.set(key, (y = get()));
  }
  return y;
};

/** Surface height at (x, z) in the Overworld (top solid block). */
export function surfaceY(x: number, z: number): number {
  return column(O, x, z, () => built(O, x, z, wildY(x, z)));
}

/** Floor height of the Nether at (x, z): netherrack hills, levelled where blocks stand. */
export function netherY(x: number, z: number): number {
  return column("minecraft:the_nether", x, z, () => built("minecraft:the_nether", x, z, 40 + noise3(x, 0, z, 30, 7) * 30));
}

/** The ground under a tree at a grid point (every 9 blocks, about one in seven has one), or null. */
function treeAt(tx: number, tz: number): number | null {
  if (hash3(tx, 0, tz, 13) <= 0.86 || builtUp(tx, tz)) return null;
  const base = surfaceY(tx, tz);
  return base > SEA + 1 && base <= 84 ? base : null;
}

/** The top block of an Overworld column: the foundation under a block, snow up high, sand where the land meets the
 * water by nature, grass where it was levelled. */
function topOf(x: number, z: number, sy: number): B {
  return padAt(O, x, z) ? "foundation" : sy > 84 ? "snow" : sy <= SEA + 1 && wildY(x, z) < SEA + 1.5 ? "sand" : "grass";
}

const ROOM = { x0: -9, x1: 9, z0: -7, z1: 7, y0: -62, y1: -55 };
const inRoom = (x: number, y: number, z: number) => x > ROOM.x0 && x < ROOM.x1 && z > ROOM.z0 && z < ROOM.z1 && y > ROOM.y0 && y < ROOM.y1;
const roomShell = (x: number, y: number, z: number) => x >= ROOM.x0 && x <= ROOM.x1 && z >= ROOM.z0 && z <= ROOM.z1 && y >= ROOM.y0 && y <= ROOM.y1;

/** Device blocks of the Deep Base (x, y, z → block). */
export const DEEP_BASE = {
  mainframe: { x: 0, y: -61, z: 0 },
  computer: { x: 4, y: -61, z: -3 },
  rs: { x: 5, y: -61, z: -3 },
  uplink: { x: 4, y: -60, z: -3 },
  relay: { x: 8, y: 75, z: 6 },
};

export function blockAt(dim: string, x: number, y: number, z: number): B {
  if (dim !== O) {
    // a simple Nether: netherrack hills, a lava sea below them
    const h = netherY(x, z);
    if (y > h) return y <= 31 ? "lava" : "air";
    return y === h && padAt(dim, x, z) ? "foundation" : "netherrack";
  }
  if (y <= -64) return "bedrock";
  for (const m of MACHINES) if (m.x === x && m.y === y && m.z === z && m.dim === dim) return "machine";
  // Deep Base devices
  for (const [k, p] of Object.entries(DEEP_BASE)) {
    if (k === "relay" || k === "uplink") continue;
    if (x === p.x && z === p.z && (y === p.y || (k === "mainframe" && y === p.y + 1))) return k as B;
  }
  if (x === DEEP_BASE.uplink.x && y === DEEP_BASE.uplink.y && z === DEEP_BASE.uplink.z) return "glass";
  if (roomShell(x, y, z)) {
    if (inRoom(x, y, z)) {
      if (y === ROOM.y0 + 1 && (x === -7 || x === 7) && Math.abs(z) <= 5 && (z & 1) === 0) return "chest";
      return "air";
    }
    if (y === ROOM.y1 && ((x + 9) % 4 === 1) && ((z + 7) % 4 === 1)) return "lamp";
    // wall lamps every 4 blocks at eye height
    if (y === ROOM.y0 + 3 && ((x === ROOM.x0 || x === ROOM.x1) ? (z + 7) % 4 === 2 : (x + 9) % 4 === 2)) return "lamp";
    if (y === ROOM.y0) return "floor";
    // an opening to the cave on the west side
    if (x === ROOM.x0 && Math.abs(z) <= 2 && y > ROOM.y0 && y < ROOM.y0 + 4) return "air";
    return "bricks";
  }
  // shaft with a ladder from the room to the surface (east wall)
  const sy = surfaceY(x, z);
  if (x === 7 && z === 5 && y > ROOM.y1 && y <= sy) return "air";
  if (x === 7 && z === 6 && y > ROOM.y0 && y <= sy) return "ladder";
  // the cave west of the base: a winding tunnel into a lit grotto with a pool
  const cave = (() => {
    const tx = x + 9;
    if (tx <= 0 && tx > -48) {
      const cz = Math.sin(tx / 9) * 6;
      const cy = -60 + Math.sin(tx / 13) * 3;
      const r = 3.2 + noise3(x, y, z, 5, 2) * 1.8;
      if (Math.hypot(z - cz, (y - cy) * 1.3) < r) return true;
    }
    const g = Math.hypot((x + 62) / 1.3, (y + 56) * 1.6, z / 1.1);
    return g < 14 + noise3(x, y, z, 6, 3) * 4;
  })();
  if (cave) {
    if (y <= -64 + 3 && Math.hypot(x + 62, z) < 9) return "water";
    return "air";
  }
  if (Math.hypot((x + 62) / 1.3, (y + 56) * 1.6, z / 1.1) < 15 + noise3(x, y, z, 6, 3) * 4) {
    // grotto walls: amethyst and glow lichen
    return noise3(x, y, z, 3, 9) > 0.62 ? "amethyst" : noise3(x, y, z, 2, 11) > 0.7 ? "glow" : y < 0 ? "deepslate" : "stone";
  }
  // generic caves (anywhere, between y -50 and 12: deep enough that the blocks around a node on the surface never
  // show one as a chunk of rock floating under the ground)
  if (y > -50 && y < 12 && noise3(x, y, z, 14, 5) > 0.74) return y < -40 && noise3(x, y, z, 8, 6) > 0.8 ? "lava" : "air";
  if (y > sy) {
    if (y <= SEA && sy < SEA) return "water";
    // trees on the grass, not on the yard or where a block stands
    const tx = Math.round(x / 9) * 9;
    const tz = Math.round(z / 9) * 9;
    const base = treeAt(tx, tz);
    if (base !== null) {
      if (x === tx && z === tz && y <= base + 4) return "log";
      if (Math.hypot(x - tx, z - tz, (y - base - 5) * 1.2) < 3) return "leaves";
    }
    return "air";
  }
  if (y === sy) return topOf(x, z, sy);
  if (y > sy - 4) return "dirt";
  return y < 0 ? "deepslate" : "stone";
}

const isTransparent = (b: B) => (BLOCKS[b].flags & 1) !== 0;
const rgbOf = (c: number): [number, number, number] => [(c >> 16) & 255, (c >> 8) & 255, c & 255];

/** The top of a column for the coarse 3D terrain (terrain-sim): its height and map colour, tree tops in leaf green. */
export function ground(dim: string, x: number, z: number): { y: number; rgb: [number, number, number] } {
  if (dim === "minecraft:the_nether") return { y: netherY(x, z), rgb: rgbOf(BLOCKS.netherrack.color) };
  if (dim !== O) return surfaceSample(dim, x, z);
  const y = surfaceY(x, z);
  if (y < SEA) return { y: SEA, rgb: rgbOf(BLOCKS.water.color) };
  const tx = Math.round(x / 9) * 9;
  const tz = Math.round(z / 9) * 9;
  if (Math.hypot(x - tx, z - tz) < 3 && treeAt(tx, tz) !== null) return { y, rgb: rgbOf(BLOCKS.leaves.color) };
  return { y, rgb: rgbOf(BLOCKS[topOf(x, z, y)].color) };
}

/** One 16³ section in the voxel API format; null when nothing is exposed. */
export function voxelSection(dim: string, cx: number, cy: number, cz: number) {
  const palette: BlockInfo[] = [];
  const index = new Map<string, number>();
  const cells = new Uint16Array(4096);
  let exposed = 0;
  const at = (x: number, y: number, z: number) => blockAt(dim, x, y, z);
  for (let y = 0; y < 16; y++)
    for (let z = 0; z < 16; z++)
      for (let x = 0; x < 16; x++) {
        const wx = cx * 16 + x;
        const wy = cy * 16 + y;
        const wz = cz * 16 + z;
        const b = at(wx, wy, wz);
        if (b === "air") continue;
        // anti x-ray: only blocks with a face to air or a transparent block
        const open =
          isTransparent(b) ||
          isTransparent(at(wx + 1, wy, wz)) ||
          isTransparent(at(wx - 1, wy, wz)) ||
          isTransparent(at(wx, wy + 1, wz)) ||
          isTransparent(at(wx, wy - 1, wz)) ||
          isTransparent(at(wx, wy, wz + 1)) ||
          isTransparent(at(wx, wy, wz - 1));
        if (!open) continue;
        let i = index.get(b);
        if (i === undefined) {
          palette.push(BLOCKS[b]);
          i = palette.length;
          index.set(b, i);
        }
        cells[x + 16 * z + 256 * y] = i;
        exposed++;
      }
  // runs: LEB128 (length, paletteIndex) pairs
  const bytes: number[] = [];
  const leb = (n: number) => {
    do {
      let b = n & 0x7f;
      n >>>= 7;
      if (n) b |= 0x80;
      bytes.push(b);
    } while (n);
  };
  for (let i = 0; i < 4096; ) {
    const v = cells[i];
    let n = 1;
    while (i + n < 4096 && cells[i + n] === v) n++;
    leb(n);
    leb(v);
    i += n;
  }
  return { dim, cx, cy, cz, palette, runs: Buffer.from(bytes).toString("base64"), exposed };
}

/** 128×128 RGBA map tile (surface or a slice at y); null when there is nothing. */
export function mapTile(dim: string, layer: "surface" | "slice", tx: number, tz: number, sliceY = 0): Uint8Array {
  const out = new Uint8Array(128 * 128 * 4);
  const put = (i: number, c: number, shade: number, a = 255) => {
    out[i * 4] = Math.min(255, ((c >> 16) & 255) * shade);
    out[i * 4 + 1] = Math.min(255, ((c >> 8) & 255) * shade);
    out[i * 4 + 2] = Math.min(255, (c & 255) * shade);
    out[i * 4 + 3] = a;
  };
  for (let pz = 0; pz < 128; pz++)
    for (let px = 0; px < 128; px++) {
      const x = tx * 128 + px;
      const z = tz * 128 + pz;
      const i = pz * 128 + px;
      if (layer === "surface") {
        let y = dim === "minecraft:overworld" ? Math.max(surfaceY(x, z), 62) : 120;
        let b = blockAt(dim, x, y, z);
        // trees and things above the ground
        for (let up = y + 8; up > y; up--) {
          const t = blockAt(dim, x, up, z);
          if (t !== "air") {
            b = t;
            y = up;
            break;
          }
        }
        while (b === "air" && y > -64) b = blockAt(dim, x, --y, z);
        if (b === "water") {
          let d = 0;
          while (blockAt(dim, x, y - d, z) === "water" && d < 12) d++;
          put(i, BLOCKS.water.color, 1 - Math.min(0.45, d * 0.06));
          continue;
        }
        // vanilla-map shading: lighter facing north-up slopes
        const north = dim === "minecraft:overworld" ? surfaceY(x, z - 1) : y;
        const shade = y > north ? 1.0 : y < north ? 0.72 : 0.86;
        put(i, BLOCKS[b].color, shade);
      } else {
        // slice: the first non-air block from the cut downwards (≤ 16 blocks)
        const at = blockAt(dim, x, sliceY, z);
        if (at !== "air" && !(BLOCKS[at].flags & 1)) {
          put(i, BLOCKS[at].color, 0.32); // rock at the cut: darkest
          continue;
        }
        let d = 0;
        let b: B = at;
        while (b === "air" && d < 16) b = blockAt(dim, x, sliceY - ++d, z);
        if (b === "air") continue; // transparent
        const emissive = (BLOCKS[b].flags & 4) !== 0;
        put(i, BLOCKS[b].color, emissive ? 1.1 : Math.max(0.45, 1.05 - d * 0.04));
      }
    }
  return out;
}
