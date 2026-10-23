// Lazy chunk: the long-term history of one widget with a range switcher and CSV export.
import { useEffect, useMemo, useState } from "preact/hooks";
import type { MainframeConn } from "../lib/conn.ts";
import type { Uplink, Widget } from "../lib/types.ts";
import { RANGE_LABEL, csvName, fetchHistory, fetchHistoryCsv, historyOf, rangesUpTo, seriesPath, stats, timeLabel, type HistoryDoc, type HistoryRange } from "../lib/history.ts";
import { Sheet } from "./Overlays.tsx";
import { DocsLink } from "./DocsLink.tsx";
import { pushToast } from "../lib/ui.ts";

const ERR: Record<string, string> = {
  no_history: "Nothing recorded yet – the server starts recording when the widget appears.",
  not_allowed: "You may not see this history.",
  unreachable: "The server cannot be reached.",
};
const fmt = (v: number) => (Math.abs(v) >= 10_000 ? Math.round(v).toLocaleString("en-US") : Number.isInteger(v) ? String(v) : v.toFixed(Math.abs(v) < 10 ? 2 : 1));

export default function HistorySheet({ conn, uplink, w, onClose }: { conn: MainframeConn; uplink: Uplink; w: Widget; onClose: () => void }) {
  const max = historyOf(w) ?? "1h";
  const ranges = rangesUpTo(max);
  const [range, setRange] = useState<HistoryRange>(ranges.includes("24h") ? "24h" : max);
  const [doc, setDoc] = useState<HistoryDoc | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [hover, setHover] = useState<number | null>(null);
  const mf = conn.info.value?.id ?? "";
  useEffect(() => {
    let stop = false;
    setError(null);
    fetchHistory(conn.baseUrl, conn.token, mf, range, "w", uplink.uplinkId, w.id)
      .then((d) => !stop && setDoc(d))
      .catch((e) => !stop && (setDoc(null), setError((e as Error).message)));
    return () => {
      stop = true;
    };
  }, [range, mf, uplink.uplinkId, w.id]);
  const unit = typeof w.opts?.unit === "string" ? w.opts.unit : "";
  const s = doc ? stats(doc.points) : null;
  const W = 600;
  const H = 180;
  const view = useMemo(() => {
    if (!doc || !doc.points.length || !s) return null;
    const lo = typeof w.opts?.min === "number" ? w.opts.min : s.min;
    const hiRaw = typeof w.opts?.max === "number" ? w.opts.max : s.max;
    const hi = hiRaw > lo ? hiRaw : lo + 1;
    const t0 = doc.points[0][0];
    const t1 = doc.points[doc.points.length - 1][0];
    return { lo, hi, t0, t1, d: seriesPath(doc.points, t0, t1, lo, hi, W, H) };
  }, [doc]);
  const hp = hover !== null && doc ? doc.points[hover] : null;
  const exportCsv = async () => {
    try {
      const csv = await fetchHistoryCsv(conn.baseUrl, conn.token, mf, range, "w", uplink.uplinkId, w.id);
      const a = document.createElement("a");
      a.href = URL.createObjectURL(new Blob([csv], { type: "text/csv" }));
      a.download = csvName(uplink.title || "card", w.label ?? w.id, range);
      a.click();
      setTimeout(() => URL.revokeObjectURL(a.href), 2000);
    } catch (e) {
      pushToast({ level: "warn", title: "CSV export", text: ERR[(e as Error).message] ?? (e as Error).message });
    }
  };
  return (
    <Sheet title={`${w.label ?? w.id} – history`} onClose={onClose} wide>
      <div class="hist">
        <div class="hist-bar">
          <div class="seg-ctl seg-s" role="radiogroup" aria-label="Range">
            {ranges.map((r) => (
              <button key={r} type="button" role="radio" aria-checked={range === r} class={range === r ? "on" : ""} onClick={() => setRange(r)}>
                {r}
              </button>
            ))}
          </div>
          {conn.can("act.buttons") && (
            <button type="button" class="btn btn-small" onClick={exportCsv} disabled={!doc}>
              Export CSV
            </button>
          )}
        </div>
        {doc && doc.kept !== range && (
          <p class="muted small">
            This mainframe keeps {RANGE_LABEL[doc.kept]} of history – a Memory upgrade card keeps more. <DocsLink to="/docs/upgrades#upgrades">Upgrades</DocsLink>
          </p>
        )}
        {error && <p class="form-error">{ERR[error] ?? error}</p>}
        {!doc && !error && <div class="hist-chart skeleton" />}
        {doc && view && (
          <>
            <div class="hist-chart screen">
              <svg
                viewBox={`0 0 ${W} ${H}`}
                preserveAspectRatio="none"
                class="hist-svg"
                role="img"
                aria-label={`${w.label ?? w.id} over ${RANGE_LABEL[doc.kept]}: min ${fmt(s!.min)}, max ${fmt(s!.max)}, average ${fmt(s!.avg)}${unit ? ` ${unit}` : ""}`}
                onPointerMove={(e) => {
                  const r = (e.currentTarget as SVGSVGElement).getBoundingClientRect();
                  const t = view.t0 + ((e.clientX - r.left) / r.width) * (view.t1 - view.t0);
                  let best = 0;
                  doc.points.forEach((p, i) => Math.abs(p[0] - t) < Math.abs(doc.points[best][0] - t) && (best = i));
                  setHover(best);
                }}
                onPointerLeave={() => setHover(null)}
              >
                {[0.25, 0.5, 0.75].map((f) => (
                  <line key={f} x1="0" x2={W} y1={H * f} y2={H * f} class="chart-grid" vector-effect="non-scaling-stroke" />
                ))}
                <path d={view.d} class="hist-line" vector-effect="non-scaling-stroke" />
                {hp && <line x1={((hp[0] - view.t0) / Math.max(1, view.t1 - view.t0)) * W} x2={((hp[0] - view.t0) / Math.max(1, view.t1 - view.t0)) * W} y1="0" y2={H} class="hist-cursor" vector-effect="non-scaling-stroke" />}
              </svg>
              <div class="chart-scale" aria-hidden="true">
                <span>{fmt(view.hi)}</span>
                <span>{fmt(view.lo)}</span>
              </div>
            </div>
            <div class="hist-axis small muted" aria-hidden="true">
              <span>{timeLabel(view.t0, doc.kept)}</span>
              <span>{hp ? `${timeLabel(hp[0], doc.kept)} · ${hp[1] === null ? "no data" : `${fmt(hp[1])} ${unit}`}` : `one point per ${doc.step >= 1_800_000 ? "30 min" : doc.step >= 300_000 ? "5 min" : "minute"}`}</span>
              <span>now</span>
            </div>
            {s && (
              <dl class="hist-stats">
                <div>
                  <dt>Min</dt>
                  <dd class="mono">
                    {fmt(s.min)} <small>{unit}</small>
                  </dd>
                </div>
                <div>
                  <dt>Average</dt>
                  <dd class="mono">
                    {fmt(s.avg)} <small>{unit}</small>
                  </dd>
                </div>
                <div>
                  <dt>Max</dt>
                  <dd class="mono">
                    {fmt(s.max)} <small>{unit}</small>
                  </dd>
                </div>
                <div>
                  <dt>Latest</dt>
                  <dd class="mono">
                    {fmt(s.last)} <small>{unit}</small>
                  </dd>
                </div>
              </dl>
            )}
            {doc.points.some((p) => p[1] === null) && <p class="muted small">Gaps: the mainframe or the chunk was not loaded.</p>}
          </>
        )}
        {doc && !view && <p class="muted">No data in this range yet.</p>}
      </div>
    </Sheet>
  );
}
