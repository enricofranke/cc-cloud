// DEV-ONLY fake block world for the mock: map tiles (surface + slice) and the
// voxel endpoint read from the same deterministic world, so 2D map, 3D terrain
// and voxels agree. Around (0, 0): a grassy hill with a lake, caves, and the
// "Deep Base" – an underground room at y −60 with a mainframe, a computer, an
// RS bridge and a lit cave next to it, connected to the surface by a shaft.
// Elsewhere: the rolling hills of terrain-sim (heights only, no caves).
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

/** Surface height at (x, z) in the Overworld (top solid block). */
export function surfaceY(x: number, z: number): number {
  const r = Math.hypot(x - 10, z - 10);
  if (r < 140) {
    // the hill above the Deep Base, a lake to the east
    const hill = 64 + 22 * Math.exp(-((r / 46) ** 2)) + (noise3(x, 0, z, 12, 4) - 0.5) * 4;
    const lake = Math.hypot(x - 60, z - 5) < 22 ? 6 : 0;
    const base = surfaceSample("minecraft:overworld", x, z).y;
    const t = Math.min(1, Math.max(0, (r - 90) / 50));
    return Math.round((hill - lake) * (1 - t) + base * t);
  }
  return surfaceSample("minecraft:overworld", x, z).y;
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
  if (dim !== "minecraft:overworld") {
    // a simple Nether: netherrack floor with lava lakes
    const h = Math.round(40 + noise3(x, 0, z, 30, 7) * 30);
    if (y > h) return y <= 31 ? "lava" : "air";
    return "stone";
  }
  if (y <= -64) return "bedrock";
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
  // generic caves (anywhere, between y -50 and 40)
  if (y > -50 && y < 40 && noise3(x, y, z, 14, 5) > 0.74) return y < -40 && noise3(x, y, z, 8, 6) > 0.8 ? "lava" : "air";
  if (y > sy) {
    if (y <= 62 && sy < 62) return "water";
    // a few trees on the hill
    const tx = Math.round(x / 9) * 9;
    const tz = Math.round(z / 9) * 9;
    if (hash3(tx, 0, tz, 13) > 0.86 && Math.hypot(tx - 10, tz - 10) < 70 && surfaceY(tx, tz) > 63) {
      const base = surfaceY(tx, tz);
      if (x === tx && z === tz && y <= base + 4) return "log";
      if (Math.hypot(x - tx, z - tz, (y - base - 5) * 1.2) < 3) return "leaves";
    }
    return "air";
  }
  if (y === sy) return sy > 84 ? "snow" : sy <= 63 ? "sand" : "grass";
  if (y > sy - 4) return "dirt";
  return y < 0 ? "deepslate" : "stone";
}

const isTransparent = (b: B) => (BLOCKS[b].flags & 1) !== 0;

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
