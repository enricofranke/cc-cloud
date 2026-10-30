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

/** Decodes raw LEB128 (runLength, paletteIndex) pairs into 4096 cells. Throws on malformed data. */
export function decodeRunBytes(bin: Uint8Array, paletteSize: number): Uint16Array {
  const cells = new Uint16Array(4096);
  let pos = 0;
  let at = 0;
  const leb = () => {
    let n = 0;
    let shift = 0;
    for (;;) {
      if (pos >= bin.length || shift > 28) throw new Error("bad runs");
      const b = bin[pos++];
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
  if (pos !== bin.length) throw new Error("bad runs");
  return cells;
}

// ------------------------------------------------------------- batch format
// docs/SPEC.md "Voxel view – batch API": "CCVB", version 1, u32 BE header
// length, the JSON header, then the runs of the "ok" sections in order.

export type BatchStatus = "ok" | "same" | "empty" | "out_of_range" | "claimed";
export interface BatchSection {
  cx: number;
  cy: number;
  cz: number;
  status: BatchStatus;
  /** content hash (16 hex digits), ok and same */
  hash?: string;
  /** the section's palette (ok and same) */
  palette?: PaletteEntry[];
  /** raw runs (ok only); a copy, safe to keep or transfer */
  runs?: Uint8Array;
  exposed?: number;
}
export interface Batch {
  dim: string;
  sections: BatchSection[];
}
const STATUSES = new Set<BatchStatus>(["ok", "same", "empty", "out_of_range", "claimed"]);
const int = (v: unknown) => (typeof v === "number" && Number.isInteger(v) ? v : NaN);

/** Decodes a voxel batch. Throws on malformed data (never trusts lengths or indices). */
export function decodeBatch(buf: Uint8Array): Batch {
  if (buf.length < 9 || buf[0] !== 0x43 || buf[1] !== 0x43 || buf[2] !== 0x56 || buf[3] !== 0x42) throw new Error("not a voxel batch");
  if (buf[4] !== 1) throw new Error(`unknown batch version ${buf[4]}`);
  const len = ((buf[5] << 24) | (buf[6] << 16) | (buf[7] << 8) | buf[8]) >>> 0;
  if (9 + len > buf.length) throw new Error("bad header length");
  const h = JSON.parse(new TextDecoder().decode(buf.subarray(9, 9 + len)));
  if (!h || !Array.isArray(h.palette) || !Array.isArray(h.sections) || h.palette.length > 65535) throw new Error("bad header");
  const palette: PaletteEntry[] = h.palette.map((p: { id?: unknown; color?: unknown; flags?: unknown }) => ({ id: String(p?.id), color: Number(p?.color) | 0, flags: Number(p?.flags) | 0 }));
  let at = 9 + len;
  const sections: BatchSection[] = [];
  for (const e of h.sections) {
    const c = Array.isArray(e?.c) ? e.c.map(int) : [];
    if (c.length !== 3 || c.some((v: number) => Number.isNaN(v)) || !STATUSES.has(e.s)) throw new Error("bad section");
    const s: BatchSection = { cx: c[0], cy: c[1], cz: c[2], status: e.s };
    if (e.s === "ok" || e.s === "same") {
      if (typeof e.h !== "string" || !/^[0-9a-f]{16}$/.test(e.h) || !Array.isArray(e.p) || e.p.length > 4095) throw new Error("bad section");
      s.hash = e.h;
      s.palette = e.p.map((i: unknown) => {
        const v = int(i);
        if (!(v >= 0 && v < palette.length)) throw new Error("bad palette index");
        return palette[v];
      });
    }
    if (e.s === "ok") {
      const l = int(e.l);
      if (!(l >= 0) || at + l > buf.length) throw new Error("bad runs length");
      s.runs = buf.slice(at, at + l);
      s.exposed = int(e.n) || 0;
      at += l;
    }
    sections.push(s);
  }
  if (at !== buf.length) throw new Error("trailing bytes");
  return { dim: String(h.dim), sections };
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

/** Collects quads in growable typed arrays (no per-quad allocations). */
class Builder {
  pos = new Float32Array(1024);
  col = new Uint8Array(1024);
  idx = new Uint32Array(1536);
  verts = 0;
  tris = 0;
  private grow() {
    const v = this.verts * 3 + 12;
    if (v > this.pos.length) {
      const n = Math.max(v, this.pos.length * 2);
      const pos = new Float32Array(n);
      pos.set(this.pos);
      this.pos = pos;
      const col = new Uint8Array(n);
      col.set(this.col);
      this.col = col;
    }
    const t = this.tris * 3 + 6;
    if (t > this.idx.length) {
      const idx = new Uint32Array(Math.max(t, this.idx.length * 2));
      idx.set(this.idx);
      this.idx = idx;
    }
  }
  /** corners q0..q3 as flat xyz (12 numbers), colour, AO per corner, front faces flipped or not */
  quad(q: Float64Array, r: number, g: number, b: number, ao: Float64Array, flip: boolean) {
    this.grow();
    const base = this.verts;
    let pi = base * 3;
    for (let i = 0; i < 4; i++) {
      this.pos[pi] = q[i * 3];
      this.pos[pi + 1] = q[i * 3 + 1];
      this.pos[pi + 2] = q[i * 3 + 2];
      this.col[pi] = Math.min(255, r * ao[i]);
      this.col[pi + 1] = Math.min(255, g * ao[i]);
      this.col[pi + 2] = Math.min(255, b * ao[i]);
      pi += 3;
    }
    this.verts += 4;
    // split along the brighter diagonal (no AO artefacts)
    const alt = ao[0] + ao[2] < ao[1] + ao[3];
    const t0 = base;
    const t1 = base + 1;
    const t2 = alt ? base + 3 : base + 2;
    const t3 = alt ? base + 1 : base;
    const t4 = base + 2;
    const t5 = base + 3;
    let k = this.tris * 3;
    if (flip) {
      this.idx[k++] = t0;
      this.idx[k++] = t2;
      this.idx[k++] = t1;
      this.idx[k++] = t3;
      this.idx[k++] = t5;
      this.idx[k++] = t4;
    } else {
      this.idx[k++] = t0;
      this.idx[k++] = t1;
      this.idx[k++] = t2;
      this.idx[k++] = t3;
      this.idx[k++] = t4;
      this.idx[k++] = t5;
    }
    this.tris += 2;
  }
  done(): MeshData {
    return { pos: this.pos.slice(0, this.verts * 3), col: this.col.slice(0, this.verts * 3), idx: this.idx.slice(0, this.tris * 3) };
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
  // equal blocks of different sections share one kind, so quads merge across section borders
  const kindOf = new Map<PaletteEntry, number>();
  const kindByValue = new Map<string, number>();
  const kindIndex = (e: PaletteEntry) => {
    let k = kindOf.get(e);
    if (k === undefined) {
      const v = `${e.id}|${e.color}|${e.flags}`;
      k = kindByValue.get(v);
      if (k === undefined) {
        kinds.push(e);
        k = kinds.length;
        kindByValue.set(v, k);
      }
      kindOf.set(e, k);
    }
    return k;
  };
  const gi = (x: number, y: number, z: number) => x + 1 + N * (z + 1) + N * N * (y + 1);
  // fill the dense grid section by section (no per-cell map lookups)
  const lo = [x0 - 1, y0 - 1, z0 - 1];
  const hi = [x0 + size, y0 + size, z0 + size];
  for (let scy = Math.floor(lo[1] / 16); scy <= Math.floor(hi[1] / 16); scy++)
    for (let scz = Math.floor(lo[2] / 16); scz <= Math.floor(hi[2] / 16); scz++)
      for (let scx = Math.floor(lo[0] / 16); scx <= Math.floor(hi[0] / 16); scx++) {
        const sec = world.sections.get(VoxelWorld.key(scx, scy, scz));
        if (!sec) continue;
        const bx0 = Math.max(scx * 16, lo[0]);
        const bx1 = Math.min(scx * 16 + 15, hi[0]);
        const by0 = Math.max(scy * 16, lo[1]);
        const by1 = Math.min(scy * 16 + 15, hi[1]);
        const bz0 = Math.max(scz * 16, lo[2]);
        const bz1 = Math.min(scz * 16 + 15, hi[2]);
        const local = sec.palette.map((e) => kindIndex(e));
        for (let wy = by0; wy <= by1; wy++)
          for (let wz = bz0; wz <= bz1; wz++) {
            const row = 16 * (wz - scz * 16) + 256 * (wy - scy * 16) - scx * 16;
            const g = gi(0, wy - y0, wz - z0) - x0;
            for (let wx = bx0; wx <= bx1; wx++) {
              const i = sec.cells[row + wx];
              if (i) grid[g + wx] = local[i - 1];
            }
          }
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
  const mask = new Int32Array(size * size);
  const aoMask = new Int32Array(size * size);
  // grid strides per axis (x, y, z) and the cell (0, 0, 0)
  const stride = [1, N * N, N];
  const origin = gi(0, 0, 0);
  const q = new Float64Array(12);
  const ao = new Float64Array(4);
  const o3 = [x0, y0, z0];
  for (const f of FACES) {
    const { d, u, v, s } = f;
    const sd = stride[d];
    const su = stride[u];
    const sv = stride[v];
    const step = s * sd;
    // the winding is the same for every quad of a face direction: u × v is +d for (x, y), (y, z), (z, x), else −d
    const flip = ((u + 1) % 3 === v ? 1 : -1) * s < 0;
    for (let layer = 0; layer < size; layer++) {
      mask.fill(0);
      for (let j = 0; j < size; j++) {
        let at = origin + layer * sd + j * sv;
        for (let i = 0; i < size; i++, at += su) {
          const b = grid[at];
          if (!b) continue;
          const na = at + step;
          const nb = grid[na];
          // visible when the neighbour is air, or transparent and a different block
          if (nb && (opq[nb] || nb === b)) continue;
          // ambient occlusion: 2 bits per corner (0 darkest … 3 open), corners (-u,-v) (+u,-v) (+u,+v) (-u,+v)
          let aoBits = 0xff;
          if (!(kinds[b - 1].flags & F_EMISSIVE)) {
            aoBits = 0;
            for (let ci = 0; ci < 4; ci++) {
              const du = ci === 0 || ci === 3 ? -su : su;
              const dv = ci < 2 ? -sv : sv;
              const a = opq[grid[na + du]];
              const c2 = opq[grid[na + dv]];
              const corner = opq[grid[na + du + dv]];
              const lvl = a && c2 ? 0 : 3 - (a + c2 + corner);
              aoBits |= lvl << (ci * 2);
            }
          }
          const k = j * size + i;
          mask[k] = b;
          aoMask[k] = aoBits;
        }
      }
      // greedy merge of equal neighbours (same block and same AO)
      const plane = o3[d] + layer + (s > 0 ? 1 : 0);
      const ou = o3[u];
      const ov = o3[v];
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
          // corners (i, j) (i+w, j) (i+w, j+h) (i, j+h)
          for (let ci = 0; ci < 4; ci++) {
            q[ci * 3 + d] = plane;
            q[ci * 3 + u] = ou + i + (ci === 1 || ci === 2 ? w : 0);
            q[ci * 3 + v] = ov + j + (ci >= 2 ? h : 0);
            ao[ci] = AO[(ab >> (ci * 2)) & 3];
          }
          const shade = e.flags & F_EMISSIVE ? 1.15 : f.shade;
          const target = e.flags & F_FLUID ? out.water : e.flags & F_TRANSPARENT ? out.glass : e.flags & F_EMISSIVE ? out.light : out.opaque;
          target.quad(q, ((e.color >> 16) & 255) * shade, ((e.color >> 8) & 255) * shade, (e.color & 255) * shade, ao, flip);
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

/** Which worker of a pool of `count` meshes region (rx, ry, rz): spread evenly, stable. */
export function regionOwner(rx: number, ry: number, rz: number, count: number): number {
  if (count <= 1) return 0;
  const h = (Math.imul(rx, 73856093) ^ Math.imul(ry, 19349663) ^ Math.imul(rz, 83492791)) >>> 0;
  return h % count;
}

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
