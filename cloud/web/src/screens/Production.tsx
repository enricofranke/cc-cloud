// v0.10 production statistics: the dashboard's "Production" page (an always-present tab like Network,
// route #/m/<id>/production). Polls {"t":"production","op":"overview"} while it is visible, shows what each
// AE2/RS storage gains, consumes and crafts, energy in/out, and an item drill-down ("what is eating my iron?").
import { useEffect, useRef, useState } from "preact/hooks";
import type { ComponentChildren } from "preact";
import type { MainframeConn } from "../lib/conn.ts";
import { getPref, setPref } from "../lib/store.ts";
import { pushToast } from "../lib/ui.ts";
import { lang, locale, t, type Key } from "../lib/i18n.ts";
import { agoText } from "../lib/schedules.ts";
import { fmtFE } from "../lib/format.ts";
import {
  fmtNum,
  fmtRate,
  inUnit,
  normalizeItemStats,
  normalizeOverview,
  pinOf,
  pollSeconds,
  windowText,
  type EnergyStats,
  type ItemStats,
  type Mover,
  type Overview,
  type Pin,
  type PinKind,
  type RateUnit,
  type StorageStats,
} from "../lib/production.ts";
import { ItemIcon } from "../components/ItemIcon.tsx";
import { PixelIcon } from "../components/PixelIcon.tsx";
import { Sheet } from "../components/Overlays.tsx";
import { DocsLink } from "../components/DocsLink.tsx";

const ERR_KEYS: Record<string, Key> = {
  disabled: "pr.err.disabled",
  unknown_storage: "pr.err.unknown_storage",
  unknown_item: "pr.err.unknown_item",
  too_many: "pr.err.too_many",
  unknown_pin: "pr.err.unknown_pin",
  rate_limited: "pr.err.rate_limited",
  not_allowed: "pr.err.not_allowed",
  read_only: "pr.err.not_allowed",
  forbidden: "pr.err.not_allowed",
  disconnected: "pr.err.disconnected",
  timeout: "pr.err.timeout",
};
const errText = (e: unknown) => {
  const m = (e as Error).message;
  return ERR_KEYS[m] ? t(ERR_KEYS[m]) : m;
};

// ------------------------------------------------------------------ charts
interface Series {
  label: string;
  data: number[];
  /** colour class: s-gain, s-use, s-net, s-craft, s-in, s-out */
  cls: string;
}
/** Several series in the chart style of the dashboard (newest on the right, one scale). */
export function SeriesChart({ series, points, unit, label, height = 120 }: { series: Series[]; points: number; unit: string; label: string; height?: number }) {
  const W = 300;
  const H = height;
  const all = series.flatMap((s) => s.data);
  const lo = Math.min(0, ...all);
  const hiRaw = Math.max(0, ...all);
  const hi = hiRaw > lo ? hiRaw : lo + 1;
  const cap = Math.max(2, points);
  const step = W / (cap - 1);
  const y = (v: number) => H - 4 - ((v - lo) / (hi - lo)) * (H - 8);
  const L = lang.value;
  const line = (d: number[]) => {
    const off = (cap - Math.min(cap, d.length)) * step;
    return d.slice(-cap).map((v, i) => `${(off + i * step).toFixed(1)},${y(v).toFixed(1)}`).join(" ");
  };
  const longest = Math.max(0, ...series.map((s) => s.data.length));
  return (
    <figure class="prod-chart">
      <div class="chart-box screen" style={{ height: `${H + 8}px` }}>
        <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" class="chart-svg" role="img" aria-label={`${label}: ${series.map((s) => `${s.label} ${fmtNum(s.data.at(-1) ?? 0, L)} ${unit}`).join(", ")}`}>
          {[0.25, 0.5, 0.75].map((f) => (
            <line key={f} x1="0" x2={W} y1={H * f} y2={H * f} class="chart-grid" vector-effect="non-scaling-stroke" />
          ))}
          {lo < 0 && <line x1="0" x2={W} y1={y(0)} y2={y(0)} class="chart-zero" vector-effect="non-scaling-stroke" />}
          {series.map((s) => s.data.length > 1 && <polyline key={s.cls} points={line(s.data)} class={`chart-line ${s.cls}`} vector-effect="non-scaling-stroke" />)}
        </svg>
        <div class="chart-scale" aria-hidden="true">
          <span>{fmtNum(hi, L)}</span>
          <span>{fmtNum(lo, L)}</span>
        </div>
        {longest < 2 && <div class="chart-empty">{t("pr.waiting")}</div>}
      </div>
      <figcaption class="prod-legend">
        {series.map((s) => (
          <span key={s.cls} class={`lg ${s.cls}`}>
            {s.label}
          </span>
        ))}
        <span class="muted">{unit}</span>
      </figcaption>
    </figure>
  );
}

/** A small count history (24 h of 5-minute averages, gaps allowed). */
function HistoryLine({ data, label }: { data: (number | null)[]; label: string }) {
  const W = 300;
  const H = 56;
  const vals = data.filter((x): x is number => x !== null);
  if (vals.length < 2) return null;
  const lo = Math.min(...vals);
  const hi = Math.max(...vals) > lo ? Math.max(...vals) : lo + 1;
  let d = "";
  let pen = false;
  data.forEach((v, i) => {
    if (v === null) return void (pen = false);
    d += `${pen ? "L" : "M"}${((i / (data.length - 1)) * W).toFixed(1)},${(H - 4 - ((v - lo) / (hi - lo)) * (H - 8)).toFixed(1)}`;
    pen = true;
  });
  const L = lang.value;
  return (
    <figure class="spark" aria-label={label}>
      <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" class="spark-svg" role="img">
        <path d={d} class="spark-line" vector-effect="non-scaling-stroke" />
      </svg>
      <figcaption class="spark-cap">
        <span>{t("pr.h24ago", { n: fmtNum(vals[0], L) })}</span>
        <span>{t("pr.lowHigh", { lo: fmtNum(lo, L), hi: fmtNum(Math.max(...vals), L) })}</span>
        <span>{t("pr.now", { n: fmtNum(vals[vals.length - 1], L) })}</span>
      </figcaption>
    </figure>
  );
}

function Stat({ label, value, cls = "" }: { label: string; value: string; cls?: string }) {
  return (
    <div class={`prod-stat ${cls}`}>
      <dt>{label}</dt>
      <dd class="mono">{value}</dd>
    </div>
  );
}

// ------------------------------------------------------------------ page
export default function ProductionPage({ conn }: { conn: MainframeConn }) {
  const L = lang.value;
  const [ov, setOv] = useState<Overview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [unit, setUnitState] = useState<RateUnit>(() => (getPref("prodUnit", "min") === "h" ? "h" : "min"));
  const setUnit = (u: RateUnit) => (setUnitState(u), setPref("prodUnit", u));
  const [item, setItem] = useState<{ uplinkId: string; name: string; displayName: string } | null>(null);
  const [pinBusy, setPinBusy] = useState(false);
  const every = useRef(60);
  const online = conn.status.value === "open";
  const load = async () => {
    try {
      const d = normalizeOverview(await conn.production("overview"));
      every.current = pollSeconds(d.interval);
      setOv(d);
      setError(null);
    } catch (e) {
      setError((e as Error).message);
    }
  };
  // poll while the page is visible (every sample interval, at least 15 s)
  useEffect(() => {
    if (!online) return;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let stop = false;
    const tick = async () => {
      if (stop) return;
      if (!document.hidden) await load();
      if (!stop) timer = setTimeout(tick, every.current * 1000);
    };
    const onVis = () => {
      if (document.hidden || stop) return;
      clearTimeout(timer);
      tick();
    };
    tick();
    document.addEventListener("visibilitychange", onVis);
    return () => {
      stop = true;
      clearTimeout(timer);
      document.removeEventListener("visibilitychange", onVis);
    };
  }, [conn, online]);

  const canPin = conn.can("layout.edit");
  const canSearch = conn.can("storage.search");
  const pinOp = async (kind: PinKind, extra: { uplinkId?: string; name?: string }, pinned?: Pin) => {
    setPinBusy(true);
    try {
      if (pinned) await conn.production("unpin", { id: pinned.id });
      else await conn.production("pin", { kind, ...extra });
      pushToast({ level: "info", title: t("pr.title"), text: pinned ? t("pr.unpinned") : t("pr.pinned") });
      await load();
    } catch (e) {
      pushToast({ level: "warn", title: t("pr.title"), text: errText(e) });
    } finally {
      setPinBusy(false);
    }
  };
  const pinButton = (kind: PinKind, extra: { uplinkId?: string; name?: string } = {}) => {
    if (!ov || !canPin || (kind !== "energy" && !canSearch)) return null;
    const pinned = pinOf(ov.pins, kind, extra.uplinkId, extra.name);
    const full = !pinned && ov.pins.length >= ov.pinLimit;
    return (
      <button type="button" class={`btn btn-small pin-btn ${pinned ? "on" : ""}`} aria-pressed={!!pinned} disabled={pinBusy || full} title={full ? t("pr.err.too_many") : t("pr.pinHint")} onClick={() => pinOp(kind, extra, pinned)}>
        {pinned ? t("pr.unpin") : t("pr.pin")}
      </button>
    );
  };

  if (error === "disabled")
    return (
      <div class="empty">
        <h2 class="px-title">{t("pr.title")}</h2>
        <p>{t("pr.err.disabled")}</p>
      </div>
    );
  const noData = ov && ov.storages.length === 0 && !ov.energy;
  return (
    <section class="prodpage" lang={L} aria-label={t("pr.title")}>
      <div class="net-toolbar">
        <div class="seg-ctl" role="radiogroup" aria-label={t("pr.unit")}>
          {(["min", "h"] as const).map((u) => (
            <button key={u} type="button" role="radio" aria-checked={unit === u} class={unit === u ? "on" : ""} onClick={() => setUnit(u)}>
              {u === "min" ? t("pr.perMin") : t("pr.perHour")}
            </button>
          ))}
        </div>
        {ov && (
          <div class="net-chips">
            <span class="chip-s chip-plain">{windowText(ov.window, ov.interval, L)}</span>
            <span class="chip-s chip-plain">{t("pr.sample", { s: ov.interval })}</span>
            {ov.since && <span class="chip-s chip-plain">{t("pr.since", { ago: agoText(ov.since, L) })}</span>}
          </div>
        )}
      </div>
      {error && (
        <p class="form-error" role="alert">
          {errText(new Error(error))}
        </p>
      )}
      {!ov && !error && <div class="card skeleton net-skeleton" />}
      {noData && (
        <div class="rules-empty">
          <b>{canSearch ? t("pr.noStorage") : t("pr.noAccess")}</b>
          <span class="muted small">
            {t("pr.noStorageHint")} <DocsLink to="/docs/bridges#setup">{t("pr.bridgesDocs")}</DocsLink>
          </span>
        </div>
      )}
      {ov && !canSearch && ov.storages.length === 0 && ov.energy && <p class="muted small">{t("pr.noAccess")}</p>}
      {ov && (
        <div class="prod-grid">
          {ov.storages.map((s) => (
            <StorageBlock key={s.uplinkId} s={s} ov={ov} unit={unit} pin={pinButton("storage", { uplinkId: s.uplinkId })} onItem={(m) => setItem({ uplinkId: s.uplinkId, name: m.name, displayName: m.displayName })} />
          ))}
          {ov.energy && <EnergyBlock e={ov.energy} ov={ov} pin={pinButton("energy")} />}
        </div>
      )}
      {ov && ov.pins.length > 0 && (
        <section class="acc-sec prod-pins" aria-labelledby="prod-pins">
          <h2 class="sec-title" id="prod-pins">
            {t("pr.pinsTitle", { n: ov.pins.length, limit: ov.pinLimit })}
          </h2>
          <ul class="pin-list">
            {ov.pins.map((p) => (
              <li key={p.id}>
                <PixelIcon name={p.kind === "energy" ? "bolt" : p.kind === "storage" ? "box" : "chart"} size={12} />
                <span>{p.label}</span>
                {canPin && (
                  <button type="button" class="btn btn-small btn-danger-ghost" disabled={pinBusy} onClick={() => pinOp(p.kind, {}, p)}>
                    {t("pr.unpin")}
                  </button>
                )}
              </li>
            ))}
          </ul>
          <p class="muted small">{t("pr.pinsHint")}</p>
        </section>
      )}
      <p class="muted small prod-note">
        {t("pr.note")} <DocsLink to="/docs/automation#production">{t("pr.docs")}</DocsLink>
      </p>
      {item && ov && (
        <ItemDrawer
          conn={conn}
          uplinkId={item.uplinkId}
          name={item.name}
          displayName={item.displayName}
          unit={unit}
          ov={ov}
          pin={pinButton("item", { uplinkId: item.uplinkId, name: item.name })}
          onClose={() => setItem(null)}
        />
      )}
    </section>
  );
}

function firstNumbers(interval: number) {
  return interval === 60 ? t("pr.firstMinute") : t("pr.firstSeconds", { s: interval });
}

function MoverList({ title, list, unit, cls, onPick }: { title: string; list: Mover[]; unit: RateUnit; cls: string; onPick: (m: Mover) => void }) {
  const L = lang.value;
  return (
    <div class="movers">
      <h4 class="block-title">{title}</h4>
      {list.length === 0 ? (
        <p class="muted small">{t("pr.none")}</p>
      ) : (
        <ul class="mover-list">
          {list.map((m) => (
            <li key={m.name}>
              <button type="button" class="mover" onClick={() => onPick(m)} title={t("pr.whatHint", { item: m.displayName })}>
                <ItemIcon name={m.name} displayName={m.displayName} size={24} />
                <span class="mover-name">{m.displayName}</span>
                <span class={`mover-rate mono ${cls}`}>{fmtRate(m.rate, unit, L)}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function StorageBlock({ s, ov, unit, pin, onItem }: { s: StorageStats; ov: Overview; unit: RateUnit; pin: ComponentChildren; onItem: (m: Mover) => void }) {
  const L = lang.value;
  const u = (v: number) => fmtRate(v, unit, L);
  const unitText = unit === "h" ? t("pr.unitH") : t("pr.unitMin");
  return (
    <article class="card prod-card" aria-label={s.title}>
      <header class="card-head">
        <PixelIcon name="box" size={16} class="card-icon" />
        <h3 class="card-title">{s.title}</h3>
        <span class="tag tag-gray">{s.bridge === "rs" ? "RS" : "ME"}</span>
        {!s.online && <span class="tag tag-red">{t("pr.offline")}</span>}
        {pin}
      </header>
      <div class="card-body">
        {s.samples === 0 ? (
          <p class="muted">{firstNumbers(ov.interval)}</p>
        ) : (
          <>
            <dl class="prod-stats">
              <Stat label={t("pr.gained")} value={u(s.gained)} cls="up" />
              <Stat label={t("pr.consumed")} value={u(s.consumed)} cls="down" />
              <Stat label={t("pr.net")} value={fmtRate(s.net, unit, L, true)} cls={s.net >= 0 ? "up" : "down"} />
              <Stat label={t("pr.crafted")} value={u(s.crafted)} />
            </dl>
            <SeriesChart
              label={s.title}
              unit={unitText}
              points={ov.window}
              series={[
                { label: t("pr.gained"), data: s.series.gained.map((v) => inUnit(v, unit)), cls: "s-gain" },
                { label: t("pr.consumed"), data: s.series.consumed.map((v) => inUnit(v, unit)), cls: "s-use" },
                ...(s.series.crafted.some((v) => v > 0) ? [{ label: t("pr.crafted"), data: s.series.crafted.map((v) => inUnit(v, unit)), cls: "s-craft" }] : []),
              ]}
            />
            <div class="prod-movers">
              <MoverList title={t("pr.producers")} list={s.producers} unit={unit} cls="up" onPick={onItem} />
              <MoverList title={t("pr.consumers")} list={s.consumers} unit={unit} cls="down" onPick={onItem} />
            </div>
            {s.samples < ov.window && <p class="muted small">{t("pr.partial", { n: s.samples, w: ov.window })}</p>}
          </>
        )}
      </div>
    </article>
  );
}

function EnergyBlock({ e, ov, pin }: { e: EnergyStats; ov: Overview; pin: ComponentChildren }) {
  const L = lang.value;
  const pct = e.capacity > 0 ? Math.round((e.stored / e.capacity) * 100) : 0;
  const net = e.in - e.out;
  return (
    <article class="card prod-card accent-yellow" aria-label={t("pr.energy")}>
      <header class="card-head">
        <PixelIcon name="bolt" size={16} class="card-icon" />
        <h3 class="card-title">{t("pr.energy")}</h3>
        <span class="muted small">{t("pr.cards", { n: e.cards.length })}</span>
        {pin}
      </header>
      <div class="card-body">
        <div class="pp-buffer prod-buffer">
          <span class="pwr-bar big" aria-hidden="true">
            <span class="pwr-fill" style={{ width: `${pct}%` }} />
          </span>
          <span class="mono small">
            {fmtFE(e.stored)} / {fmtFE(e.capacity)} · {pct} %
          </span>
        </div>
        <dl class="prod-stats">
          <Stat label={t("pr.in")} value={fmtFE(e.in, true)} cls="up" />
          <Stat label={t("pr.out")} value={fmtFE(e.out, true)} cls="down" />
          <Stat label={t("pr.net")} value={`${net > 0 ? "+" : ""}${fmtFE(net, true)}`} cls={net >= 0 ? "up" : "down"} />
        </dl>
        <SeriesChart
          label={t("pr.energy")}
          unit="FE/t"
          points={ov.window}
          series={[
            { label: t("pr.in"), data: e.series.in, cls: "s-in" },
            { label: t("pr.out"), data: e.series.out, cls: "s-out" },
          ]}
        />
        <table class="acc-table prod-cards">
          <thead>
            <tr>
              <th>{t("pr.card")}</th>
              <th>{t("pr.stored")}</th>
              <th class="num">{t("pr.in")}</th>
              <th class="num">{t("pr.out")}</th>
            </tr>
          </thead>
          <tbody>
            {e.cards.map((c) => {
              const p = c.capacity > 0 ? Math.round((c.stored / c.capacity) * 100) : 0;
              return (
                <tr key={c.uplinkId}>
                  <td>
                    {c.title}
                    {c.measured && (
                      <span class="tag tag-gray" title={t("pr.measuredHint")}>
                        {t("pr.measured")}
                      </span>
                    )}
                  </td>
                  <td>
                    <span class="mini-bar" aria-hidden="true">
                      <span style={{ width: `${p}%` }} />
                    </span>{" "}
                    <span class="mono small">{c.capacity ? `${p} %` : fmtFE(c.stored)}</span>
                  </td>
                  <td class="num mono">{fmtFE(c.in, true)}</td>
                  <td class="num mono">{fmtFE(c.out, true)}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
        <p class="muted small">{t("pr.energyNote", { n: fmtNum(e.cards.length, L) })}</p>
      </div>
    </article>
  );
}

// ------------------------------------------------------------- item drawer
const JOB_KEYS: Record<string, Key> = { queued: "pr.job.queued", running: "pr.job.running", done: "pr.job.done", failed: "pr.job.failed", cancelled: "pr.job.cancelled" };

/** "What is eating my iron?" – the rates, series, crafting and history of one item in one storage. */
function ItemDrawer(props: { conn: MainframeConn; uplinkId: string; name: string; displayName: string; unit: RateUnit; ov: Overview; pin: ComponentChildren; onClose: () => void }) {
  const { conn, unit } = props;
  const L = lang.value;
  const [doc, setDoc] = useState<ItemStats | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    let stop = false;
    const load = () =>
      conn
        .production("item", { uplinkId: props.uplinkId, name: props.name })
        .then((d) => !stop && (setDoc(normalizeItemStats(d, props.name)), setError(null)))
        .catch((e) => !stop && setError(errText(e)));
    load();
    const timer = setInterval(() => !document.hidden && load(), pollSeconds(props.ov.interval) * 1000);
    return () => {
      stop = true;
      clearInterval(timer);
    };
  }, [props.uplinkId, props.name]);
  const u = (v: number) => fmtRate(v, unit, L);
  const unitText = unit === "h" ? t("pr.unitH") : t("pr.unitMin");
  const storage = props.ov.storages.find((s) => s.uplinkId === props.uplinkId);
  return (
    <Sheet title={props.displayName} onClose={props.onClose} wide>
      <div class="item-drawer" lang={L}>
        <div class="item-head with-icon">
          <ItemIcon name={props.name} displayName={props.displayName} size={48} />
          <div class="item-big-count">
            <span class="big">{doc ? doc.count.toLocaleString(locale(L)) : "…"}</span>
            <span class="muted small">{t("pr.inStorage", { storage: storage?.title ?? "" })}</span>
          </div>
          <div class="item-ids">
            <code class="item-id">{props.name}</code>
            {props.pin}
          </div>
        </div>
        {error && <p class="form-error">{error}</p>}
        {doc && (
          <>
            <dl class="prod-stats">
              <Stat label={t("pr.gained")} value={u(doc.gained)} cls="up" />
              <Stat label={t("pr.consumed")} value={u(doc.consumed)} cls="down" />
              <Stat label={t("pr.net")} value={fmtRate(doc.net, unit, L, true)} cls={doc.net >= 0 ? "up" : "down"} />
              <Stat label={t("pr.crafted")} value={u(doc.crafted)} />
            </dl>
            {doc.net < 0 && doc.count > 0 && <p class="small prod-eta">{t("pr.eta", { t: etaText(doc.count, -doc.net, L) })}</p>}
            <SeriesChart
              label={props.displayName}
              unit={unitText}
              points={props.ov.window}
              series={[
                { label: t("pr.gained"), data: doc.series.gained.map((v) => inUnit(v, unit)), cls: "s-gain" },
                { label: t("pr.consumed"), data: doc.series.consumed.map((v) => inUnit(v, unit)), cls: "s-use" },
                { label: t("pr.net"), data: doc.series.net.map((v) => inUnit(v, unit)), cls: "s-net" },
              ]}
            />
            <div class="honest-note" role="note">
              <b>{t("pr.whoTitle")}</b> {t("pr.whoText")}
            </div>
            <h3 class="block-title">{t("pr.jobs")}</h3>
            {doc.jobs.length ? (
              <ul class="job-list">
                {doc.jobs.map((j) => (
                  <li key={j.jobId} class={`job st-${j.status}`}>
                    <div class="job-top">
                      <ItemIcon name={j.name} displayName={j.displayName} />
                      <span class="job-name">
                        {j.displayName} <span class="muted">× {j.count.toLocaleString(locale(L))}</span>
                        {j.source && j.source !== "dashboard" && <span class="mini-badge run">{j.source}</span>}
                      </span>
                      <span class={`job-status js-${j.status}`}>{t(JOB_KEYS[j.status])}</span>
                    </div>
                    <div class="job-bar" role="progressbar" aria-label={j.displayName} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(j.progress * 100)}>
                      <span class="job-fill" style={{ width: `${Math.round((j.status === "done" ? 1 : j.progress) * 100)}%` }} />
                    </div>
                  </li>
                ))}
              </ul>
            ) : (
              <p class="muted small">{t("pr.noJobs")}</p>
            )}
            <h3 class="block-title">{t("pr.tasks")}</h3>
            {doc.tasks.length ? (
              <ul class="job-list">
                {doc.tasks.map((x, i) => (
                  <li key={i} class="job st-running">
                    <div class="job-top">
                      <ItemIcon name={x.name} displayName={x.displayName} />
                      <span class="job-name">
                        {x.displayName} <span class="muted">× {x.count.toLocaleString(locale(L))}</span>
                      </span>
                      <span class="job-status js-running">{Math.round(x.progress * 100)} %</span>
                    </div>
                    <div class="job-bar" role="progressbar" aria-label={x.displayName} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(x.progress * 100)}>
                      <span class="job-fill" style={{ width: `${Math.round(x.progress * 100)}%` }} />
                    </div>
                  </li>
                ))}
              </ul>
            ) : (
              <p class="muted small">{t("pr.noTasks")}</p>
            )}
            <h3 class="block-title">{t("pr.history")}</h3>
            {doc.history && doc.history.some((x) => x !== null) ? <HistoryLine data={doc.history} label={t("pr.history")} /> : <p class="muted small">{t("pr.noHistory")}</p>}
          </>
        )}
      </div>
    </Sheet>
  );
}

/** "about 3 h" until the stock is gone at the current net loss. */
function etaText(count: number, lossPerMin: number, L: "en" | "de") {
  const mins = count / Math.max(lossPerMin, 1e-9);
  if (mins < 90) return L === "de" ? `${Math.max(1, Math.round(mins))} Min.` : `${Math.max(1, Math.round(mins))} min`;
  const h = mins / 60;
  if (h < 48) return L === "de" ? `${Math.round(h)} Std.` : `${Math.round(h)} h`;
  return L === "de" ? `${Math.round(h / 24)} Tagen` : `${Math.round(h / 24)} days`;
}
