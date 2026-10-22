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
  alive: boolean;
  hits: number[];
  queryHits: number[];
}
interface Token {
  mainframeId: string;
  name: string;
  canControl: boolean;
  pinFails: number;
  lockedUntil: number;
}
interface PendingPairing {
  mainframeId: string;
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
  /** Simulates a Lua script that never answers (for rollback tests). */
  ignoreActions: boolean;
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
  /** Add an uplink in v0.3 auto mode: the automatic Computer card (default true). */
  computer?: boolean;
  /** "sides" = one redstone_sides widget (SPEC), "widgets" = per-side indicator/toggle/slider widgets in groups. */
  computerFormat?: "sides" | "widgets";
  /** Simulated in-game approval: auto-approve after this delay (-1 = only via /dev/control). Names containing "deny" are denied. */
  approveDelayMs?: number;
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
  const newToken = (mainframeId: string, name: string, canControl = true) => {
    const token = randomBytes(32).toString("base64url");
    tokens.set(sha(token), { mainframeId, name, canControl, pinFails: 0, lockedUntil: 0 });
    return token;
  };
  function decide(id: string, approve: boolean) {
    const pp = pairings.get(id);
    if (!pp || pp.status !== "pending") return;
    if (Date.now() - pp.createdAt > pairTtlMs) {
      pp.status = "expired";
      return;
    }
    pp.status = approve ? "approved" : "denied";
    if (approve) pp.token = newToken(pp.mainframeId, pp.name);
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
  const broadcast = (mf: Mainframe, msg: object) => mf.clients.forEach((c) => sendTo(c.ws, msg));
  const info = (mf: Mainframe) => ({ id: mf.id, name: mf.name, online: mf.online, enabled: mf.enabled });
  const snapshot = (mf: Mainframe, c?: Client) => ({
    t: "snapshot",
    mainframe: info(mf),
    canControl: c?.canControl ?? true,
    uplinks: [...mf.uplinks.values()],
    layout: mf.layout,
  });
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
  function setState(mf: Mainframe, patch: { online?: boolean; enabled?: boolean }) {
    Object.assign(mf, patch);
    broadcast(mf, { t: "state", online: mf.online, enabled: mf.enabled });
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
      ignoreActions: false,
    };
    mainframes.set(mf.id, mf);
    return mf;
  }

  const base = addMainframe("Mainframe @ 12 64 -30", true);
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

  const ticker = opts.tickMs === 0 ? undefined : setInterval(() => mainframes.forEach((m) => m.online && m.tick?.()), opts.tickMs ?? 1000);

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
          // first message must be auth
          if (!parsed.success || parsed.data.t !== "auth") return reject("unauthorized");
          const hash = sha(parsed.data.token);
          const t = tokens.get(hash);
          if (!t || !mainframes.has(t.mainframeId)) return reject("unauthorized");
          clearTimeout(authTimer);
          mf = mainframes.get(t.mainframeId)!;
          client = { ws, tokenHash: hash, user: t.name, canControl: t.canControl, alive: true, hits: [], queryHits: [] };
          t.pinFails = t.pinFails ?? 0;
          mf.clients.add(client);
          sendTo(ws, snapshot(mf, client));
          return;
        }
        const c = client;
        const m = mf!;
        c.alive = true;
        if (!parsed.success) {
          if (json && typeof json.reqId === "string") result(json.reqId.slice(0, 64), false, "invalid_message");
          return;
        }
        const msg = parsed.data;
        if (msg.t === "layout_set") {
          const lres = (ok: boolean, error?: string) => sendTo(ws, { t: "layout_result", reqId: msg.reqId, ok, ...(error ? { error } : {}) });
          if (!c.canControl) return lres(false, "read_only");
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
          if (!m.online) return qres([], false, "offline");
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
        if (!m.online) return result(msg.reqId, false, "offline");
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
        result(msg.reqId, true);
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
      const mf = [...mainframes.values()].find((m) => currentCode(m) === code);
      if (!mf) {
        fails.push(now);
        return json(res, 403, { error: "invalid_code" });
      }
      mf.code = newCode();
      mf.codeIssuedAt = Date.now();
      const pairingId = randomUUID();
      pairings.set(pairingId, { mainframeId: mf.id, name: parsed.data.name, status: "pending", createdAt: now });
      log(`"${parsed.data.name}" wants to connect to ${mf.name} from ${ip} – waiting for approval; new code ${fmt(mf.code)}`);
      if (approveDelayMs >= 0) later(approveDelayMs, () => decide(pairingId, !/deny/i.test(parsed.data.name)));
      return json(res, 202, { status: "pending", pairingId, expiresIn: Math.round(pairTtlMs / 1000) });
    }
    const pm = /^\/api\/pair\/([A-Za-z0-9-]{1,64})$/.exec(p);
    if (pm && req.method === "GET") {
      const pp = pairings.get(pm[1]);
      if (!pp) return json(res, 404, { status: "expired" });
      if (pp.status === "pending" && Date.now() - pp.createdAt > pairTtlMs) pp.status = "expired";
      if (pp.status === "approved") {
        pairings.delete(pm[1]); // the token is handed out exactly once
        return json(res, 200, {
          status: "approved",
          token: pp.token,
          mainframeId: pp.mainframeId,
          name: mainframes.get(pp.mainframeId)?.name,
          device: pp.name,
          canControl: true,
        });
      }
      if (pp.status !== "pending") pairings.delete(pm[1]);
      return json(res, pp.status === "denied" ? 403 : pp.status === "expired" ? 404 : 200, { status: pp.status });
    }
    if (p === "/api/mainframes" && req.method === "GET") {
      const t = bearer(req);
      if (!t) return json(res, 401, { error: "unauthorized" });
      return json(res, 200, { mainframes: [{ ...info(mainframes.get(t.mainframeId)!), canControl: t.canControl }] });
    }
    // ---- dev-only helpers (not part of the mod's API) ----
    if (p === "/dev/codes" && req.method === "GET") {
      return json(res, 200, [...mainframes.values()].map((m) => ({ id: m.id, name: m.name, code: fmt(currentCode(m)) })));
    }
    if (p === "/dev/token" && req.method === "POST") {
      // issue a token directly, e.g. a read-only device: {"name":"TV","canControl":false}
      const b = ((await body(req).catch(() => null)) ?? {}) as Record<string, any>;
      const mf = b.mainframeId ? mainframes.get(b.mainframeId) : base;
      if (!mf) return json(res, 404, { error: "unknown mainframe" });
      const token = newToken(mf.id, String(b.name ?? "Dev device"), b.canControl !== false);
      return json(res, 200, { token, mainframeId: mf.id, name: mf.name });
    }
    if (p === "/dev/control" && req.method === "POST") {
      const b = ((await body(req).catch(() => null)) ?? {}) as Record<string, any>;
      const mf = b.mainframeId ? mainframes.get(b.mainframeId) : base;
      if (!mf) return json(res, 404, { error: "unknown mainframe" });
      if (typeof b.online === "boolean" || typeof b.enabled === "boolean")
        setState(mf, { online: b.online ?? mf.online, enabled: b.enabled ?? mf.enabled });
      if (typeof b.ignoreActions === "boolean") mf.ignoreActions = b.ignoreActions;
      if (b.uplinkOnline && mf.uplinks.has(b.uplinkOnline.uplinkId))
        putUplink(mf, { ...mf.uplinks.get(b.uplinkOnline.uplinkId)!, online: !!b.uplinkOnline.online });
      if (b.value) setValue(mf, b.value.uplinkId, b.value.id, b.value.value);
      if (b.alert) alert(mf, b.alert.uplinkId, String(b.alert.text), b.alert.level ?? "info");
      if (typeof b.approve === "string") decide(b.approve, true);
      if (typeof b.deny === "string") decide(b.deny, false);
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
