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
  device: "uplink",
  bridge: "bridge",
  me_bridge: "bridge",
  rs_bridge: "bridge",
  cloud_switch: "cloud_switch",
  cloud_button: "cloud_button",
  cloud_lamp: "cloud_lamp",
  cloud_sensor: "cloud_sensor",
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
