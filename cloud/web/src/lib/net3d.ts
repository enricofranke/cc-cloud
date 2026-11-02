// v0.7 3D view: scene logic without three.js (kept testable in Node).
import type { NetLink, NetNode, NetworkInfo, NodeStatus, NodeType } from "./types.ts";
import type { TerrainPatch } from "./api.ts";
import { pathOf, signalClass } from "./network.ts";

/** Which bundled block model a node type uses (see dev/build-models.ts KINDS). */
export const MODEL_OF: Record<NodeType, string> = {
  mainframe: "mainframe",
  relay: "relay",
  advanced_relay: "advanced_relay",
  ender_relay: "ender_relay",
  uplink: "uplink",
  advanced_uplink: "advanced_uplink",
  probe: "probe",
  device: "uplink",
  bridge: "bridge",
  me_bridge: "bridge",
  rs_bridge: "bridge",
  cloud_switch: "cloud_switch",
  cloud_button: "cloud_button",
  cloud_lamp: "cloud_lamp",
  cloud_sensor: "cloud_sensor",
  advanced_cloud_sensor: "advanced_cloud_sensor",
};

/** Block textures only know online/connecting/offline (+ no_power on relays). */
export function textureStatus(type: NodeType, status: NodeStatus, available: string[]): string {
  const want = status === "online" ? "online" : status === "connecting" ? "connecting" : status === "no_power" ? "no_power" : "offline";
  return available.includes(want) ? want : available.includes("offline") ? "offline" : available[0];
}

export interface ModelPart {
  tex: string;
  emit: boolean;
  trans: boolean;
}
export interface ModelDef {
  parts: ModelPart[];
  tall: number;
  status: Record<string, Record<string, string>>;
}

export interface InstanceGroup {
  /** `${model}|${partIndex}|${texture}` */
  key: string;
  model: string;
  part: number;
  tex: string;
  nodes: number[];
}

/**
 * Groups node instances into draw calls: one InstancedMesh per model part and
 * texture. Parts whose texture does not change with the status are shared by
 * all statuses, so a network of any size costs at most ~40 draw calls.
 */
export function instanceGroups(nodes: NetNode[], models: Record<string, ModelDef>): InstanceGroup[] {
  const groups = new Map<string, InstanceGroup>();
  nodes.forEach((n, i) => {
    if (!n.pos) return;
    const model = MODEL_OF[n.type] ?? "uplink";
    const def = models[model];
    if (!def) return;
    const st = textureStatus(n.type, n.status, Object.keys(def.status));
    const swap = def.status[st] ?? {};
    def.parts.forEach((p, pi) => {
      const tex = swap[p.tex] ?? p.tex;
      const key = `${model}|${pi}|${tex}`;
      let g = groups.get(key);
      if (!g) groups.set(key, (g = { key, model, part: pi, tex, nodes: [] }));
      g.nodes.push(i);
    });
  });
  return [...groups.values()];
}

export interface Segment {
  a: NetNode;
  b: NetNode;
  link: NetLink;
  cls: string;
  used: boolean;
}

/** Links to draw in one dimension: cables, radio (in use and spare) and same-dimension ender pairs. */
export function linkSegments(net: NetworkInfo, dim: string): Segment[] {
  const byId = new Map(net.nodes.map((n) => [n.id, n]));
  const used = new Set<string>();
  for (const n of net.nodes) if (n.parent) used.add(pairKey(n.parent, n.id));
  for (const n of net.nodes) {
    const p = pathOf(net, n.id);
    if (p) for (let i = 1; i < p.length; i++) used.add(pairKey(p[i - 1], p[i]));
  }
  const out: Segment[] = [];
  for (const l of net.links) {
    const a = byId.get(l.a);
    const b = byId.get(l.b);
    if (!a?.pos || !b?.pos || a.dim !== dim || b.dim !== dim) continue;
    out.push({ a, b, link: l, cls: signalClass(l), used: used.has(pairKey(l.a, l.b)) });
  }
  return out;
}
/**
 * Cloud Probes in one dimension: per probe the line to the machine it reads and the 12 edges of that block, as flat
 * segment endpoints (x, y, z, x, y, z, …; world coordinates, block centres).
 */
export function probeLines(net: NetworkInfo, dim: string): { id: string; pos: number[] }[] {
  const out: { id: string; pos: number[] }[] = [];
  for (const n of net.nodes) {
    if (n.type !== "probe" || !n.pos || !n.target || n.dim !== dim) continue;
    const t = n.target;
    const pos = [n.pos.x + 0.5, n.pos.y + 0.5, n.pos.z + 0.5, t.x + 0.5, t.y + 0.5, t.z + 0.5];
    // the machine's outline, a hair larger than the block
    const lo = [t.x - 0.02, t.y - 0.02, t.z - 0.02];
    const hi = [t.x + 1.02, t.y + 1.02, t.z + 1.02];
    const corner = (i: number) => [i & 1 ? hi[0] : lo[0], i & 2 ? hi[1] : lo[1], i & 4 ? hi[2] : lo[2]];
    for (let i = 0; i < 8; i++)
      for (const bit of [1, 2, 4]) if (!(i & bit)) pos.push(...corner(i), ...corner(i | bit));
    out.push({ id: n.id, pos });
  }
  return out;
}

export const pairKey = (a: string, b: string) => (a < b ? `${a}|${b}` : `${b}|${a}`);

/** A relay's subtree: the relay and everything whose route passes through it. */
export function routedThrough(net: NetworkInfo, id: string): Set<string> {
  const out = new Set<string>([id]);
  for (const n of net.nodes) if (n.id !== id && pathOf(net, n.id)?.includes(id)) out.add(n.id);
  // the path from the mainframe to the relay itself
  for (const p of pathOf(net, id) ?? []) out.add(p);
  return out;
}

/** Nearest projected point to (x, y) within `radius` px; ties go to the one closest to the camera. */
export function pickNearest(points: { i: number; x: number; y: number; depth: number }[], x: number, y: number, radius: number): number | undefined {
  let best: { i: number; score: number } | undefined;
  for (const p of points) {
    if (p.depth < -1 || p.depth > 1) continue;
    const d = Math.hypot(p.x - x, p.y - y);
    if (d > radius) continue;
    const score = d + p.depth * 4;
    if (!best || score < best.score) best = { i: p.i, score };
  }
  return best?.i;
}

/** Centre and radius that frame the positioned nodes of one dimension (with their ranges if wanted). */
export function frameNodes(nodes: NetNode[], withRanges = false) {
  const pts = nodes.filter((n) => n.pos);
  if (!pts.length) return { cx: 0, cy: 64, cz: 0, r: 64 };
  let minX = Infinity;
  let maxX = -Infinity;
  let minY = Infinity;
  let maxY = -Infinity;
  let minZ = Infinity;
  let maxZ = -Infinity;
  for (const n of pts) {
    const pad = withRanges ? n.range : 8;
    minX = Math.min(minX, n.pos!.x - pad);
    maxX = Math.max(maxX, n.pos!.x + pad);
    minZ = Math.min(minZ, n.pos!.z - pad);
    maxZ = Math.max(maxZ, n.pos!.z + pad);
    minY = Math.min(minY, n.pos!.y);
    maxY = Math.max(maxY, n.pos!.y);
  }
  return { cx: (minX + maxX) / 2, cy: (minY + maxY) / 2, cz: (minZ + maxZ) / 2, r: Math.max(24, Math.hypot(maxX - minX, maxZ - minZ, maxY - minY) / 2) };
}

export const TERRAIN_DIM = 0.5;

/**
 * Height-field mesh data for one terrain patch, map style: every grid square
 * gets its own 4 vertices in the colour of its cell (crisp, blocky colours on
 * a smooth surface); squares with an unknown corner are left out.
 */
export function terrainMesh(p: TerrainPatch, step: number) {
  const H = p.heights;
  const quads: number[] = [];
  for (let j = 0; j < p.h - 1; j++)
    for (let i = 0; i < p.w - 1; i++) {
      const a = j * p.w + i;
      if (Number.isNaN(H[a]) || Number.isNaN(H[a + 1]) || Number.isNaN(H[a + p.w]) || Number.isNaN(H[a + p.w + 1])) continue;
      quads.push(a);
    }
  const pos = new Float32Array(quads.length * 12);
  const col = new Float32Array(quads.length * 12);
  const idx = quads.length * 4 > 65535 ? new Uint32Array(quads.length * 6) : new Uint16Array(quads.length * 6);
  quads.forEach((a, q) => {
    const i = a % p.w;
    const j = (a - i) / p.w;
    // slope shading from the height difference to the next row (north lighter, south darker)
    const slope = Math.max(-0.14, Math.min(0.14, (H[a] - H[a + p.w]) * 0.06));
    const shade = TERRAIN_DIM * (0.86 + slope);
    const corners = [a, a + 1, a + p.w, a + p.w + 1];
    corners.forEach((k, v) => {
      const o = (q * 4 + v) * 3;
      pos[o] = p.x0 + (i + (v & 1)) * step;
      pos[o + 1] = H[k] + 1;
      pos[o + 2] = p.z0 + (j + (v >> 1)) * step;
      col[o] = (p.colors[a * 3] / 255) * shade;
      col[o + 1] = (p.colors[a * 3 + 1] / 255) * shade;
      col[o + 2] = (p.colors[a * 3 + 2] / 255) * shade;
    });
    const b = q * 4;
    idx.set([b, b + 2, b + 1, b + 1, b + 2, b + 3], q * 6);
  });
  return { pos, col, idx };
}

export interface ScreenLabel {
  id: string;
  /** the anchor on screen (px from the canvas' top left) */
  x: number;
  y: number;
  w: number;
  h: number;
  /** higher goes first and wins a spot */
  priority: number;
}

/**
 * Places the 3D view's labels on screen: above their anchor if there is room, else below, right or left of it; always
 * inside the canvas and never on top of another label. Labels whose anchor is off the canvas, or that find no free
 * spot, are null (hidden until the camera moves). Returns the top left corner of each label.
 */
export function placeScreenLabels(items: ScreenLabel[], width: number, height: number, gap = 10, pad = 4): Map<string, { x: number; y: number } | null> {
  const out = new Map<string, { x: number; y: number } | null>();
  const taken: { x0: number; y0: number; x1: number; y1: number }[] = [];
  for (const it of [...items].sort((a, b) => b.priority - a.priority)) {
    if (it.x < 0 || it.x > width || it.y < 0 || it.y > height || it.w + 2 * pad > width || it.h + 2 * pad > height) {
      out.set(it.id, null);
      continue;
    }
    const spots: [number, number][] = [
      [it.x - it.w / 2, it.y - gap - it.h],
      [it.x - it.w / 2, it.y + gap],
      [it.x + gap, it.y - it.h / 2],
      [it.x - gap - it.w, it.y - it.h / 2],
    ];
    let placed: { x: number; y: number } | null = null;
    for (const [sx, sy] of spots) {
      const x = Math.min(Math.max(sx, pad), width - pad - it.w);
      const y = Math.min(Math.max(sy, pad), height - pad - it.h);
      const box = { x0: x - 1, y0: y - 1, x1: x + it.w + 1, y1: y + it.h + 1 };
      if (taken.some((t) => box.x0 < t.x1 && t.x0 < box.x1 && box.y0 < t.y1 && t.y0 < box.y1)) continue;
      taken.push(box);
      placed = { x, y };
      break;
    }
    out.set(it.id, placed);
  }
  return out;
}

/** Search for "fly to": name, type or id; nodes first, best match first. */
export function searchNodes(net: NetworkInfo, q: string, limit = 8): NetNode[] {
  const s = q.trim().toLowerCase();
  if (!s) return [];
  return net.nodes
    .filter((n) => n.pos && (n.name.toLowerCase().includes(s) || n.type.replace("_", " ").includes(s) || n.id.toLowerCase() === s))
    .sort((a, b) => Number(!a.name.toLowerCase().startsWith(s)) - Number(!b.name.toLowerCase().startsWith(s)) || a.name.localeCompare(b.name))
    .slice(0, limit);
}

/** WebGL available? (false in browsers with WebGL disabled, some VMs, very old GPUs) */
export function hasWebGL(doc: Document = document): boolean {
  try {
    const c = doc.createElement("canvas");
    return !!(c.getContext("webgl2") || c.getContext("webgl"));
  } catch {
    return false;
  }
}

/** Colours for link classes and node statuses (same as the 2D CSS). */
export const LINK_RGB: Record<string, number> = {
  "sig-good": 0x5fd35f,
  "sig-ok": 0x5ec8e5,
  "sig-weak": 0xe8c547,
  "sig-ender": 0xb67cf2,
  "sig-legacy": 0xe8913a,
  "sig-wired": 0x9aa4ae,
  "sig-fiber": 0x45e6f5,
  /** a Cloud Probe's line to its machine (the Cloud Analyzer's colour) */
  probe: 0xb48ae8,
};
export const STATUS_RGB: Partial<Record<NodeStatus, number>> = {
  online: 0x5fd35f,
  connecting: 0xe8c547,
  offline: 0xd34f4f,
  no_route: 0xd34f4f,
  no_power: 0xd34f4f,
  unloaded: 0x7d8790,
  legacy_out_of_range: 0xe8913a,
  redstone: 0xd34f4f,
  cloud_disabled: 0x7d8790,
  over_capacity: 0xe8913a,
};

/** What the voxel loading indicator shows (VoxelLayer.status). */
export interface VoxelStatus {
  /** sections the server has answered */
  loaded: number;
  total: number;
  /** sections shown from the browser cache before the server answered */
  cached: number;
  /** sections that show their last known state (their chunk is not loaded right now) */
  lastSeen: number;
  triangles: number;
  /** workers are still meshing */
  building: boolean;
  /** paused by the server's rate limit */
  waiting: boolean;
  unavailable: boolean;
}

/**
 * The loading line of the voxel view, or "" when everything is shown. Truthful about each phase: waiting for the
 * server's rate limit, loading (or updating cached blocks), and meshing after the last answer.
 */
export function voxelProgress(s: VoxelStatus): string {
  if (s.unavailable || s.total === 0) return "";
  const pct = Math.min(100, Math.floor((s.loaded / s.total) * 100));
  if (s.loaded < s.total) {
    if (s.waiting) return `Server busy – waiting (${pct} %)`;
    return s.cached > 0 ? `Updating blocks ${pct} %` : `Loading blocks ${pct} %`;
  }
  return s.building ? "Building blocks…" : "";
}

/** Some of the terrain shown is from the last visit (docs/SPEC.md "Voxel view – last known terrain"). */
export function voxelLastSeen(s: VoxelStatus): boolean {
  return !s.unavailable && s.lastSeen > 0;
}
