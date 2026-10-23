// DEV-ONLY: computer file systems for the script import API (docs/SPEC.md
// "Script import – HTTP API"). One in-memory file system per uplink that sits
// on a computer; bridges and other blocks have none (404 unknown_computer).
import { createHash } from "node:crypto";

export const MAX_FILE = 256 * 1024;
const PART = /^[A-Za-z0-9_.-]{1,64}$/;
const RESERVED = /^(rom|disk\d*|\.cloud)$/;

/** first 16 hex characters of SHA-256 (the editor's ifMatch hash) */
export const contentHash = (b: Buffer) => createHash("sha256").update(b).digest("hex").slice(0, 16);

/** Validates a relative path (up to 8 levels, v0.5 E); null when invalid. */
export function checkPath(raw: string): string | null {
  if (!raw || raw.length > 255 || raw.startsWith("/") || raw.includes("\\")) return null;
  const parts = raw.split("/");
  if (parts.length > 8) return null;
  for (const p of parts) if (!PART.test(p) || p === "." || p === "..") return null;
  if (RESERVED.test(parts[0])) return null;
  return parts.join("/");
}

export interface ComputerInfo {
  id: number;
  label: string;
  on: boolean;
  advanced: boolean;
}

export class ComputerFs {
  files = new Map<string, Buffer>();
  /** folders created with ?dir=true (others exist implicitly through their files) */
  dirs = new Set<string>();
  info: ComputerInfo;
  capacity: number;
  runs: { path: string; mode: string }[] = [];
  constructor(info: ComputerInfo, capacity = 1_000_000) {
    this.info = info;
    this.capacity = capacity;
  }
  used() {
    let n = 0;
    for (const b of this.files.values()) n += b.length;
    return n;
  }
  allDirs() {
    const dirs = new Set(this.dirs);
    for (const p of this.files.keys()) {
      const parts = p.split("/");
      for (let i = 1; i < parts.length; i++) dirs.add(parts.slice(0, i).join("/"));
    }
    return dirs;
  }
  /** Two levels (dirs first, like before) or, with tree, everything depth first and sorted. */
  list(tree = false) {
    const dirs = this.allDirs();
    if (!tree) {
      const top = [...dirs].filter((d) => !d.includes("/")).sort();
      const files = [...this.files].filter(([p]) => p.split("/").length <= 2).sort(([a], [b]) => a.localeCompare(b));
      return { files: [...top.map((d) => ({ path: d, size: 0, dir: true })), ...files.map(([p, b]) => ({ path: p, size: b.length, dir: false }))].slice(0, 500), truncated: false };
    }
    const entries = [...[...dirs].map((d) => ({ path: d, size: 0, dir: true })), ...[...this.files].map(([p, b]) => ({ path: p, size: b.length, dir: false }))];
    // depth first: a folder, then its content; folders before files on the same level
    const key = (e: { path: string; dir: boolean }) => e.path.split("/").map((part, i, all) => `${i === all.length - 1 && !e.dir ? "1" : "0"}${part}`).join("/");
    entries.sort((a, b) => key(a).localeCompare(key(b)));
    return { files: entries.slice(0, 500), truncated: entries.length > 500 };
  }
}

export interface FsDeps {
  /** writes/deletes/runs need this PIN (undefined = no PIN set) */
  pin: string | undefined;
  /** per-device PIN state (shared with the action PIN checks) */
  device: { pinFails: number; lockedUntil: number };
  pinLockMs: number;
  log: (s: string) => void;
}

type Reply = { status: number; body: unknown; raw?: string };
const err = (status: number, error: string, extra: object = {}): Reply => ({ status, body: { error, ...extra } });

/**
 * Handles `…/computers/<uplinkId>/files[/<path>]` and `…/run` for one computer.
 * `rest` is the part after the uplink id ("files", "files/a.lua", "run").
 */
export function handleFs(fs: ComputerFs, method: string, rest: string, query: URLSearchParams, headers: Record<string, string | string[] | undefined>, body: Buffer | null, deps: FsDeps, writes: number[]): Reply {
  const writing = method !== "GET";
  if (writing) {
    // rate limit: 10 writes/deletes/runs per minute per device
    const now = Date.now();
    while (writes.length && now - writes[0] > 60_000) writes.shift();
    if (writes.length >= 10) return err(429, "rate_limited", { retryAfter: Math.ceil((60_000 - (now - writes[0])) / 1000) });
    if (deps.pin) {
      const d = deps.device;
      if (d.lockedUntil > now) return err(403, "pin_locked", { retryAfter: Math.ceil((d.lockedUntil - now) / 1000) });
      const given = String(headers["x-cloud-pin"] ?? "");
      if (!given) return err(403, "pin_required");
      if (given !== deps.pin) {
        d.pinFails++;
        if (d.pinFails >= 5) {
          d.pinFails = 0;
          d.lockedUntil = now + deps.pinLockMs;
          return err(403, "pin_locked", { retryAfter: Math.ceil(deps.pinLockMs / 1000) });
        }
        return err(403, "wrong_pin", { attemptsLeft: 5 - d.pinFails });
      }
      d.pinFails = 0;
    }
    writes.push(now);
  }
  if (rest === "files" && method === "GET") {
    const l = fs.list(query.get("tree") === "true");
    return { status: 200, body: { computer: fs.info, used: fs.used(), capacity: fs.capacity, files: l.files, ...(l.truncated ? { truncated: true } : {}) } };
  }
  if (rest === "move" && method === "POST") {
    let b: { from?: unknown; to?: unknown; overwrite?: unknown } = {};
    try {
      b = JSON.parse(body?.toString("utf8") || "{}");
    } catch {
      return err(400, "invalid");
    }
    const from = checkPath(String(b.from ?? ""));
    const to = checkPath(String(b.to ?? ""));
    if (!from || !to) return err(400, "invalid_path");
    const isDir = !fs.files.has(from) && fs.allDirs().has(from);
    if (!fs.files.has(from) && !isDir) return err(404, "not_found");
    if (isDir && (to === from || to.startsWith(`${from}/`))) return err(409, "into_itself");
    if (fs.files.has(to) && (isDir || b.overwrite !== true)) return err(409, "exists");
    if (fs.allDirs().has(to) && !isDir) return err(409, "exists");
    for (const parent of to.split("/").slice(0, -1).map((_, i, a) => a.slice(0, i + 1).join("/"))) if (fs.files.has(parent)) return err(409, "not_a_directory");
    if (isDir) {
      for (const [p, data] of [...fs.files]) if (p.startsWith(`${from}/`)) (fs.files.delete(p), fs.files.set(to + p.slice(from.length), data));
      for (const d of [...fs.dirs]) if (d === from || d.startsWith(`${from}/`)) (fs.dirs.delete(d), fs.dirs.add(to + d.slice(from.length)));
    } else {
      fs.files.set(to, fs.files.get(from)!);
      fs.files.delete(from);
    }
    deps.log(`moved ${from} to ${to} on computer #${fs.info.id}`);
    return { status: 200, body: { ok: true } };
  }
  if (rest === "run" && method === "POST") {
    let b: { path?: unknown; mode?: unknown } = {};
    try {
      b = JSON.parse(body?.toString("utf8") || "{}");
    } catch {
      return err(400, "invalid");
    }
    const path = checkPath(String(b.path ?? ""));
    if (!path) return err(400, "invalid_path");
    if (!fs.files.has(path)) return err(404, "not_found");
    const mode = b.mode === "event" || b.mode === "reboot" ? b.mode : b.mode === "auto" || b.mode === undefined ? "auto" : null;
    if (!mode) return err(400, "invalid");
    if (mode === "auto" && !fs.info.advanced) return err(409, "needs_reboot");
    const started = mode === "auto" ? "event" : mode;
    fs.runs.push({ path, mode: started });
    deps.log(`run ${path} on computer #${fs.info.id} (${started})`);
    return { status: 200, body: { ok: true, started } };
  }
  const m = /^files\/(.+)$/.exec(rest);
  if (!m) return err(404, "not_found");
  const path = checkPath(decodeURIComponent(m[1]));
  if (!path) return err(400, "invalid_path");
  if (method === "GET") {
    const f = fs.files.get(path);
    return f ? { status: 200, body: null, raw: f.toString("utf8") } : err(404, "not_found");
  }
  if (method === "DELETE") {
    if (!fs.files.has(path) && fs.allDirs().has(path)) {
      for (const p of [...fs.files.keys()]) if (p.startsWith(`${path}/`)) fs.files.delete(p);
      for (const d of [...fs.dirs]) if (d === path || d.startsWith(`${path}/`)) fs.dirs.delete(d);
      deps.log(`deleted folder ${path} on computer #${fs.info.id}`);
      return { status: 200, body: { ok: true } };
    }
    if (!fs.files.delete(path)) return err(404, "not_found");
    deps.log(`deleted ${path} on computer #${fs.info.id}`);
    return { status: 200, body: { ok: true } };
  }
  if (method === "PUT") {
    const data = body ?? Buffer.alloc(0);
    if (query.get("dir") === "true") {
      if (fs.files.has(path)) return err(409, "exists");
      fs.dirs.add(path);
      return { status: 200, body: { ok: true, path } };
    }
    if (data.length > MAX_FILE) return err(413, "too_large");
    if (fs.allDirs().has(path)) return err(409, "exists");
    const ifMatch = query.get("ifMatch");
    if (ifMatch !== null) {
      const cur = fs.files.get(path);
      if (!cur || contentHash(cur) !== ifMatch) return err(409, "changed");
    } else if (fs.files.has(path) && query.get("overwrite") !== "true") return err(409, "exists");
    const old = fs.files.get(path)?.length ?? 0;
    if (fs.used() - old + data.length > fs.capacity) return err(507, "quota");
    fs.files.set(path, data);
    if (query.get("startup") === "true") fs.files.set("startup.lua", Buffer.from(`shell.run("${path}")\n`));
    deps.log(`uploaded ${path} (${data.length} bytes) to computer #${fs.info.id}`);
    return { status: 200, body: { ok: true, path, size: data.length, hash: contentHash(data) } };
  }
  return err(405, "method");
}
