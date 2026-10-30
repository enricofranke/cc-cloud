// v0.10 production statistics (docs/SPEC.md "Production statistics (mod side)"): what the storages gain,
// consume and craft per minute, energy in/out, the per-item drill-down and pinned rate charts.
// Rates arrive per minute; the dashboard shows them per minute or per hour.
import type { Lang } from "./lang.ts";
import type { JobInfo } from "./conn.ts";

export interface Mover {
  name: string;
  displayName: string;
  /** items per minute */
  rate: number;
}
export interface StorageStats {
  uplinkId: string;
  title: string;
  bridge: "me" | "rs";
  online: boolean;
  samples: number;
  /** per minute */
  gained: number;
  consumed: number;
  net: number;
  crafted: number;
  /** one value per sample (per minute), oldest first */
  series: { gained: number[]; consumed: number[]; crafted: number[] };
  producers: Mover[];
  consumers: Mover[];
}
export interface EnergyCard {
  uplinkId: string;
  title: string;
  stored: number;
  capacity: number;
  /** FE/t */
  in: number;
  out: number;
  /** no rates from the mod: the measured net flow */
  measured: boolean;
}
export interface EnergyStats {
  cards: EnergyCard[];
  stored: number;
  capacity: number;
  in: number;
  out: number;
  series: { in: number[]; out: number[] };
}
export type PinKind = "item" | "storage" | "energy";
export interface Pin {
  id: string;
  kind: PinKind;
  uplinkId?: string;
  name?: string;
  label: string;
}
export interface Overview {
  /** seconds between samples */
  interval: number;
  /** samples in the window */
  window: number;
  /** when sampling started (null = no samples yet) */
  since: number | null;
  storages: StorageStats[];
  energy: EnergyStats | null;
  pins: Pin[];
  pinLimit: number;
}
export interface Task {
  name: string;
  displayName: string;
  count: number;
  progress: number;
}
export interface ItemStats {
  name: string;
  displayName: string;
  count: number;
  gained: number;
  consumed: number;
  net: number;
  crafted: number;
  series: { net: number[]; gained: number[]; consumed: number[] };
  jobs: JobInfo[];
  tasks: Task[];
  /** 24 h of counts as 5-minute averages (watched/favourite items only), null entries = gaps */
  history: (number | null)[] | null;
}

const fin = (v: unknown, d = 0) => (typeof v === "number" && Number.isFinite(v) ? v : d);
const str = (v: unknown, max: number) => (typeof v === "string" ? v.slice(0, max) : "");
const nums = (v: unknown, cap = 240) => (Array.isArray(v) ? v.slice(-cap).map((x) => fin(x)) : []);
const ITEM = /^[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,128}$/;

function movers(v: unknown): Mover[] {
  return (Array.isArray(v) ? v : [])
    .filter((m: any) => m && typeof m.name === "string" && ITEM.test(m.name))
    .slice(0, 10)
    .map((m: any) => ({ name: m.name, displayName: str(m.displayName, 64) || m.name, rate: Math.abs(fin(m.rate)) }));
}

/** Validates the untrusted overview (`op:"overview"`). */
export function normalizeOverview(raw: any): Overview {
  const r = raw && typeof raw === "object" ? raw : {};
  const storages: StorageStats[] = (Array.isArray(r.storages) ? r.storages : [])
    .filter((s: any) => s && typeof s.uplinkId === "string")
    .slice(0, 32)
    .map((s: any) => ({
      uplinkId: s.uplinkId.slice(0, 64),
      title: str(s.title, 48) || "Storage",
      bridge: s.bridge === "rs" ? "rs" : "me",
      online: s.online !== false,
      samples: Math.max(0, fin(s.samples)),
      gained: Math.max(0, fin(s.gained)),
      consumed: Math.max(0, fin(s.consumed)),
      net: fin(s.net),
      crafted: Math.max(0, fin(s.crafted)),
      series: { gained: nums(s.series?.gained), consumed: nums(s.series?.consumed), crafted: nums(s.series?.crafted) },
      producers: movers(s.producers),
      consumers: movers(s.consumers),
    }));
  const e = r.energy && typeof r.energy === "object" && Array.isArray(r.energy.cards) ? r.energy : null;
  const energy: EnergyStats | null = e
    ? {
        cards: e.cards
          .filter((c: any) => c && typeof c.uplinkId === "string")
          .slice(0, 64)
          .map((c: any) => ({
            uplinkId: c.uplinkId.slice(0, 64),
            title: str(c.title, 48) || "Energy",
            stored: Math.max(0, fin(c.stored)),
            capacity: Math.max(0, fin(c.capacity)),
            in: Math.max(0, fin(c.in)),
            out: Math.max(0, fin(c.out)),
            measured: c.measured === true,
          })),
        stored: Math.max(0, fin(e.stored)),
        capacity: Math.max(0, fin(e.capacity)),
        in: Math.max(0, fin(e.in)),
        out: Math.max(0, fin(e.out)),
        series: { in: nums(e.series?.in), out: nums(e.series?.out) },
      }
    : null;
  const pins: Pin[] = (Array.isArray(r.pins) ? r.pins : [])
    .filter((p: any) => p && typeof p.id === "string" && ["item", "storage", "energy"].includes(p.kind))
    .slice(0, 64)
    .map((p: any) => ({
      id: p.id.slice(0, 16),
      kind: p.kind,
      ...(typeof p.uplinkId === "string" ? { uplinkId: p.uplinkId.slice(0, 64) } : {}),
      ...(typeof p.name === "string" ? { name: p.name.slice(0, 200) } : {}),
      label: str(p.label, 64) || p.kind,
    }));
  return {
    interval: Math.max(1, fin(r.interval, 60)),
    window: Math.max(1, fin(r.window, 60)),
    since: typeof r.since === "number" ? r.since : null,
    storages,
    energy,
    pins,
    pinLimit: Math.max(0, fin(r.pinLimit, 16)),
  };
}

const JOB_STATUS = ["queued", "running", "done", "failed", "cancelled"];
/** Validates the untrusted item drill-down (`op:"item"`). */
export function normalizeItemStats(raw: any, name: string): ItemStats {
  const r = raw && typeof raw === "object" ? raw : {};
  return {
    name: typeof r.name === "string" && ITEM.test(r.name) ? r.name : name,
    displayName: str(r.displayName, 64) || name,
    count: Math.max(0, fin(r.count)),
    gained: Math.max(0, fin(r.gained)),
    consumed: Math.max(0, fin(r.consumed)),
    net: fin(r.net),
    crafted: Math.max(0, fin(r.crafted)),
    series: { net: nums(r.series?.net), gained: nums(r.series?.gained), consumed: nums(r.series?.consumed) },
    jobs: (Array.isArray(r.jobs) ? r.jobs : [])
      .filter((j: any) => j && j.jobId != null && typeof j.name === "string" && JOB_STATUS.includes(j.status))
      .slice(0, 20)
      .map((j: any) => ({
        jobId: String(j.jobId).slice(0, 32),
        name: j.name,
        displayName: str(j.displayName, 64) || j.name,
        count: fin(j.count),
        status: j.status,
        progress: Math.max(0, Math.min(1, fin(j.progress))),
        ...(typeof j.error === "string" ? { error: j.error.slice(0, 120) } : {}),
        ...(typeof j.source === "string" ? { source: j.source } : {}),
      })),
    tasks: (Array.isArray(r.tasks) ? r.tasks : [])
      .filter((t: any) => t && typeof t.name === "string")
      .slice(0, 20)
      .map((t: any) => ({ name: t.name, displayName: str(t.displayName, 64) || t.name, count: fin(t.count), progress: Math.max(0, Math.min(1, fin(t.progress))) })),
    history: Array.isArray(r.history) ? r.history.slice(-288).map((x: unknown) => (typeof x === "number" && Number.isFinite(x) ? x : null)) : null,
  };
}

/** How often the page asks for a fresh overview: the sample interval, at least every 15 s. */
export const pollSeconds = (interval: number) => Math.max(15, Math.round(Number.isFinite(interval) && interval > 0 ? interval : 60));

export type RateUnit = "min" | "h";
/** A per-minute rate in the chosen unit. */
export const inUnit = (perMin: number, unit: RateUnit) => (unit === "h" ? perMin * 60 : perMin);

/** Compact numbers in the language's style: 0.4, 12, 1,234 / 1.234, 12.3k / 12,3k, 4.5M. */
export function fmtNum(n: number, lang: Lang): string {
  if (!Number.isFinite(n)) return "—";
  const abs = Math.abs(n);
  const loc = lang === "de" ? "de-DE" : "en-US";
  const dec = (x: number, d: number) => x.toLocaleString(loc, { maximumFractionDigits: d, minimumFractionDigits: 0 });
  if (abs < 10) return dec(Math.round(n * 10) / 10, 1);
  if (abs < 10_000) return Math.round(n).toLocaleString(loc);
  for (const [v, u] of [[1e9, "G"], [1e6, "M"], [1e3, "k"]] as const) if (abs >= v) return `${dec(Math.round((n / v) * 10) / 10, 1)}${u}`;
  return String(Math.round(n));
}
/** "1,234/min", "74k/h"; `signed` adds + for gains (net rates). */
export function fmtRate(perMin: number, unit: RateUnit, lang: Lang, signed = false): string {
  const v = inUnit(perMin, unit);
  const s = fmtNum(v, lang);
  return `${signed && v > 0 ? "+" : ""}${s}/${unit === "h" ? (lang === "de" ? "Std." : "h") : "min"}`;
}
/** "last 60 min" / "letzte 60 Min." for the window. */
export function windowText(window: number, interval: number, lang: Lang) {
  const mins = Math.round((window * interval) / 60);
  if (mins >= 120 && mins % 60 === 0) return lang === "de" ? `letzte ${mins / 60} Std.` : `last ${mins / 60} h`;
  return lang === "de" ? `letzte ${mins} Min.` : `last ${mins} min`;
}
/** Item rows of a pin: the item, its storage, or the energy totals. */
export const pinOf = (pins: Pin[], kind: PinKind, uplinkId?: string, name?: string) =>
  pins.find((p) => p.kind === kind && (kind === "energy" || p.uplinkId === uplinkId) && (kind !== "item" || p.name === name));
