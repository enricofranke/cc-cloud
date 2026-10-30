// Script import: push Lua files onto the computer behind a card (docs/SPEC.md
// "Script import – HTTP API"). Admin only; writes, deletes and runs need the
// mainframe PIN when one is set (header X-Cloud-Pin, never in a URL).

export const MAX_SCRIPT = 256 * 1024;
const PART = /^[A-Za-z0-9_.-]{1,64}$/;
const RESERVED = /^(rom|disk\d*|\.cloud)$/;

/** First 16 hex characters of SHA-256 of the UTF-8 content – the editor's ifMatch hash. */
export async function contentHash(text: string) {
  const { sha256Hex } = await import("./scriptstore.ts");
  return (await sha256Hex(new TextEncoder().encode(text))).slice(0, 16);
}

/** Same rules as the mod: "name" or "folder/name" (the editor allows 8 levels), no rom/disk mounts, no "..". Returns an error text or null. */
export function pathError(path: string, maxLevels = 2): string | null {
  if (!path) return "Enter a file name.";
  if (path.startsWith("/") || path.includes("\\")) return "Use a relative name like control-room.lua.";
  const parts = path.split("/");
  if (parts.length > maxLevels) return maxLevels === 2 ? "At most one folder (folder/name.lua)." : `At most ${maxLevels - 1} folders deep.`;
  if (path.length > 255) return "The path is too long.";
  for (const p of parts) {
    if (p === "." || p === "..") return "“.” and “..” are not allowed.";
    if (!PART.test(p)) return "Letters, digits, “_”, “-” and “.” only (max 64 per part).";
  }
  if (RESERVED.test(parts[0])) return `“${parts[0]}” is reserved by the computer.`;
  return null;
}

/** "Control Room.LUA" → "control-room.lua"; adds .lua when missing. */
export function suggestName(raw: string): string {
  const base = raw.split(/[\\/]/).pop() ?? "";
  let n = base
    .trim()
    .replace(/\s+/g, "-")
    .replace(/[^A-Za-z0-9_.-]/g, "")
    .replace(/^\.+/, "")
    .slice(0, 60);
  if (!n) n = "program";
  if (!/\.lua$/i.test(n)) n = `${n.replace(/\.[^.]*$/, "")}.lua`;
  return n.replace(/\.LUA$/, ".lua");
}

/** Header comment of a script → a description (and its "Run:"/"Usage:" line) for the example gallery. */
export function describeScript(src: string): { text: string; run?: string } {
  const lines: string[] = [];
  for (const l of src.split(/\r?\n/)) {
    const m = /^\s*--+\s?(.*)$/.exec(l);
    if (!m) break;
    lines.push(m[1].trim());
  }
  const isRun = (l: string) => /^(run|usage):/i.test(l);
  const run = lines.find(isRun)?.replace(/^(run|usage):\s*/i, "");
  const text = lines
    .filter((l) => l && !isRun(l))
    .join(" ")
    .replace(/^(CC: Cloud (example|test program|demo)|[a-z0-9-]+):\s*/i, "");
  return { text: text.charAt(0).toUpperCase() + text.slice(1), ...(run ? { run } : {}) };
}

/** "control-room.lua" → "Control room" */
export const titleOf = (file: string) => {
  const t = file.replace(/\.lua$/, "").replace(/[-_]+/g, " ");
  return t.charAt(0).toUpperCase() + t.slice(1);
};

export interface FileEntry {
  path: string;
  size: number;
  dir: boolean;
}
/** Store scripts installed on that computer (server-side records, v0.9). */
export interface StoreRecord {
  id: string;
  version: string;
  sha256: string;
  changed: boolean;
  missing: boolean;
}
export interface FileList {
  computer: { id: number; label: string; on: boolean; advanced: boolean };
  used: number;
  capacity: number;
  files: FileEntry[];
  store?: Record<string, StoreRecord>;
}

export class ScriptError extends Error {
  readonly code: string;
  readonly status: number;
  readonly attemptsLeft?: number;
  readonly retryAfter?: number;
  constructor(status: number, code: string, extra: { attemptsLeft?: number; retryAfter?: number } = {}) {
    super(code);
    this.code = code;
    this.status = status;
    this.attemptsLeft = extra.attemptsLeft;
    this.retryAfter = extra.retryAfter;
  }
}

export const SCRIPT_ERRORS: Record<string, string> = {
  not_allowed: "Only admins of this mainframe can manage scripts.",
  no_access: "This browser has no access to this mainframe.",
  unknown_computer: "There is no computer behind this card (or its chunk is not loaded).",
  claim_blocked: "This computer is in another team's claim, so its files cannot be changed from here.",
  offline: "The mainframe is offline.",
  invalid_path: "That file name is not allowed.",
  exists: "A file with that name already exists.",
  too_large: "The file is too large (max 256 KB).",
  quota: "The computer's disk is full.",
  not_found: "The file does not exist (any more).",
  rate_limited: "Too many changes – wait a minute.",
  needs_reboot: "This computer cannot start programs in the background.",
  pin_required: "This needs the mainframe PIN.",
  wrong_pin: "Wrong PIN.",
  pin_locked: "Too many wrong PINs – locked for a few minutes.",
  changed: "The file changed on the computer since you opened it.",
  store_not_configured: "The server has no Script Store key configured, so it cannot check store scripts (server admin: set scriptStoreKey).",
  store_disabled: "The Script Store is switched off on this server.",
  invalid_store_ref: "The store script reference is not valid.",
  store_unreachable: "The server could not reach the Script Store to check the script – try again later.",
  store_bad_signature: "The store index is not correctly signed – nothing was installed.",
  store_unknown_script: "The server does not find this script version in the signed store index (maybe it was just updated – reload the store).",
  store_mismatch: "The file does not match the signed store index – nothing was installed.",
  into_itself: "A folder cannot be moved into itself.",
  not_a_directory: "A file is in the way of that folder.",
  unreachable: "Server not reachable.",
};
export const scriptErrorText = (e: unknown) =>
  e instanceof ScriptError ? (SCRIPT_ERRORS[e.code] ?? `Error: ${e.code}`) : "Something went wrong. Try again.";

export interface ScriptApi {
  list(): Promise<FileList>;
  /** v0.5 E editor: the whole tree (8 levels, depth first) */
  tree(): Promise<FileList & { truncated?: boolean }>;
  /** v0.5 E editor save: 409 changed when the file no longer has `hash` */
  save(path: string, code: string, hash: string | null, pin?: string): Promise<{ path: string; size: number; hash: string }>;
  mkdir(path: string, pin?: string): Promise<void>;
  move(from: string, to: string, pin?: string): Promise<void>;
  read(path: string): Promise<string>;
  /** `store` = "<id>@<version>" for store installs (the server checks the signed index); `storeFile` when the path differs from the store's. */
  write(path: string, code: string, opts: { overwrite?: boolean; startup?: boolean; pin?: string; store?: string; storeFile?: string }): Promise<{ path: string; size: number }>;
  remove(path: string, pin?: string): Promise<void>;
  run(path: string, mode: "auto" | "event" | "reboot", pin?: string): Promise<"event" | "reboot">;
}

const enc = (path: string) => path.split("/").map(encodeURIComponent).join("/");

export function scriptApi(base: string, token: string, mainframeId: string, uplinkId: string): ScriptApi {
  const root = `${base}/api/mainframes/${encodeURIComponent(mainframeId)}/computers/${encodeURIComponent(uplinkId)}`;
  const call = async (path: string, init: RequestInit & { pin?: string } = {}) => {
    const headers: Record<string, string> = { authorization: `Bearer ${token}` };
    if (init.pin) headers["x-cloud-pin"] = init.pin;
    let res: Response;
    try {
      res = await fetch(root + path, { ...init, headers: { ...headers, ...(init.headers as Record<string, string>) }, cache: "no-store" });
    } catch {
      throw new ScriptError(0, "unreachable");
    }
    if (!res.ok) {
      let b: { error?: string; attemptsLeft?: number; retryAfter?: number } = {};
      try {
        b = await res.json();
      } catch {}
      throw new ScriptError(res.status, b.error ?? `http_${res.status}`, b);
    }
    return res;
  };
  return {
    list: async () => (await call("/files")).json(),
    tree: async () => (await call("/files?tree=true")).json(),
    save: async (path, code, hash, pin) => {
      const q = hash === null ? "" : `?ifMatch=${encodeURIComponent(hash)}`;
      return (await call(`/files/${enc(path)}${q}`, { method: "PUT", body: code, pin, headers: { "content-type": "text/plain; charset=utf-8" } })).json();
    },
    mkdir: async (path, pin) => {
      await call(`/files/${enc(path)}?dir=true`, { method: "PUT", body: "", pin });
    },
    move: async (from, to, pin) => {
      await call("/move", { method: "POST", body: JSON.stringify({ from, to }), pin, headers: { "content-type": "application/json" } });
    },
    read: async (path) => (await call(`/files/${enc(path)}`)).text(),
    write: async (path, code, o) => {
      const q = new URLSearchParams();
      if (o.overwrite) q.set("overwrite", "true");
      if (o.startup) q.set("startup", "true");
      if (o.store) q.set("store", o.store);
      if (o.storeFile && o.storeFile !== path) q.set("storeFile", o.storeFile);
      const res = await call(`/files/${enc(path)}${q.size ? `?${q}` : ""}`, { method: "PUT", body: code, pin: o.pin, headers: { "content-type": "text/plain; charset=utf-8" } });
      return res.json();
    },
    remove: async (path, pin) => {
      await call(`/files/${enc(path)}`, { method: "DELETE", pin });
    },
    run: async (path, mode, pin) => (await (await call("/run", { method: "POST", body: JSON.stringify({ path, mode }), pin, headers: { "content-type": "application/json" } })).json()).started,
  };
}

export const fmtBytes = (n: number) => (n < 1024 ? `${n} B` : n < 1024 * 1024 ? `${(n / 1024).toFixed(n < 10240 ? 1 : 0)} KB` : `${(n / 1024 / 1024).toFixed(1)} MB`);
