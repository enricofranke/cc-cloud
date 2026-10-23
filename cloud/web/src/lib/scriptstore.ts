// Script Store, dashboard side (docs/SPEC.md "Script Store – dashboard side"):
// the static index, SHA-256-verified file downloads, install records and the
// /store#install=<id>@<version> link.

export interface StoreFile {
  path: string;
  size: number;
  sha256: string;
}
export interface StoreScript {
  id: string;
  version: string;
  name: { en: string; de: string };
  description: { en: string; de: string };
  author: { name: string; github?: string; url?: string };
  license: string;
  tags: string[];
  uses: string[];
  requires: { cccloud?: string; computer?: string; peripherals?: string[]; mods?: string[] };
  entry: string;
  files: StoreFile[];
  sha256: string;
  size: number;
  lines: number;
  reviewed: boolean;
  updated?: string;
}
export interface StoreIndex {
  name: string;
  scripts: StoreScript[];
  tags: string[];
}

const ID = /^[a-z0-9][a-z0-9-]{1,38}[a-z0-9]$/;
const VER = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(-[0-9A-Za-z.-]+)?$/;
const PATH = /^[A-Za-z0-9_.-]{1,64}(\/[A-Za-z0-9_.-]{1,64})?$/;
const HEX = /^[0-9a-f]{64}$/;
const s = (v: unknown, max: number) => (typeof v === "string" ? v.slice(0, max) : "");

/** Validates the untrusted index; malformed scripts are dropped. */
export function parseIndex(raw: unknown): StoreIndex {
  const r = raw as Record<string, any>;
  if (!r || r.format !== 1 || !Array.isArray(r.scripts)) throw new Error("bad_index");
  const scripts: StoreScript[] = [];
  for (const x of r.scripts.slice(0, 2000)) {
    if (!x || !ID.test(x.id) || !VER.test(x.version) || !Array.isArray(x.files) || !x.files.length) continue;
    const files = x.files
      .filter((f: any) => f && PATH.test(f.path) && !/^(rom|disk\d*|\.cloud)(\/|$)/.test(f.path) && HEX.test(f.sha256) && Number.isInteger(f.size) && f.size >= 0 && f.size <= 256 * 1024)
      .slice(0, 32);
    if (files.length !== x.files.length) continue;
    scripts.push({
      id: x.id,
      version: x.version,
      name: { en: s(x.name?.en, 60) || x.id, de: s(x.name?.de, 60) || s(x.name?.en, 60) || x.id },
      description: { en: s(x.description?.en, 400), de: s(x.description?.de, 400) || s(x.description?.en, 400) },
      author: { name: s(x.author?.name, 60) || "?", ...(s(x.author?.github, 40) ? { github: s(x.author.github, 40) } : {}), ...(/^https:\/\//.test(s(x.author?.url, 200)) ? { url: s(x.author.url, 200) } : {}) },
      license: s(x.license, 64),
      tags: Array.isArray(x.tags) ? x.tags.filter((t: unknown) => typeof t === "string").slice(0, 6) : [],
      uses: Array.isArray(x.uses) ? x.uses.filter((t: unknown) => typeof t === "string").slice(0, 8) : [],
      requires: x.requires && typeof x.requires === "object" ? x.requires : {},
      entry: PATH.test(x.entry) ? x.entry : files[0].path,
      files,
      sha256: HEX.test(x.sha256) ? x.sha256 : "",
      size: Number(x.size) || 0,
      lines: Number(x.lines) || 0,
      reviewed: x.reviewed === true,
      ...(typeof x.updated === "string" ? { updated: x.updated.slice(0, 32) } : {}),
    });
  }
  return { name: s(r.name, 80) || "Script Store", scripts, tags: Array.isArray(r.tags) ? r.tags.filter((t: unknown) => typeof t === "string").slice(0, 40) : [] };
}

export async function fetchIndex(url: string): Promise<StoreIndex> {
  let res: Response;
  try {
    res = await fetch(`${url}index.json`, { cache: "no-cache", credentials: "omit" });
  } catch {
    throw new Error("unreachable");
  }
  if (!res.ok) throw new Error(`http_${res.status}`);
  return parseIndex(await res.json());
}

/** Downloads one file and checks it against the index. Never returns unverified content. */
export async function fetchVerified(url: string, sc: StoreScript, f: StoreFile): Promise<string> {
  const res = await fetch(`${url}files/${sc.id}/${sc.version}/${f.path}`, { cache: "no-cache", credentials: "omit" });
  if (!res.ok) throw new Error(`http_${res.status}`);
  const buf = new Uint8Array(await res.arrayBuffer());
  if (buf.length > 256 * 1024) throw new Error("too_large");
  if ((await sha256Hex(buf)) !== f.sha256) throw new Error("hash_mismatch");
  return new TextDecoder().decode(buf);
}

// ----------------------------------------------------------------- sha-256
const K = new Uint32Array([
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5, 0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
  0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da, 0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
  0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
  0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3, 0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
]);
/** Plain-JS SHA-256 (crypto.subtle is missing on plain-HTTP pages). */
export function sha256Js(data: Uint8Array): string {
  const H = new Uint32Array([0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19]);
  const len = data.length;
  const padded = new Uint8Array(((len + 9 + 63) >> 6) << 6);
  padded.set(data);
  padded[len] = 0x80;
  const dv = new DataView(padded.buffer);
  dv.setUint32(padded.length - 4, (len * 8) >>> 0);
  dv.setUint32(padded.length - 8, Math.floor((len * 8) / 2 ** 32));
  const w = new Uint32Array(64);
  const rotr = (x: number, n: number) => (x >>> n) | (x << (32 - n));
  for (let o = 0; o < padded.length; o += 64) {
    for (let i = 0; i < 16; i++) w[i] = dv.getUint32(o + i * 4);
    for (let i = 16; i < 64; i++) {
      const s0 = rotr(w[i - 15], 7) ^ rotr(w[i - 15], 18) ^ (w[i - 15] >>> 3);
      const s1 = rotr(w[i - 2], 17) ^ rotr(w[i - 2], 19) ^ (w[i - 2] >>> 10);
      w[i] = (w[i - 16] + s0 + w[i - 7] + s1) >>> 0;
    }
    let [a, b, c, d, e, f, g, h] = H;
    for (let i = 0; i < 64; i++) {
      const S1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25);
      const t1 = (h + S1 + ((e & f) ^ (~e & g)) + K[i] + w[i]) >>> 0;
      const S0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22);
      const t2 = (S0 + ((a & b) ^ (a & c) ^ (b & c))) >>> 0;
      h = g;
      g = f;
      f = e;
      e = (d + t1) >>> 0;
      d = c;
      c = b;
      b = a;
      a = (t1 + t2) >>> 0;
    }
    H[0] += a;
    H[1] += b;
    H[2] += c;
    H[3] += d;
    H[4] += e;
    H[5] += f;
    H[6] += g;
    H[7] += h;
  }
  return [...H].map((x) => x.toString(16).padStart(8, "0")).join("");
}
export async function sha256Hex(data: Uint8Array): Promise<string> {
  const subtle = typeof crypto !== "undefined" ? crypto.subtle : undefined;
  if (subtle) {
    const d = await subtle.digest("SHA-256", data as Uint8Array<ArrayBuffer>);
    return [...new Uint8Array(d)].map((b) => b.toString(16).padStart(2, "0")).join("");
  }
  return sha256Js(data);
}

// ---------------------------------------------------------- install records
export type Installed = Record<string, Record<string, { id: string; version: string; sha256: string }>>;
const key = (mf: string) => `cccloud.store.${mf}`;
export function readInstalled(mf: string, store: Pick<Storage, "getItem"> | null = safeStorage()): Installed {
  try {
    const v = JSON.parse(store?.getItem(key(mf)) ?? "{}");
    return v && typeof v === "object" ? v : {};
  } catch {
    return {};
  }
}
export function recordInstall(mf: string, uplinkId: string, sc: StoreScript, store: Pick<Storage, "getItem" | "setItem"> | null = safeStorage()) {
  const all = readInstalled(mf, store);
  all[uplinkId] = { ...(all[uplinkId] ?? {}) };
  for (const f of sc.files) all[uplinkId][f.path] = { id: sc.id, version: sc.version, sha256: f.sha256 };
  try {
    store?.setItem(key(mf), JSON.stringify(all));
  } catch {}
}
function safeStorage(): Storage | null {
  try {
    return localStorage;
  } catch {
    return null;
  }
}

/** Semantic-version compare (pre-releases sort before the release). */
export function compareVersions(a: string, b: string): number {
  const pa = a.split("-");
  const pb = b.split("-");
  const na = pa[0].split(".").map(Number);
  const nb = pb[0].split(".").map(Number);
  for (let i = 0; i < 3; i++) if (na[i] !== nb[i]) return (na[i] ?? 0) - (nb[i] ?? 0);
  if (pa[1] && !pb[1]) return -1;
  if (!pa[1] && pb[1]) return 1;
  return (pa[1] ?? "").localeCompare(pb[1] ?? "");
}

/** Updates for installed store scripts: [uplinkId, path, installed, latest]. */
export function updatesFor(installed: Installed, index: StoreIndex) {
  const out: { uplinkId: string; id: string; from: string; to: StoreScript }[] = [];
  for (const [uplinkId, files] of Object.entries(installed)) {
    const seen = new Set<string>();
    for (const rec of Object.values(files)) {
      if (seen.has(rec.id)) continue;
      seen.add(rec.id);
      const latest = index.scripts.find((s2) => s2.id === rec.id);
      if (latest && compareVersions(latest.version, rec.version) > 0) out.push({ uplinkId, id: rec.id, from: rec.version, to: latest });
    }
  }
  return out;
}

/** /store#install=<id>@<version> (version optional). */
export function takeInstallLink(loc: { pathname: string; hash: string }): { want: { id: string; version?: string } | null; scrub: boolean } {
  if (!/^\/store\/?$/.test(loc.pathname)) return { want: null, scrub: false };
  const v = new URLSearchParams(loc.hash.replace(/^#/, "")).get("install") ?? "";
  const m = /^([a-z0-9][a-z0-9-]{1,38}[a-z0-9])(?:@((0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(-[0-9A-Za-z.-]+)?))?$/.exec(v);
  return { want: m ? { id: m[1], ...(m[2] ? { version: m[2] } : {}) } : null, scrub: true };
}
