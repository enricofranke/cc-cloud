// DEV-ONLY production statistics for the mock (docs/SPEC.md "Production statistics (mod side)"): the
// tracker mirrors the mod's RateRing/ProductionTracker – per-item deltas between two storage snapshots,
// a ring of `window` samples per item and for the storage totals – plus energy rings. Rates are per
// minute; series values are the per-minute rate of each sample (= the raw delta at 60 s samples).

export interface Snapshot {
  get(name: string): { count: number; displayName: string } | undefined;
  entries(): Iterable<[string, { count: number; displayName: string }]>;
}

const push = (ring: number[], v: number, cap: number) => {
  ring.push(v);
  if (ring.length > cap) ring.splice(0, ring.length - cap);
};
const sum = (a: number[]) => a.reduce((s, x) => s + x, 0);
const round = (v: number) => Math.round(v * 10) / 10;

/** Small seeded PRNG so the demo numbers look the same on every run. */
export function rng(seed: number) {
  return () => {
    seed = (seed * 1664525 + 1013904223) >>> 0;
    return seed / 2 ** 32;
  };
}

export class StorageTracker {
  /** item → ring of raw deltas per sample (newest last) */
  readonly items = new Map<string, number[]>();
  readonly names = new Map<string, string>();
  readonly gained: number[] = [];
  readonly consumed: number[] = [];
  readonly crafted: number[] = [];
  private prev: Map<string, number> | null = null;
  readonly window: number;
  readonly interval: number;
  readonly maxItems: number;
  constructor(window: number, interval: number, maxItems = 256) {
    this.window = window;
    this.interval = interval;
    this.maxItems = maxItems;
  }

  get samples() {
    return this.gained.length;
  }
  /** per-minute factor of one sample */
  private get perMin() {
    return 60 / this.interval;
  }

  /** One sample: deltas against the previous snapshot (the first sample only records). */
  sample(snap: Snapshot, craftedItems = 0) {
    const now = new Map<string, number>();
    for (const [name, it] of snap.entries()) {
      now.set(name, it.count);
      this.names.set(name, it.displayName);
    }
    if (!this.prev) {
      this.prev = now;
      return;
    }
    let gained = 0;
    let consumed = 0;
    const seen = new Set<string>();
    for (const [name, count] of now) {
      const d = count - (this.prev.get(name) ?? 0);
      seen.add(name);
      this.record(name, d);
      if (d > 0) gained += d;
      else consumed -= d;
    }
    for (const [name, count] of this.prev)
      if (!seen.has(name) && count > 0) {
        this.record(name, -count);
        consumed += count;
      }
    this.prev = now;
    push(this.gained, gained, this.window);
    push(this.consumed, consumed, this.window);
    push(this.crafted, craftedItems, this.window);
    // the busiest items win, idle ones are dropped
    if (this.items.size > this.maxItems) {
      const keep = [...this.items].sort((a, b) => sum(b[1].map(Math.abs)) - sum(a[1].map(Math.abs))).slice(0, this.maxItems);
      this.items.clear();
      for (const [k, v] of keep) this.items.set(k, v);
    }
  }
  private record(name: string, d: number) {
    let ring = this.items.get(name);
    if (!ring) {
      if (d === 0) return;
      ring = Array(Math.max(0, this.samples)).fill(0);
      this.items.set(name, ring);
    }
    push(ring, d, this.window);
  }

  /** Seeds the rings with a plausible history (demo: the page shows numbers right away). */
  seed(rates: Record<string, { gain: number; use: number; name: string }>, samples: number, seed = 11) {
    const r = rng(seed);
    const per = this.interval / 60;
    for (let i = 0; i < samples; i++) {
      let g = 0;
      let c = 0;
      const wave = 1 + 0.25 * Math.sin(i / 7);
      for (const [name, x] of Object.entries(rates)) {
        const gain = Math.max(0, Math.round(x.gain * per * wave * (0.75 + r() * 0.5)));
        const use = Math.max(0, Math.round(x.use * per * (0.7 + r() * 0.6)));
        const ring = this.items.get(name) ?? [];
        ring.push(gain - use);
        this.items.set(name, ring);
        this.names.set(name, x.name);
        // a sample only sees the net change of an item
        if (gain > use) g += gain - use;
        else c += use - gain;
      }
      this.gained.push(g);
      this.consumed.push(c);
      this.crafted.push(i % 9 === 4 ? 64 : i % 13 === 7 ? 512 : 0);
    }
  }

  /** Per-minute rate of a ring (averaged over the samples in the window). */
  private rate(ring: number[]) {
    return ring.length ? (sum(ring) / ring.length) * this.perMin : 0;
  }

  overview() {
    const movers = [...this.items].map(([name, ring]) => ({
      name,
      displayName: this.names.get(name) ?? name,
      gain: this.rate(ring.map((d) => Math.max(0, d))),
      use: this.rate(ring.map((d) => Math.max(0, -d))),
    }));
    const top = (k: "gain" | "use") =>
      movers
        .filter((m) => m[k] > 0)
        .sort((a, b) => b[k] - a[k])
        .slice(0, 10)
        .map((m) => ({ name: m.name, displayName: m.displayName, rate: round(m[k]) }));
    const gained = this.rate(this.gained);
    const consumed = this.rate(this.consumed);
    return {
      samples: this.samples,
      gained: round(gained),
      consumed: round(consumed),
      net: round(gained - consumed),
      crafted: round(this.rate(this.crafted)),
      series: {
        gained: this.gained.map((v) => round(v * this.perMin)),
        consumed: this.consumed.map((v) => round(v * this.perMin)),
        crafted: this.crafted.map((v) => round(v * this.perMin)),
      },
      producers: top("gain"),
      consumers: top("use"),
    };
  }

  /** The drill-down of one item: zeros when it did not move in the window. */
  item(name: string) {
    const ring = this.items.get(name) ?? [];
    const gained = this.rate(ring.map((d) => Math.max(0, d)));
    const consumed = this.rate(ring.map((d) => Math.max(0, -d)));
    return {
      gained: round(gained),
      consumed: round(consumed),
      net: round(gained - consumed),
      series: {
        net: ring.map((d) => round(d * this.perMin)),
        gained: ring.map((d) => round(Math.max(0, d) * this.perMin)),
        consumed: ring.map((d) => round(Math.max(0, -d) * this.perMin)),
      },
    };
  }

  /** Net per minute of the last sample (the value a pinned chart gets). */
  lastNet(name?: string) {
    if (name) return round((this.items.get(name)?.at(-1) ?? 0) * this.perMin);
    return round(((this.gained.at(-1) ?? 0) - (this.consumed.at(-1) ?? 0)) * this.perMin);
  }
  netSeries(name?: string) {
    if (name) return (this.items.get(name) ?? []).map((d) => round(d * this.perMin));
    return this.gained.map((g, i) => round((g - (this.consumed[i] ?? 0)) * this.perMin));
  }
}

export interface EnergyCardSim {
  uplinkId: string;
  title: string;
  stored: number;
  capacity: number;
  in: number;
  out: number;
  measured: boolean;
}

/** Energy cards with in/out rates (FE/t) and rings of the totals. */
export class EnergyTracker {
  readonly in: number[] = [];
  readonly out: number[] = [];
  private r = rng(5);
  readonly cards: EnergyCardSim[];
  readonly window: number;
  constructor(cards: EnergyCardSim[], window: number) {
    this.cards = cards;
    this.window = window;
  }
  /** Moves the cards a little (a day/night solar curve on the matrix, noise elsewhere) and records the totals. */
  sample(step = this.in.length) {
    for (const c of this.cards) {
      const day = 0.5 + 0.5 * Math.sin(step / 9);
      if (c.measured) {
        const flow = Math.round((this.r() - 0.45) * 900);
        c.in = Math.max(0, flow);
        c.out = Math.max(0, -flow);
      } else {
        c.in = Math.round(c.in * 0.6 + (c.title.includes("Matrix") ? 30_000 + 26_000 * day : 12_000) * 0.4 + (this.r() - 0.5) * 1_500);
        c.out = Math.round(c.out * 0.6 + (c.title.includes("Matrix") ? 38_000 : 4_000) * 0.4 + (this.r() - 0.5) * 2_000);
      }
      c.stored = Math.max(0, Math.min(c.capacity, c.stored + (c.in - c.out) * 20 * 20));
    }
    push(this.in, this.totals().in, this.window);
    push(this.out, this.totals().out, this.window);
  }
  totals() {
    const s = (k: "stored" | "capacity" | "in" | "out") => this.cards.reduce((a, c) => a + c[k], 0);
    return { stored: s("stored"), capacity: s("capacity"), in: s("in"), out: s("out") };
  }
  doc(visible: (uplinkId: string) => boolean) {
    const cards = this.cards.filter((c) => visible(c.uplinkId));
    if (!cards.length) return null;
    const s = (k: "stored" | "capacity" | "in" | "out") => cards.reduce((a, c) => a + c[k], 0);
    return {
      cards: cards.map((c) => ({ ...c })),
      stored: s("stored"),
      capacity: s("capacity"),
      in: s("in"),
      out: s("out"),
      series: { in: [...this.in], out: [...this.out] },
    };
  }
}

/** 24 h of 5-minute averages ending at `count` (watched and favourite items). */
export function dayHistory(count: number, seed: number) {
  const r = rng(seed);
  const out: number[] = [];
  let v = count;
  for (let i = 0; i < 288; i++) {
    out.unshift(Math.max(0, Math.round(v)));
    v = v * (1 + (r() - 0.47) * 0.012) + Math.sin(i / 20) * count * 0.004;
  }
  out[out.length - 1] = count;
  return out;
}
