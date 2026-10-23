// "Crazy 3D" stage 1: the blocks around the network as voxels (docs/SPEC.md
// "Voxel view – HTTP API"). Sections are fetched with the Bearer token
// (≤ 35/s, nearest first), meshed in a Web Worker (greedy + AO) into 32³
// regions, and drawn with baked colours: opaque, emissive (not dimmed at
// night, plus additive glow sprites), translucent water and glass. A clipping
// plane cuts away everything above a height to look into a base.
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
import { sectionsAround, type MeshData, type RegionMesh } from "./voxels.ts";

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

export class VoxelLayer {
  group = new Group();
  private worker: Worker;
  private regions = new Map<string, { meshes: Mesh[]; glow: Points | null; tris: number }>();
  private queue: [number, number, number][] = [];
  private inflight = 0;
  private stamps: number[] = [];
  private pausedUntil = 0;
  private timer = 0;
  private disposed = false;
  private src: VoxelSource | null = null;
  private triangles = 0;
  loaded = 0;
  total = 0;
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
    this.worker = new Worker(new URL("./voxelWorker.ts", import.meta.url), { type: "module" });
    this.worker.onmessage = (ev: MessageEvent<{ type: "region"; key: string; mesh: RegionMesh }>) => {
      if (ev.data.type === "region") this.applyRegion(ev.data.key, ev.data.mesh);
    };
  }

  /** Starts (or restarts, e.g. another dimension) loading around the nodes. */
  load(src: VoxelSource) {
    const same = this.src && this.src.mf === src.mf && this.src.dim === src.dim && this.src.radius === src.radius;
    this.src = src;
    if (!same) {
      this.clear();
      this.worker.postMessage({ type: "reset" });
      this.queue = sectionsAround(src.nodes, src.radius, src.focus);
      this.total = this.queue.length;
      this.loaded = 0;
    }
    this.pump();
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

  private pump() {
    clearTimeout(this.timer);
    if (this.disposed || !this.src || this.unavailable) return;
    const now = performance.now();
    this.stamps = this.stamps.filter((t) => now - t < 1000);
    while (this.queue.length && this.inflight < 4 && this.stamps.length < 35 && now >= this.pausedUntil && this.triangles < this.src.budget) {
      const s = this.queue.shift()!;
      this.stamps.push(now);
      this.inflight++;
      this.fetchSection(s).finally(() => {
        this.inflight--;
        this.pump();
      });
    }
    if (this.queue.length && this.triangles < this.src.budget) this.timer = window.setTimeout(() => this.pump(), Math.max(30, this.pausedUntil - now));
  }

  private async fetchSection([cx, cy, cz]: [number, number, number]) {
    const src = this.src!;
    try {
      const res = await fetch(`${src.base.replace(/\/$/, "")}/api/mainframes/${encodeURIComponent(src.mf)}/voxels/${src.dim}/${cx}/${cy}/${cz}`, { headers: { authorization: `Bearer ${src.token}` } });
      if (this.disposed || src !== this.src) return;
      if (res.status === 429) {
        this.queue.unshift([cx, cy, cz]);
        this.pausedUntil = performance.now() + 1000;
        return;
      }
      this.loaded++;
      if (res.status === 404 || res.status === 401) {
        this.unavailable = true;
        this.onChange();
        return;
      }
      if (res.status !== 200) return this.onChange();
      const j = await res.json();
      if (!j || !Array.isArray(j.palette) || typeof j.runs !== "string") return;
      if (j.exposed === 0) return this.onChange();
      this.worker.postMessage({ type: "section", cx, cy, cz, palette: j.palette, runs: j.runs });
    } catch {
      /* network hiccup: skip */
    }
  }

  private geometry(m: MeshData) {
    const g = new BufferGeometry();
    g.setAttribute("position", new BufferAttribute(m.pos, 3));
    g.setAttribute("color", new BufferAttribute(m.col, 3, true));
    g.setIndex(new BufferAttribute(m.idx, 1));
    g.computeBoundingSphere();
    return g;
  }

  private applyRegion(key: string, mesh: RegionMesh) {
    if (this.disposed) return;
    this.dropRegion(key);
    const meshes: Mesh[] = [];
    for (const [k, mat, order] of [
      ["opaque", this.mats.opaque, 0],
      ["light", this.mats.light, 0],
      ["glass", this.mats.glass, 2],
      ["water", this.mats.water, 3],
    ] as const) {
      const d = mesh[k];
      if (!d.idx.length) continue;
      const m = new Mesh(this.geometry(d), mat as Material);
      m.renderOrder = order;
      m.matrixAutoUpdate = false;
      meshes.push(m);
      this.group.add(m);
    }
    let glow: Points | null = null;
    if (mesh.glow.pos.length) {
      const g = new BufferGeometry();
      g.setAttribute("position", new BufferAttribute(mesh.glow.pos, 3));
      g.setAttribute("color", new BufferAttribute(mesh.glow.col, 3, true));
      glow = new Points(g, this.mats.glow);
      glow.renderOrder = 4;
      this.group.add(glow);
    }
    this.regions.set(key, { meshes, glow, tris: mesh.triangles });
    this.triangles += mesh.triangles;
    this.onChange();
  }

  private dropRegion(key: string) {
    const r = this.regions.get(key);
    if (!r) return;
    for (const m of r.meshes) {
      m.geometry.dispose();
      this.group.remove(m);
    }
    if (r.glow) {
      r.glow.geometry.dispose();
      this.group.remove(r.glow);
    }
    this.triangles -= r.tris;
    this.regions.delete(key);
  }

  get triangleCount() {
    return this.triangles;
  }
  get regionCount() {
    return this.regions.size;
  }

  clear() {
    for (const k of [...this.regions.keys()]) this.dropRegion(k);
    this.queue = [];
  }

  dispose() {
    this.disposed = true;
    clearTimeout(this.timer);
    this.clear();
    this.worker.terminate();
    for (const m of Object.values(this.mats)) m.dispose();
    this.glowTex.dispose();
  }
}
