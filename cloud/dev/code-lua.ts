/// <reference path="./fengari.d.ts" />
// DEV-ONLY: runs Cloud Scripts for the mock and the live demo with fengari (Lua 5.3 in JavaScript, MIT). Loaded on
// the first start or check only (dev/code-sim.ts imports it lazily), so the demo downloads it only when a visitor runs
// a script. The `cloud` table is built from api.json: the scheduler parts (events, timers, sleep, wait, log) live here,
// everything else goes to the host. Sandboxed like the mod's runtime: no io, os.execute, debug, package/require,
// file or bytecode loading, string.dump. This is not the mod's Cobalt runtime on worker threads: CPU time is measured
// in this JavaScript VM and there is no memory limit.
import fengari from "fengari";
import API from "../../src/main/resources/cccloud_code/api.json" with { type: "json" };

const { lua, lauxlib, lualib, to_luastring } = fengari;
const S = (s: string): Uint8Array => to_luastring(s);
type LuaState = object;

export interface LuaDiagnostic {
  line: number;
  column: number | null;
  message: string;
  severity: "error";
}
export interface LuaEvent {
  type: "change" | "online" | "offline" | "redstone";
  card?: string;
  title?: string;
  widget?: string;
  value?: unknown;
  old?: unknown;
  channel?: string;
  time: number;
}
export interface LuaHost {
  /** the chunk name in error messages ("<name>:12: …") */
  chunk: string;
  info: { id: string; name: string; author: string; version: number; language: "lua" };
  limits: { cpuMsPerResume: number; maxTimers: number; maxHandlers: number };
  /** every cloud.* function that is not scheduling or logging; an await function may answer with a promise */
  call(name: string, args: unknown[]): unknown[] | Promise<unknown[]>;
  log(level: "info" | "warn", text: string): void;
  later(ms: number, fn: () => void): () => void;
  /** ms of CPU left in this second (0 or less: the run waits for the next second) */
  cpuLeft(): number;
  cpuUsed(ms: number): void;
  gameTime(): number;
  /** the script ended: with an error message, or null when nothing was left to do */
  ended(error: string | null): void;
}
export interface LuaRun {
  stop(): void;
  emit(e: LuaEvent): void;
  counts(): { handlers: number; timers: number };
}

const lineOf = (msg: string) => {
  const m = /^[^\n]*?:(\d+):/.exec(msg);
  return m ? Number(m[1]) : 1;
};

/** Compiles without running: the parser's first error, as the mod's `check` answers it. */
export function compile(source: string, chunk: string): LuaDiagnostic[] {
  const L = lauxlib.luaL_newstate();
  const code = S(source);
  if (lauxlib.luaL_loadbufferx(L, code, code.length, S(`=${chunk}`), S("t")) === lua.LUA_OK) return [];
  const message = String(lua.lua_tojsstring(L, -1));
  return [{ line: lineOf(message), column: null, message, severity: "error" }];
}

// --------------------------------------------------------------- values

function toJs(L: LuaState, idx: number, depth = 0): unknown {
  switch (lua.lua_type(L, idx)) {
    case lua.LUA_TBOOLEAN:
      return lua.lua_toboolean(L, idx);
    case lua.LUA_TNUMBER:
      return lua.lua_tonumber(L, idx);
    case lua.LUA_TSTRING:
      return lua.lua_tojsstring(L, idx);
    case lua.LUA_TTABLE: {
      if (depth > 8) return null;
      const t = lua.lua_absindex(L, idx);
      const obj: Record<string, unknown> = {};
      const arr: unknown[] = [];
      let isArray = true;
      let n = 0;
      lua.lua_pushnil(L);
      while (lua.lua_next(L, t) !== 0 && n++ < 1000) {
        // never lua_tojsstring a number key: that would turn it into a string and break lua_next
        const kt = lua.lua_type(L, -2);
        const key = kt === lua.LUA_TNUMBER ? lua.lua_tonumber(L, -2) : kt === lua.LUA_TSTRING ? lua.lua_tojsstring(L, -2) : null;
        const val = toJs(L, -1, depth + 1);
        if (typeof key === "number" && Number.isInteger(key) && key >= 1) arr[key - 1] = val;
        else isArray = false;
        if (key !== null) obj[String(key)] = val;
        lua.lua_pop(L, 1);
      }
      return isArray && arr.length && arr.length === Object.keys(obj).length ? arr : obj;
    }
    default:
      return null;
  }
}

function push(L: LuaState, v: unknown, depth = 0): void {
  if (v === null || v === undefined || depth > 8) lua.lua_pushnil(L);
  else if (typeof v === "boolean") lua.lua_pushboolean(L, v);
  // fengari's integers are 32 bit: larger whole numbers (cloud.time()) go in as floats
  else if (typeof v === "number") Number.isInteger(v) && Math.abs(v) <= 0x7fffffff ? lua.lua_pushinteger(L, v) : lua.lua_pushnumber(L, v);
  else if (typeof v === "string") lua.lua_pushstring(L, S(v));
  else if (Array.isArray(v)) {
    lua.lua_createtable(L, v.length, 0);
    v.forEach((x, i) => {
      push(L, x, depth + 1);
      lua.lua_rawseti(L, -2, i + 1);
    });
  } else if (typeof v === "object") {
    lua.lua_newtable(L);
    for (const [k, x] of Object.entries(v as Record<string, unknown>)) {
      if (x === undefined) continue;
      push(L, x, depth + 1);
      lua.lua_setfield(L, -2, S(k));
    }
  } else lua.lua_pushnil(L);
}

// Lua that runs before the script: the parts of the sandbox that are easier to say in Lua. fengari has no bit32 (and
// 32-bit integers), so this bit32 is close but gives negative numbers above 2^31.
const PRELUDE = `
local rep, load_, pcall_, xpcall_, killed = string.rep, load, pcall, xpcall, __cloud_killed
__cloud_killed = nil
-- fengari keeps hooks off after a pcall caught an error in a coroutine, so a script stopped for running too long
-- must not get past a pcall: the error goes on up
local function rethrow(ok, ...)
  if not ok and killed() then error((...), 0) end
  return ok, ...
end
pcall = function(...) return rethrow(pcall_(...)) end
xpcall = function(...) return rethrow(xpcall_(...)) end
string.rep = function(s, n, sep)
  if type(n) == "number" and n > 0 and (#tostring(s) + #(sep or "")) * n > 1048576 then error("string.rep: the result would be over 1 MB", 2) end
  return rep(s, n, sep)
end
load = function(chunk, name, mode, env) return load_(chunk, name, "t", env) end
unpack = table.unpack
local M = 0xFFFFFFFF
local function fold(op, init, ...) local r = init for i = 1, select("#", ...) do r = op(r, (select(i, ...))) end return r & M end
local lshift = function(x, n) if n >= 32 or n <= -32 then return 0 end if n < 0 then return (x & M) >> -n end return (x << n) & M end
local rshift = function(x, n) if n >= 32 or n <= -32 then return 0 end if n < 0 then return (x << -n) & M end return (x & M) >> n end
bit32 = {
  band = function(...) return fold(function(a, b) return a & b end, M, ...) end,
  bor = function(...) return fold(function(a, b) return a | b end, 0, ...) end,
  bxor = function(...) return fold(function(a, b) return a ~ b end, 0, ...) end,
  bnot = function(x) return ~x & M end,
  btest = function(...) return fold(function(a, b) return a & b end, M, ...) ~= 0 end,
  lshift = lshift, rshift = rshift,
  arshift = function(x, n) x = x & M if n <= 0 then return lshift(x, -n) end if n >= 32 then return x >= 0x80000000 and M or 0 end local r = x >> n if x >= 0x80000000 then r = r | (M << (32 - n)) end return r & M end,
  extract = function(x, f, w) w = w or 1 return ((x & M) >> f) & ((1 << w) - 1) end,
  replace = function(x, v, f, w) w = w or 1 local m = ((1 << w) - 1) << f return ((x & ~m) | ((v << f) & m)) & M end,
  lrotate = function(x, n) n = n % 32 x = x & M return ((x << n) | (x >> (32 - n))) & M end,
  rrotate = function(x, n) n = n % 32 x = x & M return ((x >> n) | (x << (32 - n))) & M end,
}
`;

function sandbox(L: LuaState, killed: () => boolean) {
  for (const [name, open] of [
    ["_G", lualib.luaopen_base],
    ["coroutine", lualib.luaopen_coroutine],
    ["table", lualib.luaopen_table],
    ["string", lualib.luaopen_string],
    ["math", lualib.luaopen_math],
    ["utf8", lualib.luaopen_utf8],
    ["os", lualib.luaopen_os],
  ] as const) {
    lauxlib.luaL_requiref(L, S(name), open, 1);
    lua.lua_pop(L, 1);
  }
  // os keeps only what api.json lists (clock, date, difftime, time)
  const keepOs = new Set((API.lua.libraries as Record<string, string[]>).os ?? []);
  lua.lua_getglobal(L, S("os"));
  const names: string[] = [];
  lua.lua_pushnil(L);
  while (lua.lua_next(L, -2) !== 0) {
    if (lua.lua_type(L, -2) === lua.LUA_TSTRING) names.push(String(lua.lua_tojsstring(L, -2)));
    lua.lua_pop(L, 1);
  }
  for (const n of names)
    if (!keepOs.has(n)) {
      lua.lua_pushnil(L);
      lua.lua_setfield(L, -2, S(n));
    }
  lua.lua_pop(L, 1);
  for (const name of API.lua.removed) {
    const dot = name.indexOf(".");
    if (dot < 0) {
      lua.lua_pushnil(L);
      lua.lua_setglobal(L, S(name));
    } else if (lua.lua_getglobal(L, S(name.slice(0, dot))) === lua.LUA_TTABLE) {
      lua.lua_pushnil(L);
      lua.lua_setfield(L, -2, S(name.slice(dot + 1)));
      lua.lua_pop(L, 1);
    } else lua.lua_pop(L, 1);
  }
  lua.lua_pushjsfunction(L, (co: LuaState) => (lua.lua_pushboolean(co, killed()), 1));
  lua.lua_setglobal(L, S("__cloud_killed"));
  const code = S(PRELUDE);
  if (lauxlib.luaL_loadbufferx(L, code, code.length, S("=prelude"), S("t")) !== lua.LUA_OK || lua.lua_pcall(L, 0, 0, 0) !== lua.LUA_OK) throw new Error(`prelude: ${lua.lua_tojsstring(L, -1)}`);
}

// --------------------------------------------------------------- running

type Pending = { kind: "sleep"; ms: number } | { kind: "await"; result: Promise<unknown[]> } | { kind: "wait"; event: string; filter: Record<string, unknown> | null; timeout: number | null };
interface Handler {
  event: string;
  filter: Record<string, unknown> | null;
  ref: number;
}
interface Timer {
  ref: number;
  cancel: () => void;
  busy: boolean;
}
interface Waiter {
  event: string;
  filter: Record<string, unknown> | null;
  wake: (e: LuaEvent | null) => void;
}

/** An error a cloud.* function reports to the script: it becomes a Lua error at the script's line. */
export class ScriptFault extends Error {}

const EVENTS = new Set(API.events.map((e) => e.name));
const num = (v: unknown) => (typeof v === "number" ? v : typeof v === "boolean" ? (v ? 1 : 0) : Number.NaN);

/** Does a handler's or wait's filter take this event? above/below are crossings of a change. */
function matches(event: string, filter: Record<string, unknown> | null, e: LuaEvent): boolean {
  const base = event === "above" || event === "below" ? "change" : event;
  if (base !== e.type) return false;
  if (filter) {
    const card = filter.card;
    if (typeof card === "string" && card !== e.card && card.toLowerCase() !== (e.title ?? "").toLowerCase()) return false;
    if (typeof filter.widget === "string" && filter.widget !== e.widget) return false;
    if (typeof filter.channel === "string" && filter.channel !== e.channel) return false;
  }
  if (event === "above" || event === "below") {
    const t = num(filter?.value);
    const a = num(e.old);
    const b = num(e.value);
    if (Number.isNaN(t) || Number.isNaN(b)) return false;
    return event === "above" ? b > t && !(a > t) : b < t && !(a < t);
  }
  return true;
}

/** Milliseconds until the next cloud.at time; null for a spec it does not understand. */
function untilAt(spec: unknown, gameTime: number, now = new Date()): number | null {
  const days = ["sun", "mon", "tue", "wed", "thu", "fri", "sat"];
  if (spec && typeof spec === "object" && typeof (spec as { gameTime?: unknown }).gameTime === "number") {
    // 24000 game ticks are one in-game day (20 minutes)
    const target = (((spec as { gameTime: number }).gameTime % 24000) + 24000) % 24000;
    const ticks = (target - gameTime + 24000) % 24000 || 24000;
    return (ticks / 20) * 1000;
  }
  const text = typeof spec === "string" ? spec : spec && typeof spec === "object" ? (spec as { time?: unknown }).time : null;
  const m = typeof text === "string" ? /^(\d{1,2}):(\d{2})$/.exec(text) : null;
  if (!m || Number(m[1]) > 23 || Number(m[2]) > 59) return null;
  const want = Array.isArray((spec as { days?: unknown })?.days) ? ((spec as { days: unknown[] }).days.map((d) => String(d).slice(0, 3).toLowerCase()) as string[]) : null;
  for (let add = 0; add <= 7; add++) {
    const at = new Date(now);
    at.setDate(now.getDate() + add);
    at.setHours(Number(m[1]), Number(m[2]), 0, 0);
    if (at.getTime() <= now.getTime()) continue;
    if (want && !want.includes(days[at.getDay()])) continue;
    return at.getTime() - now.getTime();
  }
  return null;
}

export function start(source: string, host: LuaHost): LuaRun {
  const L = lauxlib.luaL_newstate();
  let killed = false;
  sandbox(L, () => killed);
  const started = performance.now();
  let stopped = false;
  let nextId = 1;
  let live = 0;
  let pending: Pending | null = null;
  let resumeStart = 0;
  const ours = new Set<LuaState>();
  const handlers = new Map<number, Handler>();
  const timers = new Map<number, Timer>();
  const waiters = new Set<Waiter>();

  const hook = (co: LuaState) => {
    if (!killed && performance.now() - resumeStart > host.limits.cpuMsPerResume) killed = true;
    if (killed) {
      // "<name>:<line>:" of the code that was running
      lauxlib.luaL_where(co, 0);
      const where = String(lua.lua_tojsstring(co, -1));
      lua.lua_pop(co, 1);
      lua.lua_pushstring(co, S(`${where || `${host.chunk}: `}ran too long without pausing (over ${host.limits.cpuMsPerResume} ms)`));
      lua.lua_error(co);
    }
  };

  const fail = (message: string) => {
    if (stopped) return;
    run.stop();
    host.ended(message);
  };
  const checkDone = () => {
    if (!stopped && live === 0 && handlers.size === 0 && timers.size === 0 && waiters.size === 0) {
      stopped = true;
      host.ended(null);
    }
  };

  /** A new coroutine: `load` pushes the function and its arguments onto it and says how many arguments. */
  function spawn(load: (co: LuaState) => number, onEnd?: () => void) {
    const co = lua.lua_newthread(L);
    const ref = lauxlib.luaL_ref(L, lua.LUA_REGISTRYINDEX);
    ours.add(co);
    live++;
    step(co, ref, load(co), onEnd);
  }
  function step(co: LuaState, ref: number, nargs: number, onEnd?: () => void) {
    if (stopped) return;
    if (host.cpuLeft() <= 0) {
      // the CPU second is used up: the run goes on in the next one
      host.later(100, () => step(co, ref, nargs, onEnd));
      return;
    }
    pending = null;
    killed = false;
    resumeStart = performance.now();
    lua.lua_sethook(co, hook, lua.LUA_MASKCOUNT, 1000);
    const status = lua.lua_resume(co, L, nargs);
    host.cpuUsed(performance.now() - resumeStart);
    if (stopped) return;
    if (status === lua.LUA_YIELD) {
      const p = pending as Pending | null;
      pending = null;
      lua.lua_settop(co, 0);
      const again = (n = 0) => step(co, ref, n, onEnd);
      if (!p) host.later(50, () => again());
      else if (p.kind === "sleep") host.later(p.ms, () => again());
      else if (p.kind === "await")
        p.result.then(
          (vals) =>
            host.later(50, () => {
              if (stopped) return;
              for (const v of vals) push(co, v);
              again(vals.length);
            }),
          (e) => fail(`${host.chunk}: ${String((e as Error)?.message ?? e)}`),
        );
      else {
        let cancelTimeout: (() => void) | null = null;
        const w: Waiter = {
          event: p.event,
          filter: p.filter,
          wake: (e) => {
            waiters.delete(w);
            cancelTimeout?.();
            push(co, e);
            again(1);
          },
        };
        waiters.add(w);
        if (p.timeout !== null) cancelTimeout = host.later(Math.max(50, p.timeout * 1000), () => waiters.has(w) && w.wake(null));
      }
      return;
    }
    live--;
    ours.delete(co);
    lauxlib.luaL_unref(L, lua.LUA_REGISTRYINDEX, ref);
    onEnd?.();
    if (status !== lua.LUA_OK) {
      const err = lua.lua_type(co, -1) === lua.LUA_TSTRING ? String(lua.lua_tojsstring(co, -1)) : `${host.chunk}: error object is not a string`;
      return fail(err);
    }
    checkDone();
  }

  /** Pause the calling coroutine (only ours: inside coroutine.wrap the scheduler could not resume it). */
  function pause(co: LuaState, p: Pending): number {
    if (!ours.has(co) || !lua.lua_isyieldable(co)) return lauxlib.luaL_error(co, S("this cannot pause here (inside a coroutine of your own or a metamethod)"));
    pending = p;
    return lua.lua_yield(co, 0);
  }
  /** Wraps a cloud.* function: JS errors become Lua errors; yields and Lua errors pass through. */
  const fn = (body: (co: LuaState, args: () => unknown[]) => number | Pending) => (co: LuaState) => {
    let r: number | Pending;
    try {
      r = body(co, () => Array.from({ length: lua.lua_gettop(co) }, (_, i) => toJs(co, i + 1)));
    } catch (e) {
      return lauxlib.luaL_error(co, S(e instanceof Error ? e.message : `internal error: ${String(e)}`));
    }
    return typeof r === "number" ? r : pause(co, r);
  };
  const ret = (co: LuaState, vals: unknown[]) => {
    for (const v of vals) push(co, v);
    return vals.length;
  };
  const fnRef = (co: LuaState, idx: number) => {
    if (lua.lua_type(co, idx) !== lua.LUA_TFUNCTION) throw new ScriptFault("a function is expected here");
    lua.lua_pushvalue(co, idx);
    return lauxlib.luaL_ref(co, lua.LUA_REGISTRYINDEX);
  };
  const seconds = (v: unknown, min: number, what: string) => {
    if (typeof v !== "number" || !Number.isFinite(v)) throw new ScriptFault(`${what}: seconds must be a number`);
    if (v < min) throw new ScriptFault(`${what}: at least ${min} s`);
    return v;
  };
  const callRef = (ref: number, arg?: LuaEvent) => (co: LuaState) => {
    lua.lua_rawgeti(co, lua.LUA_REGISTRYINDEX, ref);
    if (!arg) return 0;
    push(co, arg);
    return 1;
  };
  const addTimer = (co: LuaState, delayMs: () => number | null, handlerIdx: number, repeat: boolean) => {
    if (timers.size >= host.limits.maxTimers) throw new ScriptFault(`too many timers (at most ${host.limits.maxTimers})`);
    const id = nextId++;
    const ref = fnRef(co, handlerIdx);
    const t: Timer = { ref, busy: false, cancel: () => {} };
    const arm = () => {
      const ms = delayMs();
      if (ms === null) return;
      t.cancel = host.later(ms, () => {
        if (stopped || !timers.has(id)) return;
        if (!repeat) {
          timers.delete(id);
          spawn(callRef(ref), () => lauxlib.luaL_unref(L, lua.LUA_REGISTRYINDEX, ref));
          return;
        }
        // a run that is still busy is not started twice
        if (!t.busy) {
          t.busy = true;
          spawn(callRef(ref), () => (t.busy = false));
        }
        arm();
      });
    };
    timers.set(id, t);
    arm();
    return id;
  };
  const remove = (id: unknown) => {
    const n = typeof id === "number" ? id : Number.NaN;
    const h = handlers.get(n);
    if (h) {
      handlers.delete(n);
      lauxlib.luaL_unref(L, lua.LUA_REGISTRYINDEX, h.ref);
      return true;
    }
    const t = timers.get(n);
    if (t) {
      timers.delete(n);
      t.cancel();
      if (!t.busy) lauxlib.luaL_unref(L, lua.LUA_REGISTRYINDEX, t.ref);
      return true;
    }
    return false;
  };
  const text = (co: LuaState) => {
    const parts: string[] = [];
    const n = lua.lua_gettop(co);
    for (let i = 1; i <= n; i++) {
      lauxlib.luaL_tolstring(co, i);
      parts.push(String(lua.lua_tojsstring(co, -1)));
      lua.lua_pop(co, 1);
    }
    return parts.join(" ");
  };

  const special: Record<string, (co: LuaState) => number> = {
    log: (co) => (host.log("info", text(co)), 0),
    warn: (co) => (host.log("warn", text(co)), 0),
    time: (co) => (lua.lua_pushnumber(co, Date.now()), 1),
    clock: (co) => (lua.lua_pushnumber(co, (performance.now() - started) / 1000), 1),
    sleep: fn((co, args) => ({ kind: "sleep", ms: Math.max(50, seconds(args()[0], 0, "cloud.sleep") * 1000) })),
    on: fn((co, args) => {
      const a = args();
      const event = a[0];
      if (typeof event !== "string" || !EVENTS.has(event)) throw new ScriptFault(`cloud.on: unknown event ${JSON.stringify(event)} (${[...EVENTS].join(", ")})`);
      if (handlers.size >= host.limits.maxHandlers) throw new ScriptFault(`too many handlers (at most ${host.limits.maxHandlers})`);
      const withFilter = lua.lua_type(co, 2) === lua.LUA_TTABLE;
      const ref = fnRef(co, withFilter ? 3 : 2);
      const id = nextId++;
      handlers.set(id, { event, filter: withFilter ? (a[1] as Record<string, unknown>) : null, ref });
      return ret(co, [id]);
    }),
    off: fn((co, args) => ret(co, [remove(args()[0])])),
    cancel: fn((co, args) => ret(co, [remove(args()[0])])),
    every: fn((co, args) => {
      const s = seconds(args()[0], 0.25, "cloud.every");
      return ret(co, [addTimer(co, () => s * 1000, 2, true)]);
    }),
    after: fn((co, args) => {
      const s = seconds(args()[0], 0.25, "cloud.after");
      return ret(co, [addTimer(co, () => s * 1000, 2, false)]);
    }),
    at: fn((co, args) => {
      const spec = args()[0];
      if (untilAt(spec, host.gameTime()) === null) throw new ScriptFault('cloud.at: use "HH:MM", {time="HH:MM", days={…}} or {gameTime=…}');
      return ret(co, [addTimer(co, () => untilAt(spec, host.gameTime()), 2, true)]);
    }),
    wait: fn((co, args) => {
      const a = args();
      const event = a[0];
      if (typeof event !== "string" || !EVENTS.has(event)) throw new ScriptFault(`cloud.wait: unknown event ${JSON.stringify(event)}`);
      const filter = a[1] && typeof a[1] === "object" ? (a[1] as Record<string, unknown>) : null;
      const timeout = typeof a[2] === "number" ? a[2] : typeof a[1] === "number" ? a[1] : null;
      return { kind: "wait", event, filter, timeout };
    }),
  };

  // the cloud table, from the descriptor
  lua.lua_newtable(L);
  const subtables = new Set<string>();
  for (const f of API.functions) {
    const dot = f.name.indexOf(".");
    if (dot > 0) subtables.add(f.name.slice(0, dot));
  }
  for (const sub of subtables) {
    lua.lua_newtable(L);
    lua.lua_setfield(L, -2, S(sub));
  }
  for (const f of API.functions) {
    const name = f.aliasOf ?? f.name;
    const impl =
      special[name] ??
      fn((co, args) => {
        const r = host.call(name, args());
        if (f.kind === "await") return { kind: "await", result: Promise.resolve(r) };
        if (r instanceof Promise) throw new ScriptFault(`${f.name} answered late`);
        return ret(co, r);
      });
    const dot = f.name.indexOf(".");
    if (dot > 0) {
      lua.lua_getfield(L, -1, S(f.name.slice(0, dot)));
      lua.lua_pushjsfunction(L, impl);
      lua.lua_setfield(L, -2, S(f.name.slice(dot + 1)));
      lua.lua_pop(L, 1);
    } else {
      lua.lua_pushjsfunction(L, impl);
      lua.lua_setfield(L, -2, S(f.name));
    }
  }
  push(L, host.info);
  lua.lua_setfield(L, -2, S("script"));
  lua.lua_setglobal(L, S(API.name));
  // print writes to the log like cloud.log
  lua.lua_pushjsfunction(L, special.log);
  lua.lua_setglobal(L, S("print"));

  const run: LuaRun = {
    stop() {
      stopped = true;
      for (const t of timers.values()) t.cancel();
      timers.clear();
      handlers.clear();
      waiters.clear();
    },
    emit(e) {
      if (stopped) return;
      for (const w of [...waiters]) if (matches(w.event, w.filter, e)) w.wake(e);
      for (const h of handlers.values()) if (matches(h.event, h.filter, e)) spawn(callRef(h.ref, e));
    },
    counts: () => ({ handlers: handlers.size, timers: timers.size }),
  };

  const code = S(source);
  if (lauxlib.luaL_loadbufferx(L, code, code.length, S(`=${host.chunk}`), S("t")) !== lua.LUA_OK) {
    const message = String(lua.lua_tojsstring(L, -1));
    stopped = true;
    // after the caller has its handle
    queueMicrotask(() => host.ended(message));
    return run;
  }
  const main = lauxlib.luaL_ref(L, lua.LUA_REGISTRYINDEX);
  queueMicrotask(() => spawn(callRef(main)));
  return run;
}
