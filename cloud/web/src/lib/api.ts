// HTTP calls to the mod's embedded web server (same origin as the dashboard).
import type { MainframeInfo } from "./types.ts";

export type ApiErrorKind = "invalid_code" | "too_many_attempts" | "unreachable" | "unauthorized" | "bad_request" | "lan_only" | "server";

export class ApiError extends Error {
  readonly kind: ApiErrorKind;
  constructor(kind: ApiErrorKind, message: string) {
    super(message);
    this.kind = kind;
  }
}

export const portHint = () =>
  typeof location === "undefined" ? "8787" : location.port || (location.protocol === "https:" ? "443" : "80");

export function unreachableText() {
  return `Server not reachable – is the Minecraft server running and port ${portHint()} open?`;
}

export const LAN_ONLY_TEXT =
  "This Minecraft server only allows dashboard access from its local network. A server admin can allow internet access with webAllowInternet=true in the cccloud server config (ideally behind HTTPS).";

async function request(path: string, init: RequestInit = {}, base = "") {
  try {
    return await fetch(base + path, { ...init, headers: { "content-type": "application/json", ...init.headers }, cache: "no-store" });
  } catch {
    throw new ApiError("unreachable", unreachableText());
  }
}

/** Parses JSON bodies; plain-text bodies come back as {text}. */
async function body(res: Response): Promise<any> {
  let text = "";
  try {
    text = await res.text();
    return JSON.parse(text);
  } catch {
    return { text };
  }
}

/** The 403 "LAN only" answer (webAllowInternet=false): plain text from the mod, or a JSON error code. */
function isLanOnly(status: number, b: any) {
  if (status !== 403) return false;
  return /internet|lan_only/i.test(String(b?.error ?? "")) || /local network|webAllowInternet/i.test(String(b?.text ?? ""));
}

export interface Paired {
  token: string;
  mainframeId: string;
  name: string;
  canControl?: boolean;
}

export type PairStart = { status: "pending"; pairingId: string } | ({ status: "approved" } & Paired);

/** Step 1: submit the code. The device then waits for approval in-game. */
export async function pair(code: string, name: string, base = ""): Promise<PairStart> {
  const res = await request("/api/pair", { method: "POST", body: JSON.stringify({ code, name }) }, base);
  const b = await body(res);
  if (isLanOnly(res.status, b)) throw new ApiError("lan_only", LAN_ONLY_TEXT);
  if (res.status === 403 || res.status === 404) throw new ApiError("invalid_code", "That code is not valid. Codes change after every use and expire after 10 minutes – right-click the mainframe again for a fresh one.");
  if (res.status === 429) throw new ApiError("too_many_attempts", "Too many wrong codes from this network. Wait a minute and try again.");
  if (res.status === 400) throw new ApiError("bad_request", "Please enter the 8-character code and a device name.");
  if (!res.ok) throw new ApiError("server", `The server answered with an error (${res.status}).`);
  if (b.status === "pending" && typeof b.pairingId === "string") return { status: "pending", pairingId: b.pairingId };
  // servers without the approval step hand out the token right away
  if (typeof b.token === "string" && typeof b.mainframeId === "string") return { status: "approved", token: b.token, mainframeId: b.mainframeId, name: b.name ?? "Mainframe" };
  throw new ApiError("server", "Unexpected answer from the server.");
}

export interface AccountLinked {
  token: string;
  account: { player: string; uuid?: string };
  mainframes: { id: string; name: string; role?: string }[];
}
export type PairPoll =
  | { status: "pending" }
  | { status: "denied" }
  | { status: "expired" }
  | ({ status: "approved" } & Paired)
  | ({ status: "linked" } & AccountLinked);

/** Step 2: GET /api/pair/<pairingId> until the player allowed or denied the device. */
export async function pollPairing(pairingId: string, base = ""): Promise<PairPoll> {
  const res = await request(`/api/pair/${encodeURIComponent(pairingId)}`, {}, base);
  const b = await body(res);
  if (isLanOnly(res.status, b)) throw new ApiError("lan_only", LAN_ONLY_TEXT);
  if ((res.status === 404 || res.status === 410) && !b.status) return { status: "expired" };
  // v0.6: an account code links the browser to the player account
  if (b.status === "approved" && typeof b.token === "string" && b.account && typeof b.account.player === "string")
    return {
      status: "linked",
      token: b.token,
      account: { player: String(b.account.player).slice(0, 32), uuid: typeof b.account.uuid === "string" ? b.account.uuid : undefined },
      mainframes: (Array.isArray(b.mainframes) ? b.mainframes : [])
        .filter((m: any) => m && typeof m.id === "string")
        .map((m: any) => ({ id: m.id, name: typeof m.name === "string" ? m.name : "Mainframe", role: typeof m.role === "string" ? m.role : undefined })),
    };
  // check the status field first: the mod answers "denied" with 403 and "expired" with 404
  if (b.status === "approved" && typeof b.token === "string" && typeof b.mainframeId === "string")
    return {
      status: "approved",
      token: b.token,
      mainframeId: b.mainframeId,
      name: b.name ?? "Mainframe",
      canControl: typeof b.canControl === "boolean" ? b.canControl : undefined,
    };
  if (b.status === "denied" || b.status === "expired") return { status: b.status };
  if (b.status === "pending") return { status: "pending" };
  if (!res.ok) throw new ApiError("server", `The server answered with an error (${res.status}).`);
  return { status: "pending" };
}

/** GET /api/mainframes – tolerant of `{mainframes:[...]}`, a bare array or a single object. */
export async function fetchMainframes(token: string, base = ""): Promise<MainframeInfo[]> {
  const res = await request("/api/mainframes", { headers: { authorization: `Bearer ${token}` } }, base);
  const b = await body(res);
  if (isLanOnly(res.status, b)) throw new ApiError("lan_only", LAN_ONLY_TEXT);
  if (res.status === 401 || res.status === 403) throw new ApiError("unauthorized", "Access revoked");
  if (!res.ok) throw new ApiError("server", `HTTP ${res.status}`);
  const list = Array.isArray(b) ? b : Array.isArray(b?.mainframes) ? b.mainframes : b ? [b] : [];
  return list.filter((m: any) => m && typeof m.id === "string") as (MainframeInfo & { role?: string })[];
}

// ---------------------------------------------------------------- v0.6 APIs
async function authed<T>(path: string, token: string, init: RequestInit = {}, base = ""): Promise<T> {
  const res = await request(path, { ...init, headers: { authorization: `Bearer ${token}`, ...(init.headers ?? {}) } }, base);
  const b = await body(res);
  if (isLanOnly(res.status, b)) throw new ApiError("lan_only", LAN_ONLY_TEXT);
  if (res.status === 401) throw new ApiError("unauthorized", "This browser is not linked any more.");
  if (res.status === 403) throw new ApiError("server", b?.error === "not_allowed" ? "not_allowed" : b?.error ?? "forbidden");
  if (!res.ok) throw new ApiError("server", b?.error ?? `HTTP ${res.status}`);
  return b as T;
}

export interface DeviceEntry {
  id: string;
  name: string;
  type: "desktop" | "phone" | "tablet" | "other";
  lastIp?: string;
  lastSeen?: number;
  created?: number;
  current?: boolean;
  mainframes?: string[] | null;
}
export const listDevices = (token: string, base = "") => authed<{ devices: DeviceEntry[] }>("/api/devices", token, {}, base);
export const revokeDevice = (token: string, id: string, base = "") =>
  authed<{ ok: boolean }>(`/api/devices/${encodeURIComponent(id)}`, token, { method: "DELETE" }, base);

export type RoleName = "viewer" | "operator" | "admin" | "owner";
export interface AccessDoc {
  you: { player: string; role: RoleName };
  players: { uuid: string; player: string; role: RoleName; source: "owner" | "direct" | "team" | "server_admin"; teamRank?: string }[];
  team: { provider: string; id: string; name: string; mapping: Record<string, RoleName | "none"> } | null;
  teams: { provider: string; id: string; name: string; ranks: string[] }[];
  providers: string[];
}
export const getAccess = (token: string, mf: string, base = "") => authed<AccessDoc>(`/api/mainframes/${encodeURIComponent(mf)}/access`, token, {}, base);
export const postAccess = (token: string, mf: string, op: Record<string, unknown>, base = "") =>
  authed<AccessDoc>(`/api/mainframes/${encodeURIComponent(mf)}/access`, token, { method: "POST", body: JSON.stringify(op) }, base);

export interface ShareEntry {
  id: string;
  label: string;
  createdBy: string;
  created: number;
  expires: number | null;
  maxViewers: number;
  viewers: number;
  pages: string[];
  cards: string[];
  hideNumbers: boolean;
}
export const listShares = (token: string, mf: string, base = "") => authed<{ shares: ShareEntry[] }>(`/api/mainframes/${encodeURIComponent(mf)}/shares`, token, {}, base);
export const createShare = (token: string, mf: string, input: { label: string; pages: string[]; cards: string[]; expiresIn: number | null; maxViewers: number; hideNumbers: boolean }, base = "") =>
  authed<{ id: string; secret: string; url: string }>(`/api/mainframes/${encodeURIComponent(mf)}/shares`, token, { method: "POST", body: JSON.stringify(input) }, base);
export const deleteShare = (token: string, mf: string, id: string, base = "") =>
  authed<{ ok: boolean }>(`/api/mainframes/${encodeURIComponent(mf)}/shares/${encodeURIComponent(id)}`, token, { method: "DELETE" }, base);

// ---------------------------------------------------------------- v0.7 terrain
export interface TerrainPatch {
  x0: number;
  z0: number;
  w: number;
  h: number;
  /** decoded: world Y per cell, NaN = unknown */
  heights: Float32Array;
  /** decoded: RGB per cell */
  colors: Uint8Array;
}
export interface Terrain {
  dim: string;
  step: number;
  patches: TerrainPatch[];
  truncated: boolean;
}

const b64 = (s: string) => {
  const bin = atob(s);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
};

/** Validates and decodes a terrain document (docs/SPEC.md "v0.7 web protocol"). Bad patches are dropped. */
export function decodeTerrain(raw: any, dim: string): Terrain {
  const step = Number.isInteger(raw?.step) && raw.step >= 1 && raw.step <= 16 ? raw.step : 2;
  const patches: TerrainPatch[] = [];
  for (const p of Array.isArray(raw?.patches) ? raw.patches.slice(0, 64) : []) {
    const w = p?.w;
    const h = p?.h;
    if (!Number.isInteger(w) || !Number.isInteger(h) || w < 2 || h < 2 || w * h > 262144) continue;
    if (!Number.isFinite(p.x0) || !Number.isFinite(p.z0) || typeof p.heights !== "string" || typeof p.colors !== "string") continue;
    let hb: Uint8Array;
    let cb: Uint8Array;
    try {
      hb = b64(p.heights);
      cb = b64(p.colors);
    } catch {
      continue;
    }
    if (hb.length !== w * h * 2 || cb.length !== w * h * 3) continue;
    const dv = new DataView(hb.buffer);
    const heights = new Float32Array(w * h);
    for (let i = 0; i < w * h; i++) {
      const v = dv.getInt16(i * 2, true);
      heights[i] = v === -32768 ? NaN : v;
    }
    patches.push({ x0: p.x0, z0: p.z0, w, h, heights, colors: cb });
  }
  return { dim: typeof raw?.dim === "string" ? raw.dim : dim, step, patches, truncated: raw?.truncated === true };
}

/** null = the server has no terrain endpoint (older mod) or this device may not see it. */
export async function fetchTerrain(token: string, mf: string, dim: string, base = ""): Promise<Terrain | null> {
  const res = await request(`/api/mainframes/${encodeURIComponent(mf)}/terrain?dim=${encodeURIComponent(dim)}`, { headers: { authorization: `Bearer ${token}` } }, base);
  if (res.status === 404 || res.status === 403 || res.status === 401) return null;
  const b = await body(res);
  if (res.status === 429) throw new ApiError("server", `rate_limited:${Number(b?.retryAfter) || 5}`);
  if (!res.ok) throw new ApiError("server", `HTTP ${res.status}`);
  return decodeTerrain(b, dim);
}
