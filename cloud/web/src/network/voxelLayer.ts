// "Crazy 3D": the blocks around the network as voxels (docs/SPEC.md "Voxel
// view – batch API"). Sections come from the browser cache (IndexedDB) first,
// then from the server in batches of 64 (nearest to the mainframe first, two
// batches in flight; the server answers "same" for sections the cache already
// has). A small pool of Web Workers decodes and greedy-meshes 32³ regions
// (AO, transferable buffers); meshes are drawn with baked colours: opaque,
// emissive (not dimmed at night, plus additive glow sprites), translucent water
// and glass. A clipping plane cuts away everything above a height.
import {
  AdditiveBlending,
  BufferAttribute,
  BufferGeometry,
  CanvasTexture,
  Color,
  DoubleSide,
  Group,
  Mesh,
  MeshBasicMaterial,
  Plane,
  Points,
  PointsMaterial,
  SRGBColorSpace,
  Vector3,
  type Material,
} from "three";
import { decodeBatch, sectionsAround, type MeshData, type PaletteEntry, type RegionMesh } from "./voxels.ts";
import type { VoxelStatus } from "../lib/net3d.ts";
import { clearVoxelCache, keyOf, readScope, scopeOf, writeSections, type CachedSection } from "../lib/voxelcache.ts";

export interface VoxelSource {
  base: string;
  token: string;
  mf: string;
  dim: string;
  nodes: { x: number; y: number; z: number }[];
  focus: { x: number; y: number; z: number };
  radius: number;
  /** triangle budget (phones ~150k) */
  budget: number;
}

const BATCH = 64;
const IN_FLIGHT = 2;
const skey = (cx: number, cy: number, cz: number) => `${cx},${cy},${cz}`;

function glowTexture() {
  const c = document.createElement("canvas");
  c.width = c.height = 64;
  const g = c.getContext("2d")!;
  const grad = g.createRadialGradient(32, 32, 0, 32, 32, 32);
  grad.addColorStop(0, "rgba(255,255,255,1)");
  grad.addColorStop(0.35, "rgba(255,255,255,0.35)");
  grad.addColorStop(1, "rgba(255,255,255,0)");
  g.fillStyle = grad;
  g.fillRect(0, 0, 64, 64);
  const t = new CanvasTexture(c);
  t.colorSpace = SRGBColorSpace;
  return t;
}

type WorkerSection = { cx: number; cy: number; cz: number; palette: PaletteEntry[]; runs: Uint8Array };

export class VoxelLayer {
  group = new Group();
  private workers: Worker[] = [];
  private busy: boolean[] = [];
  /** the latest mesh per 32³ region ("rx,ry,rz") */
  private regionData = new Map<string, RegionMesh>();
  /** merged meshes per 2×2×2 regions ("gx,gy,gz") */
  private groups = new Map<string, { meshes: Mesh[]; glow: Points | null }>();
  private dirtyGroups = new Set<string>();
  private mergeTimer = 0;
  private queue: [number, number, number][] = [];
  /** hash per section key, from the cache or the server */
  private have = new Map<string, string>();
  private inflight = 0;
  private pausedUntil = 0;
  private timer = 0;
  private gen = 0;
  private disposed = false;
  private src: VoxelSource | null = null;
  private triangles = 0;
  loaded = 0;
  total = 0;
  cached = 0;
  unavailable = false;
  onChange: () => void = () => {};
  private clip = new Plane(new Vector3(0, -1, 0), 10_000);
  private glowTex = glowTexture();
  readonly mats: { opaque: MeshBasicMaterial; light: MeshBasicMaterial; water: MeshBasicMaterial; glass: MeshBasicMaterial; glow: PointsMaterial };

  constructor() {
    const clippingPlanes = [this.clip];
    this.mats = {
      opaque: new MeshBasicMaterial({ vertexColors: true, clippingPlanes }),
      light: new MeshBasicMaterial({ vertexColors: true, clippingPlanes }),
      water: new MeshBasicMaterial({ vertexColors: true, transparent: true, opacity: 0.58, depthWrite: false, side: DoubleSide, clippingPlanes }),
      glass: new MeshBasicMaterial({ vertexColors: true, transparent: true, opacity: 0.4, depthWrite: false, side: DoubleSide, clippingPlanes }),
      glow: new PointsMaterial({ size: 3.2, sizeAttenuation: true, map: this.glowTex, vertexColors: true, transparent: true, opacity: 0.45, depthWrite: false, blending: AdditiveBlending, clippingPlanes }),
    };
    // a small pool: meshing is the slow part on the client; phones get two workers
    const cores = navigator.hardwareConcurrency || 4;
    const small = typeof matchMedia === "function" && matchMedia("(max-width: 719px)").matches;
    const n = Math.max(1, Math.min(small ? 2 : 4, cores - 1));
    for (let i = 0; i < n; i++) {
      const w = new Worker(new URL("./voxelWorker.ts", import.meta.url), { type: "module" });
      w.postMessage({ type: "init", index: i, count: n });
      w.onmessage = (ev: MessageEvent<{ type: "region"; gen: number; key: string; mesh: RegionMesh } | { type: "idle" }>) => {
        // meshes of an earlier load (another dimension) that were still in the worker are dropped
        if (ev.data.type === "region") {
          if (ev.data.gen === this.gen) this.applyRegion(ev.data.key, ev.data.mesh);
        }
        else if (this.busy[i]) {
          this.busy[i] = false;
          this.onChange();
        }
      };
      this.workers.push(w);
      this.busy.push(false);
    }
  }

  /** Starts (or restarts, e.g. another dimension) loading around the nodes. */
  load(src: VoxelSource) {
    const same = this.src && this.src.mf === src.mf && this.src.dim === src.dim && this.src.radius === src.radius;
    this.src = src;
    if (same) return this.pump();
    this.clear();
    const gen = ++this.gen;
    for (const w of this.workers) w.postMessage({ type: "reset", gen });
    this.queue = sectionsAround(src.nodes, src.radius, src.focus);
    this.total = this.queue.length;
    this.loaded = 0;
    this.cached = 0;
    this.unavailable = false;
    this.have.clear();
    // the browser cache first (shown at once), then the server with the cached hashes; never wait long for the cache
    const scope = scopeOf(src.mf, src.dim);
    let started = false;
    const start = () => {
      if (started || gen !== this.gen) return;
      started = true;
      this.pump();
    };
    const fallback = window.setTimeout(start, 1500);
    readScope(scope)
      .then((list) => {
        if (gen !== this.gen || started) return;
        const want = new Set(this.queue.map(([x, y, z]) => skey(x, y, z)));
        const show: WorkerSection[] = [];
        const stale: string[] = [];
        for (const e of list) {
          const k = e.k.slice(scope.length + 1);
          if (!want.has(k)) {
            stale.push(e.k);
            continue;
          }
          const [cx, cy, cz] = k.split(",").map(Number);
          this.have.set(k, e.h);
          if (e.n) show.push({ cx, cy, cz, palette: e.p, runs: e.r });
        }
        this.cached = this.have.size;
        if (show.length) this.post({ type: "sections", list: show });
        void writeSections([], stale);
        this.onChange();
      })
      .finally(() => {
        clearTimeout(fallback);
        start();
      });
  }

  setClip(y: number | null) {
    this.clip.constant = y === null ? 10_000 : y;
  }
  setNight(night: boolean) {
    const dim = night ? 0.42 : 1;
    this.mats.opaque.color = new Color(dim, dim, dim * 1.15);
    this.mats.water.color = new Color(dim, dim, dim * 1.2);
    this.mats.glass.color = new Color(dim, dim, dim);
    this.mats.glow.opacity = night ? 0.95 : 0.35;
    this.mats.glow.size = night ? 4.2 : 3.2;
  }

  get status(): VoxelStatus {
    return {
      loaded: this.loaded,
      total: this.total,
      cached: this.cached,
      triangles: this.triangles,
      building: this.busy.some(Boolean),
      waiting: this.queue.length > 0 && performance.now() < this.pausedUntil,
      unavailable: this.unavailable,
    };
  }

  private post(msg: { type: "sections"; list: WorkerSection[] } | { type: "drop"; list: [number, number, number][] }) {
    this.workers.forEach((w, i) => {
      this.busy[i] = true;
      w.postMessage(msg);
    });
  }

  private pump() {
    clearTimeout(this.timer);
    if (this.disposed || !this.src || this.unavailable) return;
    const now = performance.now();
    while (this.queue.length && this.inflight < IN_FLIGHT && now >= this.pausedUntil && this.triangles < this.src.budget) {
      const list = this.queue.splice(0, BATCH);
      this.inflight++;
      this.fetchBatch(list, this.gen).finally(() => {
        this.inflight--;
        this.pump();
      });
    }
    if (this.queue.length && now < this.pausedUntil) this.timer = window.setTimeout(() => this.pump(), this.pausedUntil - now + 10);
  }

  private async fetchBatch(list: [number, number, number][], gen: number) {
    const src = this.src!;
    const scope = scopeOf(src.mf, src.dim);
    try {
      const body = JSON.stringify({ sections: list.map(([x, y, z]) => (this.have.has(skey(x, y, z)) ? [x, y, z, this.have.get(skey(x, y, z))] : [x, y, z])) });
      const res = await fetch(`${src.base.replace(/\/$/, "")}/api/mainframes/${encodeURIComponent(src.mf)}/voxels/${src.dim}`, {
        method: "POST",
        headers: { authorization: `Bearer ${src.token}`, "content-type": "application/json" },
        body,
      });
      if (this.disposed || gen !== this.gen) return;
      if (res.status === 429) {
        const ra = Number((await res.json().catch(() => ({}))).retryAfter) || 1;
        this.queue.unshift(...list);
        this.pausedUntil = performance.now() + ra * 1000;
        this.onChange();
        return;
      }
      if (res.status === 401 || res.status === 403 || res.status === 404) {
        // no access (any more), voxels off, or a server without voxels: drop what this browser kept
        this.unavailable = true;
        if (res.status !== 404) void clearVoxelCache(scope);
        this.onChange();
        return;
      }
      if (res.status !== 200) {
        this.loaded += list.length;
        return this.onChange();
      }
      const batch = decodeBatch(new Uint8Array(await res.arrayBuffer()));
      if (this.disposed || gen !== this.gen) return;
      const show: WorkerSection[] = [];
      const put: CachedSection[] = [];
      const drop: [number, number, number][] = [];
      const del: string[] = [];
      const t = Date.now();
      for (const s of batch.sections) {
        const k = skey(s.cx, s.cy, s.cz);
        if (s.status === "ok" && s.runs && s.palette && s.hash) {
          const had = this.have.has(k);
          this.have.set(k, s.hash);
          if (s.exposed) show.push({ cx: s.cx, cy: s.cy, cz: s.cz, palette: s.palette, runs: s.runs });
          else if (had) drop.push([s.cx, s.cy, s.cz]);
          put.push({ k: keyOf(scope, s.cx, s.cy, s.cz), h: s.hash, n: s.exposed ?? 0, p: s.palette, r: s.runs, t });
        } else if (s.status === "out_of_range" || s.status === "claimed") {
          if (this.have.delete(k)) {
            drop.push([s.cx, s.cy, s.cz]);
            del.push(keyOf(scope, s.cx, s.cy, s.cz));
          }
        }
        // "same": already shown from the cache; "empty": not loaded now (a cached copy stays)
      }
      if (show.length) this.post({ type: "sections", list: show });
      if (drop.length) this.post({ type: "drop", list: drop });
      void writeSections(put, del);
      this.loaded += list.length;
      this.onChange();
    } catch {
      /* network hiccup or a malformed batch: skip these sections */
      if (gen === this.gen) {
        this.loaded += list.length;
        this.onChange();
      }
    }
  }

  /**
   * Region meshes are merged per 2×2×2 regions (64³ blocks) and material, so the scene needs up to 8× fewer draw
   * calls; three.js still culls each merged mesh against the view frustum. Updates are merged once per frame.
   */
  private applyRegion(key: string, mesh: RegionMesh) {
    if (this.disposed) return;
    const old = this.regionData.get(key);
    if (old) this.triangles -= old.triangles;
    if (mesh.triangles || mesh.glow.pos.length) {
      this.regionData.set(key, mesh);
      this.triangles += mesh.triangles;
    } else this.regionData.delete(key);
    const [rx, ry, rz] = key.split(",").map(Number);
    this.dirtyGroups.add(`${Math.floor(rx / 2)},${Math.floor(ry / 2)},${Math.floor(rz / 2)}`);
    if (!this.mergeTimer) this.mergeTimer = window.setTimeout(() => this.mergeGroups(), 16);
  }

  private mergeGroups() {
    this.mergeTimer = 0;
    if (this.disposed) return;
    for (const gk of this.dirtyGroups) this.buildGroup(gk);
    this.dirtyGroups.clear();
    this.onChange();
  }

  private buildGroup(gk: string) {
    this.dropGroup(gk);
    const [gx, gy, gz] = gk.split(",").map(Number);
    const parts: RegionMesh[] = [];
    for (let i = 0; i < 2; i++)
      for (let j = 0; j < 2; j++)
        for (let k = 0; k < 2; k++) {
          const m = this.regionData.get(`${gx * 2 + i},${gy * 2 + j},${gz * 2 + k}`);
          if (m) parts.push(m);
        }
    if (!parts.length) return;
    const meshes: Mesh[] = [];
    for (const [k, mat, order] of [
      ["opaque", this.mats.opaque, 0],
      ["light", this.mats.light, 0],
      ["glass", this.mats.glass, 2],
      ["water", this.mats.water, 3],
    ] as const) {
      const d = merge(parts.map((p) => p[k]));
      if (!d.idx.length) continue;
      const g = new BufferGeometry();
      g.setAttribute("position", new BufferAttribute(d.pos, 3));
      g.setAttribute("color", new BufferAttribute(d.col, 3, true));
      g.setIndex(new BufferAttribute(d.idx, 1));
      g.computeBoundingSphere();
      const m = new Mesh(g, mat as Material);
      m.renderOrder = order;
      m.matrixAutoUpdate = false;
      meshes.push(m);
      this.group.add(m);
    }
    let glow: Points | null = null;
    const gp = concat(parts.map((p) => p.glow.pos), Float32Array);
    if (gp.length) {
      const g = new BufferGeometry();
      g.setAttribute("position", new BufferAttribute(gp, 3));
      g.setAttribute("color", new BufferAttribute(concat(parts.map((p) => p.glow.col), Uint8Array), 3, true));
      g.computeBoundingSphere();
      glow = new Points(g, this.mats.glow);
      glow.renderOrder = 4;
      this.group.add(glow);
    }
    this.groups.set(gk, { meshes, glow });
  }

  private dropGroup(gk: string) {
    const r = this.groups.get(gk);
    if (!r) return;
    for (const m of r.meshes) {
      m.geometry.dispose();
      this.group.remove(m);
    }
    if (r.glow) {
      r.glow.geometry.dispose();
      this.group.remove(r.glow);
    }
    this.groups.delete(gk);
  }

  get triangleCount() {
    return this.triangles;
  }
  get regionCount() {
    return this.regionData.size;
  }

  clear() {
    for (const k of [...this.groups.keys()]) this.dropGroup(k);
    this.regionData.clear();
    this.dirtyGroups.clear();
    this.triangles = 0;
    this.queue = [];
  }

  dispose() {
    this.disposed = true;
    clearTimeout(this.timer);
    clearTimeout(this.mergeTimer);
    this.clear();
    for (const w of this.workers) w.terminate();
    for (const m of Object.values(this.mats)) m.dispose();
    this.glowTex.dispose();
  }
}

/** Concatenates typed arrays. */
function concat<T extends Float32Array | Uint8Array | Uint32Array>(list: T[], Type: { new (n: number): T }): T {
  let n = 0;
  for (const a of list) n += a.length;
  const out = new Type(n);
  let at = 0;
  for (const a of list) {
    out.set(a, at);
    at += a.length;
  }
  return out;
}

/** One mesh from several (indices shifted by the vertices before them). */
function merge(list: MeshData[]): MeshData {
  if (list.length === 1) return list[0];
  const pos = concat(list.map((m) => m.pos), Float32Array);
  const col = concat(list.map((m) => m.col), Uint8Array);
  const idx = concat(list.map((m) => m.idx), Uint32Array);
  let at = 0;
  let base = 0;
  for (const m of list) {
    if (base) for (let i = 0; i < m.idx.length; i++) idx[at + i] += base;
    at += m.idx.length;
    base += m.pos.length / 3;
  }
  return { pos, col, idx };
}
