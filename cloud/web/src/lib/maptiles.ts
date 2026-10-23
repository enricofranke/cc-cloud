// Map tiles for the 2D network map (docs/SPEC.md "Map tiles – HTTP API"):
// 128×128 px PNGs at 1 block per pixel, Surface or a Slice at a Y. Fetched with
// the Bearer token (never in a URL), decoded to ImageBitmaps and drawn on a
// canvas behind the SVG – no blob:/data: images needed, so the strict CSP stays.

export const TILE = 128;
export type Layer = "surface" | "slice";

export interface TileRef {
  tx: number;
  tz: number;
}

/** Tiles that intersect the view and lie within 512 blocks of a node (the server refuses the rest). */
export function tilesFor(view: { x: number; y: number; w: number; h: number }, nodes: { x: number; z: number }[], max = 64): TileRef[] {
  const out: (TileRef & { d: number })[] = [];
  const x0 = Math.floor(view.x / TILE);
  const x1 = Math.floor((view.x + view.w) / TILE);
  const z0 = Math.floor(view.y / TILE);
  const z1 = Math.floor((view.y + view.h) / TILE);
  if ((x1 - x0 + 1) * (z1 - z0 + 1) > 4000) return [];
  const cx = view.x + view.w / 2;
  const cz = view.y + view.h / 2;
  for (let tx = x0; tx <= x1; tx++)
    for (let tz = z0; tz <= z1; tz++) {
      const near = nodes.some((n) => Math.hypot(Math.max(tx * TILE - n.x, 0, n.x - (tx * TILE + TILE - 1)), Math.max(tz * TILE - n.z, 0, n.z - (tz * TILE + TILE - 1))) <= 512);
      if (near) out.push({ tx, tz, d: Math.hypot(tx * TILE + 64 - cx, tz * TILE + 64 - cz) });
    }
  // nearest to the centre of the view first
  return out.sort((a, b) => a.d - b.d).slice(0, max).map(({ tx, tz }) => ({ tx, tz }));
}

/** Median Y of the nodes – the default height of the slice. */
export function medianY(ys: number[]): number {
  if (!ys.length) return 64;
  const s = [...ys].sort((a, b) => a - b);
  return Math.round(s[Math.floor(s.length / 2)]);
}

export const tileKey = (dim: string, layer: Layer, y: number, t: TileRef) => `${dim}|${layer}|${layer === "slice" ? y : ""}|${t.tx}|${t.tz}`;
export const tilePath = (mf: string, dim: string, layer: Layer, y: number, t: TileRef) =>
  `/api/mainframes/${encodeURIComponent(mf)}/map/${dim}/${layer === "slice" ? `slice/${Math.round(y)}` : "surface"}/${t.tx}/${t.tz}.png`;

type Entry = ImageBitmap | "empty" | "error" | "loading";

/**
 * Loads tiles with a small queue: ≤ 3 at once, ≤ 55 per minute (the server
 * allows 60), backs off on 429, remembers empty (204) and refused tiles.
 */
export class TileStore {
  private cache = new Map<string, Entry>();
  private queue: { key: string; path: string }[] = [];
  private running = 0;
  private stamps: number[] = [];
  private pausedUntil = 0;
  private timer = 0;
  private base: string;
  private token: string;
  onLoad: () => void = () => {};
  /** set when the server has no tile API (older mod) or refuses this browser */
  unavailable = false;
  constructor(base: string, token: string) {
    this.base = base.replace(/\/$/, "");
    this.token = token;
  }
  get(key: string): Entry | undefined {
    return this.cache.get(key);
  }
  /** Asks for these tiles (in priority order); drops queued tiles that are no longer wanted. */
  want(items: { key: string; path: string }[]) {
    if (this.unavailable) return;
    const keep = new Set(items.map((i) => i.key));
    this.queue = this.queue.filter((q) => keep.has(q.key));
    for (const i of items) if (!this.cache.has(i.key) && !this.queue.some((q) => q.key === i.key)) this.queue.push(i);
    this.pump();
  }
  private pump() {
    clearTimeout(this.timer);
    const now = Date.now();
    this.stamps = this.stamps.filter((t) => now - t < 60_000);
    while (this.running < 3 && this.queue.length && now >= this.pausedUntil && this.stamps.length < 55) {
      const job = this.queue.shift()!;
      this.stamps.push(now);
      this.running++;
      this.cache.set(job.key, "loading");
      this.load(job).finally(() => {
        this.running--;
        this.pump();
      });
    }
    if (this.queue.length && !this.unavailable) {
      const wait = Math.max(this.pausedUntil - now, this.stamps.length >= 55 ? 60_000 - (now - this.stamps[0]) : 0, 50);
      this.timer = window.setTimeout(() => this.pump(), wait);
    }
  }
  private async load(job: { key: string; path: string }) {
    try {
      const res = await fetch(this.base + job.path, { headers: { authorization: `Bearer ${this.token}` } });
      if (res.status === 200) {
        const bmp = await createImageBitmap(await res.blob());
        this.cache.set(job.key, bmp);
        this.onLoad();
        return;
      }
      if (res.status === 429) {
        this.cache.delete(job.key);
        this.queue.unshift(job);
        const ra = Number((await res.json().catch(() => ({}))).retryAfter) || 10;
        this.pausedUntil = Date.now() + ra * 1000;
        return;
      }
      if (res.status === 204) this.cache.set(job.key, "empty");
      else {
        this.cache.set(job.key, "error");
        if (res.status === 404 || res.status === 401) this.unavailable = res.status === 401 || (await res.json().catch(() => ({}))).error !== "unknown_dimension";
      }
    } catch {
      this.cache.delete(job.key);
    }
  }
  dispose() {
    clearTimeout(this.timer);
    this.queue = [];
    for (const v of this.cache.values()) if (typeof v === "object") v.close();
    this.cache.clear();
  }
}
