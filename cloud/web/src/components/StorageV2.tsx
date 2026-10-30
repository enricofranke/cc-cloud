// Storage card v2 (docs/SPEC.md "Storage card v2"): browse grid with favourites,
// craftables, fluids, CPUs & jobs, item details with a 24 h sparkline, craft
// preview with "notify me", and the auto-stock (level maintainer) editor.
import { useEffect, useMemo, useRef, useState } from "preact/hooks";
import type { CpuInfo, CraftPlan, ItemDoc, JobInfo, MainframeConn, StockState, StorageRow } from "../lib/conn.ts";
import { REJECT_TEXT } from "../lib/conn.ts";
import type { Uplink, Widget } from "../lib/types.ts";
import { fmtCount } from "../lib/format.ts";
import { Sheet } from "./Overlays.tsx";
import { ItemIcon } from "./ItemIcon.tsx";

const full = (n: number) => Math.round(n).toLocaleString("en-US");
const modOf = (name: string) => (name.includes(":") ? name.split(":")[0] : "");

export const STORAGE_ERRORS: Record<string, string> = {
  unavailable: "The storage is not reachable right now (bridge missing or its chunk not loaded).",
  offline: "The mainframe is offline.",
  unknown_item: "This item is not in the network any more.",
  invalid: "That value is not valid.",
  rate_limited: "Too many requests – slow down a little.",
  not_craftable: "There is no crafting pattern for this item.",
  no_cpu: "All crafting CPUs are busy. Try again when a job has finished.",
  missing_resources: "Not enough ingredients in the network for this amount.",
  too_many: "That amount is too large.",
  timeout: "No answer from the server.",
  disconnected: "Not connected.",
  read_only: "Your role cannot change this.",
  not_allowed: "Your role cannot change this.",
};
export const storageError = (e: unknown) => {
  const code = e instanceof Error ? e.message : String(e);
  return STORAGE_ERRORS[code] ?? REJECT_TEXT[code] ?? `Error: ${code}`;
};

export const STATE_TEXT: Record<StockState, string> = {
  idle: "In stock",
  crafting: "Crafting",
  waiting_cpu: "Waiting for a CPU",
  missing: "Missing resources",
  paused: "Paused after 3 failures",
};

/** A tiny 24 h chart (inline SVG, CSP-safe). */
export function Sparkline({ data, label }: { data: number[]; label: string }) {
  if (data.length < 2) return <p class="muted small">No history yet – it appears once the server has collected some (long-term statistics).</p>;
  const W = 280;
  const H = 56;
  const min = Math.min(...data);
  const max = Math.max(...data);
  const span = max - min || 1;
  const pts = data.map((v, i) => `${((i / (data.length - 1)) * W).toFixed(1)},${(H - 4 - ((v - min) / span) * (H - 8)).toFixed(1)}`);
  const up = data[data.length - 1] >= data[0];
  return (
    <figure class="spark" aria-label={`${label}: last 24 hours, from ${full(data[0])} to ${full(data[data.length - 1])}`}>
      <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" class={`spark-svg ${up ? "up" : "down"}`} role="img">
        <polygon points={`0,${H} ${pts.join(" ")} ${W},${H}`} class="spark-area" />
        <polyline points={pts.join(" ")} class="spark-line" vector-effect="non-scaling-stroke" />
      </svg>
      <figcaption class="spark-cap">
        <span>24 h ago · {fmtCount(data[0])}</span>
        <span>
          low {fmtCount(min)} · high {fmtCount(max)}
        </span>
        <span>now · {fmtCount(data[data.length - 1])}</span>
      </figcaption>
    </figure>
  );
}


/** Rows the browser loads by scrolling at most (a big ME system has tens of thousands; docs/SPEC.md "Scaling"). */
const MAX_BROWSE_ROWS = 1000;
function StateBadge({ state, auto }: { state?: StockState; auto?: boolean }) {
  if (!auto || !state) return null;
  return <span class={`stock-badge sb-${state}`}>{state === "idle" ? "auto" : STATE_TEXT[state]}</span>;
}

// ----------------------------------------------------------- auto-stock editor
export function AutoStockSheet(props: {
  conn: MainframeConn;
  uplink: Uplink;
  widgetId?: string;
  item: { name: string; displayName: string; count?: number };
  current?: { min: number; target: number; autoCraft: boolean; state?: StockState; failures?: number } | null;
  craftable?: boolean;
  onClose: () => void;
  onSaved?: () => void;
}) {
  const { conn, uplink, item } = props;
  const [min, setMin] = useState(String(props.current?.min ?? 1000));
  const [target, setTarget] = useState(String(props.current?.target ?? Math.max(2000, (props.current?.min ?? 1000) * 2)));
  const [auto, setAuto] = useState(props.current?.autoCraft ?? props.craftable !== false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const m = Math.floor(Number(min));
  const t = Math.floor(Number(target));
  const valid = min !== "" && target !== "" && m >= 0 && t >= m;
  const readOnly = !conn.can("storage.autostock");
  const save = async (off = false) => {
    setBusy(true);
    setError(null);
    try {
      await conn.storage(uplink.uplinkId, "autostock", off ? { name: item.name, min: 0, target: 0, autoCraft: false } : { name: item.name, min: m, target: t, autoCraft: auto }, props.widgetId);
      props.onSaved?.();
      props.onClose();
    } catch (e) {
      setError(storageError(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <Sheet title={`Keep ${item.displayName} in stock`} onClose={props.onClose}>
      <div class="item-head with-icon">
        <ItemIcon name={item.name} displayName={item.displayName} size={48} />
        <div class="item-big-count">
          <span class="big">{item.count !== undefined ? full(item.count) : "—"}</span>
          <span class="muted small">in the network now</span>
        </div>
        {props.current?.autoCraft && props.current.state && (
          <div>
            <StateBadge state={props.current.state} auto />
          </div>
        )}
      </div>
      <div class="stock-fields">
        <label class="field">
          <span class="field-label">Minimum</span>
          <input class="input" type="number" inputMode="numeric" min={0} value={min} disabled={readOnly} onInput={(e) => setMin((e.target as HTMLInputElement).value.replace(/\D/g, "").slice(0, 10))} />
          <span class="field-hint">Below this you get a warning{auto ? " and crafting starts" : ""}.</span>
        </label>
        <label class="field">
          <span class="field-label">Craft up to</span>
          <input class="input" type="number" inputMode="numeric" min={0} value={target} disabled={readOnly} onInput={(e) => setTarget((e.target as HTMLInputElement).value.replace(/\D/g, "").slice(0, 10))} />
          <span class="field-hint">Target amount (≥ minimum).</span>
        </label>
      </div>
      <label class="check-row">
        <input type="checkbox" checked={auto} disabled={readOnly || props.craftable === false} onChange={(e) => setAuto((e.target as HTMLInputElement).checked)} />
        Craft automatically when it runs low
      </label>
      <p class="muted small">
        {props.craftable === false
          ? "No crafting pattern for this item – you only get the low-stock warning."
          : "The server crafts in batches of up to 64, one job at a time per item, at most every 30 s. It pauses when there is no free CPU or after 3 failures. Works without a computer."}
      </p>
      {valid || <p class="field-err">The target must be at least the minimum.</p>}
      {error && (
        <p class="form-error" role="alert">
          {error}
        </p>
      )}
      <div class="sheet-actions">
        {props.current && (
          <button type="button" class="btn btn-danger-ghost" disabled={readOnly || busy} onClick={() => save(true)}>
            Turn off
          </button>
        )}
        <button type="button" class="pxbtn pxbtn-gold" disabled={readOnly || busy || !valid} onClick={() => save()}>
          <span>{busy ? "Saving…" : "Save"}</span>
        </button>
      </div>
      {readOnly && <p class="muted small">Changing stock rules needs the “Auto-stock” permission on this mainframe.</p>}
    </Sheet>
  );
}

// -------------------------------------------------------------- craft preview
const AMOUNTS = [1, 16, 64, 256];
export function CraftSheet(props: { conn: MainframeConn; uplink: Uplink; craftWidget?: Widget; widgetId?: string; item: { name: string; displayName: string }; max: number; onClose: () => void }) {
  const { conn, uplink, item } = props;
  const [amount, setAmount] = useState(64);
  const [custom, setCustom] = useState("");
  const [plan, setPlan] = useState<CraftPlan | null>(null);
  const [planErr, setPlanErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [notify, setNotify] = useState(true);
  const [state, setState] = useState<{ kind: "idle" | "sending" } | { kind: "ok"; jobId?: string } | { kind: "error"; text: string }>({ kind: "idle" });
  const count = custom ? Math.floor(Number(custom)) : amount;
  const valid = Number.isInteger(count) && count >= 1 && count <= props.max;
  useEffect(() => {
    if (!valid) return;
    let stop = false;
    setLoading(true);
    const t = setTimeout(() => {
      conn
        .craftPreview(uplink.uplinkId, item.name, count, props.widgetId)
        .then((p) => !stop && (setPlan(p), setPlanErr(null)))
        .catch((e) => !stop && (setPlan(null), setPlanErr(storageError(e))))
        .finally(() => !stop && setLoading(false));
    }, 250);
    return () => {
      stop = true;
      clearTimeout(t);
    };
  }, [count]);
  const start = () => {
    if (!props.craftWidget) return;
    setState({ kind: "sending" });
    const sent = conn.action(uplink.uplinkId, props.craftWidget, { op: "craft", name: item.name, count, notify }, false, {
      onResult: (r) => {
        setState(r.ok ? { kind: "ok", jobId: r.jobId } : { kind: "error", text: storageError(r.error ?? "failed") });
        return true;
      },
    });
    if (!sent) setState({ kind: "error", text: "Not connected – try again in a moment." });
  };
  const readOnly = !conn.can("storage.craft");
  return (
    <Sheet title={`Craft ${item.displayName}`} onClose={props.onClose}>
      {state.kind === "ok" ? (
        <div class="result result-ok" role="status">
          <span class="result-icon">✓</span>
          <div>
            <b>Crafting {full(count)}× {item.displayName} started.</b>
            <div class="muted small">
              {state.jobId ? `Job #${state.jobId}. ` : ""}
              {notify ? "You get a notice when it is done." : "Progress shows under crafting jobs."}
            </div>
          </div>
        </div>
      ) : (
        <>
          <div class="amounts" role="radiogroup" aria-label="Amount">
            {AMOUNTS.map((a) => (
              <button key={a} type="button" role="radio" aria-checked={!custom && amount === a} class={`chip ${!custom && amount === a ? "on" : ""}`} onClick={() => (setCustom(""), setAmount(a))}>
                {a}
              </button>
            ))}
            <input
              class="input input-amount"
              type="number"
              inputMode="numeric"
              min={1}
              max={props.max}
              placeholder="Other"
              value={custom}
              onInput={(e) => setCustom((e.target as HTMLInputElement).value.replace(/\D/g, "").slice(0, 7))}
              aria-label="Other amount"
            />
          </div>
          {!valid && <p class="field-err">Pick an amount between 1 and {full(props.max)}.</p>}
          <div class={`plan ${loading ? "is-loading" : ""}`} aria-busy={loading}>
            {planErr && <p class="form-error">{planErr}</p>}
            {plan && (
              <>
                <table class="plan-table">
                  <thead>
                    <tr>
                      <th>Item</th>
                      <th>From storage</th>
                      <th>To craft</th>
                      <th>Missing</th>
                    </tr>
                  </thead>
                  <tbody>
                    {plan.entries.map((e) => (
                      <tr key={e.name} class={e.missing ? "missing" : ""}>
                        <td>
                          <span class="plan-item">
                            <ItemIcon name={e.name} displayName={e.displayName} size={20} />
                            {e.displayName}
                          </span>
                        </td>
                        <td class="mono">{e.stored ? fmtCount(e.stored) : "–"}</td>
                        <td class="mono">{e.toCraft ? fmtCount(e.toCraft) : "–"}</td>
                        <td class={`mono ${e.missing ? "t-red" : ""}`}>{e.missing ? fmtCount(e.missing) : "–"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <p class="plan-foot muted small">
                  {plan.cpu ? (plan.cpu.enough ? `${plan.cpu.available} CPU${plan.cpu.available === 1 ? "" : "s"} free` : "No free CPU – the job waits in the queue") : ""}
                  {plan.bytes ? ` · ${fmtCount(plan.bytes)} bytes` : ""}
                </p>
                {plan.missing && <p class="form-error">Some ingredients are missing – the craft would fail. Craft a smaller amount or get the missing items first.</p>}
              </>
            )}
          </div>
          <label class="check-row">
            <input type="checkbox" checked={notify} onChange={(e) => setNotify((e.target as HTMLInputElement).checked)} />
            Notify me when it is done (or fails)
          </label>
          {state.kind === "error" && (
            <p class="form-error" role="alert">
              {state.text}
            </p>
          )}
          <button type="button" class="pxbtn pxbtn-gold pxbtn-wide" disabled={readOnly || !valid || !plan || plan.missing || state.kind === "sending" || !props.craftWidget} onClick={start}>
            <span>{state.kind === "sending" ? "Starting…" : `Craft ${valid ? full(count) : ""}×`}</span>
          </button>
          {readOnly && <p class="muted small">You can preview; starting jobs needs the “Craft” permission.</p>}
        </>
      )}
    </Sheet>
  );
}

// --------------------------------------------------------------- item details
export function ItemDetailSheet(props: { conn: MainframeConn; uplink: Uplink; widgetId?: string; name: string; displayName: string; onClose: () => void; onFallback?: () => void }) {
  const { conn, uplink } = props;
  const [doc, setDoc] = useState<ItemDoc | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [sub, setSub] = useState<"craft" | "stock" | null>(null);
  const craftW = uplink.widgets.find((w) => w.type === "item_search") ?? uplink.widgets.find((w) => w.type === "watchlist");
  const max = Number(uplink.widgets.find((w) => w.type === "item_search")?.opts?.max ?? 4096) || 4096;
  const load = () =>
    conn
      .storage<ItemDoc>(uplink.uplinkId, "item", { name: props.name }, props.widgetId)
      .then((d) => (setDoc(d), setError(null)))
      .catch((e) => {
        // older mods without storage v2: the classic item sheet
        if (e instanceof Error && (e.message === "timeout" || e.message === "invalid")) props.onFallback?.();
        setError(storageError(e));
      });
  useEffect(() => {
    load();
  }, [props.name]);
  const fav = async () => {
    if (!doc) return;
    const on = !doc.favorite;
    setDoc({ ...doc, favorite: on });
    try {
      await conn.storage(uplink.uplinkId, "favorite", { name: doc.name, on }, props.widgetId);
    } catch (e) {
      setDoc({ ...doc, favorite: !on });
      setError(storageError(e));
    }
  };
  if (sub === "craft" && doc) return <CraftSheet conn={conn} uplink={uplink} craftWidget={craftW} widgetId={props.widgetId} item={doc} max={max} onClose={() => (setSub(null), load())} />;
  if (sub === "stock" && doc)
    return <AutoStockSheet conn={conn} uplink={uplink} widgetId={props.widgetId} item={doc} current={doc.watch} craftable={doc.craftable} onClose={() => (setSub(null), load())} />;
  const readOnly = !conn.can("act.buttons");
  return (
    <Sheet title={props.displayName} onClose={props.onClose}>
      <div class="item-head with-icon">
        <ItemIcon name={props.name} displayName={props.displayName} size={48} />
        <div class="item-big-count">
          <span class="big">{doc ? full(doc.count) : "…"}</span>
          <span class="muted small">in the network</span>
        </div>
        <div class="item-ids">
          <span class="mod-tag">{modOf(props.name)}</span>
          <code class="item-id">{props.name}</code>
        </div>
        {doc && (
          <button type="button" class={`icon-btn fav-btn ${doc.favorite ? "on" : ""}`} aria-pressed={doc.favorite} aria-label={doc.favorite ? "Remove from favourites" : "Add to favourites"} disabled={readOnly} onClick={fav}>
            {doc.favorite ? "★" : "☆"}
          </button>
        )}
      </div>
      {error && <p class="form-error">{error}</p>}
      {doc && (
        <>
          <Sparkline data={doc.history} label={doc.displayName} />
          <div class="detail-actions">
            {doc.craftable ? (
              <button type="button" class="pxbtn pxbtn-gold" onClick={() => setSub("craft")}>
                <span>Craft…</span>
              </button>
            ) : (
              <span class="muted small">Not craftable – no pattern.</span>
            )}
            <button type="button" class="btn" onClick={() => setSub("stock")}>
              {doc.watch ? "Edit stock rule" : "Keep in stock…"}
            </button>
          </div>
          {doc.watch && (
            <div class="stock-summary">
              <span>
                Min <b>{full(doc.watch.min)}</b> · up to <b>{full(doc.watch.target)}</b>
              </span>
              <StateBadge state={doc.watch.state} auto={doc.watch.autoCraft} />
              {!doc.watch.autoCraft && <span class="muted small">warning only</span>}
            </div>
          )}
          <h3 class="block-title">Recent jobs</h3>
          {doc.jobs.length ? <JobList jobs={doc.jobs} conn={conn} uplink={uplink} widgetId={props.widgetId} /> : <p class="muted small">No jobs for this item yet.</p>}
        </>
      )}
    </Sheet>
  );
}

const JOB_TEXT: Record<JobInfo["status"], string> = { queued: "Queued", running: "Running", waiting: "Waiting for CPU", done: "Done", failed: "Failed", cancelled: "Cancelled" };
function JobList({ jobs, conn, uplink, widgetId }: { jobs: JobInfo[]; conn: MainframeConn; uplink: Uplink; widgetId?: string }) {
  const [cancelling, setCancelling] = useState<string | null>(null);
  return (
    <ul class="job-list">
      {jobs.map((j) => (
        <li key={j.jobId} class={`job st-${j.status}`}>
          <div class="job-top">
            <ItemIcon name={j.name} displayName={j.displayName} />
            <span class="job-name">
              {j.displayName} <span class="muted">× {full(j.count)}</span>
              {j.source === "autostock" && <span class="mini-badge run">auto</span>}
              {j.notify && <span class="mini-badge" title="You get a notice when it finishes">🔔</span>}
            </span>
            <span class={`job-status js-${j.status}`}>{JOB_TEXT[j.status]}</span>
            {(j.status === "running" || j.status === "queued") && conn.can("storage.craft") && (
              <button
                type="button"
                class="btn btn-small btn-danger-ghost"
                disabled={cancelling === j.jobId}
                onClick={async () => {
                  setCancelling(j.jobId);
                  await conn.storage(uplink.uplinkId, "cancel", { jobId: j.jobId }, widgetId).catch(() => {});
                  setCancelling(null);
                }}
              >
                Cancel
              </button>
            )}
          </div>
          <div class="job-bar" role="progressbar" aria-label={`${j.displayName ?? j.name} progress`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(j.progress * 100)}>
            <span class="job-fill" style={{ width: `${Math.round((j.status === "done" ? 1 : j.progress) * 100)}%` }} />
          </div>
          {j.error && j.status !== "waiting" && <p class="muted small">{j.error}</p>}
        </li>
      ))}
    </ul>
  );
}

// ------------------------------------------------------------------- browser
type Tab = "items" | "craftable" | "fluids" | "cpus";
export function StorageBrowser(props: { conn: MainframeConn; uplink: Uplink; widgetId?: string; initialTab?: Tab; onClose: () => void }) {
  const { conn, uplink } = props;
  const [tab, setTab] = useState<Tab>(props.initialTab ?? "items");
  const [q, setQ] = useState("");
  const [sort, setSort] = useState<"count" | "name" | "mod">("count");
  const [filter, setFilter] = useState<"all" | "favorites">("all");
  const [rows, setRows] = useState<StorageRow[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fluids, setFluids] = useState<{ name: string; displayName: string; amount: number }[] | null>(null);
  const [cpus, setCpus] = useState<CpuInfo | null>(null);
  const [open, setOpen] = useState<StorageRow | null>(null);
  const sentinel = useRef<HTMLDivElement>(null);
  const reqKey = useMemo(() => `${tab}|${q}|${sort}|${filter}`, [tab, q, sort, filter]);
  const keyRef = useRef(reqKey);
  keyRef.current = reqKey;

  const loadPage = async (offset: number) => {
    if (tab !== "items" && tab !== "craftable") return;
    const key = reqKey;
    setLoading(true);
    try {
      const op = tab === "craftable" ? "craftables" : "browse";
      const params = tab === "craftable" ? { q, offset, limit: 100 } : { q, sort, desc: sort === "count", filter, offset, limit: 100 };
      const r = await conn.storage<{ rows: StorageRow[]; total: number }>(uplink.uplinkId, op, params, props.widgetId);
      if (keyRef.current !== key) return;
      setRows((prev) => (offset === 0 ? r.rows : [...prev, ...r.rows]));
      setTotal(r.total);
      setError(null);
    } catch (e) {
      if (keyRef.current === key) setError(storageError(e));
    } finally {
      if (keyRef.current === key) setLoading(false);
    }
  };
  useEffect(() => {
    setRows([]);
    const t = setTimeout(() => loadPage(0), q ? 250 : 0);
    return () => clearTimeout(t);
  }, [reqKey]);
  useEffect(() => {
    if (tab === "fluids")
      conn
        .storage<{ rows: { name: string; displayName: string; amount: number }[] }>(uplink.uplinkId, "fluids", { offset: 0, limit: 200 }, props.widgetId)
        .then((r) => setFluids(r.rows))
        .catch((e) => setError(storageError(e)));
    if (tab !== "cpus") return;
    const load = () =>
      conn
        .storage<CpuInfo>(uplink.uplinkId, "cpus", {}, props.widgetId)
        .then(setCpus)
        .catch((e) => setError(storageError(e)));
    load();
    const t = setInterval(load, 2000);
    return () => clearInterval(t);
  }, [tab]);
  // infinite scroll
  useEffect(() => {
    const el = sentinel.current;
    if (!el) return;
    const io = new IntersectionObserver((es) => {
      if (es.some((e) => e.isIntersecting) && !loading && rows.length < total && rows.length < MAX_BROWSE_ROWS) loadPage(rows.length);
    });
    io.observe(el);
    return () => io.disconnect();
  });
  const toggleFav = async (r: StorageRow) => {
    const on = !r.favorite;
    setRows((prev) => prev.map((x) => (x.name === r.name ? { ...x, favorite: on } : x)));
    try {
      await conn.storage(uplink.uplinkId, "favorite", { name: r.name, on }, props.widgetId);
    } catch (e) {
      setRows((prev) => prev.map((x) => (x.name === r.name ? { ...x, favorite: !on } : x)));
      setError(storageError(e));
    }
  };

  if (open) return <ItemDetailSheet conn={conn} uplink={uplink} widgetId={props.widgetId} name={open.name} displayName={open.displayName} onClose={() => (setOpen(null), loadPage(0))} />;
  return (
    <Sheet title={`${uplink.title || "Storage"} – browse`} onClose={props.onClose} wide>
      <div class="sb">
        <div class="nd-tabs" role="tablist" aria-label="Storage">
          {(
            [
              ["items", "Items"],
              ["craftable", "Craftable"],
              ["fluids", "Fluids"],
              ["cpus", "CPUs & jobs"],
            ] as const
          ).map(([t, label]) => (
            <button key={t} type="button" role="tab" aria-selected={tab === t} class={`nd-tab ${tab === t ? "on" : ""}`} onClick={() => (setTab(t), setError(null))}>
              {label}
            </button>
          ))}
        </div>
        {(tab === "items" || tab === "craftable") && (
          <div class="sb-tools">
            <input class="input sb-search" type="search" placeholder="Search…" value={q} maxLength={64} spellcheck={false} onInput={(e) => setQ((e.target as HTMLInputElement).value)} aria-label="Search items" />
            {tab === "items" && (
              <>
                <select class="select sb-sort" value={sort} aria-label="Sort" onChange={(e) => setSort((e.target as HTMLSelectElement).value as typeof sort)}>
                  <option value="count">Most first</option>
                  <option value="name">Name</option>
                  <option value="mod">Mod</option>
                </select>
                <button type="button" class={`chip ${filter === "favorites" ? "on" : ""}`} aria-pressed={filter === "favorites"} onClick={() => setFilter(filter === "favorites" ? "all" : "favorites")}>
                  ★ Favourites
                </button>
              </>
            )}
          </div>
        )}
        {error && <p class="form-error">{error}</p>}
        {(tab === "items" || tab === "craftable") && (
          <>
            <ul class="sb-grid" aria-busy={loading}>
              {rows.map((r) => (
                <li key={r.name} class={`sb-tile ${r.favorite ? "fav" : ""}`}>
                  <button type="button" class="sb-open" onClick={() => setOpen(r)} title={`${r.displayName} – ${full(r.count)}`}>
                    <ItemIcon name={r.name} displayName={r.displayName} size={32} />
                    <span class="sb-name">{r.displayName}</span>
                    <span class={`sb-count ${r.count === 0 ? "zero" : ""}`}>{fmtCount(r.count)}</span>
                    {r.craftable && <span class="sb-craft" title="Craftable" aria-label="craftable">⚒</span>}
                  </button>
                  <button type="button" class={`sb-fav ${r.favorite ? "on" : ""}`} aria-pressed={!!r.favorite} aria-label={`${r.favorite ? "Unpin" : "Pin"} ${r.displayName}`} disabled={!conn.can("act.buttons")} onClick={() => toggleFav(r)}>
                    {r.favorite ? "★" : "☆"}
                  </button>
                </li>
              ))}
            </ul>
            {!loading && rows.length === 0 && !error && <p class="store-note">{filter === "favorites" ? "No favourites yet – pin items with ☆." : q ? `Nothing matches “${q}”.` : "The storage is empty."}</p>}
            <div ref={sentinel} class="sb-more">
              {loading
                ? "Loading…"
                : rows.length >= MAX_BROWSE_ROWS && rows.length < total
                  ? `${rows.length} of ${total} – search to find the rest`
                  : rows.length < total
                    ? `${rows.length} of ${total}`
                    : total
                      ? `${total} items`
                      : ""}
            </div>
          </>
        )}
        {tab === "fluids" &&
          (fluids ? (
            fluids.length ? (
              <ul class="fluid-list">
                {fluids.map((f) => (
                  <li key={f.name}>
                    <ItemIcon name={f.name} displayName={f.displayName} size={24} />
                    <span class="item-name">{f.displayName}</span>
                    <span class="mono">{fmtCount(f.amount / 1000)} B</span>
                  </li>
                ))}
              </ul>
            ) : (
              <p class="store-note">No fluids stored.</p>
            )
          ) : (
            <p class="muted small">Loading…</p>
          ))}
        {tab === "cpus" &&
          (cpus ? (
            <>
              {cpus.cpus.length > 0 && (
                <ul class="cpu-list">
                  {cpus.cpus.map((c) => (
                    <li key={c.id} class={`cpu ${c.busy ? "busy" : "idle"}`}>
                      <div class="cpu-head">
                        <b>{c.name}</b>
                        <span class="muted small">
                          {c.coProcessors} co-processor{c.coProcessors === 1 ? "" : "s"} · {fmtCount(c.storage)} bytes
                        </span>
                        <span class={`job-status ${c.busy ? "js-running" : "js-done"}`}>{c.busy ? "Busy" : "Idle"}</span>
                      </div>
                      {c.job && (
                        <>
                          <div class="job-top">
                            <ItemIcon name={c.job.name} displayName={c.job.displayName} />
                            <span class="job-name">
                              {c.job.displayName} <span class="muted">× {full(c.job.count)}</span>
                            </span>
                            <span class="muted small">{Math.round(c.job.elapsedMs / 1000)} s</span>
                          </div>
                          <div class="job-bar">
                            <span class="job-fill" style={{ width: `${Math.round(c.job.progress * 100)}%` }} />
                          </div>
                        </>
                      )}
                    </li>
                  ))}
                </ul>
              )}
              <h3 class="block-title">Running jobs</h3>
              {cpus.tasks.length ? (
                <JobList
                  conn={conn}
                  uplink={uplink}
                  widgetId={props.widgetId}
                  jobs={cpus.tasks.map((t) => ({ jobId: t.id, name: t.name, displayName: t.displayName, count: t.count, status: "running", progress: t.progress }))}
                />
              ) : (
                <p class="store-note">Nothing is crafting right now.</p>
              )}
            </>
          ) : (
            <p class="muted small">Loading…</p>
          ))}
      </div>
    </Sheet>
  );
}
