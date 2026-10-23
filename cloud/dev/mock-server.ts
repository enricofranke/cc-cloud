// DEV-ONLY mock of the mod's embedded web server (docs/SPEC.md "Browser protocol").
// Simulates mainframes with a fission reactor and a storage uplink so the
// dashboard can be developed and tested without Minecraft. Not shipped.
import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import type { AddressInfo } from "node:net";
import { createHash, randomBytes, randomInt, randomUUID } from "node:crypto";
import { WebSocketServer, WebSocket } from "ws";
import { CODE_ALPHABET, SECURITY_HEADERS, clientMessage, normalizeCode, pairRequest, parseJson, type Scalar } from "./protocol.ts";
import { serveStatic } from "./static.ts";
import { isLanHost } from "../web/src/lib/security.ts";
import { FakeAe2, search } from "./ae2-sim.ts";
import { SAMPLE_ICONS } from "./icons.ts";
import { PLAYERS, RANK, TEAMS, DEFAULT_MAPPING, accessDoc, createShare, deviceType, newAccess, playerByName, playerByUuid, resolveRole, sha as sha256, type Access, type Role, type Share } from "./access-sim.ts";
import { cardKeyOf, resolveLayout, normalizeLayout } from "../web/src/lib/layout.ts";
import { computeNetwork, exampleNodes, RANGES, RELAY_FE, type SimNode } from "./network-sim.ts";
import { terrain } from "./terrain-sim.ts";
import { MAX_LAYOUT_BYTES, layoutDoc, opValue, type OpValue } from "./protocol.ts";

type Level = "info" | "warn" | "crit";
type Row = Record<string, string | number | boolean | null>;
interface Widget {
  id: string;
  type:
    | "button" | "toggle" | "slider" | "gauge" | "label" | "item_search" | "watchlist" | "jobs"
    | "chart" | "progress" | "indicator" | "select" | "number" | "text" | "buttons" | "redstone_sides";
  label?: string;
  opts?: Record<string, unknown>;
  value?: Scalar | Row[];
  history?: number[];
}
interface Uplink {
  uplinkId: string;
  title: string;
  online: boolean;
  widgets: Widget[];
  mode?: "auto" | "lua" | "direct";
  pages?: { id: string; title: string; icon?: string }[];
  groups?: { id: string; title: string; page?: string; cols?: number }[];
}
interface Client {
  ws: WebSocket;
  tokenHash: string;
  user: string;
  canControl: boolean;
  /** v0.6 role on this mainframe ("share" for share-link viewers) */
  role: Role | "share";
  /** share-link sessions */
  share?: Share;
  uuid?: string;
  alive: boolean;
  hits: number[];
  queryHits: number[];
}
interface Token {
  /** device tokens: their one mainframe; account tokens: undefined */
  mainframeId?: string;
  kind: "device" | "account";
  /** the player who approved it (role source); undefined for dev tokens */
  uuid?: string;
  /** dev tokens: fixed role */
  fixedRole?: Role;
  deviceId: string;
  type: "desktop" | "phone" | "tablet" | "other";
  lastIp: string;
  created: number;
  lastSeen: number;
  name: string;
  canControl: boolean;
  pinFails: number;
  lockedUntil: number;
}
interface PendingPairing {
  mainframeId?: string;
  /** account linking: the player whose /cccloud link code was used */
  accountUuid?: string;
  ua: string;
  ip: string;
  name: string;
  status: "pending" | "approved" | "denied" | "expired";
  token?: string;
  createdAt: number;
}
interface Mainframe {
  id: string;
  name: string;
  online: boolean;
  enabled: boolean;
  code: string;
  codeIssuedAt: number;
  uplinks: Map<string, Uplink>;
  alerts: { uplinkId?: string; text: string; level: Level; ts: number }[];
  clients: Set<Client>;
  layout: object | null;
  layoutSaves: number[];
  access: Access;
  /** v0.4 network map */
  net?: { nodes: SimNode[]; lastSeen: Record<string, number> };
  /** v0.3 FE buffer; undefined = requirePower=false */
  power?: { stored: number; capacity: number; input: number; usage: number; ok: boolean; brownout: number; breakdown?: { key: string; count?: number; fe: number }[] };
  reason?: string;
  /** Simulates a Lua script that never answers (for rollback tests). */
  ignoreActions: boolean;
  /** action_result carries the widget value after the handler (like a mod that echoes set()) */
  echoResultValue?: boolean;
  onAction?: (uplinkId: string, id: string, value: Scalar, user: string) => void;
  /** Structured operations (craft / watchlist). Synchronous so the result can carry a jobId. */
  onOp?: (uplinkId: string, id: string, op: OpValue, user: string) => { ok: boolean; error?: string; jobId?: string };
  onQuery?: (uplinkId: string, id: string, q: string) => { rows: object[]; truncated: boolean } | undefined;
  tick?: () => void;
}

export interface MockOptions {
  port?: number;
  host?: string;
  staticDir?: string;
  /** 0 disables the simulation tick (deterministic tests). */
  tickMs?: number;
  pingIntervalMs?: number;
  /** Close a socket that has not answered a ping for this long (spec: 60 s). */
  idleTimeoutMs?: number;
  authTimeoutMs?: number;
  codeTtlMs?: number;
  maxConnsPerIp?: number;
  maxConns?: number;
  actionDelayMs?: number;
  /** Add a second, disabled mainframe and an offline uplink. */
  extras?: boolean;
  /** Add the fake AE2 network uplink (default true). */
  ae2?: boolean;
  /** Add the Lua v2 "Control Room" uplink with pages/groups (default true). */
  controlRoom?: boolean;
  /** v0.4: simulate the relay network + map (default true). */
  network?: boolean;
  /** v0.3: the base mainframe needs FE (default true). */
  power?: boolean;
  /** Add an uplink in v0.3 auto mode: the automatic Computer card (default true). */
  computer?: boolean;
  /** "sides" = one redstone_sides widget (SPEC), "widgets" = per-side indicator/toggle/slider widgets in groups. */
  computerFormat?: "sides" | "widgets";
  /** Simulated in-game approval: auto-approve after this delay (-1 = only via /dev/control). Names containing "deny" are denied. */
  approveDelayMs?: number;
  /** v0.7: min ms between terrain requests per token (default 5000) */
  terrainRateMs?: number;
  /** How long a pending pairing waits for approval (spec: 2 minutes). */
  pairTtlMs?: number;
  /** webAllowInternet=false. All test traffic is loopback, so the mock decides by the Host header. */
  lanOnly?: boolean;
  pin?: string;
  pinLockMs?: number;
  log?: (m: string) => void;
}

const sha = (s: string) => createHash("sha256").update(s).digest("hex");
const newCode = () => Array.from({ length: 8 }, () => CODE_ALPHABET[randomInt(CODE_ALPHABET.length)]).join("");
const fmt = (c: string) => `${c.slice(0, 4)}-${c.slice(4)}`;

export const REACTOR = "8a1f2c4e-reactor";
export const STORAGE = "3b7d9e10-storage";
export const TURBINE = "5c2e8f77-turbine";
export const ME = "c4e2b7a1-me-bridge";
export const CONTROL = "7d3a9c02-control";
export const COMPUTER = "b81e44d0-computer";
export const GREENHOUSE = "91d0aa3c-greenhouse";

export async function createMockServer(opts: MockOptions = {}) {
  const log = opts.log ?? ((m: string) => console.log(`[mock] ${m}`));
  const pingIntervalMs = opts.pingIntervalMs ?? 20_000;
  const actionDelayMs = opts.actionDelayMs ?? 80;
  const mainframes = new Map<string, Mainframe>();
  const idleTimeoutMs = opts.idleTimeoutMs ?? 60_000;
  const authTimeoutMs = opts.authTimeoutMs ?? 5000;
  const codeTtlMs = opts.codeTtlMs ?? 10 * 60_000;
  const maxConnsPerIp = opts.maxConnsPerIp ?? 8;
  const maxConns = opts.maxConns ?? 64;
  const tokens = new Map<string, Token>();
  const pairings = new Map<string, PendingPairing>();
  const approveDelayMs = opts.approveDelayMs ?? 3000;
  const pairTtlMs = opts.pairTtlMs ?? 120_000;
  const pin = opts.pin ?? "1234";
  const pinLockMs = opts.pinLockMs ?? 5 * 60_000;
  const timers = new Set<ReturnType<typeof setTimeout>>();
  const later = (ms: number, fn: () => void) => {
    const t = setTimeout(() => {
      timers.delete(t);
      fn();
    }, ms);
    timers.add(t);
  };
  const newToken = (mainframeId: string | undefined, name: string, canControl = true, extra: Partial<Token> = {}) => {
    const token = randomBytes(32).toString("base64url");
    const now = Date.now();
    tokens.set(sha(token), {
      mainframeId,
      kind: mainframeId ? "device" : "account",
      name,
      canControl,
      pinFails: 0,
      lockedUntil: 0,
      deviceId: randomUUID(),
      type: "desktop",
      lastIp: "127.0.0.1",
      created: now,
      lastSeen: now,
      ...extra,
    });
    return token;
  };
  const accountCodes = new Map<string, { uuid: string; code: string }>(); // code -> player
  for (const p of PLAYERS.slice(0, 3)) {
    const code = newCode();
    accountCodes.set(code, { uuid: p.uuid, code });
  }
  const shares = new Map<string, Share>();
  /** Effective role of a token on a mainframe. */
  function roleFor(t: Token, mf: Mainframe): Role | undefined {
    if (t.kind === "device" && t.mainframeId !== mf.id) return undefined;
    if (t.fixedRole) return t.fixedRole;
    if (t.uuid) return resolveRole(mf.access, t.uuid);
    // dev tokens without a player: canControl decides
    return t.canControl ? "admin" : "viewer";
  }
  const rank = (r: string | undefined) => RANK[r ?? "none"] ?? 0;
  function decide(id: string, approve: boolean) {
    const pp = pairings.get(id);
    if (!pp || pp.status !== "pending") return;
    if (Date.now() - pp.createdAt > pairTtlMs) {
      pp.status = "expired";
      return;
    }
    pp.status = approve ? "approved" : "denied";
    const extra: Partial<Token> = { type: deviceType(pp.ua), lastIp: pp.ip };
    if (approve && pp.accountUuid) pp.token = newToken(undefined, pp.name, true, { ...extra, uuid: pp.accountUuid });
    // mainframe codes are approved by the mainframe's owner
    else if (approve) pp.token = newToken(pp.mainframeId, pp.name, true, { ...extra, uuid: mainframes.get(pp.mainframeId!)?.access.owner });
    log(`pairing "${pp.name}" ${pp.status} in-game`);
  }
  const lanBlocked = (req: IncomingMessage) => {
    if (!opts.lanOnly) return false;
    const host = String(req.headers.host ?? "").replace(/:\d+$/, "");
    return !isLanHost(host);
  };
  const LAN_ONLY = {
    error: "internet_access_disabled",
    message: "This server only allows dashboard access from the local network. An admin can set webAllowInternet=true in the cccloud server config.",
  };
  const connsByIp = new Map<string, number>();
  let connCount = 0;
  const pairFails = new Map<string, number[]>();

  // ------------------------------------------------------------ broadcasting
  const sendTo = (ws: WebSocket, msg: object) => ws.readyState === WebSocket.OPEN && ws.send(JSON.stringify(msg));
  const broadcast = (mf: Mainframe, msg: object) =>
    mf.clients.forEach((c) => {
      if (!c.share) return sendTo(c.ws, msg);
      const filtered = shareFilter(mf, c.share, msg as Record<string, any>);
      if (filtered) sendTo(c.ws, filtered);
    });
  // ---- v0.6 share links: what a share session may see
  function shareCards(mf: Mainframe, share: Share) {
    const pages = resolveLayout([...mf.uplinks.values()] as never, mf.layout ? normalizeLayout(mf.layout) : null);
    const keys = new Set(share.cards);
    for (const p of pages) if (share.pages.includes(p.id)) for (const c of p.cards) keys.add(c.key);
    return keys;
  }
  function shareUplink(mf: Mainframe, share: Share, u: Uplink): Uplink | null {
    const keys = shareCards(mf, share);
    const hidden = new Set(["item_search", ...(share.hideNumbers ? ["label", "text", "number"] : [])]);
    const widgets = u.widgets.filter((w) => keys.has(cardKeyOf(u.uplinkId, w as never)) && !hidden.has(w.type));
    return widgets.length ? { ...u, widgets } : null;
  }
  function shareFilter(mf: Mainframe, share: Share, msg: Record<string, any>): object | null {
    switch (msg.t) {
      case "state":
        return { t: "state", online: msg.online, enabled: msg.enabled };
      case "layout":
        return msg;
      case "uplink": {
        const u = shareUplink(mf, share, msg as unknown as Uplink);
        return u ? { t: "uplink", ...u } : { t: "uplink_removed", uplinkId: msg.uplinkId };
      }
      case "uplink_removed":
        return msg;
      case "value": {
        const u = mf.uplinks.get(msg.uplinkId);
        const visible = u && shareUplink(mf, share, u)?.widgets.some((w) => w.id === msg.id);
        return visible ? msg : null;
      }
      case "alert":
        return msg.uplinkId && mf.uplinks.get(msg.uplinkId) && shareUplink(mf, share, mf.uplinks.get(msg.uplinkId)!) ? msg : null;
      default:
        return null; // network, power details, results … never reach share sessions
    }
  }
  const powerOf = (mf: Mainframe) =>
    mf.power
      ? {
          stored: Math.round(mf.power.stored),
          capacity: mf.power.capacity,
          usage: mf.power.usage,
          ok: mf.power.ok,
          ...(mf.power.breakdown ? { breakdown: mf.power.breakdown } : {}),
        }
      : undefined;
  const info = (mf: Mainframe) => ({
    id: mf.id,
    name: mf.name,
    online: mf.online,
    enabled: mf.enabled,
    ...(mf.reason ? { reason: mf.reason } : {}),
    ...(mf.power ? { power: powerOf(mf) } : {}),
  });
  const snapshot = (mf: Mainframe, c?: Client) =>
    c?.share
      ? {
          t: "snapshot",
          role: "share",
          share: {
            id: c.share.id,
            label: c.share.label,
            sharedBy: c.share.createdBy,
            expires: c.share.expires,
            hideNumbers: c.share.hideNumbers,
          },
          mainframe: { id: mf.id, name: mf.name, online: mf.online, enabled: mf.enabled },
          canControl: false,
          uplinks: [...mf.uplinks.values()].map((u) => shareUplink(mf, c.share!, u)).filter(Boolean),
          layout: mf.layout,
        }
      : {
          t: "snapshot",
          mainframe: info(mf),
          canControl: c?.canControl ?? true,
          role: c?.role ?? "admin",
          account: c?.uuid ? { player: playerByUuid(c.uuid)?.player ?? "?" } : null,
          uplinks: [...mf.uplinks.values()],
          layout: mf.layout,
          ...(mf.net ? { network: networkPayload(mf, c?.canControl ?? true) } : {}),
        };

  // ---- v0.4 network map
  /** The network graph in the mod's format (docs/SPEC.md "v0.4 – Implementation notes"). */
  function networkPayload(mf: Mainframe, withPositions: boolean) {
    if (!mf.net) return null;
    const { links, routes, status } = computeNetwork(mf.net.nodes);
    const isNodeT = (t: string) => t === "mainframe" || RELAY_FE[t as keyof typeof RELAY_FE] !== undefined;
    const nodes = mf.net.nodes.map((n) => {
      const path = routes[n.id];
      const fe = RELAY_FE[n.type];
      const pos = withPositions ? { pos: { x: Math.floor(n.pos.x), y: Math.floor(n.pos.y), z: Math.floor(n.pos.z) } } : {};
      // unloaded devices: the mod only knows the link entry
      if (!isNodeT(n.type) && status[n.id] === "unloaded")
        return { id: n.id, type: "device", name: "", status: "unloaded", ...pos, dim: n.dim };
      const st = n.type === "mainframe" ? (mf.online ? "online" : (mf.reason ?? "offline")) : status[n.id] === "legacy_out_of_range" ? "online" : status[n.id];
      return {
        id: n.id,
        type: n.type,
        name: n.name,
        status: st,
        ...(path && path.length > 1 ? { parent: path[path.length - 2], hops: path.length - 1 } : {}),
        ...(status[n.id] === "legacy_out_of_range" ? { flags: ["legacy_out_of_range"] } : {}),
        ...pos,
        dim: n.dim,
        range: RANGES[n.type].range,
        ...(isNodeT(n.type) ? { deviceRange: RANGES[n.type].deviceRange } : {}),
        ...(fe ? { power: { stored: n.powered === false ? 0 : 41_000, capacity: n.type === "relay" ? 50_000 : n.type === "advanced_relay" ? 100_000 : 200_000, usage: fe } } : {}),
        ...(n.type === "mainframe" && mf.power ? { power: { stored: Math.round(mf.power.stored), capacity: mf.power.capacity, usage: mf.power.usage } } : {}),
      };
    });
    return {
      nodes,
      links: links
        .filter((l) => !l.legacy)
        .map((l) => ({ a: l.a, b: l.b, signal: l.signal, ...(withPositions && l.dist ? { dist: l.dist } : {}), kind: l.kind, ...(l.kind === "radio" && l.signal < 0.15 ? { weak: true } : {}) })),
      // like the mod: relays' paths include themselves, devices' paths are the nodes before them
      routes: Object.fromEntries(
        Object.entries(routes)
          .filter(([id]) => id !== mf.net!.nodes[0].id)
          .map(([id, path]) => [id, isNodeT(mf.net!.nodes.find((x) => x.id === id)!.type) ? path : path.slice(0, -1)]),
      ),
    };
  }
  function broadcastNetwork(mf: Mainframe) {
    if (!mf.net) return;
    // deltas: full graph, coordinates stripped for read-only devices
    const full = { t: "network", network: networkPayload(mf, true) };
    const ro = { t: "network", network: networkPayload(mf, false) };
    for (const c of mf.clients) if (!c.share) sendTo(c.ws, c.canControl ? full : ro);
  }
  /** v0.4 power formula: base 40 + 4/uplink + 8/advanced uplink + 10/bridge + 2/client + 1 per extra hop. */
  function powerBreakdown(mf: Mainframe) {
    const items: { key: string; count?: number; fe: number }[] = [{ key: "base", fe: 40 }];
    if (mf.net) {
      const { routes, status } = computeNetwork(mf.net.nodes);
      const routed = mf.net.nodes.filter((n) => routes[n.id] && n.type !== "mainframe" && status[n.id] !== "unloaded");
      const count = (types: string[]) => routed.filter((n) => types.includes(n.type)).length;
      const up = count(["uplink"]);
      const adv = count(["advanced_uplink"]);
      const br = count(["bridge"]);
      const hops = routed.filter((n) => !RELAY_FE[n.type]).reduce((s, n) => s + Math.max(0, routes[n.id].length - 2), 0);
      items.push({ key: "uplinks", count: up, fe: 4 * up }, { key: "advanced_uplinks", count: adv, fe: 8 * adv }, { key: "bridges", count: br, fe: 10 * br });
      items.push({ key: "clients", count: mf.clients.size, fe: 2 * mf.clients.size }, { key: "hops", count: hops, fe: hops });
    } else {
      const uplinks = [...mf.uplinks.values()].filter((u) => u.online).length;
      items.push({ key: "uplinks", count: uplinks, fe: 5 * uplinks }, { key: "clients", count: mf.clients.size, fe: 2 * mf.clients.size });
    }
    return items;
  }
  /** Rotates the code when it is used or older than 10 minutes. */
  function currentCode(mf: Mainframe) {
    if (Date.now() - mf.codeIssuedAt > codeTtlMs) {
      mf.code = newCode();
      mf.codeIssuedAt = Date.now();
    }
    return mf.code;
  }

  function setValue(mf: Mainframe, uplinkId: string, id: string, value: Scalar | Row[]) {
    const w = mf.uplinks.get(uplinkId)?.widgets.find((x) => x.id === id);
    if (w?.type === "chart" && typeof value === "number") {
      // charts: the mod keeps a ring buffer; deltas carry only the new point
      const cap = Math.min(300, Math.max(2, Number(w.opts?.points ?? 60)));
      w.history = [...(w.history ?? []), value].slice(-cap);
      w.value = value;
      broadcast(mf, { t: "value", uplinkId, id, value });
      return;
    }
    if (!w || w.value === value || (Array.isArray(value) && JSON.stringify(w.value) === JSON.stringify(value))) return;
    w.value = value;
    broadcast(mf, { t: "value", uplinkId, id, value });
  }
  function alert(mf: Mainframe, uplinkId: string | undefined, text: string, level: Level) {
    const a = { uplinkId, text, level, ts: Date.now() };
    mf.alerts.push(a);
    log(`alert ${level}: ${text}`);
    broadcast(mf, { t: "alert", ...a });
  }
  function setState(mf: Mainframe, patch: { online?: boolean; enabled?: boolean; reason?: string }) {
    Object.assign(mf, patch);
    if (patch.online === true) mf.reason = undefined;
    broadcast(mf, { t: "state", online: mf.online, enabled: mf.enabled, ...(mf.reason ? { reason: mf.reason } : {}), ...(mf.power ? { power: powerOf(mf) } : {}) });
  }
  function putUplink(mf: Mainframe, u: Uplink) {
    mf.uplinks.set(u.uplinkId, u);
    broadcast(mf, { t: "uplink", ...u });
  }

  // -------------------------------------------------------------- simulation
  function addMainframe(name: string, enabled: boolean): Mainframe {
    const mf: Mainframe = {
      id: randomUUID(),
      name,
      online: true,
      enabled,
      code: newCode(),
      codeIssuedAt: Date.now(),
      uplinks: new Map(),
      alerts: [],
      clients: new Set(),
      layout: null,
      layoutSaves: [],
      access: newAccess(PLAYERS[0].uuid),
      ignoreActions: false,
    };
    mainframes.set(mf.id, mf);
    return mf;
  }

  const base = addMainframe("Mainframe @ 12 64 -30", true);
  if (opts.power !== false) base.power = { stored: 152_000, capacity: 200_000, input: 120, usage: 0, ok: true, brownout: 0 };
  let meSim: FakeAe2 | undefined;
  {
    const WARN = 900;
    const CRIT = 1100;
    const sim = { power: true, temp: 640, output: 40, energy: 2_450_000, items: 18_342, band: "ok" };
    const status = () => (!sim.power ? "Shut down" : sim.temp >= CRIT ? "CRITICAL" : sim.temp >= WARN ? "Overheating" : "Running");
    base.uplinks.set(REACTOR, {
      uplinkId: REACTOR,
      title: "Fission Reactor",
      online: true,
      widgets: [
        { id: "scram", type: "button", label: "SCRAM", opts: { color: "red", confirm: true, pin: true } },
        { id: "power", type: "toggle", label: "Reactor power", opts: { value: true }, value: true },
        { id: "temp", type: "gauge", label: "Core temperature", opts: { min: 0, max: 1200, unit: "K", warn: WARN, crit: CRIT }, value: 640 },
        { id: "coolant", type: "gauge", label: "Coolant", opts: { min: 0, max: 100, unit: "%", warn: 30, crit: 15 }, value: 82 },
        { id: "status", type: "label", label: "Status", value: "Running" },
        { id: "vent", type: "button", label: "Vent steam", opts: { color: "yellow" } },
      ],
    });
    base.uplinks.set(STORAGE, {
      uplinkId: STORAGE,
      title: "Storage",
      online: true,
      widgets: [
        { id: "energy", type: "label", label: "Energy stored", value: "2.45 MFE" },
        { id: "items", type: "label", label: "Items", value: "18,342" },
        { id: "fill", type: "gauge", label: "Capacity", opts: { min: 0, max: 100, unit: "%", warn: 85, crit: 95 }, value: 61 },
        { id: "output", type: "slider", label: "Output limit", opts: { min: 0, max: 100, step: 5, unit: "%" }, value: 40 },
        { id: "export", type: "toggle", label: "Export to AE2", value: false },
        { id: "sort", type: "button", label: "Sort chests", opts: { color: "cyan" } },
      ],
    });
    if (opts.extras)
      base.uplinks.set(TURBINE, {
        uplinkId: TURBINE,
        title: "Turbine Hall",
        online: false,
        widgets: [
          { id: "rpm", type: "gauge", label: "Rotor speed", opts: { min: 0, max: 2000, unit: "RPM", warn: 1800, crit: 1950 }, value: 1240 },
          { id: "coils", type: "toggle", label: "Induction coils", value: true },
        ],
      });

    base.onAction = (uplinkId, id, value, user) => {
      if (uplinkId === REACTOR && id === "power") {
        sim.power = value === true;
        setValue(base, REACTOR, "power", sim.power);
        alert(base, REACTOR, `Reactor ${sim.power ? "started" : "shut down"} by ${user}`, "info");
      } else if (uplinkId === REACTOR && id === "scram") {
        sim.power = false;
        setValue(base, REACTOR, "power", false);
        alert(base, REACTOR, `SCRAM triggered by ${user}`, "warn");
      } else if (uplinkId === REACTOR && id === "vent") {
        sim.temp = Math.max(300, sim.temp - 120);
        setValue(base, REACTOR, "temp", Math.round(sim.temp));
      } else if (uplinkId === STORAGE && id === "output") {
        sim.output = Number(value);
        setValue(base, STORAGE, "output", sim.output);
      } else if (uplinkId === STORAGE && id === "export") {
        setValue(base, STORAGE, "export", value === true);
      } else if (uplinkId === STORAGE && id === "sort") {
        alert(base, STORAGE, `Sorted 27 chests for ${user}`, "info");
      }
      setValue(base, REACTOR, "status", status());
    };
    base.tick = () => {
      // heats toward ~1160 K while running, cools toward 300 K otherwise
      sim.temp += ((sim.power ? 1160 : 300) - sim.temp) * 0.04 + (Math.random() - 0.5) * 14;
      sim.energy += sim.power ? 12_000 : -(sim.output * 300);
      sim.items += Math.round(Math.random() * 4);
      setValue(base, REACTOR, "temp", Math.round(sim.temp));
      setValue(base, REACTOR, "status", status());
      setValue(base, STORAGE, "energy", `${(sim.energy / 1e6).toFixed(2)} MFE`);
      setValue(base, STORAGE, "items", sim.items.toLocaleString("en-US"));
      const band = sim.temp >= CRIT ? "crit" : sim.temp >= WARN ? "warn" : "ok";
      if (band !== sim.band) {
        if (band === "crit") alert(base, REACTOR, `Core temperature critical: ${Math.round(sim.temp)} K`, "crit");
        else if (band === "warn" && sim.band === "ok") alert(base, REACTOR, `Core temperature high: ${Math.round(sim.temp)} K`, "warn");
        sim.band = band;
      }
    };
  }

  // ---- fake AE2 network behind an ME Cloud Bridge (direct mode, v0.2) ----
  if (opts.ae2 !== false) {
    const ae2 = new FakeAe2();
    const pct = (n: number) => Math.round(n * 1000) / 10;
    const statWidgets = (): Widget[] => {
      const st = ae2.stats;
      return [
        { id: "storage", type: "gauge", label: "Storage used", opts: { min: 0, max: 100, unit: "%", warn: 85, crit: 95 }, value: pct(st.total / st.capacity) },
        { id: "energy", type: "gauge", label: "Energy", opts: { min: 0, max: 100, unit: "%", warn: 30, crit: 10 }, value: pct(ae2.energy) },
        { id: "items", type: "label", label: "Total items", value: st.total.toLocaleString("en-US") },
        { id: "types", type: "label", label: "Item types", value: st.types.toLocaleString("en-US") },
        { id: "usage", type: "label", label: "Energy usage", value: "31.5 AE/t" },
      ];
    };
    const me: Uplink = {
      uplinkId: ME,
      title: "ME Network",
      online: true,
      widgets: [
        ...statWidgets(),
        { id: "search", type: "item_search", label: "Search network", opts: { max: 4096 } },
        { id: "watch", type: "watchlist", label: "Watchlist", value: ae2.watchRows() },
        { id: "jobs", type: "jobs", label: "Crafting jobs", value: ae2.jobRows() },
      ],
    };
    // the storage card is the main use case on phones: show it first
    const rest = [...base.uplinks.entries()];
    base.uplinks.clear();
    base.uplinks.set(ME, me);
    for (const [k, v] of rest) base.uplinks.set(k, v);

    const publish = () => {
      for (const w of statWidgets()) setValue(base, ME, w.id, w.value as Scalar);
      setValue(base, ME, "watch", ae2.watchRows());
      setValue(base, ME, "jobs", ae2.jobRows());
    };
    base.onQuery = (uplinkId, _id, q) => (uplinkId === ME ? search(ae2.items, q) : undefined);
    base.onOp = (uplinkId, id, op, user) => {
      if (uplinkId !== ME) return { ok: false, error: "unknown_uplink" };
      if (id === "search" && op.op === "craft") {
        const r = ae2.craft(op.name, op.count);
        if (r.ok) {
          later(actionDelayMs, publish);
          later(actionDelayMs, () => alert(base, ME, `${user} started crafting ${op.count.toLocaleString("en-US")}× ${ae2.items.get(op.name)?.displayName}`, "info"));
        }
        return r;
      }
      if (id === "watch" && op.op !== "craft") {
        const r = ae2.watchOp(op);
        if (r.ok) later(actionDelayMs, publish);
        return r;
      }
      return { ok: false, error: "invalid_value" };
    };
    const reactorTick = base.tick;
    base.tick = () => {
      reactorTick?.();
      for (const e of ae2.tick()) alert(base, ME, e.text, e.level);
      publish();
    };
    meSim = ae2;
  }

  // ---- a Lua v2 dashboard: pages, groups and the new widget types ----
  if (opts.controlRoom !== false) {
    const rand = (() => {
      let s = 99;
      return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 2 ** 32);
    })();
    let load = 120;
    const loadHistory = Array.from({ length: 60 }, (_, i) => Math.round(110 + 30 * Math.sin(i / 6) + rand() * 12));
    const ioHistory = Array.from({ length: 40 }, (_, i) => Math.round(20 * Math.sin(i / 4) + rand() * 6));
    const cr: Uplink = {
      uplinkId: CONTROL,
      title: "Control Room",
      online: true,
      pages: [
        { id: "power", title: "Power", icon: "bolt" },
        { id: "farm", title: "Farm", icon: "leaf" },
      ],
      groups: [
        { id: "grid", title: "Power Grid", page: "power", cols: 2 },
        { id: "matrix", title: "Induction Matrix", page: "power", cols: 1 },
        { id: "trees", title: "Tree Farm", page: "farm", cols: 1 },
        // like the user's "Kontrollraum" Lua program: long button labels, a caption-less label, a select
        { id: "gate", title: "Kontrollraum", page: "farm", cols: 1 },
        // like the mod's Server card: a long multi-entry value
        { id: "server", title: "Server", page: "power", cols: 1 },
      ],
      widgets: [
        { id: "load", type: "chart", label: "Grid load", opts: { group: "grid", min: 0, max: 200, unit: "kFE/t", warn: 160, crit: 185, points: 60 }, value: loadHistory.at(-1)!, history: loadHistory },
        { id: "gens", type: "indicator", label: "Generators", opts: { group: "grid", onColor: "green" }, value: true },
        { id: "fault", type: "indicator", label: "Fault", opts: { group: "grid", onColor: "red" }, value: false },
        { id: "mode", type: "select", label: "Mode", opts: { group: "grid", options: ["eco", "normal", "boost"] }, value: "normal" },
        { id: "priority", type: "buttons", label: "Priority", opts: { group: "grid", options: ["Low", "Mid", "High"] }, value: "Mid" },
        { id: "charge", type: "progress", label: "Charge", opts: { group: "matrix", min: 0, max: 500, unit: "MFE", icon: "bolt" }, value: 312 },
        { id: "io", type: "chart", label: "Net I/O", opts: { group: "matrix", style: "sparkline", unit: "kFE/t", points: 40 }, value: ioHistory.at(-1)!, history: ioHistory },
        { id: "limit", type: "number", label: "Output limit", opts: { group: "matrix", min: 0, max: 500, step: 10, unit: "kFE/t" }, value: 250 },
        { id: "running", type: "indicator", label: "Harvester running", opts: { group: "trees", onColor: "green" }, value: true },
        { id: "saplings", type: "progress", label: "Saplings", opts: { group: "trees", min: 0, max: 64 }, value: 41 },
        { id: "sign", type: "text", label: "Sign text", opts: { group: "trees", maxLength: 32 }, value: "Tree farm – keep out" },
        { id: "harvest", type: "toggle", label: "Auto harvest", opts: { group: "trees" }, value: true },
        { id: "types", type: "label", opts: { group: "gate" }, value: "Types: 3" },
        { id: "tps", type: "label", label: "TPS", opts: { group: "server" }, value: "19.8" },
        { id: "dimensions", type: "label", label: "Tick time per dimension", opts: { group: "server", span: 2 }, value: "overworld 12.4 ms · the_nether 3.1 ms · the_end 0.4 ms · mining_dimension 6.8 ms · the_other 1.2 ms" },
        { id: "program", type: "select", label: "Programm", opts: { group: "gate", options: ["Tag", "Nacht", "Urlaub"] }, value: "Tag" },
        { id: "door", type: "button", label: "Tuer 5 s oeffnen", opts: { group: "gate", color: "cyan" } },
        { id: "start", type: "button", label: "Programm starten", opts: { group: "gate", color: "green" } },
        { id: "stop", type: "button", label: "Notaus", opts: { group: "gate", color: "red" } },
      ],
    };
    base.uplinks.set(CONTROL, cr);
    const prevAction = base.onAction;
    base.onAction = (uplinkId, id, value, user) => {
      if (uplinkId !== CONTROL) return prevAction?.(uplinkId, id, value, user);
      setValue(base, CONTROL, id, value);
      if (id === "mode") alert(base, CONTROL, `Grid mode set to ${value} by ${user}`, "info");
    };
    const prevTick = base.tick;
    base.tick = () => {
      prevTick?.();
      const mode = cr.widgets.find((w) => w.id === "mode")!.value;
      const target = mode === "boost" ? 175 : mode === "eco" ? 90 : 125;
      load = Math.max(0, Math.min(200, load + (target - load) * 0.15 + (rand() - 0.5) * 16));
      setValue(base, CONTROL, "load", Math.round(load));
      setValue(base, CONTROL, "io", Math.round((rand() - 0.45) * 40));
      const charge = cr.widgets.find((w) => w.id === "charge")!;
      setValue(base, CONTROL, "charge", Math.max(0, Math.min(500, Number(charge.value) + Math.round((rand() - 0.4) * 6))));
    };
  }

  // ---- v0.3 auto mode: an uplink on a computer without any Lua widgets ----
  if (opts.computer !== false) {
    const SIDES = ["top", "bottom", "left", "right", "front", "back"] as const;
    const rs = Object.fromEntries(SIDES.map((s) => [s, { out: 0, in: 0 }])) as Record<(typeof SIDES)[number], { out: number; in: number }>;
    rs.back.out = 15;
    rs.left.in = 15;
    let on = true;
    let tickN = 0;
    const rows = () => SIDES.map((side) => ({ side, out: rs[side].out, in: rs[side].in }));
    const perSide = opts.computerFormat === "widgets";
    const cap = (s: string) => s[0].toUpperCase() + s.slice(1);
    base.uplinks.set(COMPUTER, {
      uplinkId: COMPUTER,
      title: "Computer #7",
      online: true,
      mode: "auto",
      widgets: [
        { id: "power", type: "indicator", label: "Computer on", opts: { onColor: "green", offColor: "gray", span: 2 }, value: on },
        { id: "turn_on", type: "button", label: "Turn on", opts: { color: "green" } },
        { id: "reboot", type: "button", label: "Reboot", opts: { color: "yellow", confirm: true } },
        { id: "shutdown", type: "button", label: "Shut down", opts: { color: "red", confirm: true } },
        ...(perSide
          ? SIDES.flatMap((side): Widget[] => [
              { id: `${side}_in`, type: "indicator", label: `${cap(side)} in`, opts: { group: "redstone", onColor: "red" }, value: rs[side].in > 0 },
              { id: `${side}_out`, type: "toggle", label: `${cap(side)} out`, opts: { group: "redstone" }, value: rs[side].out > 0 },
              { id: `${side}_level`, type: "slider", label: `${cap(side)} level`, opts: { group: "advanced", min: 0, max: 15, step: 1 }, value: rs[side].out },
            ])
          : [{ id: "redstone", type: "redstone_sides" as const, label: "Redstone", value: rows() }]),
      ],
      ...(perSide
        ? {
            groups: [
              { id: "redstone", title: "Redstone", cols: 2 },
              { id: "advanced", title: "Advanced", cols: 2 },
            ],
          }
        : {}),
    });
    const publishRs = () => {
      if (!perSide) return setValue(base, COMPUTER, "redstone", rows());
      for (const side of SIDES) {
        setValue(base, COMPUTER, `${side}_in`, rs[side].in > 0);
        setValue(base, COMPUTER, `${side}_out`, rs[side].out > 0);
        setValue(base, COMPUTER, `${side}_level`, rs[side].out);
      }
    };
    const prevOp = base.onOp;
    base.onOp = (uplinkId, id, op, user) => {
      if (uplinkId !== COMPUTER) return prevOp?.(uplinkId, id, op, user) ?? { ok: false, error: "unknown_uplink" };
      if (id !== "redstone" || op.op !== "set_output") return { ok: false, error: "invalid_value" };
      rs[op.side].out = op.value;
      // a wire loops "front" output back into "right" input
      if (op.side === "front") rs.right.in = op.value;
      later(actionDelayMs, publishRs);
      return { ok: true };
    };
    const prevAction = base.onAction;
    base.onAction = (uplinkId, id, value, user) => {
      if (uplinkId !== COMPUTER) return prevAction?.(uplinkId, id, value, user);
      const m = /^(top|bottom|left|right|front|back)_(out|level)$/.exec(id);
      if (m) {
        const side = m[1] as (typeof SIDES)[number];
        rs[side].out = m[2] === "out" ? (value === true ? 15 : 0) : Number(value);
        if (side === "front") rs.right.in = rs[side].out;
        return publishRs();
      }
      if (id === "turn_on") on = true;
      if (id === "shutdown") on = false;
      if (id === "reboot") {
        on = false;
        later(1500, () => {
          on = true;
          setValue(base, COMPUTER, "power", true);
        });
      }
      setValue(base, COMPUTER, "power", on);
      alert(base, COMPUTER, `Computer #7: ${id.replace("_", " ")} by ${user}`, "info");
    };
    const prevTick = base.tick;
    base.tick = () => {
      prevTick?.();
      // a daylight sensor on "top" pulses the input
      tickN++;
      rs.top.in = tickN % 6 < 3 ? Math.min(15, 4 + tickN % 12) : 0;
      publishRs();
    };
  }

  // v0.6: Enrico owns the base; Lisa is a direct admin; the "Builders" FTB team (Tom member, Sam ally) is assigned
  base.access.direct.set(PLAYERS[1].uuid, "admin");
  base.access.team = { provider: "ftbteams", id: "ftb-builders", mapping: { ...DEFAULT_MAPPING.ftbteams } };

  if (opts.network !== false)
    base.net = {
      nodes: exampleNodes({ reactor: REACTOR, me: ME, storage: STORAGE, turbine: TURBINE, control: CONTROL, computer: COMPUTER }),
      lastSeen: {},
    };

  if (opts.extras) {
    addMainframe("New Base @ 220 70 -15", true); // freshly paired, nothing linked yet
    const outpost = addMainframe("Outpost @ -340 72 118", false);
    outpost.uplinks.set(GREENHOUSE, {
      uplinkId: GREENHOUSE,
      title: "Greenhouse",
      online: true,
      widgets: [
        { id: "humidity", type: "gauge", label: "Humidity", opts: { min: 0, max: 100, unit: "%", warn: 30, crit: 15 }, value: 64 },
        { id: "lights", type: "toggle", label: "Grow lights", value: true },
        { id: "note", type: "label", label: "Last harvest: 2 days ago" },
      ],
    });
    outpost.onAction = (u, id, v) => setValue(outpost, u, id, v);
  }

  // ---- v0.3 power: 1 sim tick = 1 s = 20 game ticks
  let powerTicks = 0;
  function tickPower(mf: Mainframe) {
    const p = mf.power;
    if (!p) return;
    p.breakdown = powerBreakdown(mf);
    p.usage = p.breakdown.reduce((s, b) => s + b.fe, 0);
    const draw = p.ok ? p.usage : 0;
    p.stored = Math.max(0, Math.min(p.capacity, p.stored + (p.input - draw) * 20));
    if (p.ok && p.stored <= 0) {
      // brownout: keep going 5 s on nothing before cutting, to avoid flicker
      if (++p.brownout >= 5) {
        p.ok = false;
        p.brownout = 0;
        log(`${mf.name}: out of power`);
        return setState(mf, { online: false, reason: "no_power" });
      }
    } else p.brownout = 0;
    if (!p.ok && p.stored >= p.usage * 20 * 10) {
      p.ok = true;
      log(`${mf.name}: power restored`);
      return setState(mf, { online: true });
    }
    broadcast(mf, { t: "state", online: mf.online, enabled: mf.enabled, ...(mf.reason ? { reason: mf.reason } : {}), power: powerOf(mf) });
  }
  const tickAll = () => {
    powerTicks++;
    // a solar array: strong for 40 s, weak for 40 s -> the buffer drains and refills
    if (base.power) base.power.input = powerTicks % 80 < 40 ? 120 : 30;
    mainframes.forEach((m) => {
      tickPower(m);
      if (m.online) m.tick?.();
    });
  };
  const ticker = opts.tickMs === 0 ? undefined : setInterval(tickAll, opts.tickMs ?? 1000);

  // ----------------------------------------------------------------- actions
  function coerce(w: Widget, value: Scalar | undefined): Scalar | undefined {
    if (w.type === "button") return true;
    if (w.type === "toggle") return typeof value === "boolean" ? value : undefined;
    if (w.type === "slider") {
      if (typeof value !== "number" || !Number.isFinite(value)) return undefined;
      const o = w.opts as { min?: number; max?: number; step?: number };
      const min = o?.min ?? 0;
      const max = o?.max ?? 100;
      const step = o?.step ?? 1;
      return Math.min(max, Math.max(min, Number((min + Math.round((value - min) / step) * step).toFixed(6))));
    }
    const o = (w.opts ?? {}) as { options?: unknown; min?: number; max?: number; step?: number; maxLength?: number };
    if (w.type === "select" || w.type === "buttons")
      return typeof value === "string" && Array.isArray(o.options) && o.options.includes(value) ? value : undefined;
    if (w.type === "number") {
      if (typeof value !== "number" || !Number.isFinite(value)) return undefined;
      if ((o.min !== undefined && value < o.min) || (o.max !== undefined && value > o.max)) return undefined;
      const step = o.step ?? 0;
      const base = o.min ?? 0;
      return step > 0 ? Number((base + Math.round((value - base) / step) * step).toFixed(6)) : value;
    }
    if (w.type === "text") {
      if (typeof value !== "string") return undefined;
      // plain text only: drop control characters, cap the length
      return value.replace(/[\u0000-\u001f\u007f]/g, "").slice(0, Math.min(256, o.maxLength ?? 64));
    }
    return undefined;
  }

  /** A fresh socket: must authenticate with {"t":"auth","token"} within 5 s. */
  function handleSocket(ws: WebSocket, ip: string) {
    let client: Client | undefined;
    let mf: Mainframe | undefined;
    let lastSeen = Date.now();
    const authTimer = setTimeout(() => !client && reject("auth_timeout"), authTimeoutMs);
    const idleTimer = setInterval(() => Date.now() - lastSeen > idleTimeoutMs && ws.terminate(), Math.min(idleTimeoutMs, 5000));
    const reject = (error: string) => {
      sendTo(ws, { t: "error", error });
      ws.close(4001, error);
    };
    const result = (reqId: string, ok: boolean, error?: string, extra: object = {}) =>
      sendTo(ws, { t: "action_result", reqId, ok, ...(error ? { error } : {}), ...extra });

    ws.on("message", (raw) => {
      lastSeen = Date.now();
      const size = Buffer.isBuffer(raw) ? raw.length : Array.isArray(raw) ? raw.reduce((n, b) => n + b.length, 0) : (raw as ArrayBuffer).byteLength;
      if (size > 8192) {
        const head = (Buffer.isBuffer(raw) ? raw : Buffer.from(raw as ArrayBuffer)).subarray(0, 64).toString("utf8");
        if (!client || !/"t"\s*:\s*"layout_set"/.test(head)) return void ws.close(1009, "message too big");
      }
      try {
        const json = parseJson(raw) as { reqId?: unknown } | undefined;
        const parsed = clientMessage.safeParse(json);
        if (!client) {
          const deny = (error: string, code: number) => {
            sendTo(ws, { t: "error", error });
            ws.close(code, error);
          };
          // v0.6 share links: {"t":"auth_share","id","secret"}
          if (parsed.success && parsed.data.t === "auth_share") {
            const sh = shares.get(parsed.data.id);
            if (!sh || sha(parsed.data.secret) !== sh.secretHash || !mainframes.has(sh.mainframeId)) return deny("share_invalid", 4401);
            if (sh.expires && sh.expires < Date.now()) return deny("share_expired", 4401);
            const smf = mainframes.get(sh.mainframeId)!;
            if ([...smf.clients].filter((x) => x.share?.id === sh.id).length >= sh.maxViewers) return deny("share_full", 4429);
            clearTimeout(authTimer);
            mf = smf;
            client = { ws, tokenHash: `share:${sh.id}`, user: `share ${sh.label}`, canControl: false, role: "share", share: sh, alive: true, hits: [], queryHits: [] };
            mf.clients.add(client);
            sendTo(ws, snapshot(mf, client));
            return;
          }
          // first message must be auth
          if (!parsed.success || parsed.data.t !== "auth") return reject("unauthorized");
          const hash = sha(parsed.data.token);
          const t = tokens.get(hash);
          if (!t) return reject("unauthorized");
          const mfId = parsed.data.mainframeId ?? t.mainframeId;
          if (!mfId) return deny("mainframe_required", 4401);
          const target = mainframes.get(mfId);
          if (!target) return deny("no_access", 4403);
          const role = roleFor(t, target);
          if (!role) return deny("no_access", 4403);
          clearTimeout(authTimer);
          mf = target;
          t.lastSeen = Date.now();
          client = { ws, tokenHash: hash, user: t.name, canControl: rank(role) >= 2, role, uuid: t.uuid, alive: true, hits: [], queryHits: [] };
          t.pinFails = t.pinFails ?? 0;
          mf.clients.add(client);
          sendTo(ws, snapshot(mf, client));
          return;
        }
        const c = client;
        const m = mf!;
        c.alive = true;
        if (c.share) {
          // share sessions are strictly read-only: a hard deny on everything but pong
          const t = (json as { t?: unknown } | undefined)?.t;
          if (t === "pong") return;
          const reqId = typeof (json as any)?.reqId === "string" ? (json as any).reqId.slice(0, 64) : undefined;
          const type = t === "action" ? "action_result" : t === "query" ? "query_result" : t === "layout_set" ? "layout_result" : t === "net_unlink" ? "net_result" : null;
          if (type && reqId) return sendTo(ws, { t: type, reqId, ok: false, error: "forbidden", ...(type === "query_result" ? { rows: [], truncated: false } : {}) });
          return sendTo(ws, { t: "error", error: "forbidden" });
        }
        if (!parsed.success) {
          if (json && typeof json.reqId === "string") result(json.reqId.slice(0, 64), false, "invalid_message");
          return;
        }
        const msg = parsed.data;
        if (msg.t === "net_unlink") {
          const nres = (ok: boolean, error?: string) => sendTo(ws, { t: "net_result", reqId: msg.reqId, ok, ...(error ? { error } : {}) });
          if (!c.canControl) return nres(false, "read_only");
          if (rank(c.role) < 3) return nres(false, "not_allowed");
          const node = m.net?.nodes.find((n) => n.id === msg.nodeId);
          if (!node) return nres(false, "unknown_node");
          if (node.type === "mainframe") return nres(false, "not_allowed");
          m.net!.nodes = m.net!.nodes.filter((n) => n !== node);
          log(`${c.user} unlinked ${node.name} from the network`);
          nres(true);
          broadcastNetwork(m);
          return;
        }
        if (msg.t === "layout_set") {
          const lres = (ok: boolean, error?: string) => sendTo(ws, { t: "layout_result", reqId: msg.reqId, ok, ...(error ? { error } : {}) });
          if (!c.canControl) return lres(false, "read_only");
          if (rank(c.role) < 3) return lres(false, "not_allowed");
          const now = Date.now();
          m.layoutSaves = m.layoutSaves.filter((t) => now - t < 10_000);
          if (m.layoutSaves.length >= 5) return lres(false, "rate_limited");
          m.layoutSaves.push(now);
          if (Buffer.byteLength(JSON.stringify(msg.layout ?? null)) > MAX_LAYOUT_BYTES) return lres(false, "too_large");
          const doc = layoutDoc.safeParse(msg.layout);
          if (!doc.success) return lres(false, "invalid_layout");
          m.layout = doc.data;
          log(`${c.user} saved the layout (${doc.data.pages.length} pages, ${Object.keys(doc.data.cards).length} cards)`);
          lres(true);
          broadcast(m, { t: "layout", layout: m.layout });
          return;
        }
        if (msg.t === "query") {
          const now = Date.now();
          const qres = (rows: object[], truncated: boolean, error?: string) =>
            sendTo(ws, { t: "query_result", reqId: msg.reqId, rows, truncated, ...(error ? { error } : {}) });
          const recentQ = [...m.clients].filter((x) => x.tokenHash === c.tokenHash).flatMap((x) => x.queryHits).filter((h) => now - h < 1000);
          if (recentQ.length >= 5) return qres([], false, "rate_limited");
          c.queryHits = [...c.queryHits.filter((h) => now - h < 1000), now];
          if (!m.online) return qres([], false, m.reason === "no_power" ? "no_power" : "offline");
          const qu = m.uplinks.get(msg.uplinkId);
          const qw = qu?.widgets.find((x) => x.id === msg.id);
          if (!qu || !qw || qw.type !== "item_search") return qres([], false, "unknown_widget");
          if (!qu.online) return qres([], false, "uplink_offline");
          const r = m.onQuery?.(msg.uplinkId, msg.id, msg.q);
          return r ? qres(r.rows, r.truncated) : qres([], false, "unavailable");
        }
        if (msg.t !== "action") return; // pong / repeated auth
        const now = Date.now();
        // per-token sliding window: several tabs of one device share the budget
        const recent = [...m.clients].filter((x) => x.tokenHash === c.tokenHash).flatMap((x) => x.hits).filter((h) => now - h < 1000);
        if (recent.length >= 10) return result(msg.reqId, false, "rate_limited");
        c.hits = [...c.hits.filter((h) => now - h < 1000), now];
        if (!c.canControl) return result(msg.reqId, false, "read_only");
        if (!m.online) return result(msg.reqId, false, m.reason === "no_power" ? "no_power" : "offline");
        if (!m.enabled) return result(msg.reqId, false, "disabled");
        const u = m.uplinks.get(msg.uplinkId);
        if (!u) return result(msg.reqId, false, "unknown_uplink");
        if (!u.online) return result(msg.reqId, false, "uplink_offline");
        const w = u.widgets.find((x) => x.id === msg.id);
        if (!w) return result(msg.reqId, false, "unknown_widget");
        if ((w.opts as { pin?: boolean } | undefined)?.pin) {
          const t = tokens.get(c.tokenHash);
          if (!t) return reject("revoked");
          if (t.lockedUntil > now) return result(msg.reqId, false, "pin_locked", { retryAfter: Math.ceil((t.lockedUntil - now) / 1000) });
          if (!msg.pin) return result(msg.reqId, false, "pin_required");
          if (msg.pin !== pin) {
            t.pinFails++;
            if (t.pinFails >= 5) {
              t.pinFails = 0;
              t.lockedUntil = now + pinLockMs;
              log(`device ${c.user} locked for ${pinLockMs / 1000}s after 5 wrong PINs (owner notified in-game)`);
              return result(msg.reqId, false, "pin_locked", { retryAfter: Math.ceil(pinLockMs / 1000) });
            }
            return result(msg.reqId, false, "wrong_pin", { attemptsLeft: 5 - t.pinFails });
          }
          t.pinFails = 0;
        }
        if (w.type === "item_search" || w.type === "watchlist" || w.type === "redstone_sides") {
          const op = opValue.safeParse(msg.value);
          const allowed = w.type === "item_search" ? ["craft"] : w.type === "watchlist" ? ["add", "remove", "set_min"] : ["set_output"];
          if (!op.success || !allowed.includes(op.data.op)) return result(msg.reqId, false, "invalid_value");
          const r = m.onOp?.(msg.uplinkId, msg.id, op.data, c.user) ?? { ok: false, error: "unavailable" };
          log(`${c.user} ${op.data.op} ${u.title}/${w.id} ${JSON.stringify(op.data)} -> ${r.ok ? "ok" : r.error}`);
          return result(msg.reqId, r.ok, r.error, r.jobId ? { jobId: r.jobId } : {});
        }
        if (w.type === "jobs") return result(msg.reqId, false, "not_interactive");
        const value = coerce(w, msg.value as Scalar | undefined);
        if (value === undefined) return result(msg.reqId, false, w.type === "gauge" || w.type === "label" ? "not_interactive" : "invalid_value");
        log(`${c.user} pressed ${u.title}/${w.id} = ${JSON.stringify(value)}`);
        result(msg.reqId, true, undefined, m.echoResultValue ? { value } : {});
        if (!m.ignoreActions) setTimeout(() => m.onAction?.(msg.uplinkId, msg.id, value, c.user), actionDelayMs);
      } catch (e) {
        log(`ws error ${(e as Error).message}`);
      }
    });
    ws.on("close", () => {
      clearTimeout(authTimer);
      clearInterval(idleTimer);
      if (client) mf?.clients.delete(client);
      connCount--;
      const n = (connsByIp.get(ip) ?? 1) - 1;
      if (n <= 0) connsByIp.delete(ip);
      else connsByIp.set(ip, n);
    });
    ws.on("error", () => {});
  }

  // -------------------------------------------------------------------- http
  const json = (res: ServerResponse, status: number, body: unknown) => {
    res.writeHead(status, { ...SECURITY_HEADERS, "content-type": "application/json", "cache-control": "no-store" });
    res.end(JSON.stringify(body));
  };
  async function body(req: IncomingMessage): Promise<unknown> {
    const chunks: Buffer[] = [];
    let n = 0;
    for await (const ch of req) {
      n += (ch as Buffer).length;
      if (n > 4096) throw new Error("too large");
      chunks.push(ch as Buffer);
    }
    return JSON.parse(Buffer.concat(chunks).toString("utf8") || "null");
  }
  const terrainCalls = new WeakMap<object, number>();
  const terrainRateMs = opts.terrainRateMs ?? 5000;
  const bearer = (req: IncomingMessage) => {
    const m = /^Bearer\s+(\S+)$/i.exec(req.headers.authorization ?? "");
    return m && tokens.has(sha(m[1])) ? tokens.get(sha(m[1])) : undefined;
  };

  async function handle(req: IncomingMessage, res: ServerResponse) {
    const url = new URL(req.url ?? "/", "http://mock");
    const p = url.pathname;
    if (lanBlocked(req) && !p.startsWith("/dev/")) {
      // like the mod: plain-text 403
      res.writeHead(403, { ...SECURITY_HEADERS, "content-type": "text/plain; charset=utf-8", "cache-control": "no-store" });
      return void res.end(LAN_ONLY.message);
    }
    if (p === "/api/pair" && req.method === "POST") {
      const ip = req.socket.remoteAddress ?? "?";
      const now = Date.now();
      const fails = (pairFails.get(ip) ?? []).filter((t) => now - t < 60_000);
      pairFails.set(ip, fails);
      if (fails.length >= 5) return json(res, 429, { error: "too_many_attempts" });
      let parsed;
      try {
        parsed = pairRequest.safeParse(await body(req));
      } catch {
        return json(res, 400, { error: "invalid_request" });
      }
      if (!parsed.success) return json(res, 400, { error: "invalid_request" });
      const code = normalizeCode(parsed.data.code);
      const ua = String(req.headers["user-agent"] ?? "");
      // v0.6: an account code from /cccloud link links the browser to the player account
      const acc = accountCodes.get(code);
      if (acc) {
        accountCodes.delete(code);
        const fresh = newCode();
        accountCodes.set(fresh, { uuid: acc.uuid, code: fresh });
        const pairingId = randomUUID();
        pairings.set(pairingId, { accountUuid: acc.uuid, ua, ip, name: parsed.data.name, status: "pending", createdAt: now });
        log(`"${parsed.data.name}" wants to link to the account of ${playerByUuid(acc.uuid)?.player}`);
        if (approveDelayMs >= 0) later(approveDelayMs, () => decide(pairingId, !/deny/i.test(parsed.data.name)));
        return json(res, 202, { status: "pending", pairingId, expiresIn: Math.round(pairTtlMs / 1000) });
      }
      const mf = [...mainframes.values()].find((m) => currentCode(m) === code);
      if (!mf) {
        fails.push(now);
        return json(res, 403, { error: "invalid_code" });
      }
      mf.code = newCode();
      mf.codeIssuedAt = Date.now();
      const pairingId = randomUUID();
      pairings.set(pairingId, { mainframeId: mf.id, ua, ip, name: parsed.data.name, status: "pending", createdAt: now });
      log(`"${parsed.data.name}" wants to connect to ${mf.name} from ${ip} – waiting for approval; new code ${fmt(mf.code)}`);
      if (approveDelayMs >= 0) later(approveDelayMs, () => decide(pairingId, !/deny/i.test(parsed.data.name)));
      return json(res, 202, { status: "pending", pairingId, expiresIn: Math.round(pairTtlMs / 1000) });
    }
    // public, like the mod: item icons (a few samples, 404 for everything else)
    const im = /^\/api\/icon\/([a-z0-9_.-]+)\/((?:[a-z0-9_.-]+\/)*[a-z0-9_.-]+)\.png$/.exec(p);
    if (p.startsWith("/api/icon/") && req.method === "GET") {
      const png = im && !im[2].split("/").some((s) => s === "." || s === "..") ? SAMPLE_ICONS[`${im[1]}:${im[2]}`] : undefined;
      if (!png) return json(res, 404, { error: "unknown_item" });
      res.writeHead(200, { ...SECURITY_HEADERS, "content-type": "image/png", "content-length": png.length, "cache-control": "public, max-age=86400" });
      return void res.end(png);
    }
    const pm = /^\/api\/pair\/([A-Za-z0-9-]{1,64})$/.exec(p);
    if (pm && req.method === "GET") {
      const pp = pairings.get(pm[1]);
      if (!pp) return json(res, 404, { status: "expired" });
      if (pp.status === "pending" && Date.now() - pp.createdAt > pairTtlMs) pp.status = "expired";
      if (pp.status === "approved") {
        pairings.delete(pm[1]); // the token is handed out exactly once
        if (pp.accountUuid) {
          const t = tokens.get(sha(pp.token!))!;
          return json(res, 200, {
            status: "approved",
            token: pp.token,
            account: { player: playerByUuid(pp.accountUuid)?.player, uuid: pp.accountUuid },
            mainframes: [...mainframes.values()].map((m) => ({ id: m.id, name: m.name, role: roleFor(t, m) })).filter((m) => m.role),
          });
        }
        const role = roleFor(tokens.get(sha(pp.token!))!, mainframes.get(pp.mainframeId!)!);
        return json(res, 200, {
          status: "approved",
          token: pp.token,
          mainframeId: pp.mainframeId,
          name: mainframes.get(pp.mainframeId!)?.name,
          device: pp.name,
          canControl: rank(role) >= 2,
          role,
        });
      }
      if (pp.status !== "pending") pairings.delete(pm[1]);
      return json(res, pp.status === "denied" ? 403 : pp.status === "expired" ? 404 : 200, { status: pp.status });
    }
    if (p === "/api/mainframes" && req.method === "GET") {
      const t = bearer(req);
      if (!t) return json(res, 401, { error: "unauthorized" });
      const list = [...mainframes.values()]
        .map((m) => ({ m, role: roleFor(t, m) }))
        .filter((x) => x.role)
        .map(({ m, role }) => ({ ...info(m), role, canControl: rank(role) >= 2 }));
      return json(res, 200, { mainframes: list, account: t.uuid && t.kind === "account" ? { player: playerByUuid(t.uuid)?.player, uuid: t.uuid } : null });
    }
    // ---- v0.6 devices: all browsers of the player
    if (p === "/api/devices" && req.method === "GET") {
      const t = bearer(req);
      if (!t) return json(res, 401, { error: "unauthorized" });
      const mine = [...tokens.values()].filter((x) => x === t || (t.kind === "account" && t.uuid && x.uuid === t.uuid));
      return json(res, 200, {
        devices: mine.map((x) => ({
          id: x.deviceId,
          name: x.name,
          type: x.type,
          lastIp: x.lastIp,
          lastSeen: x.lastSeen,
          created: x.created,
          current: x === t,
          mainframes: x.kind === "account" ? null : [x.mainframeId],
        })),
      });
    }
    const dm = /^\/api\/devices\/([A-Za-z0-9-]{1,64})$/.exec(p);
    if (dm && req.method === "DELETE") {
      const t = bearer(req);
      if (!t) return json(res, 401, { error: "unauthorized" });
      const entry = [...tokens.entries()].find(([, x]) => x.deviceId === dm[1]);
      if (!entry || !(entry[1] === t || (t.kind === "account" && t.uuid && entry[1].uuid === t.uuid))) return json(res, 404, { error: "not_found" });
      tokens.delete(entry[0]);
      for (const m of mainframes.values())
        m.clients.forEach((c) => {
          if (c.tokenHash === entry[0]) {
            sendTo(c.ws, { t: "error", error: "revoked" });
            c.ws.close(4401, "revoked");
          }
        });
      return json(res, 200, { ok: true });
    }
    // ---- v0.7 coarse terrain for the 3D view (operator+, rate-limited per token)
    const tm = /^\/api\/mainframes\/([A-Za-z0-9-]{1,64})\/terrain$/.exec(p);
    if (tm && req.method === "GET") {
      const t = bearer(req);
      if (!t) return json(res, 401, { error: "unauthorized" });
      const m = mainframes.get(tm[1]);
      const role = m ? roleFor(t, m) : undefined;
      if (!m || !role) return json(res, 403, { error: "no_access" });
      if (rank(role) < 2) return json(res, 403, { error: "not_allowed" });
      const now = Date.now();
      const last = terrainCalls.get(t) ?? 0;
      if (now - last < terrainRateMs) return json(res, 429, { error: "rate_limited", retryAfter: Math.ceil((terrainRateMs - (now - last)) / 1000) });
      terrainCalls.set(t, now);
      const dim = url.searchParams.get("dim") ?? "minecraft:overworld";
      const doc = m.net ? terrain(m.net.nodes, dim) : { dim, step: 2, patches: [], truncated: false };
      res.writeHead(200, { ...SECURITY_HEADERS, "content-type": "application/json", "cache-control": "private, max-age=60" });
      return void res.end(JSON.stringify(doc));
    }
    // ---- v0.6 access + shares (admin+)
    const am = /^\/api\/mainframes\/([A-Za-z0-9-]{1,64})\/(access|shares)(?:\/([A-Za-z0-9]{1,32}))?$/.exec(p);
    if (am) {
      const t = bearer(req);
      if (!t) return json(res, 401, { error: "unauthorized" });
      const m = mainframes.get(am[1]);
      const role = m ? roleFor(t, m) : undefined;
      if (!m || !role) return json(res, 403, { error: "no_access" });
      const you = { player: (t.uuid && playerByUuid(t.uuid)?.player) || t.name, role };
      if (rank(role) < 3) return json(res, 403, { error: "not_allowed" });
      if (am[2] === "access") {
        if (req.method === "GET") return json(res, 200, accessDoc(m.access, you));
        if (req.method !== "POST") return json(res, 405, { error: "method" });
        const b = ((await body(req).catch(() => null)) ?? {}) as Record<string, any>;
        if (b.op === "set_role" && ["viewer", "operator", "admin"].includes(b.role)) {
          const pl = playerByName(String(b.player ?? ""));
          if (!pl) return json(res, 400, { error: "unknown_player" });
          if (pl.uuid === m.access.owner) return json(res, 400, { error: "invalid" });
          if (b.role === "admin" && rank(role) < 3) return json(res, 403, { error: "not_allowed" });
          m.access.direct.set(pl.uuid, b.role);
        } else if (b.op === "remove") {
          const pl = playerByName(String(b.player ?? ""));
          if (!pl) return json(res, 400, { error: "unknown_player" });
          m.access.direct.delete(pl.uuid);
        } else if (b.op === "set_team") {
          if (b.teamId === null) m.access.team = null;
          else {
            const team = TEAMS.find((x) => x.provider === b.provider && x.id === b.teamId);
            if (!team) return json(res, 400, { error: "invalid" });
            m.access.team = { provider: team.provider, id: team.id, mapping: { ...DEFAULT_MAPPING[team.provider] } };
          }
        } else if (b.op === "set_mapping" && m.access.team && ["viewer", "operator", "admin", "none"].includes(b.role)) {
          if (!(String(b.rank) in m.access.team.mapping)) return json(res, 400, { error: "invalid" });
          m.access.team.mapping[String(b.rank)] = b.role;
        } else return json(res, 400, { error: "invalid" });
        // role changes apply live
        for (const c of m.clients) {
          if (!c.uuid || c.share) continue;
          const nr = resolveRole(m.access, c.uuid);
          if (!nr) {
            sendTo(c.ws, { t: "error", error: "no_access" });
            c.ws.close(4403, "no_access");
          } else if (nr !== c.role) {
            c.role = nr;
            c.canControl = rank(nr) >= 2;
            sendTo(c.ws, { t: "role", role: nr, canControl: c.canControl });
          }
        }
        log(`${you.player} changed access: ${JSON.stringify(b)}`);
        return json(res, 200, accessDoc(m.access, { ...you, role: roleFor(t, m)! }));
      }
      // shares
      const list = () => ({
        shares: [...shares.values()]
          .filter((s) => s.mainframeId === m.id)
          .map(({ secretHash: _s, mainframeId: _m, ...rest }) => ({ ...rest, viewers: [...m.clients].filter((c) => c.share?.id === rest.id).length })),
      });
      if (req.method === "GET" && !am[3]) return json(res, 200, list());
      if (req.method === "POST" && !am[3]) {
        const b = ((await body(req).catch(() => null)) ?? {}) as Record<string, any>;
        const pages = Array.isArray(b.pages) ? b.pages.filter((x: unknown) => typeof x === "string").slice(0, 12) : [];
        const cards = Array.isArray(b.cards) ? b.cards.filter((x: unknown) => typeof x === "string").slice(0, 64) : [];
        const exp = b.expiresIn === null ? null : [3600, 86400, 604800].includes(b.expiresIn) ? b.expiresIn : undefined;
        const max = Number.isInteger(b.maxViewers) && b.maxViewers >= 1 && b.maxViewers <= 50 ? b.maxViewers : undefined;
        if (!pages.length && !cards.length) return json(res, 400, { error: "invalid", detail: "pick at least one page or card" });
        if (exp === undefined || max === undefined) return json(res, 400, { error: "invalid" });
        const { share, secret } = createShare({
          mainframeId: m.id,
          label: String(b.label ?? "Shared view").slice(0, 48) || "Shared view",
          createdBy: you.player,
          maxViewers: max,
          pages,
          cards,
          hideNumbers: b.hideNumbers === true,
          expiresIn: exp,
        });
        shares.set(share.id, share);
        log(`${you.player} created share link ${share.id}`);
        return json(res, 200, { id: share.id, secret, url: `/s/${share.id}#${secret}` });
      }
      if (req.method === "DELETE" && am[3]) {
        const sh = shares.get(am[3]);
        if (!sh || sh.mainframeId !== m.id) return json(res, 404, { error: "not_found" });
        shares.delete(sh.id);
        m.clients.forEach((c) => {
          if (c.share?.id === sh.id) {
            sendTo(c.ws, { t: "error", error: "share_revoked" });
            c.ws.close(4401, "share_revoked");
          }
        });
        return json(res, 200, { ok: true });
      }
      return json(res, 405, { error: "method" });
    }
    // ---- dev-only helpers (not part of the mod's API) ----
    if (p === "/dev/codes" && req.method === "GET") {
      return json(res, 200, [
        ...[...mainframes.values()].map((m) => ({ id: m.id, name: m.name, code: fmt(currentCode(m)) })),
        ...[...accountCodes.values()].map((a) => ({ account: playerByUuid(a.uuid)?.player, code: fmt(a.code) })),
      ]);
    }
    if (p === "/dev/token" && req.method === "POST") {
      // issue a token directly, e.g. a read-only device: {"name":"TV","canControl":false}
      const b = ((await body(req).catch(() => null)) ?? {}) as Record<string, any>;
      const mf = b.mainframeId ? mainframes.get(b.mainframeId) : base;
      if (!mf) return json(res, 404, { error: "unknown mainframe" });
      // v0.6: {"account":"Lisa"} -> account token of that player; {"role":"operator"} -> fixed role
      if (typeof b.account === "string") {
        const pl = playerByName(b.account);
        if (!pl) return json(res, 404, { error: "unknown player" });
        const token = newToken(undefined, String(b.name ?? `${pl.player}'s browser`), true, { uuid: pl.uuid, type: b.type ?? "desktop" });
        return json(res, 200, { token, account: pl.player });
      }
      const fixedRole = ["viewer", "operator", "admin", "owner"].includes(b.role) ? (b.role as Role) : undefined;
      const token = newToken(mf.id, String(b.name ?? "Dev device"), fixedRole ? rank(fixedRole) >= 2 : b.canControl !== false, fixedRole ? { fixedRole } : {});
      return json(res, 200, { token, mainframeId: mf.id, name: mf.name });
    }
    if (p === "/dev/control" && req.method === "POST") {
      const b = ((await body(req).catch(() => null)) ?? {}) as Record<string, any>;
      const mf = b.mainframeId ? mainframes.get(b.mainframeId) : base;
      if (!mf) return json(res, 404, { error: "unknown mainframe" });
      if (typeof b.online === "boolean" || typeof b.enabled === "boolean")
        setState(mf, { online: b.online ?? mf.online, enabled: b.enabled ?? mf.enabled });
      if (typeof b.ignoreActions === "boolean") mf.ignoreActions = b.ignoreActions;
      if (typeof b.echoResultValue === "boolean") mf.echoResultValue = b.echoResultValue;
      if (b.uplinkOnline && mf.uplinks.has(b.uplinkOnline.uplinkId))
        putUplink(mf, { ...mf.uplinks.get(b.uplinkOnline.uplinkId)!, online: !!b.uplinkOnline.online });
      if (b.value) setValue(mf, b.value.uplinkId, b.value.id, b.value.value);
      if (b.alert) alert(mf, b.alert.uplinkId, String(b.alert.text), b.alert.level ?? "info");
      if (typeof b.approve === "string") decide(b.approve, true);
      if (typeof b.deny === "string") decide(b.deny, false);
      if (b.power && typeof b.power === "object" && mf.power) {
        if (typeof b.power.stored === "number") mf.power.stored = Math.max(0, Math.min(mf.power.capacity, b.power.stored));
        if (typeof b.power.input === "number") mf.power.input = Math.max(0, b.power.input);
        if (b.power.tick) for (let i = 0; i < Number(b.power.tick); i++) tickPower(mf);
      }
      if (b.relay && typeof b.relay === "object" && mf.net) {
        const n = mf.net.nodes.find((x) => x.id === b.relay.id);
        if (n) {
          if (typeof b.relay.powered === "boolean") n.powered = b.relay.powered;
          if (typeof b.relay.loaded === "boolean") n.loaded = b.relay.loaded;
          broadcastNetwork(mf);
        }
      }
      if (b.revokePlayer) {
        // the approving player lost access (untrusted / banned / de-opped): devices die immediately
        for (const [h, t] of tokens) if (t.mainframeId === mf.id) tokens.delete(h);
        mf.clients.forEach((c) => {
          sendTo(c.ws, { t: "error", error: "access_revoked" });
          c.ws.close(4003, "access_revoked");
        });
      }
      if (b.revokeAll) {
        for (const [h, t] of tokens) if (t.mainframeId === mf.id) tokens.delete(h);
        mf.clients.forEach((c) => {
          sendTo(c.ws, { t: "error", error: "revoked" });
          c.ws.close(4001, "revoked");
        });
      }
      if (b.dropClients) mf.clients.forEach((c) => c.ws.terminate());
      return json(res, 200, { ok: true });
    }
    if (opts.staticDir && (req.method === "GET" || req.method === "HEAD")) return serveStatic(opts.staticDir, p, req, res);
    json(res, 404, { error: "not_found" });
  }

  const server = createServer((req, res) =>
    handle(req, res).catch((e) => {
      log(`http error ${(e as Error).message}`);
      if (!res.headersSent) json(res, 500, { error: "internal" });
    }),
  );
  // 64 KB frames so a layout_set fits; every other message is still limited to 8 KB below
  const wss = new WebSocketServer({ noServer: true, maxPayload: MAX_LAYOUT_BYTES + 1024 });
  server.on("upgrade", (req, socket, head) => {
    socket.on("error", () => {});
    const url = new URL(req.url ?? "/", "http://mock");
    const deny = (status: string) => socket.end(`HTTP/1.1 ${status}\r\nConnection: close\r\n\r\n`);
    if (url.pathname !== "/ws/client") return void deny("404 Not Found");
    if (lanBlocked(req)) return void deny("403 Forbidden");
    // Cross-site WebSocket hijacking protection: Origin must match Host.
    const origin = req.headers.origin;
    if (origin) {
      let originHost = "";
      try {
        originHost = new URL(origin).host;
      } catch {}
      const allowed = [req.headers.host, req.headers["x-forwarded-host"]].filter(Boolean);
      if (!allowed.includes(originHost)) return void deny("403 Forbidden");
    }
    const ip = req.socket.remoteAddress ?? "?";
    if (connCount >= maxConns || (connsByIp.get(ip) ?? 0) >= maxConnsPerIp) return void deny("429 Too Many Requests");
    connCount++;
    connsByIp.set(ip, (connsByIp.get(ip) ?? 0) + 1);
    wss.handleUpgrade(req, socket, head, (ws) => handleSocket(ws, ip));
  });

  const pinger = setInterval(() => {
    for (const mf of mainframes.values())
      for (const c of mf.clients) sendTo(c.ws, { t: "ping" }); // idle sockets are closed by handleSocket
  }, pingIntervalMs);

  await new Promise<void>((resolve, reject) => {
    server.once("error", reject);
    server.listen(opts.port ?? 8787, opts.host ?? "0.0.0.0", resolve);
  });
  const port = (server.address() as AddressInfo).port;
  for (const m of mainframes.values()) log(`${m.name}: pairing code ${fmt(currentCode(m))}`);
  log(`listening on http://localhost:${port}`);

  return {
    port,
    mainframes,
    base,
    code: (mf: Mainframe = base) => fmt(currentCode(mf)),
    pairings,
    /** v0.6 share links (tests) */
    shares,
    accountCodes,
    tickPower: (mf: Mainframe = base) => tickPower(mf),
    /** The fake AE2 network (undefined with ae2:false). */
    get ae2() {
      return meSim;
    },
    async close() {
      clearInterval(pinger);
      timers.forEach(clearTimeout);
      if (ticker) clearInterval(ticker);
      wss.clients.forEach((c) => c.terminate());
      await new Promise<void>((r) => server.close(() => r()));
    },
  };
}

export type MockServer = Awaited<ReturnType<typeof createMockServer>>;
