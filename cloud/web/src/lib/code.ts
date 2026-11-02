// Cloud Scripts: the shapes of the dashboard's `code` protocol, normalising what the server sends, error positions
// and the log buffer – what the connection needs (the IDE's own logic is in code/ide.ts). The contract lives with the
// mod (cccloud_code/api.json and the protocol notes); everything here is defensive because a frame can come
// from an older or newer server.

export type ScriptStatus = "running" | "stopped" | "error";
export type LogLevel = "info" | "warn" | "error" | "system";

export interface ScriptError {
  message: string;
  line: number | null;
  time: number;
}
export interface ScriptSummary {
  id: string;
  name: string;
  language: "lua";
  status: ScriptStatus;
  /** why it stopped: stopped, finished, kill_switch, offline, unloaded, command, author_lost_access, server_stopping, deleted */
  reason: string | null;
  autostart: boolean;
  version: number;
  /** bytes of the current source */
  size: number;
  updated: number;
  /** who saved last: the script acts with their rights */
  author: string;
  startedAt: number | null;
  startedBy: string | null;
  /** ms of the last full second, average ms/s of the last 10 s, ms/s allowed */
  cpu: { ms: number; avg: number; limit: number };
  handlers: number;
  timers: number;
  lastError: ScriptError | null;
  logLines: number;
}
export interface CodeLimits {
  maxScripts: number;
  maxRunning: number;
  /** bytes */
  maxSource: number;
  cpuMsPerSecond: number;
  cpuMsPerResume: number;
  versionsKept: number;
  maxTimers: number;
  maxHandlers: number;
  storageBytes: number;
  logLinesPerSecond: number;
  /** allocation rate and estimated retained size per script, bytes */
  allocBytesPerSecond: number;
  memoryBytes: number;
}
export interface Diagnostic {
  line: number;
  column: number | null;
  message: string;
  severity: "error" | "warning";
}
export interface LogLine {
  ts: number;
  level: LogLevel;
  text: string;
}
export interface VersionMeta {
  version: number;
  time: number;
  author: string;
  size: number;
  note: string | null;
}

/** The server's defaults (config cloudScripts.*); used until the first list answer. */
export const DEFAULT_LIMITS: CodeLimits = {
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
  allocBytesPerSecond: 32 * 1024 * 1024,
  memoryBytes: 16 * 1024 * 1024,
};
/** The server keeps this many log lines per script. */
export const LOG_KEEP = 200;
export const MAX_NAME = 32;

const str = (v: unknown, max: number) => (typeof v === "string" ? v.slice(0, max) : "");
const num = (v: unknown, fallback = 0) => (typeof v === "number" && Number.isFinite(v) ? v : fallback);
const int = (v: unknown, fallback = 0) => Math.max(0, Math.floor(num(v, fallback)));
const lineNo = (v: unknown) => (typeof v === "number" && Number.isInteger(v) && v > 0 ? v : null);

export function parseSummary(raw: unknown): ScriptSummary | null {
  const r = raw as Record<string, any> | null;
  if (!r || typeof r !== "object" || typeof r.id !== "string" || !r.id || r.id.length > 64) return null;
  const status: ScriptStatus = r.status === "running" || r.status === "error" ? r.status : "stopped";
  const cpu = r.cpu && typeof r.cpu === "object" ? r.cpu : {};
  const e = r.lastError && typeof r.lastError === "object" ? r.lastError : null;
  return {
    id: r.id,
    name: str(r.name, 64) || r.id,
    language: "lua",
    status,
    reason: typeof r.reason === "string" ? r.reason.slice(0, 40) : null,
    autostart: r.autostart === true,
    version: int(r.version),
    size: int(r.size),
    updated: num(r.updated),
    author: str(r.author, 40),
    startedAt: typeof r.startedAt === "number" ? r.startedAt : null,
    startedBy: typeof r.startedBy === "string" ? r.startedBy.slice(0, 40) : null,
    cpu: { ms: Math.max(0, num(cpu.ms)), avg: Math.max(0, num(cpu.avg)), limit: Math.max(0, num(cpu.limit, DEFAULT_LIMITS.cpuMsPerSecond)) },
    handlers: int(r.handlers),
    timers: int(r.timers),
    lastError: e && typeof e.message === "string" ? { message: e.message.slice(0, 500), line: lineNo(e.line) ?? errorLine(e.message), time: num(e.time) } : null,
    logLines: int(r.logLines),
  };
}
export function parseSummaries(raw: unknown): ScriptSummary[] {
  if (!Array.isArray(raw)) return [];
  return raw
    .slice(0, 64)
    .map(parseSummary)
    .filter((s): s is ScriptSummary => !!s);
}
export function parseLimits(raw: unknown): CodeLimits {
  const r = (raw && typeof raw === "object" ? raw : {}) as Record<string, unknown>;
  const out = { ...DEFAULT_LIMITS };
  for (const k of Object.keys(out) as (keyof CodeLimits)[]) if (typeof r[k] === "number" && Number.isFinite(r[k]) && (r[k] as number) >= 0) out[k] = r[k] as number;
  return out;
}
export function parseDiagnostics(raw: unknown): Diagnostic[] {
  if (!Array.isArray(raw)) return [];
  const out: Diagnostic[] = [];
  for (const d of raw.slice(0, 50)) {
    if (!d || typeof d !== "object" || typeof d.message !== "string") continue;
    out.push({ line: lineNo(d.line) ?? errorLine(d.message) ?? 1, column: lineNo(d.column), message: d.message.slice(0, 500), severity: d.severity === "warning" ? "warning" : "error" });
  }
  return out;
}
const LEVELS: LogLevel[] = ["info", "warn", "error", "system"];
export function parseLogLines(raw: unknown): LogLine[] {
  if (!Array.isArray(raw)) return [];
  const out: LogLine[] = [];
  for (const l of raw.slice(-LOG_KEEP)) {
    if (!l || typeof l !== "object" || typeof l.text !== "string") continue;
    out.push({ ts: num(l.ts), level: LEVELS.includes(l.level) ? l.level : "info", text: l.text.slice(0, 2000) });
  }
  return out;
}
export function parseVersions(raw: unknown): VersionMeta[] {
  if (!Array.isArray(raw)) return [];
  return raw
    .slice(0, 100)
    .filter((v) => v && typeof v === "object" && typeof v.version === "number")
    .map((v) => ({ version: int(v.version), time: num(v.time), author: str(v.author, 40), size: int(v.size), note: typeof v.note === "string" ? v.note.slice(0, 200) : null }));
}

/** The line of a Lua error message: "night_mode:12: attempt to index a nil value" -> 12. */
export function errorLine(message: string): number | null {
  const m = /^[^\n]*?:(\d+):/.exec(message);
  return m ? Number(m[1]) || null : null;
}
/** The message without the "name:12: " prefix (the editor shows the line itself). */
export function errorText(message: string): string {
  return message.replace(/^[^\n:]*:\d+:\s*/, "");
}

// ------------------------------------------------------------------ log

/** Appends pushed lines, keeping the newest `max` (the server keeps 200, so does the view). */
export function appendLog(prev: LogLine[], add: LogLine[], max = LOG_KEEP): LogLine[] {
  if (!add.length) return prev;
  const all = prev.concat(add);
  return all.length > max ? all.slice(all.length - max) : all;
}
