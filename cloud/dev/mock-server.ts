// DEV-ONLY mock of the mod's embedded web server (docs/SPEC.md "Browser protocol").
// Simulates mainframes with a fission reactor and a storage uplink so the
// dashboard can be developed and tested without Minecraft. Not shipped.
import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import type { AddressInfo } from "node:net";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { createHash, generateKeyPairSync, randomBytes, randomInt, randomUUID, sign as cryptoSign } from "node:crypto";
import { WebSocketServer, WebSocket } from "ws";
import { CODE_ALPHABET, SECURITY_HEADERS, clientMessage, normalizeCode, pairRequest, parseJson, type Scalar } from "./protocol.ts";
import { serveStatic } from "./static.ts";
import { isLanHost } from "../web/src/lib/security.ts";
import { FakeAe2, search } from "./ae2-sim.ts";
import { mockIcon } from "./icons.ts";
import { COMPAT_CARDS } from "./compat-cards.ts";
import { FakeTerminal } from "./terminal-sim.ts";
import { PLAYERS, RANK, TEAMS, DEFAULT_MAPPING, accessDoc, grant, isPerm, presetPerms, resolvePerms, createShare, deviceType, newAccess, playerByName, playerByUuid, resolveRole, sha as sha256, type Access, type Role, type Share } from "./access-sim.ts";
import { cardKeyOf, resolveLayout, normalizeLayout } from "../web/src/lib/layout.ts";
import { computeNetwork, exampleNodes, RANGES, RELAY_FE, type SimNode } from "./network-sim.ts";
import { terrain } from "./terrain-sim.ts";
import { DEEP_BASE, mapTile, surfaceY, voxelSection } from "./world-sim.ts";
import { encodeBatch, sectionHash, type OutSection } from "./voxel-batch.ts";
import { gzipSync } from "node:zlib";
import { encodePng } from "./icons.ts";
/** The Script Store build served at /store/: CCCLOUD_STORE_DIST (e.g. ../cc-cloud-scripts/dist after its build-index), else the test snapshot. */
const STORE_DIST = process.env.CCCLOUD_STORE_DIST || fileURLToPath(new URL("../test/fixtures/store/", import.meta.url));
import { ComputerFs, MAX_FILE, handleFs } from "./computer-fs.ts";
import { MAX_LAYOUT_BYTES, layoutDoc, opValue, ICONS, type OpValue } from "./protocol.ts";
import { EnergyTracker, StorageTracker, dayHistory, rng } from "./production-sim.ts";
import { nextScheduleRun, validAt, validDays, type Day } from "../web/src/lib/schedules.ts";
import { SCOPES as API_SCOPES, validIp } from "../web/src/lib/apikeys.ts";

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
  /** false = cached snapshot of an unloaded chunk */
  loaded?: boolean;
  lastSeen?: number;
  pages?: { id: string; title: string; icon?: string }[];
  groups?: { id: string; title: string; page?: string; cols?: number }[];
  /** v0.5 B mainframe cards / compat cards */
  template?: string;
  /** coordinates inside: only for sessions with view.positions */
  restricted?: boolean;
  /** map overlays of mainframe cards (claims, waystones) */
  claims?: unknown;
  waystones?: unknown;
  /** v0.10 sections the mainframe publishes itself (Scenes, Production) */
  source?: "mainframe";
}
interface Client {
  ws: WebSocket;
  tokenHash: string;
  user: string;
  canControl: boolean;
  /** v0.6 role on this mainframe ("share" for share-link viewers) */
  role: Role | "share";
  /** effective fine-grained permissions (docs/SPEC.md "Fine-grained permissions") */
  perms: string[];
  /** share-link sessions */
  share?: Share;
  uuid?: string;
  alive: boolean;
  hits: number[];
  queryHits: number[];
  storageHits?: number[];
  /** v0.5 E open terminals (uplink ids) */
  terms?: Set<string>;
  termHits?: number[];
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
  /** v0.7 upgrade effects: device limit (null = Quantum Core) and the FE factor of an Efficiency card */
  upgrades?: { devices: number | null; feFactor: number; historyHours?: number };
  /** v0.5 E fake terminals per computer uplink */
  terms?: Map<string, FakeTerminal>;
  /** v0.5 C rules (run state in memory like the mod); v0.10 log entries may be skipped runs */
  rules?: { list: Record<string, any>[]; log: { time: number; rule: string; name: string; result: string; manual: boolean; skipped?: boolean }[]; loadedAt?: number };
  /** v0.10 scenes: stored list + log, run state in memory */
  scenes?: { list: Record<string, any>[]; log: { time: number; scene: string; name: string; by: string; source: string; result: string }[]; running: Set<string>; minuteRuns: number[] };
  /** v0.10 production statistics: trackers per storage card, energy, pins and the synthetic terminal tasks */
  production?: {
    storages: Map<string, StorageTracker>;
    energy: EnergyTracker | null;
    pins: { id: string; kind: "item" | "storage" | "energy"; uplinkId?: string; name?: string; label: string }[];
    since: number;
    countedJobs: Set<string>;
    tasks: { name: string; displayName: string; count: number; progress: number }[];
    hits: WeakMap<object, number[]>;
  };
  /** v0.5 D push targets (url/token kept here, masked in answers) */
  push?: { id: string; type: "discord" | "ntfy" | "webhook"; name: string; url: string; token?: string; minLevel: string; events: string[]; enabled: boolean; lastSent: number | null; lastResult: string | null }[];
  /** v0.7 wireless redstone channels */
  redstone?: Map<string, { signal: number; transmitters: number; receivers: number; override?: { level: number; until: number | null } }>;
  /** v0.3 FE buffer; undefined = requirePower=false */
  power?: { stored: number; capacity: number; input: number; usage: number; ok: boolean; brownout: number; breakdown?: { key: string; count?: number; fe: number; fiber?: number }[] };
  reason?: string;
  /** Simulates a Lua script that never answers (for rollback tests). */
  ignoreActions: boolean;
  /** action_result carries the widget value after the handler (like a mod that echoes set()) */
  echoResultValue?: boolean;
  onAction?: (uplinkId: string, id: string, value: Scalar, user: string) => void;
  /** Structured operations (craft / watchlist). Synchronous so the result can carry a jobId. */
  onOp?: (uplinkId: string, id: string, op: OpValue, user: string) => { ok: boolean; error?: string; jobId?: string };
  onQuery?: (uplinkId: string, id: string, q: string) => { rows: object[]; truncated: boolean } | undefined;
  /** Storage card v2: storage ops and craft previews */
  onStorage?: (uplinkId: string, op: string, p: Record<string, unknown>, user: string) => { ok: boolean; error?: string; data?: object } | undefined;
  onPreview?: (uplinkId: string, name: string, count: number) => { ok: boolean; error?: string; plan?: object } | undefined;
  tick?: () => void;
  /** script import: one file system per uplink that sits on a computer */
  computers?: Map<string, ComputerFs>;
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
  /** dev store signature: valid (default), bad (tampered) or none (no index.json.sig) */
  storeSignature?: "valid" | "bad" | "none";
  /** override the public config's feature switches */
  features?: Partial<Record<"terminal" | "voxels" | "push" | "rules" | "map" | "scenes" | "production" | "api", boolean>>;
  /** v0.10 [scenes] scenesEnabled (default true) */
  scenes?: boolean;
  /** v0.10 [production] productionEnabled (default true) */
  production?: boolean;
  /** v0.10 seconds per production sample (default 20; one sample every that many sim ticks) */
  productionInterval?: number;
  /** v0.10 [api] apiEnabled – the mock defaults to true so the keys page can be tried (the mod: false) */
  apiEnabled?: boolean;
  /** v0.10 [schedules] minIntervalMinutes (1), maxSchedulesPerMainframe (16), timezone ("Europe/Berlin" in the mock) */
  minIntervalMinutes?: number;
  maxSchedules?: number;
  timezone?: string;
  /** v0.5 D config pushEnabled (default true) */
  pushEnabled?: boolean;
  /** serve the Script Store build (STORE_DIST) at /store/ and announce it in the snapshot (default true) */
  scriptStore?: boolean;
  /** add the underground "Deep Base" mainframe (also with extras) */
  deepBase?: boolean;
  /** map tiles per minute per device (default 60) */
  tileRate?: number;
  /** voxel section budget per device: refilled per second (default 256, capacity 8×) */
  voxelRate?: number;
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
export const DEEP_COMPUTER = "0d0c0e00-deep-computer";
export const DEEP_RS = "0d0c0e01-deep-rs";
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
  /** v0.10 API keys: only the SHA-256 of a key is kept (docs/SPEC.md "Public API v1") */
  const apiKeys = new Map<string, { id: string; mainframeId: string; player: string; name: string; scopes: string[]; created: number; expires: number | null; lastUsed: number | null; lastIp: string | null; ips: string[]; hash: string }>();
  const apiOn = opts.apiEnabled !== false;
  // dev Script Store signing key (the mod verifies index.json.sig with [store] scriptStoreKey)
  const storeKey = (() => {
    const { publicKey, privateKey } = generateKeyPairSync("ed25519");
    const spki = publicKey.export({ type: "spki", format: "der" });
    return { publicB64: Buffer.from(spki.subarray(spki.length - 32)).toString("base64"), sign: (b: Buffer) => cryptoSign(null, b, privateKey) };
  })();
  /** Like the mod: a store install is written only if the signed index lists exactly this content. */
  const storeCheck = (ref: string, storeFile: string, content: Buffer): string | null => {
    if (opts.scriptStore === false) return "store_disabled";
    const m = /^([a-z0-9][a-z0-9-]{0,63})@([0-9A-Za-z][0-9A-Za-z.+-]{0,31})$/.exec(ref);
    if (!m) return "invalid_store_ref";
    if (opts.storeSignature === "bad" || opts.storeSignature === "none") return "store_bad_signature";
    let index: { scripts?: { id: string; version: string; files: { path: string; sha256: string }[] }[] };
    try {
      index = JSON.parse(readFileSync(join(STORE_DIST, "index.json"), "utf8"));
    } catch {
      return "store_unreachable";
    }
    const sc = index.scripts?.find((x) => x.id === m[1] && x.version === m[2]);
    const f = sc?.files.find((x) => x.path === storeFile);
    if (!sc || !f) return "store_unknown_script";
    return createHash("sha256").update(content).digest("hex") === f.sha256 ? null : "store_mismatch";
  };
  /** Effective role of a token on a mainframe. */
  function roleFor(t: Token, mf: Mainframe): Role | undefined {
    if (t.kind === "device" && t.mainframeId !== mf.id) return undefined;
    if (t.fixedRole) return t.fixedRole;
    if (t.uuid) return resolveRole(mf.access, t.uuid);
    // dev tokens without a player: canControl decides
    return t.canControl ? "admin" : "viewer";
  }
  const rank = (r: string | undefined) => RANK[r ?? "none"] ?? 0;
  /** Effective permissions of a token; read-only devices are capped at the viewer preset. */
  function permsFor(t: Token, mf: Mainframe): string[] {
    const role = roleFor(t, mf);
    if (!role) return [];
    const all = t.uuid && !t.fixedRole ? resolvePerms(mf.access, t.uuid) : presetPerms(role);
    return t.canControl ? all : all.filter((x) => presetPerms("viewer").includes(x));
  }
  /** WebSocket: null when allowed; else not_allowed, or read_only for sessions without act.buttons. */
  const denied = (c: Client, perm: string) => (c.perms.includes(perm) ? null : c.canControl ? "not_allowed" : "read_only");
  /** HTTP gate: 403 no_access / not_allowed, else the mainframe, role and perms. */
  const gate = (t: Token, mfId: string, perm: string) => {
    const m = mainframes.get(mfId);
    const role = m ? roleFor(t, m) : undefined;
    const perms = m && role ? permsFor(t, m) : [];
    if (!m || !role || !perms.includes("view")) return { error: "no_access" as const };
    if (!perms.includes(perm)) return { error: "not_allowed" as const };
    return { m, role, perms };
  };
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
          perms: c?.perms ?? presetPerms("admin"),
          store: (c?.perms ?? presetPerms("admin")).includes("scripts.upload") && opts.scriptStore !== false ? { url: `http://localhost:${port}/store/`, key: storeKey.publicB64 } : null,
          account: c?.uuid ? { player: playerByUuid(c.uuid)?.player ?? "?" } : null,
          uplinks: [...mf.uplinks.values()].filter((u) => !u.restricted || (c?.perms ?? presetPerms("admin")).includes("view.positions")),
          layout: mf.layout,
          ...(mf.net ? { network: networkPayload(mf, c?.canControl ?? true) } : {}),
          ...(mf.redstone ? { redstone: redstonePayload(mf) } : {}),
          ...(mf.rules ? { rules: rulesPayload(mf) } : {}),
          ...(mf.scenes && scenesOn ? { scenes: scenesPayload(mf) } : {}),
        };

  // ---- v0.4 network map
  /** The network graph in the mod's format (docs/SPEC.md "v0.4 – Implementation notes"). */
  function networkPayload(mf: Mainframe, withPositions: boolean) {
    if (!mf.net) return null;
    const { links, routes, status } = computeNetwork(mf.net.nodes);
    const isNodeT = (t: string) => t === "mainframe" || RELAY_FE[t as keyof typeof RELAY_FE] !== undefined;
    // v0.7: how each device is attached (last hop), capacity in use and who is over it
    const viaOf = (id: string) => {
      const path = routes[id];
      if (!path || path.length < 2) return undefined;
      const prev = path[path.length - 2];
      const l = links.find((x) => x.kind === "wired" && ((x.a === prev && x.b === id) || (x.b === prev && x.a === id)));
      return l ? (l.medium === "fiber" ? "fiber" : "cable") : undefined;
    };
    const routed = mf.net.nodes.filter((n) => !isNodeT(n.type) && routes[n.id] && status[n.id] !== "unloaded");
    const relaysOnRoutes = new Set(routed.flatMap((n) => routes[n.id].slice(1, -1)));
    const fiberDevices = routed.filter((n) => viaOf(n.id) === "fiber").length;
    const used = routed.length + relaysOnRoutes.size - fiberDevices * 0.5;
    const limit = mf.upgrades ? mf.upgrades.devices : 16;
    const overIds = new Set<string>();
    if (limit !== null && used > limit)
      [...routed]
        .sort((a, b) => routes[b.id].length - routes[a.id].length)
        .slice(0, Math.ceil(used - limit))
        .forEach((n) => overIds.add(n.id));
    const capacity = {
      devices: routed.length + relaysOnRoutes.size,
      used,
      limit,
      over: overIds.size,
      wired: routed.filter((n) => viaOf(n.id)).length,
      fiber: fiberDevices,
    };
    const nodes = mf.net.nodes.map((n) => {
      const path = routes[n.id];
      const fe = RELAY_FE[n.type];
      const pos = withPositions ? { pos: { x: Math.floor(n.pos.x), y: Math.floor(n.pos.y), z: Math.floor(n.pos.z) } } : {};
      // unloaded devices: the mod only knows the link entry
      if (!isNodeT(n.type) && status[n.id] === "unloaded")
        return { id: n.id, type: "device", name: "", status: "unloaded", ...pos, dim: n.dim };
      const st = n.type === "mainframe" ? (mf.online ? "online" : (mf.reason ?? "offline")) : overIds.has(n.id) ? "over_capacity" : status[n.id] === "legacy_out_of_range" ? "online" : status[n.id];
      const via = isNodeT(n.type) ? undefined : viaOf(n.id);
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
        ...(n.type === "mainframe" ? { capacity } : {}),
        ...(via ? { via } : {}),
        ...(withPositions && n.target ? { target: n.target } : {}),
      };
    });
    return {
      nodes,
      links: links
        .filter((l) => !l.legacy)
        .map((l) => ({
          a: l.a,
          b: l.b,
          signal: l.signal,
          ...(withPositions && l.dist ? { dist: l.dist } : {}),
          kind: l.kind,
          ...(l.medium ? { medium: l.medium } : {}),
          ...(l.kind === "radio" && l.signal < 0.15 ? { weak: true } : {}),
        })),
      // like the mod: relays' paths include themselves, devices' paths are the nodes before them
      routes: Object.fromEntries(
        Object.entries(routes)
          .filter(([id]) => id !== mf.net!.nodes[0].id)
          .map(([id, path]) => [id, isNodeT(mf.net!.nodes.find((x) => x.id === id)!.type) ? path : path.slice(0, -1)]),
      ),
    };
  }
  /** v0.7 wireless redstone: {channels, limit}; level = override or the transmitters' max */
  function redstonePayload(mf: Mainframe) {
    if (!mf.redstone) return null;
    const now = Date.now();
    for (const [name, c] of mf.redstone) {
      if (c.override?.until && c.override.until <= now) delete c.override;
      if (!c.override && !c.transmitters && !c.receivers) mf.redstone.delete(name);
    }
    return {
      channels: [...mf.redstone.entries()]
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([name, c]) => ({ name, level: c.override ? c.override.level : c.signal, signal: c.signal, transmitters: c.transmitters, receivers: c.receivers, ...(c.override ? { override: c.override } : {}) })),
      limit: 64,
    };
  }
  // ---- v0.10 schedules: time triggers, their limits and the next occurrence
  const minInterval = opts.minIntervalMinutes ?? 1;
  const maxSchedules = opts.maxSchedules ?? 16;
  const timezone = opts.timezone ?? "Europe/Berlin";
  const TIME_TRIGGERS = ["interval", "schedule", "time"];
  const isTime = (r: Record<string, any> | undefined) => TIME_TRIGGERS.includes(r?.when?.type);
  /** interval: last fire (or load) + N minutes, the next step ahead of now; schedule: next day/time in the zone */
  function nextRunOf(mf: Mainframe, r: Record<string, any>): number | null {
    const w = r.when;
    if (w?.type === "schedule") return nextScheduleRun(w.days as Day[], w.at, timezone);
    if (w?.type === "interval") {
      const step = w.minutes * 60_000;
      const from = r.lastFire ?? mf.rules?.loadedAt ?? Date.now();
      const now = Date.now();
      return from + step > now ? from + step : now + step - ((now - from) % step);
    }
    return null;
  }
  /** v0.5 C rules document: rules + run state, last 50 runs, limit from the Memory upgrade; v0.10 schedules */
  function rulesPayload(mf: Mainframe) {
    if (!mf.rules) return null;
    const h = mf.upgrades?.historyHours ?? 1;
    return {
      rules: mf.rules.list.map((r) => ({ ...r, nextRun: nextRunOf(mf, r), lastFire: r.lastFire ?? null })),
      log: mf.rules.log.slice(-50),
      limit: h >= 720 ? 128 : h >= 168 ? 64 : h >= 24 ? 24 : 8,
      schedules: { count: mf.rules.list.filter(isTime).length, limit: maxSchedules, minInterval, timezone },
    };
  }
  function broadcastRules(mf: Mainframe) {
    const r = rulesPayload(mf);
    if (r) for (const c of mf.clients) if (!c.share) sendTo(c.ws, { t: "rules", ...r });
  }
  /** Runs a rule's actions (the mock skips trigger evaluation; "Run now" and tests use this). */
  function runRule(mf: Mainframe, rule: Record<string, any>, manual: boolean) {
    let result = "ok";
    for (const a of rule.then as Record<string, any>[]) {
      if (a.type === "alert") alert(mf, undefined, String(a.text), a.level === "error" ? "crit" : a.level === "warn" ? "warn" : "info");
      else if (a.type === "widget") {
        const u = mf.uplinks.get(a.uplinkId);
        const w = u?.widgets.find((x) => x.id === a.widgetId);
        if (!u || !w) result = result === "ok" ? "unknown_widget" : result;
        else if (w.type !== "button") setValue(mf, a.uplinkId, a.widgetId, a.value);
      } else if (a.type === "dashboard") setState(mf, { enabled: a.enabled === true });
      else if ((a.type === "redstone" || a.type === "craft") && !mf.uplinks.get(a.uplinkId)) result = result === "ok" ? "unknown_uplink" : result;
      else if (a.type === "scene") {
        // v0.10: the scene runs with its owner's live rights, never with a PIN
        const sc = mf.scenes?.list.find((x) => x.id === a.sceneId);
        const perms = sc?.ownerUuid ? resolvePerms(mf.access, sc.ownerUuid) : presetPerms("admin");
        const err = !sc ? "unknown_scene" : !perms.includes("view") ? "not_allowed" : sceneNeedsPin(mf, sc) ? "pin_required" : startScene(mf, sc, { by: sc.owner, device: `rule ${rule.name}`, source: "rule", perms, pinVerified: false });
        if (err && result === "ok") result = err;
      }
    }
    rule.lastRun = Date.now();
    rule.lastResult = result;
    rule.runs = (rule.runs ?? 0) + 1;
    mf.rules!.log.push({ time: Date.now(), rule: rule.id, name: rule.name, result, manual });
    broadcastRules(mf);
    return result;
  }
  /** The strict schema of docs/SPEC.md "C. Rules" (enough of it for the dashboard: first problem as detail). */
  function ruleProblem(r: Record<string, any>): string | null {
    const known = ["id", "name", "enabled", "cooldown", "notify", "when", "if", "then", "lastRun", "lastResult", "runs", "nextRun", "lastFire"];
    const extra = Object.keys(r).find((k) => !known.includes(k));
    if (extra) return `unknown key ${extra}`;
    if (JSON.stringify(r).length > 4096) return "rule too large";
    if (r.id !== undefined && !/^[0-9a-f]{8}$/.test(r.id)) return "id";
    if (typeof r.name !== "string" || !r.name.trim() || r.name.length > 48) return "name";
    if (r.cooldown !== undefined && (!Number.isInteger(r.cooldown) || r.cooldown < 5 || r.cooldown > 86400)) return "cooldown";
    const ops = ["<", "<=", ">", ">=", "==", "!="];
    const check = (c: any, trig: boolean): string | null => {
      if (!c || typeof c !== "object") return "missing";
      if (c.type === "widget") return typeof c.uplinkId === "string" && typeof c.widgetId === "string" && ops.includes(c.op) && typeof c.value === "number" ? null : "widget check";
      if (c.type === "items") return /^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(c.item ?? "") && ops.includes(c.op) && c.value >= 0 ? null : "items check";
      if (c.type === "power") return ops.includes(c.op) && c.value >= 0 && c.value <= 100 ? null : "power check";
      if (trig && c.type === "offline") return typeof c.uplinkId === "string" ? null : "offline";
      if (!trig && c.type === "online") return typeof c.uplinkId === "string" ? null : "online";
      if (trig && c.type === "interval") return Number.isInteger(c.minutes) && c.minutes >= minInterval && c.minutes <= 1440 ? null : `interval: ${minInterval}–1440 minutes`;
      if (trig && c.type === "schedule") return Array.isArray(c.days) && validDays(c.days) && typeof c.at === "string" && validAt(c.at) && Object.keys(c).every((k) => ["type", "days", "at"].includes(k)) ? null : "schedule";
      if (trig && c.type === "time") return Number.isInteger(c.time) && c.time >= 0 && c.time <= 23999 ? null : "time";
      return `unknown type ${c.type}`;
    };
    const w = check(r.when, true);
    if (w) return `when: ${w}`;
    if (r.if !== undefined && (!Array.isArray(r.if) || r.if.length > 8)) return "if";
    for (const c of r.if ?? []) {
      const e = check(c, false);
      if (e) return `if: ${e}`;
    }
    if (!Array.isArray(r.then) || !r.then.length || r.then.length > 10) return "then: 1–10 actions";
    for (const a of r.then) {
      if (a.type === "redstone" && !(["top", "bottom", "left", "right", "front", "back"].includes(a.side) && Number.isInteger(a.value) && a.value >= 0 && a.value <= 15)) return "then: redstone";
      else if (a.type === "alert" && !(typeof a.text === "string" && a.text.trim() && a.text.length <= 120 && ["info", "warn", "error"].includes(a.level))) return "then: alert";
      else if (a.type === "craft" && !(Number.isInteger(a.amount) && a.amount >= 1 && a.amount <= 4096)) return "then: craft amount";
      else if (a.type === "scene" && !(typeof a.sceneId === "string" && /^[0-9a-f]{8}$/.test(a.sceneId))) return "then: scene";
      else if (!["redstone", "widget", "craft", "alert", "dashboard", "scene"].includes(a.type)) return `then: unknown type ${a.type}`;
    }
    if (r.when.type === "widget" && r.then.some((a: any) => a.type === "widget" && a.uplinkId === r.when.uplinkId && a.widgetId === r.when.widgetId)) return "loop: an action writes the trigger's widget";
    return null;
  }

  // ---- v0.10 scenes (docs/SPEC.md "Scenes (mod side)")
  const scenesOn = opts.scenes !== false;
  const SCENE_COLORS = ["cyan", "green", "yellow", "red", "gray"];
  const STEP_TYPES = ["widget", "redstone", "craft", "alert", "dashboard", "delay"];
  /** a stable UUID-looking id from the mainframe id and a name (the mod derives it the same way) */
  const stableId = (mf: Mainframe, what: string) => {
    const h = sha(`${mf.id}:${what}`);
    return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20, 32)}`;
  };
  /** AccessGate.actionPerm: redstone widgets and the auto Computer card's power buttons need act.redstone */
  function actionPerm(u: Uplink | undefined, widgetId: string) {
    const w = u?.widgets.find((x) => x.id === widgetId);
    if (widgetId === "redstone" || widgetId.endsWith("_redstone") || w?.type === "redstone_sides") return "act.redstone";
    if (u?.mode === "auto" && ["power", "turn_on", "reboot", "shutdown"].includes(widgetId)) return "act.redstone";
    return "act.buttons";
  }
  function stepPerm(mf: Mainframe, st: Record<string, any>): string | null {
    if (st.type === "widget") return actionPerm(mf.uplinks.get(st.uplinkId), String(st.widgetId));
    return st.type === "redstone" ? "act.redstone" : st.type === "craft" ? "storage.craft" : st.type === "dashboard" ? "power.toggle" : null;
  }
  const sceneNeedsPin = (mf: Mainframe, sc: Record<string, any>) =>
    sc.pin === true || (sc.steps as Record<string, any>[]).some((st) => st.type === "widget" && (mf.uplinks.get(st.uplinkId)?.widgets.find((w) => w.id === st.widgetId)?.opts as { pin?: boolean } | undefined)?.pin === true);
  /** The strict schema of the scene document (first problem as detail, like the mod's SceneCodec). */
  function sceneProblem(sc: Record<string, any>): string | null {
    const known = ["id", "name", "icon", "color", "confirm", "pin", "steps", "owner", "needsPin", "perms", "runs", "lastRun"];
    const extra = Object.keys(sc).find((k) => !known.includes(k));
    if (extra) return `unknown key ${extra}`;
    if (JSON.stringify(sc).length > 4096) return "scene too large";
    if (sc.id !== undefined && !(typeof sc.id === "string" && /^[0-9a-f]{8}$/.test(sc.id))) return "id";
    if (typeof sc.name !== "string" || !sc.name.trim() || sc.name.length > 32 || /[\u0000-\u001f]/.test(sc.name)) return "name";
    if (sc.icon !== undefined && !(ICONS as readonly string[]).includes(sc.icon)) return "icon";
    if (sc.color !== undefined && !SCENE_COLORS.includes(sc.color)) return "color";
    if (sc.confirm !== undefined && typeof sc.confirm !== "boolean") return "confirm";
    if (sc.pin !== undefined && typeof sc.pin !== "boolean") return "pin";
    if (!Array.isArray(sc.steps) || !sc.steps.length || sc.steps.length > 16) return "steps: 1–16";
    let delay = 0;
    for (let i = 0; i < sc.steps.length; i++) {
      const st = sc.steps[i];
      const n = `step ${i + 1}`;
      if (!st || typeof st !== "object" || !STEP_TYPES.includes(st.type)) return `${n}: unknown type ${st?.type}`;
      if (st.type === "delay") {
        if (!(typeof st.seconds === "number" && st.seconds >= 0.05 && st.seconds <= 300)) return `${n}: delay 0.05–300 s`;
        delay += Math.ceil(st.seconds * 20) / 20;
      } else if (st.type === "widget" && !(typeof st.uplinkId === "string" && typeof st.widgetId === "string" && ["number", "string", "boolean"].includes(typeof st.value) && (typeof st.value !== "string" || st.value.length <= 256))) return `${n}: widget`;
      else if (st.type === "redstone" && !(typeof st.uplinkId === "string" && ["top", "bottom", "left", "right", "front", "back"].includes(st.side) && Number.isInteger(st.value) && st.value >= 0 && st.value <= 15)) return `${n}: redstone`;
      else if (st.type === "craft" && !(typeof st.uplinkId === "string" && /^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(st.item ?? "") && Number.isInteger(st.amount) && st.amount >= 1 && st.amount <= 4096)) return `${n}: craft`;
      else if (st.type === "alert" && !(typeof st.text === "string" && st.text.trim() && st.text.length <= 120 && ["info", "warn", "error"].includes(st.level))) return `${n}: alert`;
      else if (st.type === "dashboard" && typeof st.enabled !== "boolean") return `${n}: dashboard`;
    }
    if (delay > 600) return "delays: at most 600 s in total";
    return null;
  }
  function scenesPayload(mf: Mainframe) {
    if (!mf.scenes || !scenesOn) return null;
    return {
      scenes: mf.scenes.list.map(({ ownerUuid: _o, ...sc }) => ({
        ...sc,
        needsPin: sceneNeedsPin(mf, sc),
        perms: [...new Set((sc.steps as Record<string, any>[]).map((st) => stepPerm(mf, st)).filter(Boolean))],
      })),
      log: mf.scenes.log.slice(-50),
      limit: 32,
      sectionId: mf.scenes.list.length && mf.online ? stableId(mf, "scenes") : null,
    };
  }
  function broadcastScenes(mf: Mainframe) {
    const d = scenesPayload(mf);
    if (d) for (const c of mf.clients) if (!c.share) sendTo(c.ws, { t: "scenes", ...d });
  }
  /** The Scenes section: one button per scene (opts.pin when the scene needs the PIN). */
  function publishScenes(mf: Mainframe) {
    const id = stableId(mf, "scenes");
    if (!scenesOn || !mf.scenes?.list.length) {
      if (mf.uplinks.delete(id)) broadcast(mf, { t: "uplink_removed", uplinkId: id });
      return;
    }
    putUplink(mf, {
      uplinkId: id,
      title: "Scenes",
      online: true,
      mode: "direct",
      template: "scenes",
      source: "mainframe",
      widgets: mf.scenes.list.map((sc) => ({
        id: `s_${sc.id}`,
        type: "button" as const,
        label: sc.name,
        opts: { color: sc.color ?? "gray", ...(sc.icon ? { icon: sc.icon } : {}), ...(sc.confirm ? { confirm: true } : {}), ...(sceneNeedsPin(mf, sc) ? { pin: true } : {}) },
      })),
    });
  }
  /** One step with the given rights (null = ok, else the error code). */
  function runStep(mf: Mainframe, st: Record<string, any>, who: { by: string; perms: string[]; pinVerified: boolean }): string | null {
    const perm = stepPerm(mf, st);
    if (perm && !who.perms.includes(perm)) return "not_allowed";
    switch (st.type) {
      case "widget": {
        const u = mf.uplinks.get(st.uplinkId);
        if (!u) return "unknown_uplink";
        if (u.loaded === false) return "unloaded";
        if (!u.online) return "uplink_offline";
        const w = u.widgets.find((x) => x.id === st.widgetId);
        if (!w) return "unknown_widget";
        if ((w.opts as { pin?: boolean } | undefined)?.pin && !who.pinVerified) return "pin_required";
        const value = coerce(w, st.value);
        if (value === undefined) return "invalid_value";
        if (!mf.ignoreActions) mf.onAction?.(st.uplinkId, st.widgetId, value, who.by);
        return null;
      }
      case "redstone": {
        if (!mf.uplinks.get(st.uplinkId)) return "unknown_uplink";
        const r = mf.onOp?.(st.uplinkId, "redstone", { op: "set_output", side: st.side, value: st.value }, who.by);
        return r?.ok ? null : (r?.error ?? "unavailable");
      }
      case "craft": {
        if (!mf.uplinks.get(st.uplinkId)) return "unknown_uplink";
        const r = mf.onOp?.(st.uplinkId, "search", { op: "craft", name: st.item, count: st.amount }, who.by);
        return r?.ok ? null : (r?.error ?? "unavailable");
      }
      case "alert":
        alert(mf, undefined, String(st.text), st.level === "error" ? "crit" : st.level === "warn" ? "warn" : "info");
        return null;
      case "dashboard":
        setState(mf, { enabled: st.enabled === true });
        return null;
    }
    return null;
  }
  /**
   * Starts a run (null = started, else busy / too_many / rate_limited / offline). Steps up to the next delay
   * run at once, a delay parks the run on a timer; every step result is broadcast.
   */
  function startScene(mf: Mainframe, sc: Record<string, any>, who: { by: string; device: string; source: string; perms: string[]; pinVerified: boolean }): string | null {
    const st = mf.scenes!;
    if (!mf.online) return "offline";
    if (st.running.has(sc.id)) return "busy";
    if (st.running.size >= 4) return "too_many";
    const now = Date.now();
    st.minuteRuns = st.minuteRuns.filter((x) => now - x < 60_000);
    if (st.minuteRuns.length >= 30) return "rate_limited";
    st.minuteRuns.push(now);
    const steps = sc.steps as Record<string, any>[];
    const run = { by: who.by, source: who.source, started: now, finished: null as number | null, steps: steps.map(() => null as { ok: boolean; error?: string } | null) };
    sc.lastRun = run;
    st.running.add(sc.id);
    const finish = () => {
      run.finished = Date.now();
      sc.runs = (sc.runs ?? 0) + 1;
      const bad = run.steps.find((x) => x && !x.ok);
      const result = bad?.error ?? "ok";
      st.running.delete(sc.id);
      st.log.push({ time: run.finished, scene: sc.id, name: sc.name, by: who.by, source: who.source, result });
      log(`[cccloud] ${who.by} (${who.device}) ran scene '${sc.name}' on ${mf.name}: ${result}`);
      broadcastScenes(mf);
    };
    const go = (from: number) => {
      for (let i = from; i < steps.length; i++) {
        if (!mf.online) {
          for (let j = i; j < steps.length; j++) run.steps[j] = { ok: false, error: "offline" };
          break;
        }
        const step = steps[i];
        if (step.type === "delay") {
          broadcastScenes(mf);
          later(Math.ceil(step.seconds * 20) * 50, () => {
            run.steps[i] = { ok: true };
            go(i + 1);
          });
          return;
        }
        const err = runStep(mf, step, who);
        run.steps[i] = err ? { ok: false, error: err } : { ok: true };
      }
      finish();
    };
    broadcastScenes(mf);
    // the action_result goes out first, then the steps run on the next "tick"
    later(actionDelayMs, () => go(0));
    return null;
  }

  // ---- v0.10 production statistics (docs/SPEC.md "Production statistics (mod side)")
  const prodOn = opts.production !== false;
  const prodInterval = opts.productionInterval ?? 20;
  const PROD_WINDOW = 60;
  /** per-minute movements of the demo base on top of the fake AE2 network (smelting, quarry, farms) */
  const DEMO_FLOW: Record<string, { gain: number; use: number; name: string }> = {
    "minecraft:iron_ingot": { gain: 12_000, use: 21_000, name: "Iron Ingot" },
    "minecraft:raw_iron": { gain: 11_000, use: 12_300, name: "Raw Iron" },
    "minecraft:cobblestone": { gain: 60_000, use: 20_000, name: "Cobblestone" },
    "minecraft:redstone": { gain: 9_000, use: 27_000, name: "Redstone Dust" },
    "mysticalagriculture:inferium_essence": { gain: 1_250, use: 80, name: "Inferium Essence" },
    "minecraft:wheat": { gain: 410, use: 120, name: "Wheat" },
    "minecraft:copper_ingot": { gain: 380, use: 60, name: "Copper Ingot" },
    "ae2:certus_quartz_crystal": { gain: 160, use: 210, name: "Certus Quartz Crystal" },
    "minecraft:glass": { gain: 260, use: 40, name: "Glass" },
    "ae2:silicon": { gain: 0, use: 140, name: "Silicon" },
    "minecraft:gold_ingot": { gain: 120, use: 30, name: "Gold Ingot" },
    "create:andesite_alloy": { gain: 90, use: 150, name: "Andesite Alloy" },
  };
  const prodRand = rng(23);
  const pinLabel = (mf: Mainframe, p: { kind: string; uplinkId?: string; name?: string }) =>
    p.kind === "energy" ? "Energy in/out FE/t" : p.kind === "storage" ? `${mf.uplinks.get(p.uplinkId ?? "")?.title ?? "Storage"} net /min` : `${meSim?.items.get(p.name ?? "")?.displayName ?? p.name} /min`;
  /** a pin's chart: net per minute of the item or storage, energy in − out */
  function pinSeries(mf: Mainframe, p: { kind: string; uplinkId?: string; name?: string }) {
    const pr = mf.production!;
    if (p.kind === "energy") return pr.energy ? pr.energy.in.map((v, i) => v - (pr.energy!.out[i] ?? 0)) : [];
    return pr.storages.get(p.uplinkId ?? "")?.netSeries(p.kind === "item" ? p.name : undefined) ?? [];
  }
  /** The Production section: one chart per pin (p_<pinId>), the window as history. */
  function publishProduction(mf: Mainframe) {
    const id = stableId(mf, "production");
    const pr = mf.production;
    if (!prodOn || !pr?.pins.length) {
      if (mf.uplinks.delete(id)) broadcast(mf, { t: "uplink_removed", uplinkId: id });
      return;
    }
    putUplink(mf, {
      uplinkId: id,
      title: "Production",
      online: true,
      mode: "direct",
      template: "production",
      source: "mainframe",
      widgets: pr.pins.map((p) => {
        const history = pinSeries(mf, p);
        return { id: `p_${p.id}`, type: "chart" as const, label: p.label, opts: { unit: p.kind === "energy" ? "FE/t" : "/min", points: PROD_WINDOW, interval: prodInterval, style: "line" }, value: history.at(-1) ?? 0, history };
      }),
    });
  }
  /** One sample: the base keeps moving items, the trackers diff the storage snapshots, pinned charts get a point. */
  function sampleProduction(mf: Mainframe) {
    const pr = mf.production;
    if (!pr || !prodOn || !mf.online) return;
    const ae2 = meSim;
    if (ae2 && pr.storages.has(ME)) {
      const per = prodInterval / 60;
      for (const [name, f] of Object.entries(DEMO_FLOW)) {
        const it = ae2.items.get(name);
        if (!it) continue;
        // the fake network already eats iron/redstone and adds cobblestone every tick; add the rest of the base
        const live = name === "minecraft:iron_ingot" ? { gain: f.gain, use: 0 } : name === "minecraft:redstone" ? { gain: f.gain, use: 0 } : name === "minecraft:cobblestone" ? { gain: 0, use: f.use } : f;
        const d = Math.round((live.gain * (0.75 + prodRand() * 0.5) - live.use * (0.7 + prodRand() * 0.6)) * per);
        it.count = Math.max(0, it.count + d);
      }
      let crafted = 0;
      for (const j of ae2.jobs)
        if (j.status === "done" && !pr.countedJobs.has(j.jobId)) {
          pr.countedJobs.add(j.jobId);
          crafted += j.count;
        }
      pr.storages.get(ME)!.sample(ae2.items, crafted);
      for (const t of pr.tasks) t.progress = t.progress >= 0.98 ? 0.05 : Math.min(0.98, t.progress + 0.04);
    }
    pr.energy?.sample();
    // a pinned chart gets a point when its ring got one (the first storage sample only records the snapshot)
    for (const p of pr.pins) {
      const ring = pinSeries(mf, p);
      const w = mf.uplinks.get(stableId(mf, "production"))?.widgets.find((x) => x.id === `p_${p.id}`);
      if (w && ring.length && (ring.length > (w.history?.length ?? 0) || ring.length >= PROD_WINDOW)) setValue(mf, stableId(mf, "production"), `p_${p.id}`, ring.at(-1)!);
    }
  }
  /** What a session may see: storages need storage.search (restricted ones view.positions), energy cards visible ones. */
  function productionOverview(mf: Mainframe, c: Client) {
    const pr = mf.production!;
    const visible = (uplinkId: string) => {
      const u = mf.uplinks.get(uplinkId);
      return !!u && (!u.restricted || c.perms.includes("view.positions"));
    };
    return {
      interval: prodInterval,
      window: PROD_WINDOW,
      since: pr.since,
      storages: c.perms.includes("storage.search")
        ? [...pr.storages]
            .filter(([id]) => visible(id))
            .map(([id, tr]) => ({ uplinkId: id, title: mf.uplinks.get(id)?.title ?? "Storage", bridge: "me", online: !!mf.uplinks.get(id)?.online, ...tr.overview() }))
        : [],
      energy: pr.energy?.doc(visible) ?? null,
      pins: pr.pins,
      pinLimit: 16,
    };
  }
  function productionItem(mf: Mainframe, uplinkId: string, name: string) {
    const tr = mf.production!.storages.get(uplinkId);
    const it = uplinkId === ME ? meSim?.items.get(name) : undefined;
    if (!tr) return { error: "unknown_storage" };
    if (!it) return { error: "unknown_item" };
    const watched = meSim!.watch.some((w) => w.name === name) || meSim!.favorites.has(name);
    return {
      data: {
        name,
        displayName: it.displayName,
        count: it.count,
        ...tr.item(name),
        crafted: 0,
        jobs: meSim!.jobRows().filter((j) => j.name === name),
        // like the mod: everything crafting on the network now (terminal jobs included), not only this item
        tasks: [...mf.production!.tasks, ...meSim!.cpus().tasks.map(({ name: n, displayName, count, progress }) => ({ name: n, displayName, count, progress }))].slice(0, 16),
        history: watched ? dayHistory(it.count, name.length * 31 + name.charCodeAt(0)) : null,
      },
    };
  }
  function broadcastRedstone(mf: Mainframe) {
    const r = redstonePayload(mf);
    if (r) for (const c of mf.clients) if (!c.share) sendTo(c.ws, { t: "redstone", ...r });
  }
  function broadcastNetwork(mf: Mainframe) {
    if (!mf.net) return;
    // deltas: full graph, coordinates stripped for read-only devices
    const full = { t: "network", network: networkPayload(mf, true) };
    const ro = { t: "network", network: networkPayload(mf, false) };
    for (const c of mf.clients) if (!c.share) sendTo(c.ws, c.canControl ? full : ro);
  }
  /** v0.4 power formula: base 40 + 4/uplink + 8/advanced uplink + 24/probe + 10/bridge + 2/client + 1 per extra hop. */
  function powerBreakdown(mf: Mainframe) {
    const items: { key: string; count?: number; fe: number }[] = [{ key: "base", fe: 40 }];
    if (mf.net) {
      const { routes, status } = computeNetwork(mf.net.nodes);
      const routed = mf.net.nodes.filter((n) => routes[n.id] && n.type !== "mainframe" && status[n.id] !== "unloaded");
      const count = (types: string[]) => routed.filter((n) => types.includes(n.type)).length;
      const up = count(["uplink"]);
      const adv = count(["advanced_uplink"]);
      const probes = count(["probe"]);
      const br = count(["bridge"]);
      const hops = routed.filter((n) => !RELAY_FE[n.type]).reduce((s, n) => s + Math.max(0, routes[n.id].length - 2), 0);
      // fiber-attached devices pay half (rounded up per category)
      const fib = (types: string[]) => routed.filter((n) => types.includes(n.type) && n.wired && n.fiber).length;
      const cost = (per: number, total: number, fiber: number) => per * (total - fiber) + Math.ceil((per * fiber) / 2);
      const fu = fib(["uplink"]);
      const fa = fib(["advanced_uplink"]);
      const fb = fib(["bridge"]);
      items.push(
        { key: "uplinks", count: up, fe: cost(4, up, fu), ...(fu ? { fiber: fu } : {}) },
        { key: "advanced_uplinks", count: adv, fe: cost(8, adv, fa), ...(fa ? { fiber: fa } : {}) },
        ...(probes ? [{ key: "probes", count: probes, fe: 24 * probes }] : []),
        { key: "bridges", count: br, fe: cost(10, br, fb), ...(fb ? { fiber: fb } : {}) },
      );
      items.push({ key: "clients", count: mf.clients.size, fe: 2 * mf.clients.size }, { key: "hops", count: hops, fe: hops });
    } else {
      const uplinks = [...mf.uplinks.values()].filter((u) => u.online).length;
      items.push({ key: "uplinks", count: uplinks, fe: 5 * uplinks }, { key: "clients", count: mf.clients.size, fe: 2 * mf.clients.size });
    }
    // Efficiency card: the saved FE as a negative item, so the items still sum to the usage
    if (mf.upgrades && mf.upgrades.feFactor < 1) {
      const sum = items.reduce((s, b) => s + b.fe, 0);
      items.push({ key: "efficiency", fe: -Math.round(sum * (1 - mf.upgrades.feFactor)) });
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
    if (patch.online !== undefined && mf.scenes) broadcastScenes(mf);
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
        { id: "temp", type: "gauge", label: "Core temperature", opts: { min: 0, max: 1200, unit: "K", warn: WARN, crit: CRIT, history: "30d" }, value: 640 },
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
        // its chunk is unloaded: the cached last snapshot (docs/SPEC.md "Cached sections of unloaded uplinks/bridges")
        loaded: false,
        lastSeen: Date.now() - 12 * 60_000,
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
      mode: "direct", // a bridge's ready-made card: no computer behind it
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
      if ((id === "search" || id === "jobs" || id === "watch") && op.op === "craft") {
        const r = ae2.craft(op.name, op.count, (op as { notify?: boolean }).notify === true);
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
    base.onStorage = (uplinkId, op, p) => {
      if (uplinkId !== ME) return undefined;
      const done = (r: { ok: boolean; error?: string }) => {
        if (r.ok) later(actionDelayMs, publish);
        return r.ok ? { ok: true, data: {} } : r;
      };
      switch (op) {
        case "browse":
          return { ok: true, data: ae2.browse(p) };
        case "craftables":
          return { ok: true, data: ae2.craftables(p) };
        case "fluids":
          return { ok: true, data: ae2.fluidRows(p) };
        case "item": {
          const d = ae2.itemDoc(String(p.name ?? ""));
          return d ? { ok: true, data: d } : { ok: false, error: "unknown_item" };
        }
        case "cpus":
          return { ok: true, data: ae2.cpus() };
        case "cancel":
          return done(ae2.cancel(String(p.jobId ?? "")));
        case "favorite":
          return done(ae2.favorite(String(p.name ?? ""), p.on === true));
        case "autostock":
          return done(ae2.autostock(p as never));
      }
      return { ok: false, error: "invalid" };
    };
    base.onPreview = (uplinkId, name, count) => (uplinkId === ME ? ae2.preview(name, count) : undefined);
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
        { id: "load", type: "chart", label: "Grid load", opts: { group: "grid", min: 0, max: 200, unit: "kFE/t", warn: 160, crit: 185, points: 60, history: "7d" }, value: loadHistory.at(-1)!, history: loadHistory },
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
  base.access.direct.set(PLAYERS[1].uuid, grant("admin"));
  base.access.team = { provider: "ftbteams", id: "ftb-builders", mapping: { ...DEFAULT_MAPPING.ftbteams }, overrides: {} };

  if (opts.network !== false)
    base.net = {
      nodes: exampleNodes({ reactor: REACTOR, me: ME, storage: STORAGE, turbine: TURBINE, control: CONTROL, computer: COMPUTER }),
      lastSeen: {},
    };

  // v0.7: an Allthemodium Capacity card (48 devices) and Efficiency card (×0.8); wireless redstone channels
  base.upgrades = { devices: 48, feFactor: 0.8, historyHours: 168 };
  base.redstone = new Map([
    ["alarm", { signal: 0, transmitters: 1, receivers: 3, override: { level: 15, until: null } }],
    ["base_door", { signal: 15, transmitters: 2, receivers: 1 }],
    ["farm.lights", { signal: 9, transmitters: 1, receivers: 4 }],
    ["reactor_scram", { signal: 0, transmitters: 1, receivers: 1 }],
  ]);
  // v0.5 C: two example rules
  base.rules = {
    list: [
      {
        id: "0a1b2c3d",
        name: "Cool the reactor",
        enabled: true,
        cooldown: 30,
        notify: true,
        when: { type: "widget", uplinkId: REACTOR, widgetId: "temp", op: ">", value: 900 },
        if: [{ type: "power", op: ">=", value: 20 }],
        then: [
          { type: "widget", uplinkId: REACTOR, widgetId: "power", value: false },
          { type: "alert", text: "Reactor hot – shut down", level: "warn" },
        ],
        lastRun: Date.now() - 42 * 60_000,
        lastResult: "ok",
        runs: 3,
      },
      {
        id: "5e6f7a8b",
        name: "Night lights",
        enabled: false,
        cooldown: 60,
        notify: false,
        when: { type: "time", time: 13000 },
        if: [],
        then: [{ type: "alert", text: "Lights on", level: "info" }],
        lastRun: null,
        lastResult: null,
        runs: 0,
      },
    ],
    log: [
      { time: Date.now() - 42 * 60_000, rule: "0a1b2c3d", name: "Cool the reactor", result: "ok", manual: false },
      { time: Date.now() - 3 * 3600_000, rule: "0a1b2c3d", name: "Cool the reactor", result: "pin_required", manual: false },
    ],
  };

  // v0.10: demo scenes (they use the Control Room and the auto Computer card), schedules and production
  base.rules.loadedAt = Date.now() - 37 * 60_000;
  const demoScenes = opts.controlRoom !== false && opts.computer !== false;
  if (demoScenes) {
    const ago = (m: number) => Date.now() - m * 60_000;
    const enrico = PLAYERS[0];
    base.scenes = {
      list: [
        {
          id: "4e1a9c20",
          name: "Night mode",
          icon: "star",
          color: "cyan",
          confirm: true,
          pin: false,
          steps: [
            { type: "widget", uplinkId: CONTROL, widgetId: "mode", value: "eco" },
            { type: "widget", uplinkId: CONTROL, widgetId: "harvest", value: false },
            { type: "delay", seconds: 2 },
            { type: "redstone", uplinkId: COMPUTER, side: "back", value: 0 },
            { type: "widget", uplinkId: STORAGE, widgetId: "output", value: 20 },
            { type: "alert", text: "Good night – base in eco mode", level: "info" },
          ],
          owner: enrico.player,
          ownerUuid: enrico.uuid,
          runs: 14,
          lastRun: { by: "Lisa", source: "tablet", started: ago(62), finished: ago(62) + 2300, steps: [{ ok: true }, { ok: true }, { ok: true }, { ok: true }, { ok: true }, { ok: true }] },
        },
        {
          id: "7b3f0d51",
          name: "Reactor safe",
          icon: "shield",
          color: "red",
          confirm: true,
          pin: false,
          steps: [
            { type: "widget", uplinkId: REACTOR, widgetId: "power", value: false },
            { type: "widget", uplinkId: REACTOR, widgetId: "vent", value: true },
            { type: "delay", seconds: 5 },
            { type: "widget", uplinkId: REACTOR, widgetId: "vent", value: true },
            { type: "alert", text: "Reactor is safe", level: "warn" },
          ],
          owner: enrico.player,
          ownerUuid: enrico.uuid,
          runs: 3,
          lastRun: { by: "Tom", source: "dashboard", started: ago(185), finished: ago(185) + 5200, steps: [{ ok: true }, { ok: false, error: "not_allowed" }, { ok: true }, { ok: false, error: "not_allowed" }, { ok: true }] },
        },
        {
          id: "c2d84e17",
          name: "Good morning",
          icon: "home",
          color: "green",
          confirm: false,
          pin: false,
          steps: [
            { type: "widget", uplinkId: CONTROL, widgetId: "mode", value: "normal" },
            { type: "widget", uplinkId: CONTROL, widgetId: "harvest", value: true },
            { type: "redstone", uplinkId: COMPUTER, side: "back", value: 15 },
            { type: "alert", text: "Good morning – farms are running", level: "info" },
          ],
          owner: enrico.player,
          ownerUuid: enrico.uuid,
          runs: 22,
          lastRun: { by: enrico.player, source: "rule", started: ago(26 * 60), finished: ago(26 * 60) + 120, steps: [{ ok: true }, { ok: true }, { ok: true }, { ok: true }] },
        },
        {
          id: "e90a6b3c",
          name: "Alarm",
          icon: "warning",
          color: "yellow",
          confirm: false,
          pin: false,
          steps: [
            { type: "widget", uplinkId: REACTOR, widgetId: "scram", value: true },
            { type: "redstone", uplinkId: COMPUTER, side: "front", value: 15 },
            { type: "alert", text: "Emergency stop!", level: "error" },
          ],
          owner: enrico.player,
          ownerUuid: enrico.uuid,
          runs: 0,
          lastRun: null,
        },
      ],
      log: [
        { time: ago(26 * 60) + 120, scene: "c2d84e17", name: "Good morning", by: "Enrico", source: "rule", result: "ok" },
        { time: ago(185) + 5200, scene: "7b3f0d51", name: "Reactor safe", by: "Tom", source: "dashboard", result: "not_allowed" },
        { time: ago(62) + 2300, scene: "4e1a9c20", name: "Night mode", by: "Lisa", source: "tablet", result: "ok" },
      ],
      running: new Set(),
      minuteRuns: [],
    };
    base.rules.list.push(
      {
        id: "9d2c7a14",
        name: "Morning routine",
        enabled: true,
        cooldown: 30,
        notify: false,
        when: { type: "schedule", days: ["mon", "tue", "wed", "thu", "fri"], at: "07:00" },
        if: [],
        then: [{ type: "scene", sceneId: "c2d84e17" }],
        lastRun: null,
        lastResult: null,
        runs: 0,
        lastFire: ago(26 * 60),
      },
      {
        id: "1f6e3b88",
        name: "Coolant check",
        enabled: true,
        cooldown: 30,
        notify: false,
        when: { type: "interval", minutes: 15 },
        if: [{ type: "power", op: ">=", value: 20 }],
        then: [{ type: "alert", text: "Coolant check done", level: "info" }],
        lastRun: ago(9),
        lastResult: "ok",
        runs: 2,
        lastFire: ago(9),
      },
    );
    base.rules.log.push(
      { time: ago(6 * 60), rule: "1f6e3b88", name: "Coolant check", result: "skipped_unloaded", manual: false, skipped: true },
      { time: ago(24), rule: "1f6e3b88", name: "Coolant check", result: "ok", manual: false },
      { time: ago(9), rule: "1f6e3b88", name: "Coolant check", result: "ok", manual: false },
    );
    base.rules.log.sort((a, b) => a.time - b.time);
    publishScenes(base);
  }
  if (prodOn) {
    const storages = new Map<string, StorageTracker>();
    if (meSim) {
      const tr = new StorageTracker(PROD_WINDOW, prodInterval);
      tr.seed(DEMO_FLOW, PROD_WINDOW - 6);
      storages.set(ME, tr);
    }
    const cards = [
      ...(opts.controlRoom !== false ? [{ uplinkId: CONTROL, title: "Induction Matrix", stored: 312e6, capacity: 500e6, in: 52_000, out: 38_000, measured: false }] : []),
      { uplinkId: REACTOR, title: "Fission Reactor", stored: 2.45e6, capacity: 4e6, in: 12_000, out: 4_000, measured: false },
      { uplinkId: STORAGE, title: "Storage cell", stored: 0.86e6, capacity: 2e6, in: 0, out: 0, measured: true },
    ];
    const energy = new EnergyTracker(cards, PROD_WINDOW);
    for (let i = 0; i < PROD_WINDOW - 6; i++) energy.sample(i);
    base.production = {
      storages,
      energy,
      pins: [],
      since: Date.now() - (PROD_WINDOW - 6) * prodInterval * 1000,
      countedJobs: new Set(meSim?.jobs.filter((j) => j.status === "done").map((j) => j.jobId) ?? []),
      tasks: meSim ? [{ name: "minecraft:iron_ingot", displayName: "Iron Ingot", count: 2048, progress: 0.35 }] : [],
      hits: new WeakMap(),
    };
    if (meSim) {
      for (const pin of [
        { id: "a1b2c3d4", kind: "item" as const, uplinkId: ME, name: "minecraft:iron_ingot", label: "" },
        { id: "e5f6a7b8", kind: "energy" as const, label: "" },
      ])
        base.production.pins.push({ ...pin, label: pinLabel(base, pin) });
      publishProduction(base);
    }
  }
  // v0.10: API keys of the demo (the key strings themselves are unknown – only their hashes exist)
  {
    const day = 86_400_000;
    const now = Date.now();
    const demoKey = (k: { name: string; player: string; scopes: string[]; created: number; expires: number | null; lastUsed: number | null; lastIp: string | null; ips: string[] }) => {
      const id = sha(k.name).slice(0, 16);
      apiKeys.set(id, { id, mainframeId: base.id, hash: sha(`demo:${k.name}`), ...k });
    };
    demoKey({ name: "Discord bot", player: "Enrico", scopes: ["read:sections", "run:scenes"], created: now - 9 * day, expires: now + 21 * day, lastUsed: now - 4 * 60_000, lastIp: "203.0.113.24", ips: [] });
    demoKey({ name: "Home Assistant", player: "Enrico", scopes: ["read:sections", "read:stats", "action:widgets"], created: now - 30 * day, expires: null, lastUsed: now - 50_000, lastIp: "192.168.1.20", ips: ["192.168.1.0/24"] });
    demoKey({ name: "Stock sheet", player: "Lisa", scopes: ["read:storage"], created: now - 40 * day, expires: now - 3 * day, lastUsed: now - 5 * day, lastIp: "198.51.100.7", ips: [] });
  }
  // v0.5 D: one Discord target
  base.push = [
    { id: "p1", type: "discord", name: "Base channel", url: "https://discord.com/api/webhooks/1234567890/abcdefghijk7f3a", minLevel: "warn", events: ["alerts", "rules", "power"], enabled: true, lastSent: Date.now() - 42 * 60_000, lastResult: "ok" },
  ];
  // FTB Chunks claims and Waystones mainframe cards (restricted: they carry coordinates)
  if (opts.extras)
    for (const card of COMPAT_CARDS.filter((k) => k.key === "claims" || k.key === "waystones")) {
      const id = `mf-card-${card.key}`;
      base.uplinks.set(id, { uplinkId: id, title: card.title, online: true, mode: "direct", template: card.template, restricted: true, widgets: card.widgets as Widget[], ...(card.extra ?? {}) });
    }

  if (opts.extras) {
    // every compat card the mod can build, with sample values (dev/compat-cards.ts mirrors the Java builders)
    const show = addMainframe("Mod Showcase @ 500 64 500", true);
    for (const card of COMPAT_CARDS) {
      const id = `compat-${card.key}`;
      show.uplinks.set(id, { uplinkId: id, title: card.title, online: true, mode: "direct", template: card.template, ...(card.restricted ? { restricted: true } : {}), widgets: card.widgets as Widget[], ...(card.extra ?? {}) });
    }
    show.onAction = (u, id, v) => setValue(show, u, id, v);
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

  // ---- the "Deep Base": an underground room at y −60 (map tiles, slice view, voxels; dev/world-sim.ts)
  if (opts.extras || opts.deepBase) {
    const deep = addMainframe("Deep Base @ 0 -61 0", true);
    deep.power = { stored: 180_000, capacity: 200_000, input: 200, usage: 0, ok: true, brownout: 0 };
    deep.uplinks.set(DEEP_COMPUTER, {
      uplinkId: DEEP_COMPUTER,
      title: "Computer #0",
      online: true,
      widgets: [
        { id: "power", type: "gauge", label: "Base power", opts: { min: 0, max: 100, unit: "%", warn: 25, crit: 10 }, value: 87 },
        { id: "lights", type: "toggle", label: "Lights", value: true },
        { id: "door", type: "button", label: "Open hatch", opts: { color: "cyan" } },
        { id: "status", type: "label", label: "Status", value: "All systems nominal" },
      ],
    });
    deep.uplinks.set(DEEP_RS, {
      uplinkId: DEEP_RS,
      title: "RS Network",
      online: true,
      mode: "direct",
      widgets: [
        { id: "items", type: "label", label: "Items", value: "412,880" },
        { id: "types", type: "label", label: "Item types", value: "6" },
        { id: "energy", type: "gauge", label: "Energy", opts: { min: 0, max: 100, unit: "%" }, value: 71 },
      ],
    });
    const O = "minecraft:overworld";
    const sy = (x: number, z: number) => surfaceY(x, z) + 1;
    deep.net = {
      nodes: [
        { id: "deep-mf", type: "mainframe", name: "Mainframe", pos: { ...DEEP_BASE.mainframe }, dim: O },
        { id: DEEP_COMPUTER, type: "uplink", name: "Computer #0", pos: { ...DEEP_BASE.uplink }, dim: O, uplinkId: DEEP_COMPUTER, wired: "deep-mf", fiber: true },
        { id: DEEP_RS, type: "bridge", name: "RS Network", pos: { ...DEEP_BASE.rs }, dim: O, uplinkId: DEEP_RS, wired: "deep-mf" },
        { id: "deep-relay", type: "advanced_relay", name: "Shaft Relay", pos: { x: 8, y: sy(8, 7), z: 7 }, dim: O, powered: true },
        { id: "deep-farm", type: "advanced_uplink", name: "Hill Farm", pos: { x: 34, y: sy(34, 28), z: 28 }, dim: O },
        { id: "deep-lake", type: "uplink", name: "Lake Pump", pos: { x: 52, y: 63, z: 4 }, dim: O },
      ],
      lastSeen: {},
    };
    deep.onAction = (u, id, v) => setValue(deep, u, id, v);
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
    // expired redstone overrides
    mainframes.forEach((m) => {
      if (m.redstone && [...m.redstone.values()].some((c) => c.override?.until && c.override.until <= Date.now())) broadcastRedstone(m);
    });
    // a solar array: strong for 40 s, weak for 40 s -> the buffer drains and refills
    if (base.power) base.power.input = powerTicks % 80 < 40 ? 120 : 30;
    mainframes.forEach((m) => {
      tickPower(m);
      if (m.online) m.tick?.();
      if (powerTicks % prodInterval === 0) sampleProduction(m);
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
            client = { ws, tokenHash: `share:${sh.id}`, user: `share ${sh.label}`, canControl: false, role: "share", perms: [], share: sh, alive: true, hits: [], queryHits: [] };
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
          const perms = permsFor(t, target);
          if (!role || !perms.includes("view")) return deny("no_access", 4403);
          clearTimeout(authTimer);
          mf = target;
          t.lastSeen = Date.now();
          client = { ws, tokenHash: hash, user: t.name, canControl: perms.includes("act.buttons"), role, perms, uuid: t.uuid, alive: true, hits: [], queryHits: [] };
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
          const type =
            t === "action" ? "action_result" : t === "query" ? "query_result" : t === "layout_set" ? "layout_result" : t === "net_unlink" ? "net_result" : t === "rs_device" || t === "rs_override" ? "rs_result" : t === "storage" ? "storage_result" : t === "craft_preview" ? "craft_plan" : t === "scene_set" || t === "scene_delete" ? "scene_result" : t === "production" ? "production_result" : t === "rule_set" || t === "rule_delete" || t === "rule_enable" || t === "rule_run" ? "rule_result" : null;
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
          const nd = denied(c, "network.link");
          if (nd) return nres(false, nd);
          const node = m.net?.nodes.find((n) => n.id === msg.nodeId);
          if (!node) return nres(false, "unknown_node");
          if (node.type === "mainframe") return nres(false, "not_allowed");
          m.net!.nodes = m.net!.nodes.filter((n) => n !== node);
          log(`${c.user} unlinked ${node.name} from the network`);
          nres(true);
          broadcastNetwork(m);
          return;
        }
        if (msg.t === "term_open" || msg.t === "term_input" || msg.t === "term_close") {
          const id = msg.uplinkId;
          const tres = (reqId: string | undefined, ok: boolean, extra: Record<string, unknown> = {}) => reqId && sendTo(ws, { t: "term_result", reqId, ok, ...extra });
          if (msg.t === "term_close") {
            c.terms?.delete(id);
            return;
          }
          if (msg.t === "term_open") {
            const td = denied(c, "terminal");
            if (td) return tres(msg.reqId, false, { error: td });
            if (pin) {
              if (!msg.pin) return tres(msg.reqId, false, { error: "pin_required" });
              if (msg.pin !== pin) return tres(msg.reqId, false, { error: "wrong_pin", attemptsLeft: 4 });
            }
            const fsys = computerFor(m, id);
            if (!fsys) return tres(msg.reqId, false, { error: "unknown_computer" });
            c.terms ??= new Set();
            if (!c.terms.has(id) && c.terms.size >= 2) return tres(msg.reqId, false, { error: "too_many" });
            m.terms ??= new Map();
            let term = m.terms.get(id);
            if (!term) {
              term = new FakeTerminal(fsys.info.label, fsys.info.advanced, () => [...fsys.files.keys()].filter((p) => !p.includes("/")));
              m.terms.set(id, term);
            }
            c.terms.add(id);
            log(`${c.user} opened the terminal of ${fsys.info.label}`);
            tres(msg.reqId, true);
            sendTo(ws, term.frame(id, true));
            return;
          }
          // term_input
          if (!c.terms?.has(id)) return tres(msg.reqId, false, { error: "not_open" });
          const now = Date.now();
          c.termHits = [...(c.termHits ?? []).filter((h) => now - h < 1000), now];
          if (c.termHits.length > 20) return tres(msg.reqId, false, { error: "rate_limited" });
          const term = m.terms!.get(id)!;
          const ev = msg.events as Record<string, any>[];
          const valid = ev.every(
            (e) =>
              (e.type === "char" && typeof e.c === "string" && e.c.length === 1) ||
              (e.type === "key" && Number.isInteger(e.key) && e.key >= 1 && e.key <= 348) ||
              (e.type === "key_up" && Number.isInteger(e.key)) ||
              (e.type === "paste" && typeof e.text === "string" && e.text.length <= 512) ||
              ["terminate", "turn_on", "reboot", "shutdown"].includes(e.type) ||
              (/^mouse_(click|up|drag|scroll)$/.test(e.type) && Number.isInteger(e.x) && Number.isInteger(e.y)),
          );
          if (!valid) return tres(msg.reqId, false, { error: "invalid_input" });
          const powerWasOn = term.on;
          if (!term.on && ev.some((e) => e.type !== "turn_on")) return tres(msg.reqId, false, { error: "computer_off" });
          for (const e of ev) term.input(e as { type: string });
          const full = powerWasOn !== term.on || ev.some((e) => e.type === "reboot" || e.type === "turn_on");
          const frame = term.frame(id, full);
          for (const x of m.clients) if (x.terms?.has(id)) sendTo(x.ws, frame);
          tres(msg.reqId, true);
          return;
        }
        if (msg.t === "rule_set" || msg.t === "rule_delete" || msg.t === "rule_enable" || msg.t === "rule_run") {
          const rr = (ok: boolean, extra: Record<string, unknown> = {}) => sendTo(ws, { t: "rule_result", reqId: msg.reqId, ok, ...extra });
          const rd = denied(c, msg.t === "rule_set" || msg.t === "rule_delete" ? "rules.edit" : "rules.toggle");
          if (rd) return rr(false, { error: rd });
          if (!m.rules) m.rules = { list: [], log: [] };
          const list = m.rules.list;
          if (msg.t === "rule_set") {
            const r = msg.rule as Record<string, any>;
            const problem = ruleProblem(r);
            if (problem) return rr(false, { error: "invalid_rule", detail: problem });
            if ((r.then as any[]).some((a) => a.type === "widget" && (m.uplinks.get(a.uplinkId)?.widgets.find((w) => w.id === a.widgetId)?.opts as { pin?: boolean } | undefined)?.pin)) return rr(false, { error: "pin_widget" });
            // v0.10: a scene that needs the PIN cannot be run by a rule
            if ((r.then as any[]).some((a) => a.type === "scene" && ((sc) => sc && sceneNeedsPin(m, sc))(m.scenes?.list.find((x) => x.id === a.sceneId)))) return rr(false, { error: "pin_widget" });
            const i = r.id ? list.findIndex((x) => x.id === r.id) : -1;
            if (i < 0 && list.length >= rulesPayload(m)!.limit) return rr(false, { error: "too_many" });
            if (isTime(r) && !(i >= 0 && isTime(list[i])) && list.filter(isTime).length >= maxSchedules) return rr(false, { error: "too_many_schedules" });
            const id = r.id && i >= 0 ? r.id : Math.floor(Math.random() * 2 ** 32).toString(16).padStart(8, "0");
            const prev = i >= 0 ? list[i] : { lastRun: null, lastResult: null, runs: 0, lastFire: null };
            const rule = { id, name: r.name.trim(), enabled: r.enabled !== false, cooldown: r.cooldown ?? 30, notify: r.notify === true, when: r.when, if: r.if ?? [], then: r.then, lastRun: prev.lastRun, lastResult: prev.lastResult, runs: prev.runs, lastFire: prev.lastFire ?? null };
            if (i >= 0) list[i] = rule;
            else list.push(rule);
            log(`${c.user} saved rule ${rule.name}`);
            rr(true, { id });
            broadcastRules(m);
            return;
          }
          const rule = list.find((x) => x.id === msg.id);
          if (!rule) return rr(false, { error: "unknown_rule" });
          if (msg.t === "rule_delete") m.rules.list = list.filter((x) => x !== rule);
          else if (msg.t === "rule_enable") rule.enabled = msg.enabled;
          else {
            if (!m.online) return rr(false, { error: "offline" });
            const res = runRule(m, rule, true);
            return res === "ok" ? rr(true) : rr(false, { error: res });
          }
          rr(true);
          broadcastRules(m);
          return;
        }
        if (msg.t === "scene_set" || msg.t === "scene_delete") {
          const sr = (ok: boolean, extra: Record<string, unknown> = {}) => sendTo(ws, { t: "scene_result", reqId: msg.reqId, ok, ...extra });
          const sd = denied(c, "rules.edit");
          if (sd) return sr(false, { error: sd });
          if (!scenesOn) return sr(false, { error: "disabled" });
          if (!m.scenes) m.scenes = { list: [], log: [], running: new Set(), minuteRuns: [] };
          const list = m.scenes.list;
          if (msg.t === "scene_delete") {
            const i = list.findIndex((x) => x.id === msg.id);
            if (i < 0) return sr(false, { error: "unknown_scene" });
            const [gone] = list.splice(i, 1);
            log(`${c.user} deleted scene ${gone.name} (audit)`);
            sr(true);
            publishScenes(m);
            broadcastScenes(m);
            return;
          }
          const sc = msg.scene as Record<string, any>;
          const problem = sceneProblem(sc);
          if (problem) return sr(false, { error: "invalid_scene", detail: problem });
          // saving needs every permission the steps need: no escalation through a scene
          const lacking = (sc.steps as Record<string, any>[]).map((st, i) => ({ i, perm: stepPerm(m, st) })).find((x) => x.perm && !c.perms.includes(x.perm));
          if (lacking) return sr(false, { error: "not_allowed", detail: `step ${lacking.i + 1}: ${lacking.perm}` });
          const i = sc.id ? list.findIndex((x) => x.id === sc.id) : -1;
          if (i < 0 && list.length >= 32) return sr(false, { error: "too_many" });
          const id = sc.id && i >= 0 ? sc.id : Math.floor(Math.random() * 2 ** 32).toString(16).padStart(8, "0");
          const prev = i >= 0 ? list[i] : { runs: 0, lastRun: null };
          const owner = (c.uuid && playerByUuid(c.uuid)?.player) || c.user;
          const saved = {
            id,
            name: sc.name.trim(),
            ...(sc.icon ? { icon: sc.icon } : {}),
            ...(sc.color ? { color: sc.color } : {}),
            confirm: sc.confirm === true,
            pin: sc.pin === true,
            steps: sc.steps,
            owner,
            ownerUuid: c.uuid,
            runs: prev.runs ?? 0,
            lastRun: prev.lastRun ?? null,
          };
          if (i >= 0) list[i] = saved;
          else list.push(saved);
          log(`${c.user} saved scene ${saved.name} (audit)`);
          sr(true, { id });
          publishScenes(m);
          broadcastScenes(m);
          return;
        }
        if (msg.t === "production") {
          const pres = (ok: boolean, extra: Record<string, unknown> = {}) => sendTo(ws, { t: "production_result", reqId: msg.reqId, ok, ...extra });
          const now = Date.now();
          const pr = m.production;
          if (!prodOn || !pr) return pres(false, { error: "disabled" });
          const hits = (pr.hits.get(c) ?? []).filter((h) => now - h < 1000);
          if (hits.length >= 5) return pres(false, { error: "rate_limited" });
          pr.hits.set(c, [...hits, now]);
          const p = msg as Record<string, any>;
          if (p.op === "overview") return pres(true, { data: productionOverview(m, c) });
          if (p.op === "item") {
            const pd = denied(c, "storage.search");
            if (pd) return pres(false, { error: pd });
            const r = productionItem(m, String(p.uplinkId ?? ""), String(p.name ?? ""));
            return "error" in r ? pres(false, { error: r.error }) : pres(true, { data: r.data });
          }
          const ld = denied(c, "layout.edit");
          if (ld) return pres(false, { error: ld });
          if (p.op === "unpin") {
            const i = pr.pins.findIndex((x) => x.id === p.id);
            if (i < 0) return pres(false, { error: "unknown_pin" });
            pr.pins.splice(i, 1);
            log(`${c.user} unpinned a production chart (audit)`);
            publishProduction(m);
            return pres(true, { data: {} });
          }
          const kind = p.kind;
          if (!["item", "storage", "energy"].includes(kind)) return pres(false, { error: "invalid" });
          if (kind !== "energy") {
            const sd = denied(c, "storage.search");
            if (sd) return pres(false, { error: sd });
            if (!pr.storages.has(String(p.uplinkId ?? ""))) return pres(false, { error: "unknown_storage" });
            if (kind === "item" && !meSim?.items.has(String(p.name ?? ""))) return pres(false, { error: "unknown_item" });
          }
          const same = pr.pins.find((x) => x.kind === kind && (kind === "energy" || x.uplinkId === p.uplinkId) && (kind !== "item" || x.name === p.name));
          if (same) return pres(true, { data: { id: same.id } });
          if (pr.pins.length >= 16) return pres(false, { error: "too_many" });
          const pin = { id: randomBytes(4).toString("hex"), kind, ...(kind !== "energy" ? { uplinkId: String(p.uplinkId) } : {}), ...(kind === "item" ? { name: String(p.name) } : {}), label: "" };
          pin.label = pinLabel(m, pin);
          pr.pins.push(pin);
          log(`${c.user} pinned ${pin.label} (audit)`);
          publishProduction(m);
          return pres(true, { data: { id: pin.id } });
        }
        if (msg.t === "rs_device") {
          // the mock world has no Cloud Redstone devices: rights are checked, the device is unknown
          const rd = denied(c, "network.link");
          return sendTo(ws, { t: "rs_result", reqId: msg.reqId, ok: false, error: rd ?? "unknown_node" });
        }
        if (msg.t === "rs_override") {
          const rres = (ok: boolean, error?: string) => sendTo(ws, { t: "rs_result", reqId: msg.reqId, ok, ...(error ? { error } : {}) });
          const rd = denied(c, "act.redstone");
          if (rd) return rres(false, rd);
          if (!m.online) return rres(false, "offline");
          if (!m.redstone) m.redstone = new Map();
          if (!/^[A-Za-z0-9_.-]{1,32}$/.test(msg.channel)) return rres(false, "invalid_channel");
          const lv = msg.level;
          const secs = msg.seconds ?? null;
          if (lv !== null && (!Number.isInteger(lv) || lv < 0 || lv > 15)) return rres(false, "invalid_argument");
          if (secs !== null && (!Number.isInteger(secs) || secs < 1 || secs > 86400)) return rres(false, "invalid_argument");
          let ch = m.redstone.get(msg.channel);
          if (!ch) {
            if (lv === null) return rres(true);
            if (m.redstone.size >= 64) return rres(false, "too_many_channels");
            ch = { signal: 0, transmitters: 0, receivers: 0 };
            m.redstone.set(msg.channel, ch);
          }
          if (lv === null) delete ch.override;
          else ch.override = { level: lv, until: secs ? Date.now() + secs * 1000 : null };
          log(`${c.user} ${lv === null ? "cleared the override of" : `set ${lv} on`} redstone channel ${msg.channel}`);
          rres(true);
          broadcastRedstone(m);
          return;
        }
        if (msg.t === "layout_set") {
          const lres = (ok: boolean, error?: string) => sendTo(ws, { t: "layout_result", reqId: msg.reqId, ok, ...(error ? { error } : {}) });
          const ld = denied(c, "layout.edit");
          if (ld) return lres(false, ld);
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
        if (msg.t === "storage" || msg.t === "craft_preview") {
          const now = Date.now();
          const t = msg.t;
          const reply = (body: object) => sendTo(ws, { t: t === "storage" ? "storage_result" : "craft_plan", reqId: msg.reqId, ...body });
          c.storageHits = [...(c.storageHits ?? []).filter((h: number) => now - h < 1000), now];
          if (c.storageHits.length > 10) return reply({ ok: false, error: "rate_limited" });
          if (!m.online) return reply({ ok: false, error: "offline" });
          const su = m.uplinks.get(msg.uplinkId);
          if (!su) return reply({ ok: false, error: "unavailable" });
          if (su.loaded === false || !su.online) return reply({ ok: false, error: "unavailable" });
          const sop = (msg as { op?: string }).op ?? "";
          const need = t === "craft_preview" || sop === "cancel" ? "storage.craft" : sop === "autostock" ? "storage.autostock" : sop === "favorite" ? "act.buttons" : "storage.search";
          const sd = denied(c, need);
          if (sd) return reply({ ok: false, error: sd });
          if (t === "craft_preview") {
            const r = m.onPreview?.(msg.uplinkId, msg.name, msg.count);
            return reply(r ?? { ok: false, error: "unavailable" });
          }
          const { t: _t, reqId: _r, uplinkId: _u, id: _i, op, ...params } = msg as Record<string, unknown>;
          const r = m.onStorage?.(msg.uplinkId, String(op), params, c.user);
          return reply(r ? { ok: r.ok, ...(r.error ? { error: r.error } : {}), ...(r.ok ? { data: r.data ?? {} } : {}) } : { ok: false, error: "unavailable" });
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
          if (!c.perms.includes("storage.search")) return qres([], false, "not_allowed");
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
        if (u.loaded === false) return result(msg.reqId, false, "unloaded");
        if (!u.online) return result(msg.reqId, false, "uplink_offline");
        const w = u.widgets.find((x) => x.id === msg.id);
        if (!w) return result(msg.reqId, false, "unknown_widget");
        if ((w.id === "redstone" || w.id.endsWith("_redstone") || w.type === "redstone_sides") && !c.perms.includes("act.redstone")) return result(msg.reqId, false, "not_allowed");
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
        if (u.template === "scenes" && u.source === "mainframe") {
          const sc = m.scenes?.list.find((x) => `s_${x.id}` === w.id);
          if (!sc) return result(msg.reqId, false, "unknown_widget");
          const who = (c.uuid && playerByUuid(c.uuid)?.player) || c.user;
          const err = startScene(m, sc, { by: who, device: c.user, source: "dashboard", perms: c.perms, pinVerified: (w.opts as { pin?: boolean } | undefined)?.pin === true });
          return err ? result(msg.reqId, false, err) : result(msg.reqId, true);
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
  const tileCalls = new WeakMap<object, number[]>();
  /** voxel section budget per token: [tokens, last refill ms] (like the mod's TokenBucket) */
  const voxelBudget = new WeakMap<object, [number, number]>();
  const voxelTake = (t: object, n: number) => {
    const rate = opts.voxelRate ?? 256;
    const cap = rate * 8;
    const now = Date.now();
    const b = voxelBudget.get(t) ?? [cap, now];
    b[0] = Math.min(cap, b[0] + ((now - b[1]) / 1000) * rate);
    b[1] = now;
    voxelBudget.set(t, b);
    if (b[0] < n) return Math.max(1, Math.ceil((Math.min(n, cap) - b[0]) / rate));
    b[0] -= n;
    return 0;
  };
  const tileCache = new Map<string, Buffer>();
  const voxelCache = new Map<string, string>();
  const fsWrites = new WeakMap<object, number[]>();
  /** script import: which uplinks sit on a computer (bridges have none) */
  const computerFor = (m: Mainframe, uplinkId: string) => {
    m.computers ??= new Map();
    let fs = m.computers.get(uplinkId);
    if (fs) return fs;
    const u = m.uplinks.get(uplinkId);
    if (!u || uplinkId === ME || !u.online) return undefined;
    const known: Record<string, [number, string, boolean]> = {
      [CONTROL]: [0, "Base", true],
      [REACTOR]: [3, "Reactor", true],
      [STORAGE]: [12, "Storage", true],
      [COMPUTER]: [7, "Computer #7", false],
    };
    const [id, label, advanced] = known[uplinkId] ?? [20 + m.computers.size, u.title || "Computer", true];
    fs = new ComputerFs({ id, label, on: true, advanced });
    if (uplinkId === CONTROL) {
      fs.files.set("control-room.lua", Buffer.from('local ui = require("cloud.ui")\nlocal dash = ui.connect()\ndash:title("Kontrollraum")\ndash:run()\n'));
      fs.files.set("startup.lua", Buffer.from('shell.run("control-room.lua")\n'));
      fs.files.set("lib/util.lua", Buffer.from("return {}\n"));
    }
    if (uplinkId === REACTOR) fs.files.set("reactor.lua", Buffer.from("-- reactor control\n"));
    m.computers.set(uplinkId, fs);
    return fs;
  };
  async function rawBody(req: IncomingMessage, max: number): Promise<Buffer | null> {
    const chunks: Buffer[] = [];
    let n = 0;
    for await (const ch of req) {
      n += (ch as Buffer).length;
      if (n > max) return null;
      chunks.push(ch as Buffer);
    }
    return Buffer.concat(chunks);
  }
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
    // public config (no token): version, store, feature switches
    if (p === "/api/config" && req.method === "GET") {
      const on = opts.scriptStore !== false;
      return json(res, 200, {
        version: "0.9.0-dev",
        store: { enabled: on, url: on ? `http://localhost:${port}/store/` : null, ...(on ? { key: storeKey.publicB64 } : {}) },
        features: { terminal: true, voxels: true, push: opts.pushEnabled !== false, rules: true, map: true, scenes: scenesOn, production: prodOn, api: true, ...(opts.features ?? {}) },
      });
    }
    // dev: the Script Store's static build (STORE_DIST) on the same origin, signed with a dev key
    if (p === "/store/index.json.sig" && req.method === "GET" && opts.scriptStore !== false) {
      if (opts.storeSignature === "none") return json(res, 404, { error: "not_found" });
      const sig = storeKey.sign(readFileSync(join(STORE_DIST, "index.json")));
      if (opts.storeSignature === "bad") sig[0] ^= 1;
      res.writeHead(200, { "content-type": "text/plain; charset=utf-8", "access-control-allow-origin": "*", "cache-control": "no-cache" });
      return void res.end(sig.toString("base64"));
    }
    if (p.startsWith("/store/") && req.method === "GET" && opts.scriptStore !== false) {
      const rel = decodeURIComponent(p.slice("/store/".length));
      if (!rel || rel.split("/").some((x) => x === ".." || x === "")) return json(res, 404, { error: "not_found" });
      try {
        const body = readFileSync(join(STORE_DIST, rel));
        res.writeHead(200, { "content-type": rel.endsWith(".json") ? "application/json" : "text/plain; charset=utf-8", "access-control-allow-origin": "*", "cache-control": "no-cache" });
        return void res.end(body);
      } catch {
        return json(res, 404, { error: "not_found" });
      }
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
    // public, like the mod: item icons (exported ones or a few samples, 404 for everything else)
    const im = /^\/api\/icon\/([a-z0-9_.-]+)\/((?:[a-z0-9_.-]+\/)*[a-z0-9_.-]+)\.png$/.exec(p);
    if (p.startsWith("/api/icon/") && req.method === "GET") {
      const png = im && !im[2].split("/").some((s) => s === "." || s === "..") ? mockIcon(`${im[1]}:${im[2]}`) : undefined;
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
    // ---- script import (admin; PIN for writes; docs/SPEC.md "Script import – HTTP API")
    const fm = /^\/api\/mainframes\/([A-Za-z0-9-]{1,64})\/computers\/([A-Za-z0-9-]{1,64})\/(files(?:\/.*)?|run|move)$/.exec(p);
    if (fm) {
      const t = bearer(req);
      if (!t) return json(res, 401, { error: "unauthorized" });
      const g = gate(t, fm[1], fm[3] === "run" ? "scripts.run" : req.method === "DELETE" ? "scripts.delete" : "scripts.upload");
      if ("error" in g) return json(res, 403, { error: g.error });
      const m = g.m;
      if (!m.online) return json(res, 409, { error: "offline" });
      const fs = computerFor(m, fm[2]);
      if (!fs) return json(res, 404, { error: "unknown_computer" });
      const body = req.method === "PUT" || req.method === "POST" ? await rawBody(req, MAX_FILE) : null;
      if ((req.method === "PUT" || req.method === "POST") && !body) return json(res, 413, { error: "too_large" });
      if (!fsWrites.has(t)) fsWrites.set(t, []);
      const r = handleFs(fs, req.method ?? "GET", fm[3], url.searchParams, req.headers, body, { pin, device: t, pinLockMs, log: (x) => log(`${t.name}: ${x}`), storeCheck }, fsWrites.get(t)!);
      if (r.raw !== undefined) {
        res.writeHead(r.status, { ...SECURITY_HEADERS, "content-type": "text/plain; charset=utf-8", "cache-control": "no-store" });
        return void res.end(r.raw);
      }
      if (r.status === 200 && fm[3] !== "files" && req.method !== "GET") {
        const file = (r.body as { path?: string }).path ?? decodeURIComponent(fm[3].replace(/^files\//, ""));
        // like the mod's chat note to the owner
        alert(m, fm[2], fm[3] === "run" ? `${t.name} started a script on ${fs.info.label}` : `${t.name} ${req.method === "PUT" ? "uploaded" : "deleted"} ${file} on ${fs.info.label}`, "info");
      }
      return json(res, r.status, r.body);
    }
    // ---- v0.5 D push targets (docs/SPEC.md "D. Push – web protocol")
    const pushM = /^\/api\/mainframes\/([A-Za-z0-9-]{1,64})\/push$/.exec(p);
    if (pushM) {
      const t = bearer(req);
      if (!t) return json(res, 401, { error: "unauthorized" });
      const g = gate(t, pushM[1], "push.manage");
      if ("error" in g) return json(res, 403, { error: g.error });
      const m = g.m;
      if (!m.push) m.push = [];
      const mask = (u: string) => {
        try {
          const x = new URL(u);
          return `${x.protocol}//${x.host}/…${u.slice(-4)}`;
        } catch {
          return "…";
        }
      };
      const doc = () => ({
        enabled: opts.pushEnabled !== false,
        targets: m.push!.map(({ url, token, ...rest }) => ({ ...rest, url: mask(url), hasToken: !!token })),
        limit: 5,
        perMinute: 30,
        allowedHosts: [],
      });
      if (req.method === "GET") return json(res, 200, doc());
      if (req.method !== "POST") return json(res, 405, { error: "method" });
      const b = ((await body(req).catch(() => null)) ?? {}) as Record<string, any>;
      const urlError = (type: string, url: unknown) => {
        if (typeof url !== "string" || url.length > 512) return "invalid_url";
        let u: URL;
        try {
          u = new URL(url);
        } catch {
          return "invalid_url";
        }
        if (u.username || u.password || u.hash) return "invalid_url";
        if (u.protocol !== "https:") return "https_only";
        if (/^(localhost|.*\.local|.*\.internal|127\.|10\.|192\.168\.|\d+\.\d+\.\d+\.\d+$)/.test(u.hostname)) return "host_not_allowed";
        if (type === "discord" && !(/^(discord\.com|discordapp\.com|canary\.discord\.com|ptb\.discord\.com)$/.test(u.hostname) && u.pathname.startsWith("/api/webhooks/"))) return "invalid_url";
        return null;
      };
      const pick = (x: Record<string, any>, cur?: (typeof m.push)[number]) => {
        const type = x.type ?? cur?.type;
        if (!["discord", "ntfy", "webhook"].includes(type)) return { error: "invalid" };
        if (cur && x.type && x.type !== cur.type && !x.url) return { error: "invalid" };
        if (x.url !== undefined || !cur) {
          const e = urlError(type, x.url);
          if (e) return { error: e };
        }
        if (x.minLevel !== undefined && !["info", "warn", "error"].includes(x.minLevel)) return { error: "invalid" };
        if (x.events !== undefined && !(Array.isArray(x.events) && x.events.every((e: unknown) => ["alerts", "rules", "approvals", "power"].includes(e as string)))) return { error: "invalid" };
        return {
          value: {
            id: cur?.id ?? `t${Date.now().toString(36)}`,
            type,
            name: typeof x.name === "string" && x.name.trim() ? x.name.trim().slice(0, 48) : (cur?.name ?? type),
            url: x.url ?? cur!.url,
            ...(x.token !== undefined ? (x.token ? { token: String(x.token) } : {}) : cur?.token ? { token: cur.token } : {}),
            minLevel: x.minLevel ?? cur?.minLevel ?? "warn",
            events: x.events ?? cur?.events ?? ["alerts", "rules", "power"],
            enabled: typeof x.enabled === "boolean" ? x.enabled : (cur?.enabled ?? true),
            lastSent: cur?.lastSent ?? null,
            lastResult: cur?.lastResult ?? null,
          },
        };
      };
      if (b.op === "add") {
        if (m.push.length >= 5) return json(res, 400, { error: "too_many" });
        const r = pick(b.target ?? {});
        if ("error" in r) return json(res, 400, { error: r.error });
        m.push.push(r.value as (typeof m.push)[number]);
      } else if (b.op === "update" || b.op === "delete" || b.op === "test") {
        const i = m.push.findIndex((x) => x.id === b.id);
        if (i < 0) return json(res, 404, { error: "unknown_target" });
        if (b.op === "delete") m.push.splice(i, 1);
        else if (b.op === "test") {
          if (opts.pushEnabled === false) return json(res, 409, { error: "disabled" });
          const tg = m.push[i];
          setTimeout(() => {
            tg.lastSent = Date.now();
            tg.lastResult = /404/.test(tg.url) ? "http_404" : "ok";
          }, 300);
        } else {
          const r = pick(b.target ?? {}, m.push[i]);
          if ("error" in r) return json(res, 400, { error: r.error });
          m.push[i] = r.value as (typeof m.push)[number];
        }
      } else return json(res, 400, { error: "invalid" });
      log(`${g.role}: push ${b.op}`);
      return json(res, 200, doc());
    }
    // ---- v0.5 F long-term history (docs/SPEC.md "F. Long-term statistics – implementation notes")
    const hm = /^\/api\/mainframes\/([A-Za-z0-9-]{1,64})\/history\/(1h|24h|7d|30d)\/(json|csv)\/(w|i)\/([A-Za-z0-9-]{1,64})\/([^/]{1,96})$/.exec(p);
    if (hm && req.method === "GET") {
      const t = bearer(req);
      if (!t) return json(res, 401, { error: "unauthorized" });
      const [, mfId, range, fmtH, kind, uplinkId, rawKey] = hm;
      const g = gate(t, mfId, kind === "i" ? "storage.search" : fmtH === "csv" ? "act.buttons" : "view");
      if ("error" in g) return json(res, 403, { error: g.error });
      if (fmtH === "csv" && !g.perms.includes("act.buttons")) return json(res, 403, { error: "not_allowed" });
      const key = decodeURIComponent(rawKey);
      const u = g.m.uplinks.get(uplinkId);
      if (u?.restricted && !g.perms.includes("view.positions")) return json(res, 403, { error: "not_allowed" });
      const w = u?.widgets.find((x) => x.id === key);
      const recorded = kind === "i" ? "24h" : (w?.opts as { history?: string } | undefined)?.history;
      if (!u || !recorded) return json(res, 404, { error: "no_history" });
      const order = ["1h", "24h", "7d", "30d"];
      const retentionHours = g.m.upgrades?.historyHours ?? 1;
      const keepable = order.filter((r) => ({ "1h": 1, "24h": 24, "7d": 168, "30d": 720 })[r]! <= Math.max(1, retentionHours));
      const kept = order[Math.min(order.indexOf(range), order.indexOf(recorded), order.indexOf(keepable.at(-1)!))];
      const step = kept === "30d" ? 1_800_000 : kept === "7d" ? 300_000 : 60_000;
      const n = { "1h": 60, "24h": 1440, "7d": 2016, "30d": 1440 }[kept]!;
      const o = (w?.opts ?? {}) as { min?: number; max?: number };
      const lo = o.min ?? 0;
      const hi = o.max ?? 100;
      const now = Math.floor(Date.now() / step) * step;
      let seed = [...uplinkId + key].reduce((a, c) => (a * 31 + c.charCodeAt(0)) >>> 0, 7);
      const rnd = () => ((seed = (seed * 1103515245 + 12345) >>> 0) / 2 ** 32);
      const points: [number, number | null][] = [];
      for (let i = n - 1; i >= 0; i--) {
        const tt = now - i * step;
        // a day/night wave plus noise; a gap where "the chunk was unloaded"
        const day = Math.sin((tt / 86_400_000) * Math.PI * 2);
        const v = lo + (hi - lo) * (0.55 + 0.25 * day + 0.08 * Math.sin(tt / 900_000) + (rnd() - 0.5) * 0.06);
        const gap = i > n * 0.62 && i < n * 0.66;
        points.push([tt, gap ? null : Math.round(Math.max(lo, Math.min(hi, v)) * 10) / 10]);
      }
      if (fmtH === "csv") {
        res.writeHead(200, { ...SECURITY_HEADERS, "content-type": "text/csv; charset=utf-8", "cache-control": "no-store" });
        return void res.end(["time,value", ...points.map(([tt, v]) => `${new Date(tt).toISOString()},${v ?? ""}`)].join("\n") + "\n");
      }
      return json(res, 200, { key: `${kind}/${uplinkId}/${key}`, range, kept, retentionHours, step, points });
    }
    // ---- voxel batch (docs/SPEC.md "Voxel view – batch API"): many sections in one binary, gzip-compressed answer
    const vb = /^\/api\/mainframes\/([A-Za-z0-9-]{1,64})\/voxels\/([a-z0-9_.-]+:[a-z0-9_.-]+)$/.exec(p);
    if (vb && req.method === "POST") {
      const t = bearer(req);
      if (!t) return json(res, 401, { error: "unauthorized" });
      const g = gate(t, vb[1], "view.positions");
      if ("error" in g) return json(res, 403, { error: g.error });
      const dim = vb[2];
      if (!["minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"].includes(dim)) return json(res, 404, { error: "unknown_dimension" });
      let b: { sections?: unknown } | null;
      try {
        b = (await body(req)) as { sections?: unknown } | null;
      } catch {
        return json(res, 400, { error: "invalid" });
      }
      const list = b && Array.isArray(b.sections) && Object.keys(b).length === 1 ? (b.sections as unknown[]) : null;
      const ok = (e: unknown): e is [number, number, number, string?] =>
        Array.isArray(e) && (e.length === 3 || (e.length === 4 && typeof e[3] === "string" && /^[0-9a-f]{16}$/.test(e[3]))) && e.slice(0, 3).every((v) => Number.isInteger(v));
      if (!list || !list.length || list.length > 64 || !list.every(ok)) return json(res, 400, { error: "invalid" });
      const wait = voxelTake(t, list.length);
      if (wait) return json(res, 429, { error: "rate_limited", retryAfter: wait });
      const nodes = (g.m.net?.nodes ?? []).filter((n) => n.dim === dim);
      const out: OutSection[] = list.map(([cx, cy, cz, have]) => {
        const near = nodes.some((n) => {
          const d = (c: number, q: number) => Math.max(c * 16 - q, 0, q - (c * 16 + 15));
          return Math.hypot(d(cx, n.pos.x), d(cy, n.pos.y), d(cz, n.pos.z)) <= 64;
        });
        if (!near) return { cx, cy, cz, status: "out_of_range" };
        if (cy < -4 || cy > 19) return { cx, cy, cz, status: "empty" };
        const key = `${dim}|${cx}|${cy}|${cz}`;
        let sec = voxelCache.get(key);
        if (!sec) voxelCache.set(key, (sec = JSON.stringify(voxelSection(dim, cx, cy, cz))));
        const v = JSON.parse(sec) as { palette: { id: string; color: number; flags: number }[]; runs: string; exposed: number };
        const data = { palette: v.palette, runs: new Uint8Array(Buffer.from(v.runs, "base64")), exposed: v.exposed };
        return { cx, cy, cz, status: have && have === sectionHash(data) ? "same" : "ok", data };
      });
      const raw = encodeBatch(dim, out);
      const gz = /\bgzip\b/.test(String(req.headers["accept-encoding"] ?? ""));
      res.writeHead(200, { ...SECURITY_HEADERS, "content-type": "application/octet-stream", "cache-control": "no-store", ...(gz ? { "content-encoding": "gzip" } : {}) });
      return void res.end(gz ? gzipSync(raw) : raw);
    }
    // ---- map tiles (surface / slice) and voxel sections (docs/SPEC.md "Map tiles – HTTP API", "Voxel view – HTTP API")
    const mt = /^\/api\/mainframes\/([A-Za-z0-9-]{1,64})\/(map|voxels)\/([a-z0-9_.-]+:[a-z0-9_./-]+?)\/(.+)$/.exec(p);
    if (mt && req.method === "GET") {
      const t = bearer(req);
      if (!t) return json(res, 401, { error: "unauthorized" });
      const g = gate(t, mt[1], "view.positions");
      if ("error" in g) return json(res, 403, { error: g.error });
      const m = g.m;
      const dim = decodeURIComponent(mt[3]);
      if (!["minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"].includes(dim)) return json(res, 404, { error: "unknown_dimension" });
      const nodes = (m.net?.nodes ?? []).filter((n) => n.dim === dim);
      const now = Date.now();
      if (mt[2] === "map") {
        const r = /^(surface|slice\/(-?\d{1,3}))\/(-?\d{1,6})\/(-?\d{1,6})\.png$/.exec(mt[4]);
        if (!r) return json(res, 404, { error: "not_found" });
        const tx = Number(r[3]);
        const tz = Number(r[4]);
        // only tiles within 512 blocks of a node (box to point)
        const near = nodes.some((n) => {
          const dx = Math.max(tx * 128 - n.pos.x, 0, n.pos.x - (tx * 128 + 127));
          const dz = Math.max(tz * 128 - n.pos.z, 0, n.pos.z - (tz * 128 + 127));
          return Math.hypot(dx, dz) <= 512;
        });
        if (!near) return json(res, 403, { error: "out_of_range" });
        const calls = (tileCalls.get(t) ?? []).filter((x) => now - x < 60_000);
        if (calls.length >= (opts.tileRate ?? 60)) return json(res, 429, { error: "rate_limited", retryAfter: Math.ceil((60_000 - (now - calls[0])) / 1000) });
        calls.push(now);
        tileCalls.set(t, calls);
        const layer = r[1] === "surface" ? "surface" : "slice";
        const key = `${dim}|${layer}|${r[2] ?? ""}|${tx}|${tz}`;
        let png = tileCache.get(key);
        if (!png) {
          const rgba = mapTile(dim, layer, tx, tz, Number(r[2] ?? 0));
          let any = false;
          const px: [number, number, number, number][] = [];
          for (let i = 0; i < 128 * 128; i++) {
            px.push([rgba[i * 4], rgba[i * 4 + 1], rgba[i * 4 + 2], rgba[i * 4 + 3]]);
            if (rgba[i * 4 + 3]) any = true;
          }
          png = any ? encodePng(128, 128, px) : Buffer.alloc(0);
          tileCache.set(key, png);
        }
        if (!png.length) {
          res.writeHead(204, { ...SECURITY_HEADERS, "cache-control": "private, max-age=60" });
          return void res.end();
        }
        const etag = `"${createHash("sha1").update(png).digest("hex").slice(0, 16)}"`;
        if (req.headers["if-none-match"] === etag) {
          res.writeHead(304, { ...SECURITY_HEADERS, etag, "cache-control": "private, max-age=60" });
          return void res.end();
        }
        res.writeHead(200, { ...SECURITY_HEADERS, "content-type": "image/png", etag, "cache-control": "private, max-age=60" });
        return void res.end(png);
      }
      const v = /^(-?\d{1,7})\/(-?\d{1,3})\/(-?\d{1,7})$/.exec(mt[4]);
      if (!v) return json(res, 404, { error: "not_found" });
      const [cx, cy, cz] = [Number(v[1]), Number(v[2]), Number(v[3])];
      // within voxelRadius (64) of a node, measured box to point
      const near = nodes.some((n) => {
        const d = (c: number, p: number) => Math.max(c * 16 - p, 0, p - (c * 16 + 15));
        return Math.hypot(d(cx, n.pos.x), d(cy, n.pos.y), d(cz, n.pos.z)) <= 64;
      });
      if (!near) return json(res, 403, { error: "out_of_range" });
      const wait = voxelTake(t, 1);
      if (wait) return json(res, 429, { error: "rate_limited", retryAfter: wait });
      const key = `${dim}|${cx}|${cy}|${cz}`;
      let sec = voxelCache.get(key);
      if (!sec) voxelCache.set(key, (sec = JSON.stringify(voxelSection(dim, cx, cy, cz))));
      res.writeHead(200, { ...SECURITY_HEADERS, "content-type": "application/json", "cache-control": "private, max-age=5" });
      return void res.end(sec);
    }
    // ---- v0.7 coarse terrain for the 3D view (operator+, rate-limited per token)
    const tm = /^\/api\/mainframes\/([A-Za-z0-9-]{1,64})\/terrain$/.exec(p);
    if (tm && req.method === "GET") {
      const t = bearer(req);
      if (!t) return json(res, 401, { error: "unauthorized" });
      const g = gate(t, tm[1], "view.positions");
      if ("error" in g) return json(res, 403, { error: g.error });
      const m = g.m;
      const now = Date.now();
      const last = terrainCalls.get(t) ?? 0;
      if (now - last < terrainRateMs) return json(res, 429, { error: "rate_limited", retryAfter: Math.ceil((terrainRateMs - (now - last)) / 1000) });
      terrainCalls.set(t, now);
      const dim = url.searchParams.get("dim") ?? "minecraft:overworld";
      const doc = m.net ? terrain(m.net.nodes, dim) : { dim, step: 2, patches: [], truncated: false };
      res.writeHead(200, { ...SECURITY_HEADERS, "content-type": "application/json", "cache-control": "private, max-age=60" });
      return void res.end(JSON.stringify(doc));
    }

    // ---- v0.10 API key management (docs/SPEC.md "Public API v1" → "Key management"; api.keys)
    const km = /^\/api\/mainframes\/([A-Za-z0-9-]{1,64})\/apikeys$/.exec(p);
    if (km) {
      const t = bearer(req);
      if (!t) return json(res, 401, { error: "unauthorized" });
      const g = gate(t, km[1], "api.keys");
      if ("error" in g) return json(res, 403, { error: g.error });
      const player = (t.uuid && playerByUuid(t.uuid)?.player) || t.name;
      const moderator = g.perms.includes("access.manage");
      const row = (k: typeof apiKeys extends Map<string, infer V> ? V : never) => {
        const { hash: _h, mainframeId: _m, ...rest } = k;
        return { ...rest, expired: k.expires !== null && k.expires <= Date.now(), own: k.player === player };
      };
      const doc = () => ({
        enabled: apiOn,
        scopes: [...API_SCOPES],
        limit: 5,
        keys: [...apiKeys.values()].filter((k) => k.mainframeId === g.m.id && (k.player === player || moderator)).sort((a, b) => b.created - a.created).map(row),
      });
      if (req.method === "GET") return json(res, 200, doc());
      if (req.method !== "POST") return json(res, 405, { error: "method" });
      const b = ((await body(req).catch(() => null)) ?? {}) as Record<string, any>;
      if (b.op === "revoke") {
        const k = apiKeys.get(String(b.id ?? ""));
        if (!k || k.mainframeId !== g.m.id) return json(res, 404, { error: "not_found" });
        if (k.player !== player && !moderator) return json(res, 403, { error: "not_allowed" });
        apiKeys.delete(k.id);
        log(`${player} revoked API key '${k.name}' of ${k.player} (audit)`);
        return json(res, 200, { ok: true });
      }
      if (b.op !== "create") return json(res, 400, { error: "invalid" });
      if (!apiOn) return json(res, 409, { error: "disabled" });
      const known = ["op", "name", "scopes", "expiresIn", "ips"];
      const name = typeof b.name === "string" ? b.name.trim() : "";
      const scopes = Array.isArray(b.scopes) ? b.scopes : null;
      const ips = b.ips === undefined ? [] : Array.isArray(b.ips) ? b.ips : null;
      if (
        Object.keys(b).some((k) => !known.includes(k)) ||
        !name ||
        name.length > 40 ||
        !scopes ||
        !scopes.length ||
        new Set(scopes).size !== scopes.length ||
        !scopes.every((x: unknown) => (API_SCOPES as readonly unknown[]).includes(x)) ||
        !(b.expiresIn === null || b.expiresIn === undefined || [3600, 86400, 604800, 2592000, 7776000].includes(b.expiresIn)) ||
        !ips ||
        ips.length > 8 ||
        !ips.every((x: unknown) => typeof x === "string" && validIp(x))
      )
        return json(res, 400, { error: "invalid" });
      // a key never reaches past its creator: each scope's base permission is needed to create it
      const basePerm: Record<string, string> = { "read:sections": "view", "read:stats": "view", "read:storage": "storage.search", "action:widgets": "act.buttons", "run:scenes": "act.buttons" };
      if (scopes.some((x: string) => !g.perms.includes(basePerm[x]))) return json(res, 403, { error: "not_allowed" });
      if ([...apiKeys.values()].filter((k) => k.player === player).length >= 5) return json(res, 409, { error: "too_many" });
      const key = `cck_${randomBytes(32).toString("base64url")}`;
      const entry = {
        id: randomBytes(8).toString("hex"),
        mainframeId: g.m.id,
        player,
        name,
        scopes,
        created: Date.now(),
        expires: b.expiresIn ? Date.now() + b.expiresIn * 1000 : null,
        lastUsed: null,
        lastIp: null,
        ips,
        hash: sha(key),
      };
      apiKeys.set(entry.id, entry);
      log(`${player} created API key '${name}' (${scopes.join(", ")}) (audit)`);
      // the only time the key itself leaves the server
      return json(res, 200, { key, ...row(entry) });
    }
    // ---- v0.6 access + shares (admin+)
    const am = /^\/api\/mainframes\/([A-Za-z0-9-]{1,64})\/(access|shares)(?:\/([A-Za-z0-9]{1,32}))?$/.exec(p);
    if (am) {
      const t = bearer(req);
      if (!t) return json(res, 401, { error: "unauthorized" });
      const g = gate(t, am[1], am[2] === "access" ? "access.manage" : "shares.create");
      if ("error" in g) return json(res, 403, { error: g.error });
      const { m, role } = g;
      const you = { player: (t.uuid && playerByUuid(t.uuid)?.player) || t.name, role, perms: g.perms };
      if (am[2] === "access") {
        if (req.method === "GET") return json(res, 200, accessDoc(m.access, you));
        if (req.method !== "POST") return json(res, 405, { error: "method" });
        const b = ((await body(req).catch(() => null)) ?? {}) as Record<string, any>;
        const list = (x: unknown) => (Array.isArray(x) ? x : null);
        const allow = list(b.allow);
        const denyL = list(b.deny);
        if ((allow && !allow.every(isPerm)) || (denyL && !denyL.every(isPerm))) return json(res, 400, { error: "invalid" });
        // escalation rule: nobody allows what they do not have (the owner has everything)
        const mayAllow = (xs: string[] | null) => !xs || role === "owner" || xs.every((x) => g.perms.includes(x));
        if (!mayAllow(allow)) return json(res, 403, { error: "not_allowed" });
        if (b.op === "set_role" && ["viewer", "operator", "admin"].includes(b.role)) {
          const pl = playerByName(String(b.player ?? ""));
          if (!pl) return json(res, 400, { error: "unknown_player" });
          if (pl.uuid === m.access.owner) return json(res, 400, { error: "invalid" });
          if (!mayAllow(presetPerms(b.role))) return json(res, 403, { error: "not_allowed" });
          const cur = m.access.direct.get(pl.uuid);
          m.access.direct.set(pl.uuid, grant(b.role, allow ?? cur?.allow ?? [], denyL ?? cur?.deny ?? []));
        } else if (b.op === "set_perms") {
          const pl = playerByName(String(b.player ?? ""));
          const cur = pl && m.access.direct.get(pl.uuid);
          if (!pl || !cur) return json(res, 400, { error: "invalid" });
          m.access.direct.set(pl.uuid, grant(cur.role, allow ?? [], denyL ?? []));
        } else if (b.op === "remove") {
          const pl = playerByName(String(b.player ?? ""));
          if (!pl) return json(res, 400, { error: "unknown_player" });
          m.access.direct.delete(pl.uuid);
        } else if (b.op === "set_team") {
          if (b.teamId === null) m.access.team = null;
          else {
            const team = TEAMS.find((x) => x.provider === b.provider && x.id === b.teamId);
            if (!team) return json(res, 400, { error: "invalid" });
            m.access.team = { provider: team.provider, id: team.id, mapping: { ...DEFAULT_MAPPING[team.provider] }, overrides: {} };
          }
        } else if (b.op === "set_mapping" && m.access.team && ["viewer", "operator", "admin", "none"].includes(b.role)) {
          const rk = String(b.rank);
          if (!(rk in m.access.team.mapping)) return json(res, 400, { error: "invalid" });
          if (b.role !== "none" && !mayAllow(presetPerms(b.role))) return json(res, 403, { error: "not_allowed" });
          m.access.team.mapping[rk] = b.role;
          if (allow || denyL) {
            const cur = m.access.team.overrides[rk] ?? { allow: [], deny: [] };
            m.access.team.overrides[rk] = { allow: allow ?? cur.allow, deny: denyL ?? cur.deny };
          }
        } else return json(res, 400, { error: "invalid" });
        // role changes apply live
        for (const c of m.clients) {
          if (!c.uuid || c.share) continue;
          const tok = tokens.get(c.tokenHash);
          const nr = resolveRole(m.access, c.uuid);
          const np = tok ? permsFor(tok, m) : resolvePerms(m.access, c.uuid);
          if (!nr || !np.includes("view")) {
            sendTo(c.ws, { t: "error", error: "no_access" });
            c.ws.close(4403, "no_access");
          } else if (nr !== c.role || np.join() !== c.perms.join()) {
            c.role = nr;
            c.perms = np;
            c.canControl = np.includes("act.buttons");
            sendTo(c.ws, { t: "role", role: nr, canControl: c.canControl, perms: np });
          }
        }
        log(`${you.player} changed access: ${JSON.stringify(b)}`);
        return json(res, 200, accessDoc(m.access, { ...you, role: roleFor(t, m)!, perms: permsFor(t, m) }));
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
    /** v0.10: one production sample now (tests run without the tick) */
    sampleProduction: (mf: Mainframe = base) => sampleProduction(mf),
    /** v0.10 API keys (hashes only) */
    apiKeys,
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
