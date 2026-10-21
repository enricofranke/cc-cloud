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

export type PairPoll = { status: "pending" } | { status: "denied" } | { status: "expired" } | ({ status: "approved" } & Paired);

/** Step 2: GET /api/pair/<pairingId> until the player allowed or denied the device. */
export async function pollPairing(pairingId: string, base = ""): Promise<PairPoll> {
  const res = await request(`/api/pair/${encodeURIComponent(pairingId)}`, {}, base);
  const b = await body(res);
  if (isLanOnly(res.status, b)) throw new ApiError("lan_only", LAN_ONLY_TEXT);
  if ((res.status === 404 || res.status === 410) && !b.status) return { status: "expired" };
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
  return list.filter((m: any) => m && typeof m.id === "string");
}
