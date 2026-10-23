// v0.5 F long-term statistics (docs/SPEC.md "F. Long-term statistics – implementation notes"):
// widgets with opts.history get a history sheet with a range switcher and CSV export.
import type { Widget } from "./types.ts";

export type HistoryRange = "1h" | "24h" | "7d" | "30d";
export const RANGES: HistoryRange[] = ["1h", "24h", "7d", "30d"];
export const RANGE_LABEL: Record<HistoryRange, string> = { "1h": "1 hour", "24h": "24 hours", "7d": "7 days", "30d": "30 days" };
const HOURS: Record<HistoryRange, number> = { "1h": 1, "24h": 24, "7d": 168, "30d": 720 };
const NUMERIC = new Set(["gauge", "progress", "chart", "slider", "number", "indicator"]);

export interface HistoryDoc {
  key: string;
  range: HistoryRange;
  kept: HistoryRange;
  retentionHours: number;
  step: number;
  points: [number, number | null][];
}

const isRange = (v: unknown): v is HistoryRange => typeof v === "string" && (RANGES as string[]).includes(v);

/** The range a widget records (opts.history), or null. */
export function historyOf(w: Widget): HistoryRange | null {
  const h = w.opts?.history;
  return NUMERIC.has(w.type) && isRange(h) ? h : null;
}

/** Ranges worth offering for a widget that records `max`. */
export const rangesUpTo = (max: HistoryRange) => RANGES.filter((r) => HOURS[r] <= HOURS[max]);

export function parseHistory(raw: unknown): HistoryDoc {
  const r = raw as Record<string, any>;
  if (!r || !Array.isArray(r.points) || !isRange(r.range)) throw new Error("bad_history");
  const points: [number, number | null][] = [];
  for (const p of r.points.slice(0, 5000)) {
    if (!Array.isArray(p) || typeof p[0] !== "number" || !Number.isFinite(p[0])) continue;
    points.push([p[0], typeof p[1] === "number" && Number.isFinite(p[1]) ? p[1] : null]);
  }
  points.sort((a, b) => a[0] - b[0]);
  return {
    key: typeof r.key === "string" ? r.key.slice(0, 160) : "",
    range: r.range,
    kept: isRange(r.kept) ? r.kept : r.range,
    retentionHours: typeof r.retentionHours === "number" ? r.retentionHours : 1,
    step: typeof r.step === "number" && r.step > 0 ? r.step : 60_000,
    points,
  };
}

export function historyPath(mf: string, range: HistoryRange, fmt: "json" | "csv", kind: "w" | "i", uplinkId: string, key: string) {
  return `/api/mainframes/${encodeURIComponent(mf)}/history/${range}/${fmt}/${kind}/${encodeURIComponent(uplinkId)}/${encodeURIComponent(key)}`;
}

async function get(base: string, token: string, path: string) {
  let res: Response;
  try {
    res = await fetch(`${base}${path}`, { headers: { authorization: `Bearer ${token}` }, cache: "no-store" });
  } catch {
    throw new Error("unreachable");
  }
  if (!res.ok) {
    let code = `http_${res.status}`;
    try {
      code = (await res.json()).error ?? code;
    } catch {}
    throw new Error(code);
  }
  return res;
}

export async function fetchHistory(base: string, token: string, mf: string, range: HistoryRange, kind: "w" | "i", uplinkId: string, key: string) {
  return parseHistory(await (await get(base, token, historyPath(mf, range, "json", kind, uplinkId, key))).json());
}
export async function fetchHistoryCsv(base: string, token: string, mf: string, range: HistoryRange, kind: "w" | "i", uplinkId: string, key: string) {
  return (await get(base, token, historyPath(mf, range, "csv", kind, uplinkId, key))).text();
}

/** min / max / average of the values (gaps ignored), null without data. */
export function stats(points: [number, number | null][]) {
  const v = points.map((p) => p[1]).filter((x): x is number => x !== null);
  if (!v.length) return null;
  return { min: Math.min(...v), max: Math.max(...v), avg: v.reduce((s, x) => s + x, 0) / v.length, last: v[v.length - 1], count: v.length };
}

/** SVG path for the series with gaps as breaks ("M … L … M …"); x/y in 0..w / 0..h. */
export function seriesPath(points: [number, number | null][], t0: number, t1: number, lo: number, hi: number, w: number, h: number) {
  const span = Math.max(1, t1 - t0);
  const range = hi > lo ? hi - lo : 1;
  let d = "";
  let pen = false;
  for (const [t, v] of points) {
    if (v === null) {
      pen = false;
      continue;
    }
    const x = ((t - t0) / span) * w;
    const y = h - ((Math.min(hi, Math.max(lo, v)) - lo) / range) * h;
    d += `${pen ? "L" : "M"}${x.toFixed(1)},${y.toFixed(1)}`;
    pen = true;
  }
  return d;
}

/** Axis label for a time at the given range: clock time for short ranges, day + time for long ones. */
export function timeLabel(t: number, range: HistoryRange) {
  const d = new Date(t);
  const hm = d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  return range === "1h" || range === "24h" ? hm : `${d.toLocaleDateString([], { day: "numeric", month: "short" })} ${hm}`;
}

/** A safe file name for the CSV download. */
export const csvName = (title: string, widget: string, range: HistoryRange) => `${`${title}-${widget}`.replace(/[^A-Za-z0-9_-]+/g, "_").slice(0, 60)}-${range}.csv`;
