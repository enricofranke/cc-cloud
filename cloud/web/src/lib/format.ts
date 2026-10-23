/** Forge Energy: 40 FE, 182.4k FE, 1.2M FE (`perTick` adds /t). */
export function fmtFE(n: number, perTick = false) {
  const unit = perTick ? "FE/t" : "FE";
  if (!Number.isFinite(n)) return `— ${unit}`;
  const abs = Math.abs(n);
  const short = (x: number) => (x >= 100 ? String(Math.round(x)) : String(Math.round(x * 10) / 10));
  if (abs >= 1e9) return `${short(n / 1e9)}G ${unit}`;
  if (abs >= 1e6) return `${short(n / 1e6)}M ${unit}`;
  if (abs >= 1e3) return `${short(n / 1e3)}k ${unit}`;
  return `${Math.round(n)} ${unit}`;
}

/** Item counts: 1,234 below 100k, then compact (312K, 14.3M, 1.2B). */
export function fmtCount(n: number) {
  if (!Number.isFinite(n)) return "—";
  const abs = Math.abs(n);
  if (abs < 100_000) return Math.round(n).toLocaleString("en-US");
  for (const [v, u] of [[1e12, "T"], [1e9, "B"], [1e6, "M"], [1e3, "K"]] as const)
    if (abs >= v) {
      const x = n / v;
      return `${x >= 100 ? Math.round(x) : Math.round(x * 10) / 10}${u}`;
    }
  return String(n);
}

/** Button labels longer than this get tighter type (and may wrap to two lines) instead of being cut off. */
export const LONG_LABEL = 11;
export const labelFit = (label: string) => (label.length > LONG_LABEL ? "long" : "");

/**
 * Splits multi-entry label values like "overworld 12.4 ms · the_nether 3.1 ms"
 * into name/value pairs (separators: " · ", ";", newlines). Entries with a
 * tick time in ms also get the TPS that tick time allows (max 20).
 */
export function splitEntries(text: string): { name: string; value: string; tps?: string }[] {
  const parts = text.split(/\s+·\s+|\s*;\s*|\n+/).map((p) => p.trim()).filter(Boolean);
  if (parts.length < 2) return [];
  return parts.map((p) => {
    const m = /^([^:]*\S):\s*(.+)$/.exec(p) ?? /^(.*\S)\s+(-?[\d.,]+\s*[a-zA-Z%/]*)$/.exec(p);
    if (!m) return { name: p, value: "" };
    const ms = /^([\d.]+)\s*ms$/.exec(m[2]);
    return { name: m[1], value: m[2], ...(ms ? { tps: Math.min(20, 1000 / Math.max(0.001, Number(ms[1]))).toFixed(1) } : {}) };
  });
}
