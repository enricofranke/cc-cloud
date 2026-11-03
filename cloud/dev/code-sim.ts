// DEV-ONLY: Cloud Scripts in the mock, one simulation per mainframe. It speaks the dashboard's `code` protocol
// (list, get, check, save, delete, start, stop, restart, autostart, rename, versions, version, revert, logs, unwatch;
// code_status and code_log pushes) over in-memory scripts with versions, limits, autostart, logs and stored values.
// Running scripts execute real Lua in fengari (dev/code-lua.ts, imported on the first check or start, so the live demo
// only downloads it when a visitor runs something); mock-core.ts answers the cloud.* calls from its cards.
import type { LuaDiagnostic, LuaEvent, LuaRun } from "./code-lua.ts";

export interface Limits {
  maxScripts: number;
  maxRunning: number;
  maxSource: number;
  cpuMsPerSecond: number;
  cpuMsPerResume: number;
  versionsKept: number;
  maxTimers: number;
  maxHandlers: number;
  storageBytes: number;
  logLinesPerSecond: number;
  allocBytesPerSecond: number;
  memoryBytes: number;
}
/** The mod's defaults (cloudScripts.* in the server config). */
export const LIMITS: Limits = {
  maxScripts: 16,
  maxRunning: 8,
  maxSource: 32_768,
  cpuMsPerSecond: 50,
  cpuMsPerResume: 200,
  versionsKept: 20,
  maxTimers: 32,
  maxHandlers: 64,
  storageBytes: 16_384,
  logLinesPerSecond: 20,
  // sent for display only: the mock's fengari runner does not meter memory
  allocBytesPerSecond: 32 * 1024 * 1024,
  memoryBytes: 16 * 1024 * 1024,
};
const LOG_KEEP = 200;
const ACTIONS_PER_SECOND = 20;

/** A dashboard session that may talk to the simulation. */
export interface CodeSession {
  player: string;
  /** code.edit right now (a role change takes it away mid-session) */
  canEdit(): boolean;
  send(msg: object): void;
}
/** What the simulation needs from the mock's mainframe. */
export interface CodeWorld {
  /** loaded, powered and no kill switch: scripts can run */
  online(): boolean;
  /** a player's live rights on this mainframe */
  perms(player: string): string[];
  /** every cloud.* function except scheduling, logging and the store; actions may answer with a promise */
  call(name: string, args: unknown[], who: { script: string; player: string; perms: string[] }): unknown[] | Promise<unknown[]>;
  gameTime(): number;
  log(msg: string): void;
}
export interface SeedScript {
  name: string;
  source: string;
  author: string;
  autostart?: boolean;
  /** older versions first (they get older timestamps), the source above is the newest */
  history?: { source: string; author: string; note?: string; agoMs: number }[];
  /** a runtime error the script stopped with */
  lastError?: { message: string; line: number | null; agoMs: number };
  updatedAgoMs?: number;
}

type Status = "running" | "stopped" | "error";
interface Version {
  version: number;
  time: number;
  author: string;
  size: number;
  note: string | null;
  source: string;
}
interface LogLine {
  ts: number;
  level: "info" | "warn" | "error" | "system";
  text: string;
}
interface Script {
  id: string;
  name: string;
  versions: Version[];
  autostart: boolean;
  status: Status;
  reason: string | null;
  startedAt: number | null;
  startedBy: string | null;
  /** the rights of whoever started it: the script never acts with more */
  starterPerms: string[];
  runningVersion: number | null;
  lastError: { message: string; line: number | null; time: number } | null;
  logs: LogLine[];
  store: Map<string, unknown>;
  cpu: { used: number; window: number[] };
  logCount: number;
  logDropped: number;
  actions: number[];
  run?: LuaRun;
  /** set while a start waits for the Lua runtime to load */
  starting?: boolean;
}

const bytes = (s: string) => new TextEncoder().encode(s).length;
const errLine = (msg: string) => {
  const m = /^[^\n]*?:(\d+):/.exec(msg);
  return m ? Number(m[1]) : null;
};
const cleanName = (v: unknown) => {
  if (typeof v !== "string") return null;
  const t = v.trim();
  return t && t.length <= 32 && !/[\u0000-\u001f\u007f]/.test(t) ? t : null;
};
const newId = () => Math.floor(Math.random() * 2 ** 32).toString(16).padStart(8, "0");

let luaModule: Promise<typeof import("./code-lua.ts")> | null = null;
const loadLua = () => (luaModule ??= import("./code-lua.ts"));

export function createCodeSim(world: CodeWorld, limits: Limits = LIMITS) {
  const scripts: Script[] = [];
  const statusWatchers = new Set<CodeSession>();
  const logWatchers = new Map<string, Set<CodeSession>>();
  const pendingLogs = new Map<string, LogLine[]>();
  const timers = new Set<ReturnType<typeof setTimeout>>();
  let closed = false;
  let booted = false;
  let statusTimer: ReturnType<typeof setTimeout> | null = null;
  let logTimer: ReturnType<typeof setTimeout> | null = null;
  let secondTimer: ReturnType<typeof setTimeout> | null = null;

  const later = (ms: number, fn: () => void) => {
    if (closed) return () => {};
    const t = setTimeout(() => {
      timers.delete(t);
      if (!closed) fn();
    }, ms);
    timers.add(t);
    return () => {
      clearTimeout(t);
      timers.delete(t);
    };
  };
  const current = (s: Script) => s.versions[s.versions.length - 1];
  const author = (s: Script) => current(s).author;
  const summary = (s: Script) => {
    const v = current(s);
    const w = s.cpu.window;
    const counts = s.run?.counts() ?? { handlers: 0, timers: 0 };
    return {
      id: s.id,
      name: s.name,
      language: "lua",
      status: s.status,
      reason: s.reason,
      autostart: s.autostart,
      version: v.version,
      size: v.size,
      updated: v.time,
      author: v.author,
      startedAt: s.startedAt,
      startedBy: s.startedBy,
      runningVersion: s.status === "running" ? s.runningVersion : null,
      cpu: { ms: Math.round((w[w.length - 1] ?? 0) * 10) / 10, avg: w.length ? Math.round((w.reduce((a, b) => a + b, 0) / w.length) * 10) / 10 : 0, limit: limits.cpuMsPerSecond },
      handlers: counts.handlers,
      timers: counts.timers,
      lastError: s.lastError,
      logLines: s.logs.length,
    };
  };

  // ------------------------------------------------------------ pushes
  const statusNow = () => {
    statusTimer = null;
    const msg = { t: "code_status", scripts: scripts.map(summary), limits };
    for (const c of [...statusWatchers]) if (c.canEdit()) c.send(msg);
      else statusWatchers.delete(c);
  };
  /** code_status at most twice a second */
  const dirty = () => {
    if (!statusTimer && !closed) statusTimer = setTimeout(statusNow, 500);
  };
  const flushLogs = () => {
    logTimer = null;
    for (const [id, lines] of pendingLogs) {
      const to = logWatchers.get(id);
      if (to) for (const c of [...to]) if (c.canEdit()) c.send({ t: "code_log", id, lines });
    }
    pendingLogs.clear();
  };
  const addLog = (s: Script, level: LogLine["level"], text: string, force = false) => {
    // 20 lines a second; the rest is counted and reported once the second is over
    if (!force && level !== "system") {
      if (s.logCount >= limits.logLinesPerSecond) {
        s.logDropped++;
        return;
      }
      s.logCount++;
    }
    const line = { ts: Date.now(), level, text: text.slice(0, 2000) };
    s.logs.push(line);
    if (s.logs.length > LOG_KEEP) s.logs.splice(0, s.logs.length - LOG_KEEP);
    if (logWatchers.has(s.id)) {
      pendingLogs.set(s.id, [...(pendingLogs.get(s.id) ?? []), line]);
      // code_log at most four times a second
      logTimer ??= setTimeout(flushLogs, 250);
    }
  };
  /** The CPU second: moves the window, reports dropped log lines, keeps the list fresh while something runs. */
  const second = () => {
    secondTimer = null;
    let any = false;
    for (const s of scripts) {
      if (s.status === "running") {
        any = true;
        s.cpu.window.push(s.cpu.used);
        if (s.cpu.window.length > 10) s.cpu.window.shift();
      }
      s.cpu.used = 0;
      s.logCount = 0;
      if (s.logDropped) {
        addLog(s, "system", `${s.logDropped} log lines dropped (more than ${limits.logLinesPerSecond} per second)`);
        s.logDropped = 0;
      }
    }
    if (any) {
      dirty();
      secondTimer = setTimeout(second, 1000);
    }
  };
  const tick = () => {
    if (!secondTimer && !closed) secondTimer = setTimeout(second, 1000);
  };

  // ------------------------------------------------------------ running
  function halt(s: Script, reason: string, line?: string) {
    if (s.status === "running") {
      s.run?.stop();
      s.run = undefined;
      s.status = "stopped";
      s.reason = reason;
      s.runningVersion = null;
      addLog(s, "system", line ?? `stopped (${reason})`);
    }
    dirty();
  }
  /** null = started, else the error code (and its detail). */
  async function start(s: Script, starter: string, starterPerms: string[]): Promise<{ error: string; detail?: string } | null> {
    if (s.status === "running" || s.starting) return { error: "already_running" };
    if (!world.online()) return { error: "offline" };
    if (scripts.filter((x) => x.status === "running" || x.starting).length >= limits.maxRunning) return { error: "quota" };
    s.starting = true;
    const lua = await loadLua();
    s.starting = false;
    if (closed || !scripts.includes(s)) return { error: "unknown_script" };
    const v = current(s);
    const diags = lua.compile(v.source, s.name);
    if (diags.length) return { error: "has_errors", detail: diags[0].message };
    s.status = "running";
    s.reason = null;
    s.startedAt = Date.now();
    s.startedBy = starter;
    s.starterPerms = starterPerms;
    s.runningVersion = v.version;
    s.lastError = null;
    s.cpu = { used: 0, window: [] };
    addLog(s, "system", `started by ${starter} (v${v.version}; mock runtime: fengari, Lua 5.3)`);
    const who = () => {
      // live rights of the author, capped by the starter's
      const mine = world.perms(author(s));
      return { script: s.name, player: author(s), perms: mine.filter((p) => s.starterPerms.includes(p)) };
    };
    s.run = lua.start(v.source, {
      chunk: s.name,
      info: { id: s.id, name: s.name, author: v.author, version: v.version, language: "lua" },
      limits,
      call: (name, args) => {
        const w = who();
        if (!w.perms.includes("code.edit")) {
          later(0, () => halt(s, "author_lost_access", `stopped: ${author(s)} lost access to this mainframe`));
          throw new Error("the script's author lost access");
        }
        if (name.startsWith("store.")) return storeCall(s, name.slice(6), args);
        if (name === "push" && !args[2]) args[2] = s.name;
        const action = ["press", "set", "setRedstone", "craft", "setEnabled", "runScene", "setRuleEnabled", "runRule", "setStyle"].includes(name);
        if (action) {
          const now = Date.now();
          s.actions = s.actions.filter((t) => now - t < 1000);
          if (s.actions.length >= ACTIONS_PER_SECOND) return [false, "rate_limited"];
          s.actions.push(now);
        }
        return world.call(name, args, w);
      },
      log: (level, text) => addLog(s, level, text),
      later,
      cpuLeft: () => limits.cpuMsPerSecond - s.cpu.used,
      cpuUsed: (ms) => {
        s.cpu.used += ms;
      },
      gameTime: () => world.gameTime(),
      ended: (error) => {
        s.run = undefined;
        s.runningVersion = null;
        if (error) {
          s.status = "error";
          s.reason = null;
          s.lastError = { message: error, line: errLine(error), time: Date.now() };
          addLog(s, "error", error, true);
        } else {
          s.status = "stopped";
          s.reason = "finished";
          addLog(s, "system", "finished: nothing left to wait for");
        }
        dirty();
      },
    });
    tick();
    dirty();
    return null;
  }
  function storeCall(s: Script, op: string, args: unknown[]): unknown[] {
    const key = args[0];
    if (op === "keys") return [[...s.store.keys()]];
    if (typeof key !== "string" || !key || key.length > 64) throw new Error("cloud.store: a key is a string of 1 to 64 characters");
    if (op === "get") return [s.store.get(key) ?? null];
    if (op === "delete") return [s.store.delete(key)];
    const next = new Map(s.store);
    if (args[1] === null || args[1] === undefined) next.delete(key);
    else next.set(key, args[1]);
    if (bytes(JSON.stringify([...next])) > limits.storageBytes) throw new Error(`cloud.store: over ${limits.storageBytes} bytes for this script`);
    s.store = next;
    return [true];
  }
  /** The first `code` message of any session starts the autostart scripts (the mod does it when the mainframe loads). */
  function boot() {
    if (booted) return;
    booted = true;
    for (const s of scripts) if (s.autostart && s.status !== "running") void start(s, author(s), world.perms(author(s)));
  }

  // ------------------------------------------------------------ protocol
  async function handle(c: CodeSession, msg: Record<string, any>) {
    const reply = (ok: boolean, extra: Record<string, unknown> = {}) => c.send({ t: "code_result", reqId: msg.reqId, ok, ...extra });
    const no = (error: string, detail?: string) => reply(false, { error, ...(detail ? { detail } : {}) });
    if (!c.canEdit()) return no("not_allowed");
    boot();
    const op = String(msg.op ?? "");
    const s = typeof msg.id === "string" ? scripts.find((x) => x.id === msg.id) : undefined;
    const needs = ["get", "delete", "start", "stop", "restart", "autostart", "rename", "versions", "version", "revert", "logs", "unwatch"];
    if (needs.includes(op) && !s) return no("unknown_script");
    switch (op) {
      case "list":
        statusWatchers.add(c);
        return reply(true, { scripts: scripts.map(summary), limits, enabled: true });
      case "get":
        return reply(true, { script: { ...summary(s!), source: current(s!).source } });
      case "check": {
        if (typeof msg.source !== "string") return no("invalid");
        if (bytes(msg.source) > limits.maxSource) return no("too_large");
        const lua = await loadLua();
        return reply(true, { diagnostics: lua.compile(msg.source, "check") });
      }
      case "save": {
        const name = cleanName(msg.name);
        if (!name || typeof msg.source !== "string") return no("invalid");
        if (bytes(msg.source) > limits.maxSource) return no("too_large");
        const target = typeof msg.id === "string" ? scripts.find((x) => x.id === msg.id) : undefined;
        if (typeof msg.id === "string" && !target) return no("unknown_script");
        if (!target && scripts.length >= limits.maxScripts) return no("too_many");
        if (scripts.some((x) => x !== target && x.name.toLowerCase() === name.toLowerCase())) return no("name_taken");
        if (target && typeof msg.baseVersion === "number" && msg.baseVersion !== current(target).version) return no("changed", String(current(target).version));
        const lua = await loadLua();
        const diagnostics: LuaDiagnostic[] = lua.compile(msg.source, name);
        const sc = target ?? newScript(name);
        sc.name = name;
        pushVersion(sc, msg.source, c.player, null);
        world.log(`${c.player} saved script ${name} v${current(sc).version} (audit)`);
        dirty();
        return reply(true, { id: sc.id, version: current(sc).version, diagnostics });
      }
      case "delete": {
        halt(s!, "deleted");
        scripts.splice(scripts.indexOf(s!), 1);
        logWatchers.delete(s!.id);
        world.log(`${c.player} deleted script ${s!.name} (audit)`);
        dirty();
        return reply(true);
      }
      case "start":
      case "restart": {
        if (op === "restart") halt(s!, "stopped", `restarted by ${c.player}`);
        const err = await start(s!, c.player, world.perms(c.player));
        return err ? no(err.error, err.detail) : reply(true);
      }
      case "stop":
        if (s!.status !== "running") return no("not_running");
        halt(s!, "stopped", `stopped by ${c.player}`);
        return reply(true);
      case "autostart":
        if (typeof msg.enabled !== "boolean") return no("invalid");
        s!.autostart = msg.enabled;
        dirty();
        return reply(true);
      case "rename": {
        const name = cleanName(msg.name);
        if (!name) return no("invalid");
        if (scripts.some((x) => x !== s && x.name.toLowerCase() === name.toLowerCase())) return no("name_taken");
        s!.name = name;
        dirty();
        return reply(true);
      }
      case "versions":
        return reply(true, { versions: [...s!.versions].reverse().map(({ source: _source, ...meta }) => meta) });
      case "version": {
        const v = s!.versions.find((x) => x.version === msg.version);
        return v ? reply(true, { source: v.source, version: v.version, time: v.time, author: v.author }) : no("unknown_version");
      }
      case "revert": {
        const v = s!.versions.find((x) => x.version === msg.version);
        if (!v) return no("unknown_version");
        pushVersion(s!, v.source, c.player, `revert of v${v.version}`);
        dirty();
        return reply(true, { version: current(s!).version });
      }
      case "logs": {
        const set = logWatchers.get(s!.id) ?? new Set();
        set.add(c);
        logWatchers.set(s!.id, set);
        return reply(true, { lines: s!.logs.slice(-LOG_KEEP) });
      }
      case "unwatch":
        logWatchers.get(s!.id)?.delete(c);
        return reply(true);
    }
    return no("invalid");
  }

  function newScript(name: string): Script {
    const sc: Script = {
      id: newId(),
      name,
      versions: [],
      autostart: false,
      status: "stopped",
      reason: null,
      startedAt: null,
      startedBy: null,
      starterPerms: [],
      runningVersion: null,
      lastError: null,
      logs: [],
      store: new Map(),
      cpu: { used: 0, window: [] },
      logCount: 0,
      logDropped: 0,
      actions: [],
    };
    scripts.push(sc);
    return sc;
  }
  function pushVersion(s: Script, source: string, by: string, note: string | null, time = Date.now()) {
    const version = (s.versions[s.versions.length - 1]?.version ?? 0) + 1;
    s.versions.push({ version, time, author: by, size: bytes(source), note, source });
    if (s.versions.length > limits.versionsKept) s.versions.splice(0, s.versions.length - limits.versionsKept);
  }

  return {
    handle: (c: CodeSession, msg: Record<string, any>) => void handle(c, msg),
    /** a closed session: no more pushes to it */
    drop(c: CodeSession) {
      statusWatchers.delete(c);
      for (const set of logWatchers.values()) set.delete(c);
    },
    /** a value or device change on the mainframe: the running scripts' handlers and waits */
    emit(e: LuaEvent) {
      for (const s of scripts) s.run?.emit(e);
    },
    /** the mainframe went offline (or came back): scripts stop, autostart ones start again */
    setOnline(online: boolean) {
      if (!online) for (const s of scripts) halt(s, "offline", "stopped: the mainframe went offline");
      else if (booted) for (const s of scripts) if (s.autostart && s.status !== "running") void start(s, author(s), world.perms(author(s)));
    },
    /** scripts for a fresh mock base (stopped until the first session talks to the simulation) */
    seed(list: SeedScript[]) {
      const now = Date.now();
      for (const x of list) {
        const sc = newScript(x.name);
        sc.autostart = x.autostart === true;
        for (const h of x.history ?? []) pushVersion(sc, h.source, h.author, h.note ?? null, now - h.agoMs);
        pushVersion(sc, x.source, x.author, null, now - (x.updatedAgoMs ?? 0));
        if (x.lastError) {
          sc.status = "error";
          sc.lastError = { message: x.lastError.message, line: x.lastError.line, time: now - x.lastError.agoMs };
          sc.logs.push({ ts: now - x.lastError.agoMs - 4000, level: "system", text: `started by ${x.author} (v${current(sc).version})` }, { ts: now - x.lastError.agoMs, level: "error", text: x.lastError.message });
        }
      }
    },
    /** for tests: the scripts with their state */
    scripts,
    close() {
      closed = true;
      for (const s of scripts) s.run?.stop();
      timers.forEach(clearTimeout);
      for (const t of [statusTimer, logTimer, secondTimer]) if (t) clearTimeout(t);
    },
  };
}

export type CodeSim = ReturnType<typeof createCodeSim>;
