// v0.5 D push targets (docs/SPEC.md "D. Push – web protocol and implementation notes").
export type PushType = "discord" | "ntfy" | "webhook";
export type PushEvent = "alerts" | "rules" | "approvals" | "power";
export type PushLevel = "info" | "warn" | "error";
export const PUSH_EVENTS: PushEvent[] = ["alerts", "rules", "approvals", "power"];

export interface PushTarget {
  id: string;
  type: PushType;
  name: string;
  /** masked: scheme + host + last 4 characters */
  url: string;
  hasToken: boolean;
  minLevel: PushLevel;
  events: PushEvent[];
  enabled: boolean;
  lastSent: number | null;
  lastResult: string | null;
}
export interface PushDoc {
  enabled: boolean;
  targets: PushTarget[];
  limit: number;
  perMinute: number;
  allowedHosts: string[];
}
export interface PushInput {
  type?: PushType;
  name?: string;
  url?: string;
  token?: string;
  minLevel?: PushLevel;
  events?: PushEvent[];
  enabled?: boolean;
}

const TYPES: PushType[] = ["discord", "ntfy", "webhook"];
const LEVELS: PushLevel[] = ["info", "warn", "error"];

export function parsePush(raw: any): PushDoc {
  if (!raw || typeof raw !== "object" || !Array.isArray(raw.targets)) throw new Error("bad_push");
  return {
    enabled: raw.enabled !== false,
    limit: typeof raw.limit === "number" ? raw.limit : 5,
    perMinute: typeof raw.perMinute === "number" ? raw.perMinute : 30,
    allowedHosts: Array.isArray(raw.allowedHosts) ? raw.allowedHosts.filter((h: unknown) => typeof h === "string").slice(0, 32) : [],
    targets: raw.targets
      .filter((t: any) => t && typeof t.id === "string" && TYPES.includes(t.type))
      .slice(0, 16)
      .map((t: any) => ({
        id: t.id.slice(0, 32),
        type: t.type,
        name: typeof t.name === "string" && t.name.trim() ? t.name.slice(0, 48) : t.type,
        url: typeof t.url === "string" ? t.url.slice(0, 120) : "",
        hasToken: t.hasToken === true,
        minLevel: LEVELS.includes(t.minLevel) ? t.minLevel : "warn",
        events: Array.isArray(t.events) ? t.events.filter((e: unknown): e is PushEvent => PUSH_EVENTS.includes(e as PushEvent)) : [],
        enabled: t.enabled !== false,
        lastSent: typeof t.lastSent === "number" ? t.lastSent : null,
        lastResult: typeof t.lastResult === "string" ? t.lastResult.slice(0, 40) : null,
      })),
  };
}

async function call(base: string, token: string, mf: string, init: RequestInit = {}) {
  let res: Response;
  try {
    res = await fetch(`${base}/api/mainframes/${encodeURIComponent(mf)}/push`, {
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
  if (!res.ok) throw new Error(body?.error ?? `http_${res.status}`);
  return parsePush(body);
}
export const getPush = (base: string, token: string, mf: string) => call(base, token, mf);
export const postPush = (base: string, token: string, mf: string, op: Record<string, unknown>) => call(base, token, mf, { method: "POST", body: JSON.stringify(op) });

/** Client-side URL check (the server's rules are stricter, e.g. DNS, private ranges). */
export function urlProblem(type: PushType, url: string): string | null {
  let u: URL;
  try {
    u = new URL(url);
  } catch {
    return "That is not a URL.";
  }
  if (u.username || u.password || u.hash) return "No user name, password or # part in the URL.";
  if (type === "discord" && !(u.protocol === "https:" && /^(discord\.com|discordapp\.com|canary\.discord\.com|ptb\.discord\.com)$/.test(u.hostname) && u.pathname.startsWith("/api/webhooks/")))
    return "Discord webhook URLs look like https://discord.com/api/webhooks/…";
  if (type !== "ntfy" && u.protocol !== "https:") return "Only https:// URLs.";
  if (type === "ntfy" && !/^https?:$/.test(u.protocol)) return "ntfy topics are http(s) URLs, e.g. https://ntfy.sh/my-base-alerts.";
  return null;
}

export const PUSH_ERRORS: Record<string, string> = {
  invalid: "Something in the form is not valid.",
  invalid_url: "The URL is not valid.",
  https_only: "Only https:// URLs are allowed (plain http only for an allow-listed ntfy server).",
  host_not_allowed: "The server admin does not allow this host (pushAllowedHosts).",
  too_many: "This mainframe already has the maximum number of targets.",
  unknown_target: "That target no longer exists.",
  disabled: "Push notifications are switched off on this server.",
  rate_limited: "Too many messages right now – try again in a minute.",
  not_allowed: "You need the “Push targets” permission.",
  unreachable: "The server cannot be reached.",
};
export const RESULT_TEXT: Record<string, string> = {
  ok: "delivered",
  timeout: "timed out",
  http_404: "not found (404) – was the webhook deleted?",
  http_401: "unauthorized (401) – check the token",
  http_403: "forbidden (403)",
  http_429: "rate limited by the service (429)",
  blocked_address: "blocked: the host resolves to a private address",
  redirect: "the service answered with a redirect (not followed)",
};
