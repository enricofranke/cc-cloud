// v0.10 public API keys (docs/SPEC.md "Public API v1 (mod side)" → "Key management"): list, create and revoke
// the keys external tools (Discord bots, Home Assistant, scripts) use for /api/v1. The dashboard calls
// /api/mainframes/<id>/apikeys with its own device token, like the push targets.

export const SCOPES = ["read:sections", "read:storage", "read:stats", "action:widgets", "run:scenes"] as const;
export type Scope = (typeof SCOPES)[number];
/** The permission a scope needs: the server refuses a scope its creator could not use (scope ∩ rights). */
export const SCOPE_PERM: Record<Scope, string> = {
  "read:sections": "view",
  "read:storage": "storage.search",
  "read:stats": "view",
  "action:widgets": "act.buttons",
  "run:scenes": "act.buttons",
};
/** Expiry choices in seconds (null = never). */
export const EXPIRY = [3600, 86400, 604800, 2592000, 7776000, null] as const;
export type Expiry = (typeof EXPIRY)[number];
export const MAX_IPS = 8;
export const MAX_NAME = 40;
/** cck_ + 43 base64url characters (32 random bytes). */
export const KEY_PATTERN = /^cck_[A-Za-z0-9_-]{43}$/;

export interface ApiKey {
  id: string;
  name: string;
  /** the player the key belongs to */
  player: string;
  scopes: Scope[];
  created: number;
  expires: number | null;
  lastUsed: number | null;
  lastIp: string | null;
  ips: string[];
  /** past its expiry (the server says so; else computed from `expires`) */
  expired: boolean;
  /** a key of this session's player (others are listed for players with access.manage) */
  own: boolean;
}
export interface ApiKeysDoc {
  /** config apiEnabled */
  enabled: boolean;
  scopes: Scope[];
  /** keys per player */
  limit: number;
  keys: ApiKey[];
}
export interface NewKey {
  name: string;
  scopes: Scope[];
  expiresIn: Expiry;
  ips: string[];
}

const isScope = (s: unknown): s is Scope => (SCOPES as readonly unknown[]).includes(s);
const ts = (v: unknown) => (typeof v === "number" && Number.isFinite(v) ? v : null);

export function parseApiKeys(raw: any): ApiKeysDoc {
  if (!raw || typeof raw !== "object" || !Array.isArray(raw.keys)) throw new Error("bad_answer");
  return {
    enabled: raw.enabled === true,
    scopes: Array.isArray(raw.scopes) ? raw.scopes.filter(isScope) : [...SCOPES],
    limit: typeof raw.limit === "number" ? raw.limit : 5,
    keys: raw.keys
      .filter((k: any) => k && typeof k.id === "string")
      .slice(0, 200)
      .map((k: any) => ({
        id: k.id.slice(0, 32),
        name: typeof k.name === "string" && k.name.trim() ? k.name.slice(0, MAX_NAME) : "key",
        player: typeof k.player === "string" ? k.player.slice(0, 32) : "?",
        scopes: Array.isArray(k.scopes) ? k.scopes.filter(isScope) : [],
        created: ts(k.created) ?? 0,
        expires: ts(k.expires),
        lastUsed: ts(k.lastUsed),
        lastIp: typeof k.lastIp === "string" ? k.lastIp.slice(0, 64) : null,
        ips: Array.isArray(k.ips) ? k.ips.filter((x: unknown): x is string => typeof x === "string").slice(0, MAX_IPS).map((x: string) => x.slice(0, 64)) : [],
        expired: k.expired === true || (typeof k.expires === "number" && k.expires <= Date.now()),
        own: k.own === true,
      })),
  };
}

async function call(base: string, token: string, mf: string, init: RequestInit = {}): Promise<any> {
  let res: Response;
  try {
    res = await fetch(`${base}/api/mainframes/${encodeURIComponent(mf)}/apikeys`, {
      ...init,
      headers: { authorization: `Bearer ${token}`, ...(init.body ? { "content-type": "application/json" } : {}) },
      cache: "no-store",
    });
  } catch {
    throw new Error("unreachable");
  }
  let body: any = null;
  try {
    body = await res.json();
  } catch {}
  // an older mod without the API: the route does not exist
  if (res.status === 404 && !body?.error) throw new Error("unsupported");
  if (!res.ok) throw new Error(body?.error ?? `http_${res.status}`);
  return body;
}
export const getApiKeys = async (base: string, token: string, mf: string) => parseApiKeys(await call(base, token, mf));
/** Creates a key: the answer is the only time the key itself is returned. */
export async function createApiKey(base: string, token: string, mf: string, k: NewKey): Promise<{ key: string; id: string }> {
  const b = await call(base, token, mf, { method: "POST", body: JSON.stringify({ op: "create", name: k.name.trim(), scopes: k.scopes, expiresIn: k.expiresIn, ips: k.ips }) });
  if (typeof b?.key !== "string" || !KEY_PATTERN.test(b.key)) throw new Error("bad_answer");
  return { key: b.key, id: String(b.id ?? "") };
}
export const revokeApiKey = (base: string, token: string, mf: string, id: string) => call(base, token, mf, { method: "POST", body: JSON.stringify({ op: "revoke", id }) });

// ------------------------------------------------------------ IP allow-list
const V4 = /^(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}$/;
function isV6(s: string) {
  if (!/^[0-9a-f:.]+$/i.test(s) || !s.includes(":")) return false;
  const dbl = s.split("::");
  if (dbl.length > 2) return false;
  const groups = s.split(":").filter((g) => g !== "");
  const v4tail = groups.length && V4.test(groups[groups.length - 1]);
  const hex = v4tail ? groups.slice(0, -1) : groups;
  if (!hex.every((g) => /^[0-9a-f]{1,4}$/i.test(g))) return false;
  const n = hex.length + (v4tail ? 2 : 0);
  return dbl.length === 2 ? n < 8 : n === 8;
}
/** One allow-list entry: an IPv4/IPv6 address, optionally with a /prefix. */
export function validIp(entry: string): boolean {
  const [addr, prefix, extra] = entry.trim().split("/");
  if (extra !== undefined || !addr) return false;
  const v4 = V4.test(addr);
  const v6 = !v4 && isV6(addr);
  if (!v4 && !v6) return false;
  if (prefix === undefined) return true;
  if (!/^\d{1,3}$/.test(prefix)) return false;
  return Number(prefix) <= (v4 ? 32 : 128);
}
/** Splits the allow-list field (commas, spaces, new lines). */
export const parseIps = (text: string) => text.split(/[\s,;]+/).map((x) => x.trim()).filter(Boolean);
/** The first entry that is not an IP or CIDR, or a too-long list (null = fine). */
export function ipProblem(list: readonly string[]): { kind: "too_many" } | { kind: "bad"; entry: string } | null {
  if (list.length > MAX_IPS) return { kind: "too_many" };
  const bad = list.find((x) => !validIp(x));
  return bad ? { kind: "bad", entry: bad } : null;
}
