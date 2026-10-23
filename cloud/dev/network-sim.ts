// DEV-ONLY network simulation for the v0.4 map (docs/SPEC.md "v0.4 – Network").
// Computes links from ranges and routes every node to the mainframe over the
// best path (fewest hops, then best weakest-link signal), max 8 hops.

export type NodeType = "mainframe" | "relay" | "advanced_relay" | "ender_relay" | "uplink" | "advanced_uplink" | "bridge";
export type Status = "online" | "offline" | "no_power" | "no_route" | "unloaded" | "legacy_out_of_range";

export interface SimNode {
  id: string;
  type: NodeType;
  name: string;
  pos: { x: number; y: number; z: number };
  dim: string;
  uplinkId?: string;
  /** relays: has FE */
  powered?: boolean;
  /** chunk loaded */
  loaded?: boolean;
  /** linked before v0.4 */
  legacy?: boolean;
  /** ender relays of the same group link to each other */
  enderGroup?: string;
  /** v0.7: cabled (CC wired network) to this mainframe/relay – no range limit, preferred over radio */
  wired?: string;
  /** v0.7: that cable is CC: Cloud fiber (counts half for capacity, half the FE) */
  fiber?: boolean;
}

export interface Link {
  a: string;
  b: string;
  signal: number;
  dist: number;
  kind: "radio" | "ender" | "wired";
  /** wired links: CC networking cable or fiber */
  medium?: "cc" | "fiber";
  legacy?: boolean;
}

/** v0.4 defaults (blocks). */
export const RANGES: Record<NodeType, { range: number; deviceRange?: number }> = {
  mainframe: { range: 96, deviceRange: 96 },
  relay: { range: 96, deviceRange: 48 },
  advanced_relay: { range: 256, deviceRange: 128 },
  ender_relay: { range: 48, deviceRange: 48 },
  uplink: { range: 48 },
  advanced_uplink: { range: 128 },
  bridge: { range: 48 },
};

export const RELAY_FE: Partial<Record<NodeType, number>> = { relay: 20, advanced_relay: 60, ender_relay: 120 };
export const MAX_HOPS = 8;

const isNode = (t: NodeType) => t === "mainframe" || t === "relay" || t === "advanced_relay" || t === "ender_relay";
const dist = (a: SimNode, b: SimNode) => Math.hypot(a.pos.x - b.pos.x, a.pos.y - b.pos.y, a.pos.z - b.pos.z);
const round = (n: number) => Math.round(n * 100) / 100;

export function computeNetwork(nodes: SimNode[]) {
  const up = (n: SimNode) => n.loaded !== false && (!RELAY_FE[n.type] || n.powered !== false);
  const links: Link[] = [];
  const adj = new Map<string, { to: string; link: Link }[]>();
  const add = (l: Link) => {
    links.push(l);
    for (const [x, y] of [[l.a, l.b], [l.b, l.a]]) {
      if (!adj.has(x)) adj.set(x, []);
      adj.get(x)!.push({ to: y, link: l });
    }
  };
  // v0.7 wired links (CC networking cable): 100 % signal, no range limit, preferred over radio
  const wiredPair = new Set<string>();
  for (const n of nodes)
    if (n.wired && nodes.some((m) => m.id === n.wired)) {
      add({ a: n.wired, b: n.id, signal: 1, dist: 0, kind: "wired", medium: n.fiber ? "fiber" : "cc" });
      wiredPair.add([n.wired, n.id].sort().join("|"));
    }
  for (let i = 0; i < nodes.length; i++)
    for (let j = i + 1; j < nodes.length; j++) {
      const a = nodes[i];
      const b = nodes[j];
      // ender relays linked to the same mainframe form one group (any distance, any dimension)
      if (a.type === "ender_relay" && b.type === "ender_relay") {
        add({ a: a.id, b: b.id, signal: 1, dist: a.dim === b.dim ? round(dist(a, b)) : 0, kind: "ender" });
        continue;
      }
      if (a.dim !== b.dim) continue;
      if (wiredPair.has([a.id, b.id].sort().join("|"))) continue;
      const d = dist(a, b);
      // the mod's rule: a link exists when either side reaches the other (the larger range counts)
      let range: number | undefined;
      if (isNode(a.type) && isNode(b.type)) range = Math.max(RANGES[a.type].range, RANGES[b.type].range);
      else if (isNode(a.type) && !isNode(b.type)) range = Math.max(RANGES[a.type].deviceRange ?? 0, RANGES[b.type].range);
      else if (!isNode(a.type) && isNode(b.type)) range = Math.max(RANGES[b.type].deviceRange ?? 0, RANGES[a.type].range);
      if (range && d <= range) add({ a: a.id, b: b.id, signal: round(1 - d / range), dist: round(d), kind: "radio" });
    }

  // BFS by hop count; among equal hops keep the path with the best weakest link.
  // Devices are leaves: only nodes (mainframe/relays) forward traffic.
  const byId = new Map(nodes.map((n) => [n.id, n]));
  const root = nodes.find((n) => n.type === "mainframe")!;
  const best = new Map<string, { path: string[]; weakest: number }>([[root.id, { path: [root.id], weakest: 1 }]]);
  let frontier = [root.id];
  for (let hop = 1; hop <= MAX_HOPS && frontier.length; hop++) {
    const next: string[] = [];
    for (const id of frontier) {
      const from = byId.get(id)!;
      if (!isNode(from.type) || !up(from)) continue;
      for (const { to, link } of adj.get(id) ?? []) {
        const target = byId.get(to)!;
        if (!up(target) && isNode(target.type)) continue;
        const cur = best.get(id)!;
        const cand = { path: [...cur.path, to], weakest: Math.min(cur.weakest, link.signal) };
        const prev = best.get(to);
        if (!prev) {
          best.set(to, cand);
          next.push(to);
        } else if (prev.path.length === cand.path.length && cand.weakest > prev.weakest) best.set(to, cand);
      }
    }
    frontier = next;
  }

  const routes: Record<string, string[]> = {};
  const status: Record<string, Status> = {};
  for (const n of nodes) {
    const r = best.get(n.id);
    if (RELAY_FE[n.type] && n.powered === false) status[n.id] = "no_power";
    else if (n.loaded === false) status[n.id] = "unloaded";
    else if (r) status[n.id] = "online";
    else if (n.legacy) status[n.id] = "legacy_out_of_range";
    else status[n.id] = "no_route";
    if (r && n.id !== root.id && status[n.id] !== "no_power") routes[n.id] = r.path;
    else if (n.id === root.id) routes[n.id] = [root.id];
    // unloaded nodes keep their last route so the map can show where they were
    if (status[n.id] === "unloaded" && r) routes[n.id] = r.path;
    // legacy: still connected straight to the mainframe
    if (status[n.id] === "legacy_out_of_range" && n.dim === root.dim) {
      const d = dist(n, root);
      add({ a: root.id, b: n.id, signal: 0, dist: round(d), kind: "radio", legacy: true });
      routes[n.id] = [root.id, n.id];
    }
  }
  return { links, routes, status };
}

/** The example base of the dev mock: 4 radio relays, an ender relay pair to the Nether, 11 devices (2 cabled). */
export function exampleNodes(ids: { reactor: string; me: string; storage: string; turbine: string; control: string; computer: string }): SimNode[] {
  const O = "minecraft:overworld";
  const N = "minecraft:the_nether";
  return [
    { id: "mf", type: "mainframe", name: "Mainframe", pos: { x: 12, y: 64, z: -30 }, dim: O },
    // a weak link: far out at the edge of the advanced relay's range
    { id: "relay-north", type: "advanced_relay", name: "North Relay", pos: { x: 30, y: 70, z: -250 }, dim: O, powered: true },
    { id: "relay-hill", type: "advanced_relay", name: "Hill Relay", pos: { x: -150, y: 80, z: 40 }, dim: O, powered: true },
    // out of FE: the tree farm re-routes over the west relay, the quarry loses its route
    { id: "relay-east", type: "advanced_relay", name: "East Relay", pos: { x: 100, y: 64, z: -30 }, dim: O, powered: false },
    { id: "relay-west", type: "relay", name: "West Relay", pos: { x: 50, y: 64, z: -100 }, dim: O, powered: true },
    { id: "ender-home", type: "ender_relay", name: "Ender Relay", pos: { x: 5, y: 64, z: -40 }, dim: O, powered: true },
    { id: "ender-nether", type: "ender_relay", name: "Nether Ender Relay", pos: { x: 2, y: 70, z: -5 }, dim: N, powered: true },
    // devices (device ids = uplink ids, like the mod)
    { id: ids.reactor, type: "uplink", name: "Fission Reactor", pos: { x: 20, y: 62, z: -10 }, dim: O, uplinkId: ids.reactor },
    // v0.7: cabled to the mainframe over a CC wired network
    { id: ids.me, type: "bridge", name: "ME Network", pos: { x: 0, y: 60, z: -50 }, dim: O, uplinkId: ids.me, wired: "mf", fiber: true },
    { id: ids.computer, type: "uplink", name: "Computer #7", pos: { x: 30, y: 64, z: -40 }, dim: O, uplinkId: ids.computer, wired: "mf" },
    { id: ids.storage, type: "uplink", name: "Storage", pos: { x: 40, y: 64, z: -270 }, dim: O, uplinkId: ids.storage },
    { id: ids.control, type: "advanced_uplink", name: "Control Room", pos: { x: -160, y: 82, z: 120 }, dim: O, uplinkId: ids.control },
    { id: ids.turbine, type: "uplink", name: "Turbine Hall", pos: { x: -180, y: 85, z: 60 }, dim: O, uplinkId: ids.turbine, loaded: false },
    { id: "dev-farm", type: "advanced_uplink", name: "Tree Farm", pos: { x: 120, y: 64, z: -110 }, dim: O },
    { id: "dev-quarry", type: "advanced_uplink", name: "Quarry", pos: { x: 200, y: 62, z: -20 }, dim: O },
    { id: "dev-old", type: "uplink", name: "Old Sensor", pos: { x: -200, y: 64, z: -200 }, dim: O, legacy: true },
    { id: "dev-nether", type: "uplink", name: "Nether Farm", pos: { x: 10, y: 70, z: 10 }, dim: N },
  ];
}
