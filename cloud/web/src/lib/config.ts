// GET /api/config (docs/SPEC.md "Script Store – dashboard side", public config): the server's switches
// before login. No secrets. A missing endpoint (older mods) means "everything on" – except the v0.10
// switches (scenes, production, api), which need an explicit true; the session's permissions still
// decide what a user may do.
import { signal } from "@preact/signals";
import { parseStore } from "./conn.ts";

export interface Features {
  terminal: boolean;
  voxels: boolean;
  push: boolean;
  rules: boolean;
  map: boolean;
  /** v0.10: these are off unless the server says so (older servers do not have them) */
  scenes: boolean;
  production: boolean;
  api: boolean;
}
export interface PublicConfig {
  version: string | null;
  store: { enabled: boolean; url: string | null; key?: string };
  features: Features;
}
const ALL_ON: Features = { terminal: true, voxels: true, push: true, rules: true, map: true, scenes: false, production: false, api: false };
export const publicConfig = signal<PublicConfig>({ version: null, store: { enabled: true, url: null }, features: ALL_ON });

export function parseConfig(raw: any): PublicConfig {
  const f = raw?.features && typeof raw.features === "object" ? raw.features : {};
  const flag = (k: keyof Features) => f[k] !== false;
  const newer = (k: keyof Features) => f[k] === true;
  const st = raw?.store && typeof raw.store === "object" ? raw.store : {};
  const parsed = parseStore(st);
  return {
    version: typeof raw?.version === "string" ? raw.version.slice(0, 32) : null,
    store: { enabled: st.enabled !== false && !!parsed, url: parsed?.url ?? null, ...(parsed?.key ? { key: parsed.key } : {}) },
    features: { terminal: flag("terminal"), voxels: flag("voxels"), push: flag("push"), rules: flag("rules"), map: flag("map"), scenes: newer("scenes"), production: newer("production"), api: newer("api") },
  };
}

/** Loads the public config once (best effort). */
export async function loadPublicConfig(base = "") {
  try {
    const r = await fetch(`${base}/api/config`, { cache: "no-store", credentials: "omit" });
    if (r.ok) publicConfig.value = parseConfig(await r.json());
  } catch {}
}
export const feature = (k: keyof Features) => publicConfig.value.features[k];
