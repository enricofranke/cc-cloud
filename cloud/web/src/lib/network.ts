// v0.4 network map: pure helpers (tree layout, signal classes, labels), unit-tested.
import type { ClaimsOverlay, NetLink, NetNode, NetworkInfo, NodeStatus, NodeType, Uplink, Waystone } from "./types.ts";

export const WEAK_SIGNAL = 0.15;

export const isNodeType = (t: NodeType) => t === "mainframe" || t === "relay" || t === "advanced_relay" || t === "ender_relay";
export const isBridge = (t: NodeType) => t === "bridge" || t === "me_bridge" || t === "rs_bridge";
export const isRelay = (t: NodeType) => t === "relay" || t === "advanced_relay" || t === "ender_relay";

export const TYPE_LABEL: Record<NodeType, string> = {
  mainframe: "Cloud Mainframe",
  relay: "Cloud Relay",
  advanced_relay: "Advanced Cloud Relay",
  ender_relay: "Ender Relay",
  uplink: "Cloud Uplink",
  advanced_uplink: "Advanced Cloud Uplink",
  probe: "Cloud Probe",
  bridge: "Cloud Bridge",
  device: "Linked device",
  me_bridge: "ME Cloud Bridge",
  rs_bridge: "RS Cloud Bridge",
  cloud_switch: "Cloud Switch",
  cloud_button: "Cloud Button",
  cloud_lamp: "Cloud Lamp",
  cloud_sensor: "Cloud Sensor",
  advanced_cloud_sensor: "Advanced Cloud Sensor",
};

export const isRedstoneDevice = (t: NodeType) =>
  t === "cloud_switch" || t === "cloud_button" || t === "cloud_lamp" || t === "cloud_sensor" || t === "advanced_cloud_sensor";

/** Widget id of a Cloud Redstone device on its card: d_ + the first 12 hex digits of its id. */
export const redstoneWidgetId = (deviceId: string) => `d_${deviceId.replace(/-/g, "").slice(0, 12).toLowerCase()}`;

/** The card (section) a Cloud Redstone device sits on right now, if any. */
export function redstoneCardOf(uplinks: Uplink[], deviceId: string): Uplink | undefined {
  const wid = redstoneWidgetId(deviceId);
  return uplinks.find((u) => u.template === "redstone" && u.widgets.some((w) => w.id === wid));
}

export const STATUS_LABEL: Record<NodeStatus, string> = {
  online: "Online",
  offline: "Offline",
  connecting: "Connecting",
  redstone: "Redstone kill switch",
  cloud_disabled: "Cloud disabled",
  no_power: "No power",
  no_route: "No route",
  unloaded: "Unloaded",
  legacy_out_of_range: "Out of range (legacy)",
  over_capacity: "Over capacity",
};

export const STATUS_HINT: Partial<Record<NodeStatus, string>> = {
  no_power: "This relay has no FE. Everything behind it re-routes over another path, if there is one.",
  no_route: "No node within range is online. Place a relay closer, or power the relay it used. Cloud Redstone devices reach 32 blocks.",
  unloaded: "The chunk is not loaded. Relays never force-load chunks.",
  legacy_out_of_range:
    "Linked before v0.4 and out of range under the new rules. It keeps working for now – place a relay nearby or re-link it.",
  offline: "The block is offline (broken, redstone kill switch or disabled).",
  redstone: "A redstone signal on the mainframe switched it off (kill switch).",
  cloud_disabled: "The server admin disabled the cloud function (allowCloud=false).",
  over_capacity: "The mainframe has more devices than its upgrade cards allow. This one stopped until there is room again – add a Capacity card or remove a device.",
};

/** green ≥ 50 %, cyan ≥ 15 %, yellow below (weak); ender links are purple. */
export function signalClass(l: Pick<NetLink, "signal" | "kind" | "legacy" | "weak" | "medium">) {
  if (l.legacy) return "sig-legacy";
  if (l.kind === "ender") return "sig-ender";
  if (l.kind === "wired") return l.medium === "fiber" ? "sig-fiber" : "sig-wired";
  if (l.weak || l.signal < WEAK_SIGNAL) return "sig-weak";
  if (l.signal < 0.5) return "sig-ok";
  return "sig-good";
}

export function signalBars(signal: number) {
  return signal >= 0.75 ? 4 : signal >= 0.5 ? 3 : signal >= WEAK_SIGNAL ? 2 : signal > 0 ? 1 : 0;
}

export function dimLabel(dim: string) {
  const known: Record<string, string> = { "minecraft:overworld": "Overworld", "minecraft:the_nether": "Nether", "minecraft:the_end": "End" };
  return known[dim] ?? dim.replace(/^[a-z0-9_.-]+:/, "").replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}

export const linkKey = (a: string, b: string) => (a < b ? `${a}|${b}` : `${b}|${a}`);

export function findLink(net: NetworkInfo, a: string, b: string) {
  return net.links.find((l) => (l.a === a && l.b === b) || (l.a === b && l.b === a));
}

/**
 * Previous hop of a node towards the mainframe. Prefers the node's `parent`
 * field (mod); falls back to `routes`, which may or may not include the
 * node itself (devices: path of nodes only; relays: including themselves).
 */
export function parentOf(net: NetworkInfo, id: string): string | undefined {
  const n = net.nodes.find((x) => x.id === id);
  if (n?.parent) return n.parent;
  const r = net.routes[id];
  if (!r || !r.length) return undefined;
  if (r[r.length - 1] === id) return r.length >= 2 ? r[r.length - 2] : undefined;
  return r[r.length - 1];
}

/** Full path from the mainframe to the node (both ends included). */
export function pathOf(net: NetworkInfo, id: string): string[] | undefined {
  const r = net.routes[id];
  if (r && r.length) return r[r.length - 1] === id ? r : [...r, id];
  // rebuild from parents
  const out = [id];
  for (let cur = parentOf(net, id), guard = 0; cur && guard < 16; cur = parentOf(net, cur), guard++) out.unshift(cur);
  return out.length > 1 ? out : undefined;
}

/** The link a node uses towards the mainframe. */
export function uplinkOf(net: NetworkInfo, id: string): NetLink | undefined {
  const p = parentOf(net, id);
  return p ? findLink(net, p, id) : undefined;
}

export const hopsOf = (net: NetworkInfo, id: string) => {
  const n = net.nodes.find((x) => x.id === id);
  if (typeof n?.hops === "number") return n.hops;
  const p = pathOf(net, id);
  return p ? p.length - 1 : undefined;
};

export const mainframeOf = (net: NetworkInfo) => net.nodes.find((n) => n.type === "mainframe");

// -------------------------------------------------------------- topology tree
export interface TreeNode {
  node: NetNode;
  x: number;
  y: number;
  depth: number;
  parent?: string;
  /** not reachable: drawn in the "not connected" group */
  orphan: boolean;
}

export const TREE_DX = 150;
export const TREE_DY = 120;
/** left-to-right layout for portrait screens: depth along x, leaves stacked along y */
export const LR_DX = 190;
export const LR_DY = 76;

/**
 * UniFi-style tree: mainframe on top, relays as branches, devices as leaves.
 * Parent = previous hop of each node's route. Nodes without a route hang off
 * a virtual "not connected" column on the right.
 */
export function layoutTree(
  net: NetworkInfo,
  orientation: "tb" | "lr" = "tb",
): { nodes: TreeNode[]; edges: { from: string; to: string; link?: NetLink; orphan: boolean }[]; width: number; height: number } {
  const t = layoutTreeTB(net);
  if (orientation === "tb") return t;
  // swap axes: depth -> x, leaf order -> y
  const nodes = t.nodes.map((n) => ({ ...n, x: (n.y / TREE_DY) * LR_DX, y: (n.x / TREE_DX) * LR_DY }));
  return {
    nodes,
    edges: t.edges,
    width: nodes.reduce((m, n) => Math.max(m, n.x), 0),
    height: nodes.reduce((m, n) => Math.max(m, n.y), 0),
  };
}

function layoutTreeTB(net: NetworkInfo): { nodes: TreeNode[]; edges: { from: string; to: string; link?: NetLink; orphan: boolean }[]; width: number; height: number } {
  const root = mainframeOf(net);
  const ids = new Set(net.nodes.map((n) => n.id));
  const parents = new Map<string, string>();
  for (const n of net.nodes) {
    if (n === root || n.status === "no_route") continue;
    const p = parentOf(net, n.id);
    if (p && ids.has(p) && p !== n.id) parents.set(n.id, p);
  }
  const children = new Map<string, NetNode[]>();
  for (const n of net.nodes) {
    const p = parents.get(n.id);
    if (!p) continue;
    if (!children.has(p)) children.set(p, []);
    children.get(p)!.push(n);
  }
  // relays first (they carry subtrees), then by name – stable and readable
  for (const list of children.values())
    list.sort((a, b) => Number(isRelay(b.type)) - Number(isRelay(a.type)) || a.name.localeCompare(b.name));

  const out: TreeNode[] = [];
  let leaf = 0;
  const place = (n: NetNode, depth: number, parent?: string): number => {
    const kids = children.get(n.id) ?? [];
    let x: number;
    if (!kids.length) x = leaf++ * TREE_DX;
    else {
      const xs = kids.map((k) => place(k, depth + 1, n.id));
      x = (xs[0] + xs[xs.length - 1]) / 2;
    }
    out.push({ node: n, x, y: depth * TREE_DY, depth, parent, orphan: false });
    return x;
  };
  if (root) place(root, 0);
  let maxDepth = out.reduce((m, t) => Math.max(m, t.depth), 0);

  // not connected: everything that has no path to the mainframe
  const placed = new Set(out.map((t) => t.node.id));
  const orphans = net.nodes.filter((n) => !placed.has(n.id));
  if (orphans.length) {
    const ox = (leaf + 0.6) * TREE_DX;
    orphans.forEach((n, i) => out.push({ node: n, x: ox + (i % 2) * TREE_DX, y: (1 + Math.floor(i / 2)) * TREE_DY, depth: 1, orphan: true }));
    maxDepth = Math.max(maxDepth, 1 + Math.ceil(orphans.length / 2) - 1);
  }
  const edges = out
    .filter((t) => t.parent)
    .map((t) => ({ from: t.parent!, to: t.node.id, link: findLink(net, t.parent!, t.node.id), orphan: false }));
  const width = out.reduce((m, t) => Math.max(m, t.x), 0);
  return { nodes: out, edges, width, height: (maxDepth + 1) * TREE_DY };
}

// --------------------------------------------------------------------- map
export function dimsOf(net: NetworkInfo) {
  const order = ["minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"];
  const dims = [...new Set(net.nodes.map((n) => n.dim))];
  return dims.sort((a, b) => (order.indexOf(a) + 1 || 99) - (order.indexOf(b) + 1 || 99) || a.localeCompare(b));
}

/** Bounds of a dimension in map coordinates (x → x, z → y), with some padding. */
export function mapBounds(nodes: NetNode[], withRanges: boolean) {
  let minX = Infinity,
    minY = Infinity,
    maxX = -Infinity,
    maxY = -Infinity;
  for (const n of nodes) {
    if (!n.pos) continue;
    const r = withRanges && isNodeType(n.type) ? Math.max(n.range, n.deviceRange ?? 0) : 8;
    minX = Math.min(minX, n.pos.x - r);
    maxX = Math.max(maxX, n.pos.x + r);
    minY = Math.min(minY, n.pos.z - r);
    maxY = Math.max(maxY, n.pos.z + r);
  }
  if (!Number.isFinite(minX)) return { minX: -64, minY: -64, maxX: 64, maxY: 64 };
  const pad = Math.max(24, (maxX - minX + maxY - minY) * 0.1);
  return { minX: minX - pad, minY: minY - pad, maxX: maxX + pad, maxY: maxY + pad };
}

/** Grid step (16 · 2^k blocks) so that roughly `target` lines fit across `span` blocks. */
export function gridStep(span: number, target = 8) {
  let step = 16;
  while (span / step > target * 1.5) step *= 2;
  while (step > 1 && span / step < target / 2) step /= 2;
  return step;
}

export function distance(a: NetNode, b: NetNode) {
  if (!a.pos || !b.pos) return undefined;
  return Math.hypot(a.pos.x - b.pos.x, a.pos.y - b.pos.y, a.pos.z - b.pos.z);
}

/** Counts per status, for the header chips. */
export function statusCounts(net: NetworkInfo) {
  const c: Partial<Record<NodeStatus, number>> = {};
  for (const n of net.nodes) c[n.status] = (c[n.status] ?? 0) + 1;
  return c;
}

// ------------------------------------------------------------ map labels
export interface LabelIn {
  id: string;
  /** dot centre in screen px */
  x: number;
  y: number;
  text: string;
  /** higher wins (selected > mainframe > relays > problems > devices) */
  priority: number;
  /** dot radius in px */
  r: number;
}
export interface LabelOut {
  x: number;
  y: number;
  anchor: "middle" | "start" | "end";
}
type Rect = { x0: number; y0: number; x1: number; y1: number };
const hit = (a: Rect, b: Rect) => a.x0 < b.x1 && b.x0 < a.x1 && a.y0 < b.y1 && b.y0 < a.y1;

/**
 * Greedy label placement without overlaps: tries below, above, right and left
 * of each dot (most important labels first); labels that fit nowhere are left
 * out (they appear when zoomed in). Positions are relative to the dot, in px.
 */
export function placeLabels(items: LabelIn[], charW = 6.6, h = 13, pad = 3): Map<string, LabelOut | null> {
  const out = new Map<string, LabelOut | null>();
  const taken: Rect[] = items.map((i) => ({ x0: i.x - i.r, y0: i.y - i.r, x1: i.x + i.r, y1: i.y + i.r }));
  for (const it of [...items].sort((a, b) => b.priority - a.priority)) {
    const w = it.text.length * charW + 4;
    const cands: [LabelOut, Rect][] = [
      [{ x: 0, y: it.r + h, anchor: "middle" }, { x0: it.x - w / 2, y0: it.y + it.r + 1, x1: it.x + w / 2, y1: it.y + it.r + h + 2 }],
      [{ x: 0, y: -it.r - 4, anchor: "middle" }, { x0: it.x - w / 2, y0: it.y - it.r - h - 3, x1: it.x + w / 2, y1: it.y - it.r - 2 }],
      [{ x: it.r + pad, y: 4, anchor: "start" }, { x0: it.x + it.r + pad - 1, y0: it.y - h / 2, x1: it.x + it.r + pad + w, y1: it.y + h / 2 }],
      [{ x: -it.r - pad, y: 4, anchor: "end" }, { x0: it.x - it.r - pad - w, y0: it.y - h / 2, x1: it.x - it.r - pad + 1, y1: it.y + h / 2 }],
    ];
    const own = (r: Rect) => taken.filter((t) => !(t.x0 === it.x - it.r && t.y0 === it.y - it.r));
    const pick = cands.find(([, r]) => !own(r).some((t) => hit(r, t)));
    if (pick) {
      out.set(it.id, pick[0]);
      taken.push(pick[1]);
    } else out.set(it.id, null);
  }
  return out;
}

/** Short label of a link: "fiber", "cable", "ender", "legacy" or the radio signal. */
export function linkLabel(l: Pick<NetLink, "signal" | "kind" | "legacy" | "medium">) {
  if (l.kind === "wired") return l.medium === "fiber" ? "fiber" : "cable";
  if (l.kind === "ender") return "ender";
  if (l.legacy) return "legacy";
  return `${Math.round(l.signal * 100)}%`;
}

export interface MapOverlays {
  claims: ClaimsOverlay[];
  waystones: Waystone[];
}
/** Claims and waystones from the mainframe cards (only sent to sessions with view.positions). */
export function overlaysOf(uplinks: Uplink[]): MapOverlays {
  return {
    claims: uplinks.flatMap((u) => (u.claims ? [u.claims] : [])),
    waystones: uplinks.flatMap((u) => u.waystones?.items ?? []),
  };
}
