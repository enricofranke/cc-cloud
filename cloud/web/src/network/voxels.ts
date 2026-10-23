// Voxel data for the 3D view (docs/SPEC.md "Voxel view – HTTP API"): decoding
// of the run-length sections and greedy meshing with ambient occlusion.
// Pure functions – used by the Web Worker (voxelWorker.ts) and by the tests.

export interface PaletteEntry {
  id: string;
  /** ARGB */
  color: number;
  /** 1 transparent, 2 fluid, 4 emissive */
  flags: number;
}
export interface SectionData {
  cx: number;
  cy: number;
  cz: number;
  palette: PaletteEntry[];
  /** 4096 palette indices (0 = nothing), order x + 16·z + 256·y */
  cells: Uint8Array | Uint16Array;
}

export const F_TRANSPARENT = 1;
export const F_FLUID = 2;
export const F_EMISSIVE = 4;

/** Decodes base64 LEB128 (runLength, paletteIndex) pairs into 4096 cells. Throws on malformed data. */
export function decodeRuns(b64: string, paletteSize: number): Uint16Array {
  const bin = atob(b64);
  const cells = new Uint16Array(4096);
  let pos = 0;
  let at = 0;
  const leb = () => {
    let n = 0;
    let shift = 0;
    for (;;) {
      if (pos >= bin.length || shift > 28) throw new Error("bad runs");
      const b = bin.charCodeAt(pos++);
      n |= (b & 0x7f) << shift;
      if (!(b & 0x80)) return n >>> 0;
      shift += 7;
    }
  };
  while (at < 4096) {
    const len = leb();
    const idx = leb();
    if (len === 0 || at + len > 4096 || idx > paletteSize) throw new Error("bad runs");
    if (idx) cells.fill(idx, at, at + len);
    at += len;
  }
  return cells;
}

/** World lookup across sections. */
export class VoxelWorld {
  sections = new Map<string, SectionData>();
  static key(cx: number, cy: number, cz: number) {
    return `${cx},${cy},${cz}`;
  }
  put(s: SectionData) {
    this.sections.set(VoxelWorld.key(s.cx, s.cy, s.cz), s);
  }
  /** palette entry at a world block, or null (air / hidden / not loaded) */
  at(x: number, y: number, z: number): PaletteEntry | null {
    const cx = Math.floor(x / 16);
    const cy = Math.floor(y / 16);
    const cz = Math.floor(z / 16);
    const s = this.sections.get(VoxelWorld.key(cx, cy, cz));
    if (!s) return null;
    const i = s.cells[(x - cx * 16) + 16 * (z - cz * 16) + 256 * (y - cy * 16)];
    return i ? s.palette[i - 1] : null;
  }
}


export interface MeshData {
  pos: Float32Array;
  col: Uint8Array;
  idx: Uint32Array;
}
export interface RegionMesh {
  opaque: MeshData;
  /** emissive blocks (not dimmed at night) */
  light: MeshData;
  water: MeshData;
  glass: MeshData;
  /** emissive block centres + colours (for glow sprites) */
  glow: { pos: Float32Array; col: Uint8Array };
  triangles: number;
}

// face directions: normal axis d (0 x, 1 y, 2 z), sign s; u/v the other two axes
const FACES: { d: number; s: 1 | -1; u: number; v: number; shade: number }[] = [
  { d: 1, s: 1, u: 0, v: 2, shade: 1.0 },
  { d: 1, s: -1, u: 0, v: 2, shade: 0.5 },
  { d: 0, s: 1, u: 2, v: 1, shade: 0.6 },
  { d: 0, s: -1, u: 2, v: 1, shade: 0.6 },
  { d: 2, s: 1, u: 0, v: 1, shade: 0.8 },
  { d: 2, s: -1, u: 0, v: 1, shade: 0.8 },
];
const AO = [0.5, 0.68, 0.84, 1];

class Builder {
  pos: number[] = [];
  col: number[] = [];
  idx: number[] = [];
  quad(p: number[][], c: [number, number, number], ao: number[], normal: number[]) {
    const b = this.pos.length / 3;
    // wind counter-clockwise as seen from the normal side (front faces)
    const e1 = [p[1][0] - p[0][0], p[1][1] - p[0][1], p[1][2] - p[0][2]];
    const e2 = [p[2][0] - p[0][0], p[2][1] - p[0][1], p[2][2] - p[0][2]];
    const cr = [e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]];
    const flip = cr[0] * normal[0] + cr[1] * normal[1] + cr[2] * normal[2] < 0;
    for (let i = 0; i < 4; i++) {
      this.pos.push(p[i][0], p[i][1], p[i][2]);
      this.col.push(Math.min(255, c[0] * ao[i]), Math.min(255, c[1] * ao[i]), Math.min(255, c[2] * ao[i]));
    }
    // split along the brighter diagonal (no AO artefacts)
    const alt = ao[0] + ao[2] < ao[1] + ao[3];
    const t = alt ? [b, b + 1, b + 3, b + 1, b + 2, b + 3] : [b, b + 1, b + 2, b, b + 2, b + 3];
    if (flip) this.idx.push(t[0], t[2], t[1], t[3], t[5], t[4]);
    else this.idx.push(...t);
  }
  done(): MeshData {
    return { pos: Float32Array.from(this.pos), col: Uint8Array.from(this.col), idx: Uint32Array.from(this.idx) };
  }
}

/**
 * Greedy-meshes the box [x0, x0+size) × [y0, y0+size) × [z0, z0+size): faces
 * between a block and air (or a transparent block of another kind), merged
 * into rectangles with equal block and AO. Water and glass-like blocks go to
 * their own translucent meshes; emissive blocks are unshaded and get glow points.
 * Works on a dense copy of the box plus a 1-block border (fast, no allocations per cell).
 */
export function meshRegion(world: VoxelWorld, x0: number, y0: number, z0: number, size: number): RegionMesh {
  const out = { opaque: new Builder(), light: new Builder(), water: new Builder(), glass: new Builder() };
  const glowPos: number[] = [];
  const glowCol: number[] = [];
  // dense grid with a border: entry index into `kinds` (0 = empty)
  const N = size + 2;
  const grid = new Int32Array(N * N * N);
  const kinds: PaletteEntry[] = [];
  const kindOf = new Map<PaletteEntry, number>();
  const gi = (x: number, y: number, z: number) => x + 1 + N * (z + 1) + N * N * (y + 1);
  for (let y = -1; y <= size; y++)
    for (let z = -1; z <= size; z++)
      for (let x = -1; x <= size; x++) {
        const e = world.at(x0 + x, y0 + y, z0 + z);
        if (!e) continue;
        let k = kindOf.get(e);
        if (k === undefined) {
          kinds.push(e);
          k = kinds.length;
          kindOf.set(e, k);
        }
        grid[gi(x, y, z)] = k;
      }
  // glow sprites sit just in front of a light's open faces (not inside the block, where the depth test hides them)
  for (let y = 0; y < size; y++)
    for (let z = 0; z < size; z++)
      for (let x = 0; x < size; x++) {
        const k = grid[gi(x, y, z)];
        if (!k || !(kinds[k - 1].flags & F_EMISSIVE)) continue;
        const e = kinds[k - 1];
        // one sprite in front of every open face: from the room the one inside the rock is
        // hidden by the wall, so this works without knowing which side is really air
        let any = false;
        for (const [dx, dy, dz] of DIRS6) {
          const nk = grid[gi(x + dx, y + dy, z + dz)];
          if (nk && !(kinds[nk - 1].flags & F_TRANSPARENT)) continue;
          any = true;
          glowPos.push(x0 + x + 0.5 + dx * 0.75, y0 + y + 0.5 + dy * 0.75, z0 + z + 0.5 + dz * 0.75);
          glowCol.push((e.color >> 16) & 255, (e.color >> 8) & 255, e.color & 255);
        }
        if (any || !(e.flags & F_TRANSPARENT)) continue;
        glowPos.push(x0 + x + 0.5, y0 + y + 0.5, z0 + z + 0.5);
        glowCol.push((e.color >> 16) & 255, (e.color >> 8) & 255, e.color & 255);
      }
  const opq = new Uint8Array(kinds.length + 1);
  kinds.forEach((e, i) => (opq[i + 1] = e.flags & F_TRANSPARENT ? 0 : 1));
  const cell = (c: number[]) => grid[gi(c[0], c[1], c[2])];
  const mask = new Int32Array(size * size);
  const aoMask = new Int32Array(size * size);
  const p = [0, 0, 0];
  const n = [0, 0, 0];
  const t = [0, 0, 0];
  for (const f of FACES) {
    const { d, u, v, s } = f;
    for (let layer = 0; layer < size; layer++) {
      mask.fill(0);
      for (let j = 0; j < size; j++)
        for (let i = 0; i < size; i++) {
          p[d] = layer;
          p[u] = i;
          p[v] = j;
          const b = cell(p);
          if (!b) continue;
          n[0] = p[0];
          n[1] = p[1];
          n[2] = p[2];
          n[d] += s;
          const nb = cell(n);
          // visible when the neighbour is air, or transparent and a different block
          if (nb && (opq[nb] || nb === b)) continue;
          const emissive = (kinds[b - 1].flags & F_EMISSIVE) !== 0;
          // ambient occlusion: 2 bits per corner (0 darkest … 3 open)
          let aoBits = 0;
          if (!emissive) {
            let ci = 0;
            for (const [du, dv] of CORNERS) {
              t[0] = n[0];
              t[1] = n[1];
              t[2] = n[2];
              t[u] += du;
              const a = opq[cell(t)] ?? 0;
              t[u] -= du;
              t[v] += dv;
              const c2 = opq[cell(t)] ?? 0;
              t[u] += du;
              const corner = opq[cell(t)] ?? 0;
              const lvl = a && c2 ? 0 : 3 - (a + c2 + corner);
              aoBits |= lvl << (ci * 2);
              ci++;
            }
          } else aoBits = 0xff;
          const k = j * size + i;
          mask[k] = b;
          aoMask[k] = aoBits;
        }
      // greedy merge of equal neighbours (same block and same AO)
      for (let j = 0; j < size; j++)
        for (let i = 0; i < size; ) {
          const k0 = j * size + i;
          const b = mask[k0];
          if (!b) {
            i++;
            continue;
          }
          const ab = aoMask[k0];
          let w = 1;
          while (i + w < size && mask[k0 + w] === b && aoMask[k0 + w] === ab) w++;
          let h = 1;
          outer: for (; j + h < size; h++)
            for (let k = 0; k < w; k++) {
              const kk = (j + h) * size + i + k;
              if (mask[kk] !== b || aoMask[kk] !== ab) break outer;
            }
          for (let jj = 0; jj < h; jj++) mask.fill(0, (j + jj) * size + i, (j + jj) * size + i + w);
          const e = kinds[b - 1];
          const plane = (d === 0 ? x0 : d === 1 ? y0 : z0) + layer + (s > 0 ? 1 : 0);
          const ou = u === 0 ? x0 : u === 1 ? y0 : z0;
          const ov = v === 0 ? x0 : v === 1 ? y0 : z0;
          const corner = (cu: number, cv: number) => {
            const q = [0, 0, 0];
            q[d] = plane;
            q[u] = ou + cu;
            q[v] = ov + cv;
            return q;
          };
          const pts = [corner(i, j), corner(i + w, j), corner(i + w, j + h), corner(i, j + h)];
          const shade = e.flags & F_EMISSIVE ? 1.15 : f.shade;
          const c: [number, number, number] = [((e.color >> 16) & 255) * shade, ((e.color >> 8) & 255) * shade, (e.color & 255) * shade];
          const ao = [0, 1, 2, 3].map((ci) => AO[(ab >> (ci * 2)) & 3]);
          const normal = [0, 0, 0];
          normal[d] = s;
          const target = e.flags & F_FLUID ? out.water : e.flags & F_TRANSPARENT ? out.glass : e.flags & F_EMISSIVE ? out.light : out.opaque;
          target.quad(pts, c, ao, normal);
          i += w;
        }
    }
  }
  const opaqueM = out.opaque.done();
  const lightM = out.light.done();
  const waterM = out.water.done();
  const glassM = out.glass.done();
  return {
    opaque: opaqueM,
    light: lightM,
    water: waterM,
    glass: glassM,
    glow: { pos: Float32Array.from(glowPos), col: Uint8Array.from(glowCol) },
    triangles: (opaqueM.idx.length + lightM.idx.length + waterM.idx.length + glassM.idx.length) / 3,
  };
}
const DIRS6 = [
  [1, 0, 0],
  [-1, 0, 0],
  [0, 1, 0],
  [0, -1, 0],
  [0, 0, 1],
  [0, 0, -1],
] as const;
const CORNERS = [
  [-1, -1],
  [1, -1],
  [1, 1],
  [-1, 1],
] as const;

/** Sections to load around the nodes (radius in blocks), nearest to `focus` first. */
export function sectionsAround(nodes: { x: number; y: number; z: number }[], radius: number, focus: { x: number; y: number; z: number }): [number, number, number][] {
  const set = new Map<string, [number, number, number, number]>();
  for (const n of nodes) {
    const r = Math.ceil(radius / 16);
    const nx = Math.floor(n.x / 16);
    const ny = Math.floor(n.y / 16);
    const nz = Math.floor(n.z / 16);
    for (let dx = -r; dx <= r; dx++)
      for (let dy = -r; dy <= r; dy++)
        for (let dz = -r; dz <= r; dz++) {
          const cx = nx + dx;
          const cy = ny + dy;
          const cz = nz + dz;
          if (cy < -4 || cy > 19) continue; // world height −64…319
          // box-to-point distance, like the server's check
          const dd = (c: number, p: number) => Math.max(c * 16 - p, 0, p - (c * 16 + 15));
          if (Math.hypot(dd(cx, n.x), dd(cy, n.y), dd(cz, n.z)) > radius) continue;
          const key = `${cx},${cy},${cz}`;
          const df = Math.hypot(cx * 16 + 8 - focus.x, cy * 16 + 8 - focus.y, cz * 16 + 8 - focus.z);
          const prev = set.get(key);
          if (!prev || df < prev[3]) set.set(key, [cx, cy, cz, df]);
        }
  }
  return [...set.values()].sort((a, b) => a[3] - b[3]).map(([cx, cy, cz]) => [cx, cy, cz]);
}
