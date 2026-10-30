// v0.2 storage widgets (AE2 / Refined Storage bridges): item_search, watchlist, jobs.
import { useEffect, useMemo, useRef, useState } from "preact/hooks";
import type { MainframeConn, ActionOutcome } from "../lib/conn.ts";
import { REJECT_TEXT } from "../lib/conn.ts";
import type { ItemRow, JobRow, Uplink, WatchRow, Widget } from "../lib/types.ts";
import { Sheet } from "./Overlays.tsx";
import { fmtCount } from "../lib/format.ts";
import { ItemIcon } from "./ItemIcon.tsx";
import { IconClose, IconPlus } from "./Icons.tsx";
import { AutoStockSheet, ItemDetailSheet, STATE_TEXT, StorageBrowser } from "./StorageV2.tsx";

// ------------------------------------------------------------------ helpers
const full = (n: number) => Math.round(n).toLocaleString("en-US");
const modOf = (name: string) => (name.includes(":") ? name.split(":")[0] : "");
const num = (v: unknown, d = 0) => (typeof v === "number" && Number.isFinite(v) ? v : d);
const str = (v: unknown, d = "") => (typeof v === "string" ? v : d);

export const CRAFT_ERRORS: Record<string, string> = {
  not_craftable: "There is no crafting pattern for this item in the network.",
  no_cpu: "All crafting CPUs are busy. Try again when a job has finished.",
  missing_resources: "Not enough ingredients in the network for this amount. Try a smaller amount.",
  too_many: "That amount is too large.",
  unknown_item: "This item is not in the network any more.",
  already_watched: "This item is already on the watchlist.",
};
const errorText = (r: ActionOutcome, max?: number) =>
  r.error === "too_many" && max ? `Pick an amount between 1 and ${full(max)}.` : CRAFT_ERRORS[r.error ?? ""] ?? REJECT_TEXT[r.error ?? ""] ?? "That did not work. Please try again.";

const STATES = ["idle", "crafting", "waiting_cpu", "missing", "paused"] as const;
function watchRows(w: Widget | undefined): WatchRow[] {
  return Array.isArray(w?.value)
    ? w!.value
        .map((r) => ({
          name: str(r.name),
          displayName: str(r.displayName) || str(r.name),
          count: num(r.count),
          min: num(r.min),
          ...(typeof r.target === "number" ? { target: r.target, autoCraft: r.autoCraft === true, state: (STATES as readonly string[]).includes(str(r.state)) ? (r.state as WatchRow["state"]) : "idle" } : {}),
        }))
        .filter((r) => r.name)
    : [];
}
function jobRows(w: Widget): JobRow[] {
  return Array.isArray(w.value)
    ? w.value
        .map((r) => ({
          jobId: String(r.jobId ?? ""),
          name: str(r.name),
          displayName: str(r.displayName) || undefined,
          count: num(r.count),
          status: (["queued", "running", "waiting", "done", "failed", "cancelled"].includes(str(r.status)) ? r.status : "queued") as JobRow["status"],
          progress: Math.min(1, Math.max(0, num(r.progress))),
          ...(["dashboard", "lua", "autostock", "rule"].includes(str(r.source)) ? { source: r.source as JobRow["source"] } : {}),
          ...(r.notify === true ? { notify: true } : {}),
          ...(str(r.error) ? { error: str(r.error).slice(0, 200) } : {}),
        }))
        .filter((r) => r.jobId || r.name)
    : [];
}
const prettyName = (name: string) => title(name.split(":").pop() ?? name);
const title = (s: string) => s.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());

function ModTag({ name }: { name: string }) {
  const mod = modOf(name);
  return mod ? <span class="mod-tag">{mod}</span> : null;
}

/** Debounced live search against an item_search widget. */
function useItemSearch(conn: MainframeConn, uplinkId: string, widgetId: string | undefined, q: string, delay = 250) {
  const [state, setState] = useState<{ rows: ItemRow[]; truncated: boolean; loading: boolean; error: string | null; q: string }>({
    rows: [],
    truncated: false,
    loading: false,
    error: null,
    q: "",
  });
  const seq = useRef(0);
  useEffect(() => {
    const query = q.trim();
    if (!widgetId || !query) {
      seq.current++;
      setState({ rows: [], truncated: false, loading: false, error: null, q: "" });
      return;
    }
    const my = ++seq.current;
    setState((s) => ({ ...s, loading: true }));
    const t = setTimeout(async () => {
      try {
        const r = await conn.query(uplinkId, widgetId, query);
        if (seq.current === my) setState({ rows: r.rows, truncated: r.truncated, loading: false, error: null, q: query });
      } catch (e) {
        if (seq.current !== my) return;
        const code = (e as Error).message;
        const text = code === "rate_limited" ? "Searching too fast – one moment…" : code === "timeout" ? "The network did not answer. Try again." : REJECT_TEXT[code] ?? "Search is not available right now.";
        setState((s) => ({ ...s, loading: false, error: text }));
      }
    }, delay);
    return () => clearTimeout(t);
  }, [q, widgetId, uplinkId, conn]);
  return state;
}

// -------------------------------------------------------------- item search
export function ItemSearch({ w, uplink, conn, locked }: { w: Widget; uplink: Uplink; conn: MainframeConn; locked: boolean }) {
  const [q, setQ] = useState("");
  const [open, setOpen] = useState<ItemRow | null>(null);
  const [classic, setClassic] = useState(false);
  const [browse, setBrowse] = useState(false);
  const canSearch = conn.status.value === "open" && !!conn.info.value?.online && uplink.online;
  const res = useItemSearch(conn, uplink.uplinkId, canSearch ? w.id : undefined, q);
  const watch = uplink.widgets.find((x) => x.type === "watchlist");
  const watched = new Set(watchRows(watch).map((r) => r.name));
  const inputRef = useRef<HTMLInputElement>(null);

  return (
    <section class="store-sec item-search" aria-label={w.label ?? "Search"}>
      <div class="search-bar">
        <span class="search-icon" aria-hidden="true">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4">
            <circle cx="10.5" cy="10.5" r="6.5" />
            <path d="M15.5 15.5L21 21" />
          </svg>
        </span>
        <input
          ref={inputRef}
          class="search-input"
          type="search"
          inputMode="search"
          enterKeyHint="search"
          placeholder={canSearch ? `${w.label ?? "Search"}…` : "Search unavailable while offline"}
          value={q}
          disabled={!canSearch}
          maxLength={64}
          autoComplete="off"
          autoCorrect="off"
          spellcheck={false}
          aria-label={w.label ?? "Search items"}
          onInput={(e) => setQ((e.target as HTMLInputElement).value)}
        />
        {res.loading && <span class="search-spin" aria-label="Searching" />}
        {!q && canSearch && (
          <button type="button" class="btn btn-small sb-browse" onClick={() => setBrowse(true)} title="Browse all items, craftables, fluids, CPUs">
            Browse
          </button>
        )}
        {q && !res.loading && (
          <button
            type="button"
            class="search-clear"
            aria-label="Clear search"
            onClick={() => {
              setQ("");
              inputRef.current?.focus();
            }}
          >
            <IconClose size={16} />
          </button>
        )}
      </div>

      {res.error && <p class="store-note warn">{res.error}</p>}
      {!q.trim() && !res.error && <p class="store-note">Type an item name or id, e.g. “iron”, “processor” or “ae2:”.</p>}
      {q.trim() && res.q && res.rows.length === 0 && !res.loading && !res.error && <p class="store-note">No items match “{res.q}”.</p>}

      {res.rows.length > 0 && (
        <ul class={`item-list ${res.loading ? "is-loading" : ""}`}>
          {res.rows.map((r) => (
            <li key={r.name}>
              <button type="button" class="item-row" onClick={() => setOpen(r)}>
                <ItemIcon name={r.name} displayName={r.displayName} />
                <span class="item-main">
                  <span class="item-name">{r.displayName}</span>
                  <span class="item-sub">
                    <ModTag name={r.name} />
                    {watched.has(r.name) && <span class="mini-badge watch">watched</span>}
                  </span>
                </span>
                <span class="item-side">
                  <span class={`item-count ${r.count === 0 ? "zero" : ""}`} title={full(r.count)}>
                    {fmtCount(r.count)}
                  </span>
                  {r.craftable && <span class="craft-badge">craftable</span>}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {res.truncated && <p class="store-note">Showing the first {res.rows.length} results – type more to narrow it down.</p>}

      {browse && <StorageBrowser conn={conn} uplink={uplink} widgetId={w.id} onClose={() => setBrowse(false)} />}
      {open && !classic && (
        <ItemDetailSheet conn={conn} uplink={uplink} widgetId={w.id} name={open.name} displayName={open.displayName} onClose={() => setOpen(null)} onFallback={() => setClassic(true)} />
      )}
      {open && classic && (
        <ItemSheet
          item={open}
          w={w}
          uplink={uplink}
          conn={conn}
          locked={locked}
          watch={watch}
          watched={watched.has(open.name)}
          onClose={() => setOpen(null)}
        />
      )}
    </section>
  );
}

const AMOUNTS = [1, 16, 64];

function ItemSheet(props: {
  item: ItemRow;
  w: Widget;
  uplink: Uplink;
  conn: MainframeConn;
  locked: boolean;
  watch?: Widget;
  watched: boolean;
  onClose: () => void;
}) {
  const { item, w, uplink, conn, locked } = props;
  const max = num(w.opts?.max, 4096);
  const [amount, setAmount] = useState<number | "custom">(64);
  const [custom, setCustom] = useState("");
  const [craft, setCraft] = useState<{ kind: "idle" } | { kind: "sending" } | { kind: "ok"; jobId?: string; count: number } | { kind: "error"; text: string }>({ kind: "idle" });
  const [watchState, setWatchState] = useState<{ kind: "idle" | "sending" | "ok" } | { kind: "error"; text: string }>({ kind: "idle" });
  const [min, setMin] = useState(String(Math.max(1, Math.round(item.count / 2 / 64) * 64) || 64));
  const count = amount === "custom" ? Math.floor(Number(custom)) : amount;
  const countValid = Number.isInteger(count) && count >= 1 && count <= max;
  const reason = !conn.can("storage.craft") ? (conn.can("act.buttons") ? "You may not start crafting jobs on this mainframe." : "This device is read-only.") : locked ? "Controls are locked right now (see the banner above)." : null;

  const startCraft = () => {
    if (!countValid || reason) return;
    setCraft({ kind: "sending" });
    const sent = conn.action(uplink.uplinkId, w, { op: "craft", name: item.name, count }, false, {
      onResult: (r) => {
        setCraft(r.ok ? { kind: "ok", jobId: r.jobId, count } : { kind: "error", text: errorText(r, max) });
        return true;
      },
    });
    if (!sent) setCraft({ kind: "error", text: "Not connected – try again in a moment." });
  };

  const addWatch = () => {
    if (!props.watch || reason) return;
    const m = Math.floor(Number(min));
    if (!Number.isInteger(m) || m < 0) return setWatchState({ kind: "error", text: "Enter a minimum of 0 or more." });
    setWatchState({ kind: "sending" });
    conn.action(uplink.uplinkId, props.watch, { op: "add", name: item.name, min: m }, false, {
      onResult: (r) => {
        setWatchState(r.ok ? { kind: "ok" } : { kind: "error", text: errorText(r) });
        return true;
      },
    });
  };

  return (
    <Sheet title={item.displayName} onClose={props.onClose}>
      <div class="item-head with-icon">
        <ItemIcon name={item.name} displayName={item.displayName} size={48} />
        <div class="item-big-count">
          <span class="big">{full(item.count)}</span>
          <span class="muted small">in the network</span>
        </div>
        <div class="item-ids">
          <ModTag name={item.name} />
          <code class="item-id">{item.name}</code>
        </div>
      </div>

      {item.craftable ? (
        <div class="sheet-block">
          <h3 class="block-title">Craft</h3>
          {craft.kind === "ok" ? (
            <div class="result result-ok" role="status">
              <span class="result-icon">✓</span>
              <div>
                <b>Crafting {full(craft.count)}× started.</b>
                <div class="muted small">{craft.jobId ? `Job #${craft.jobId} – progress shows under crafting jobs.` : "Progress shows under crafting jobs."}</div>
              </div>
            </div>
          ) : (
            <>
              <div class="amounts" role="radiogroup" aria-label="Amount">
                {AMOUNTS.map((a) => (
                  <button key={a} type="button" role="radio" aria-checked={amount === a} class={`chip ${amount === a ? "on" : ""}`} onClick={() => setAmount(a)}>
                    {a}
                  </button>
                ))}
                <button type="button" role="radio" aria-checked={amount === "custom"} class={`chip ${amount === "custom" ? "on" : ""}`} onClick={() => setAmount("custom")}>
                  Custom
                </button>
              </div>
              {amount === "custom" && (
                <input
                  class="input input-amount"
                  type="number"
                  inputMode="numeric"
                  min={1}
                  max={max}
                  placeholder={`1 – ${full(max)}`}
                  value={custom}
                  autoFocus
                  onInput={(e) => setCustom((e.target as HTMLInputElement).value.replace(/\D/g, "").slice(0, 7))}
                  aria-label="Custom amount"
                />
              )}
              {amount === "custom" && custom && !countValid && <p class="field-err">Pick an amount between 1 and {full(max)}.</p>}
              {craft.kind === "error" && (
                <div class="result result-err" role="alert">
                  <span class="result-icon">!</span>
                  <div>{craft.text}</div>
                </div>
              )}
              {reason && <p class="muted small">{reason}</p>}
              <button type="button" class="pxbtn pxbtn-gold pxbtn-wide" disabled={!countValid || !!reason || craft.kind === "sending"} onClick={startCraft}>
                <span>{craft.kind === "sending" ? "Starting…" : countValid ? `Craft ${full(count)}×` : "Craft"}</span>
              </button>
            </>
          )}
        </div>
      ) : (
        <p class="muted small">Not craftable – there is no pattern for this item.</p>
      )}

      {props.watch && (
        <div class="sheet-block">
          <h3 class="block-title">Watchlist</h3>
          {props.watched || watchState.kind === "ok" ? (
            <p class="muted small">✓ On the watchlist. You get a warning when it drops below its minimum.</p>
          ) : (
            <>
              <label class="field">
                <span class="field-label">Warn me below</span>
                <input class="input" type="number" inputMode="numeric" min={0} value={min} onInput={(e) => setMin((e.target as HTMLInputElement).value.replace(/\D/g, "").slice(0, 10))} />
              </label>
              {watchState.kind === "error" && <p class="field-err">{watchState.text}</p>}
              <button type="button" class="btn btn-wide" disabled={!!reason || watchState.kind === "sending"} onClick={addWatch}>
                <IconPlus size={16} /> Add to watchlist
              </button>
            </>
          )}
        </div>
      )}
    </Sheet>
  );
}

// ---------------------------------------------------------------- watchlist
export function Watchlist({ w, uplink, conn, locked, viewOnly }: { w: Widget; uplink: Uplink; conn: MainframeConn; locked: boolean; viewOnly?: boolean }) {
  const rows = useMemo(() => {
    const list = watchRows(w);
    // items below their minimum first
    return list.sort((a, b) => Number(b.count < b.min) - Number(a.count < a.min) || a.displayName.localeCompare(b.displayName));
  }, [w.value]);
  const [edit, setEdit] = useState<WatchRow | null>(null);
  const [adding, setAdding] = useState(false);
  const [stock, setStock] = useState<WatchRow | null>(null);
  const searchW = uplink.widgets.find((x) => x.type === "item_search");
  const below = rows.filter((r) => r.count < r.min).length;
  const readOnly = locked || !conn.can("storage.autostock");

  return (
    <section class="store-sec store-half" aria-label={w.label ?? "Watchlist"}>
      <header class="sec-head">
        <h4 class="sec-title">
          {w.label ?? "Watchlist"}
          {below > 0 && <span class="mini-badge low">{below} low</span>}
        </h4>
        {!viewOnly && (
          <button type="button" class="btn btn-small" disabled={readOnly} title={readOnly ? "Needs the operator role and a working connection" : undefined} onClick={() => setAdding(true)}>
            <IconPlus size={14} /> Add
          </button>
        )}
      </header>
      {rows.length === 0 ? (
        <div class="store-empty">
          <p class="store-note">Nothing watched yet. Get a warning when something runs low – or let the network craft it for you.</p>
          {!viewOnly && (
            <button
              type="button"
              class="btn store-cta"
              disabled={readOnly}
              onClick={() => setStock({ name: "minecraft:iron_ingot", displayName: "Iron Ingot", count: 0, min: 1000 })}
            >
              Keep 1,000 iron in stock automatically →
            </button>
          )}
        </div>
      ) : (
        <ul class="watch-list">
          {rows.map((r) => {
            const ratio = r.min > 0 ? r.count / r.min : 1;
            const tone = r.count < r.min ? "low" : ratio < 1.1 ? "near" : "ok";
            return (
              <li key={r.name}>
                <button type="button" class={`watch-row tone-${tone} ${viewOnly ? "static" : ""}`} disabled={viewOnly} onClick={() => !viewOnly && (r.target !== undefined || searchW ? setStock(r) : setEdit(r))}>
                  <ItemIcon name={r.name} displayName={r.displayName} />
                  <span class="item-main">
                    <span class="item-name">{r.displayName}</span>
                    <span class="item-sub">
                      <ModTag name={r.name} />
                      {r.autoCraft && <span class={`stock-badge sb-${r.state ?? "idle"}`}>{r.state && r.state !== "idle" ? STATE_TEXT[r.state] : `auto → ${fmtCount(r.target ?? r.min)}`}</span>}
                    </span>
                  </span>
                  <span class="watch-side">
                    <span class="watch-nums">
                      <b title={full(r.count)}>{fmtCount(r.count)}</b>
                      <span class="muted"> / {fmtCount(r.min)}</span>
                    </span>
                    <span class="watch-bar" aria-hidden="true">
                      <span class="watch-fill" style={{ width: `${Math.min(100, ratio * 50)}%` }} />
                      <span class="watch-min" />
                    </span>
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
      )}
      {edit && <WatchEdit row={edit} w={w} uplink={uplink} conn={conn} readOnly={readOnly} onClose={() => setEdit(null)} />}
      {stock && (
        <AutoStockSheet
          conn={conn}
          uplink={uplink}
          widgetId={searchW?.id ?? w.id}
          item={stock}
          current={stock.target !== undefined ? { min: stock.min, target: stock.target, autoCraft: !!stock.autoCraft, state: stock.state } : rows.some((x) => x.name === stock.name) ? { min: stock.min, target: Math.max(stock.min, stock.min * 2), autoCraft: false } : null}
          onClose={() => setStock(null)}
        />
      )}
      {adding && <WatchAdd w={w} uplink={uplink} conn={conn} existing={new Set(rows.map((r) => r.name))} onClose={() => setAdding(false)} />}
    </section>
  );
}

function WatchEdit(props: { row: WatchRow; w: Widget; uplink: Uplink; conn: MainframeConn; readOnly: boolean; onClose: () => void }) {
  const { row, w, uplink, conn } = props;
  const [min, setMin] = useState(String(row.min));
  const [state, setState] = useState<{ kind: "idle" | "sending" } | { kind: "error"; text: string }>({ kind: "idle" });
  const m = Math.floor(Number(min));
  const valid = min !== "" && Number.isInteger(m) && m >= 0;
  const step = row.min >= 10_000 ? 1000 : row.min >= 1000 ? 100 : 16;
  const send = (value: { op: string; name: string; min?: number }) => {
    setState({ kind: "sending" });
    conn.action(uplink.uplinkId, w, value, false, {
      onResult: (r) => {
        if (r.ok) props.onClose();
        else setState({ kind: "error", text: errorText(r) });
        return true;
      },
    });
  };
  return (
    <Sheet title={row.displayName} onClose={props.onClose}>
      <div class="item-head with-icon">
        <ItemIcon name={row.name} displayName={row.displayName} size={48} />
        <div class="item-big-count">
          <span class={`big ${row.count < row.min ? "t-red" : ""}`}>{full(row.count)}</span>
          <span class="muted small">in the network · minimum {full(row.min)}</span>
        </div>
        <div class="item-ids">
          <ModTag name={row.name} />
          <code class="item-id">{row.name}</code>
        </div>
      </div>
      <label class="field">
        <span class="field-label">Warn me below</span>
        <div class="stepper">
          <button type="button" class="key-sm" aria-label="Less" disabled={props.readOnly} onClick={() => setMin(String(Math.max(0, (valid ? m : 0) - step)))}>
            −
          </button>
          <input
            class="input"
            type="number"
            inputMode="numeric"
            min={0}
            value={min}
            disabled={props.readOnly}
            onInput={(e) => setMin((e.target as HTMLInputElement).value.replace(/\D/g, "").slice(0, 10))}
          />
          <button type="button" class="key-sm" aria-label="More" disabled={props.readOnly} onClick={() => setMin(String((valid ? m : 0) + step))}>
            +
          </button>
        </div>
      </label>
      {state.kind === "error" && <p class="field-err">{state.text}</p>}
      {props.readOnly && <p class="muted small">Controls are locked right now.</p>}
      <div class="sheet-actions">
        <button type="button" class="btn btn-danger-ghost" disabled={props.readOnly || state.kind === "sending"} onClick={() => send({ op: "remove", name: row.name })}>
          Remove
        </button>
        <button
          type="button"
          class="pxbtn pxbtn-gold"
          disabled={props.readOnly || !valid || m === row.min || state.kind === "sending"}
          onClick={() => send({ op: "set_min", name: row.name, min: m })}
        >
          <span>Save</span>
        </button>
      </div>
    </Sheet>
  );
}

function WatchAdd(props: { w: Widget; uplink: Uplink; conn: MainframeConn; existing: Set<string>; onClose: () => void }) {
  const { w, uplink, conn } = props;
  const searchW = uplink.widgets.find((x) => x.type === "item_search");
  const [q, setQ] = useState("");
  const [picked, setPicked] = useState<{ name: string; displayName: string } | null>(null);
  const [min, setMin] = useState("64");
  const [state, setState] = useState<{ kind: "idle" | "sending" } | { kind: "error"; text: string }>({ kind: "idle" });
  const res = useItemSearch(conn, uplink.uplinkId, searchW?.id, picked ? "" : q);
  // typing a full id ("mod:item") works without a search widget too
  const typedId = /^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(q.trim()) ? q.trim() : null;
  const target = picked ?? (typedId ? { name: typedId, displayName: prettyName(typedId) } : null);
  const m = Math.floor(Number(min));
  const add = () => {
    if (!target || !Number.isInteger(m) || m < 0) return;
    setState({ kind: "sending" });
    conn.action(uplink.uplinkId, w, { op: "add", name: target.name, min: m }, false, {
      onResult: (r) => {
        if (r.ok) props.onClose();
        else setState({ kind: "error", text: errorText(r) });
        return true;
      },
    });
  };
  return (
    <Sheet title="Watch an item" onClose={props.onClose} tall={!!searchW}>
      {picked ? (
        <div class="picked">
          <ItemIcon name={picked.name} displayName={picked.displayName} />
          <span class="item-main">
            <span class="item-name">{picked.displayName}</span>
            <span class="item-sub">
              <ModTag name={picked.name} />
            </span>
          </span>
          <button type="button" class="btn btn-small" onClick={() => setPicked(null)}>
            Change
          </button>
        </div>
      ) : (
        <>
          <label class="field">
            <span class="field-label">{searchW ? "Search or type an item id" : "Item id"}</span>
            <input
              class="input"
              type="search"
              placeholder={searchW ? "e.g. diamond or minecraft:diamond" : "minecraft:diamond"}
              value={q}
              autoFocus
              autoComplete="off"
              spellcheck={false}
              maxLength={64}
              onInput={(e) => setQ((e.target as HTMLInputElement).value)}
            />
          </label>
          {res.rows.length > 0 && (
            <ul class="item-list compact">
              {res.rows.slice(0, 8).map((r) => (
                <li key={r.name}>
                  <button type="button" class="item-row" disabled={props.existing.has(r.name)} onClick={() => setPicked(r)}>
                    <ItemIcon name={r.name} displayName={r.displayName} />
                    <span class="item-main">
                      <span class="item-name">{r.displayName}</span>
                      <span class="item-sub">
                        <ModTag name={r.name} />
                        {props.existing.has(r.name) && <span class="mini-badge watch">watched</span>}
                      </span>
                    </span>
                    <span class="item-count">{fmtCount(r.count)}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
      <label class="field">
        <span class="field-label">Warn me below</span>
        <input class="input" type="number" inputMode="numeric" min={0} value={min} onInput={(e) => setMin((e.target as HTMLInputElement).value.replace(/\D/g, "").slice(0, 10))} />
      </label>
      {state.kind === "error" && <p class="field-err">{state.text}</p>}
      <button type="button" class="pxbtn pxbtn-gold pxbtn-wide sheet-cta" disabled={!target || min === "" || state.kind === "sending"} onClick={add}>
        <span>{target ? `Watch ${target.displayName}` : "Pick an item"}</span>
      </button>
    </Sheet>
  );
}

// --------------------------------------------------------------------- jobs
// "waiting": an auto-stock request that found no free crafting CPU; it is planned again later (not a failure).
const STATUS_TEXT: Record<JobRow["status"], string> = { queued: "Queued", running: "Running", waiting: "Waiting for CPU", done: "Done", failed: "Failed", cancelled: "Cancelled" };

export function Jobs({ w, uplink, conn }: { w: Widget; uplink?: Uplink; conn?: MainframeConn }) {
  const rows = jobRows(w);
  const [browse, setBrowse] = useState(false);
  const searchW = uplink?.widgets.find((x) => x.type === "item_search");
  const active = rows.filter((r) => r.status === "running" || r.status === "queued").length;
  return (
    <section class="store-sec store-half" aria-label={w.label ?? "Crafting jobs"}>
      <header class="sec-head">
        <h4 class="sec-title">
          {w.label ?? "Crafting jobs"}
          {active > 0 && <span class="mini-badge run">{active} active</span>}
        </h4>
      </header>
      {rows.length === 0 ? (
        <div class="store-empty">
          <p class="store-note">Nothing is crafting right now.</p>
          {conn && uplink && searchW && (
            <button type="button" class="btn store-cta" onClick={() => setBrowse(true)}>
              Browse craftable items →
            </button>
          )}
        </div>
      ) : (
        <ul class="job-list">
          {rows.map((j) => (
            <li key={j.jobId || j.name} class={`job st-${j.status}`}>
              <div class="job-top">
                <ItemIcon name={j.name} displayName={j.displayName ?? prettyName(j.name)} />
                <span class="job-name">
                  {j.displayName ?? prettyName(j.name)} <span class="muted">× {full(j.count)}</span>
                  {j.source === "autostock" && <span class="mini-badge run">auto</span>}
                  {j.notify && (
                    <span class="mini-badge" title="You get a notice when it finishes">
                      notify
                    </span>
                  )}
                </span>
                <span class={`job-status js-${j.status}`}>{STATUS_TEXT[j.status]}</span>
                {conn && uplink && conn.can("storage.craft") && (j.status === "running" || j.status === "queued") && (
                  <button type="button" class="icon-btn sm" aria-label={`Cancel ${j.displayName ?? j.name}`} title="Cancel" onClick={() => conn.storage(uplink.uplinkId, "cancel", { jobId: j.jobId }, searchW?.id).catch(() => {})}>
                    ×
                  </button>
                )}
              </div>
              <div class="job-bar" role="progressbar" aria-label={`${j.displayName ?? j.name} progress`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(j.progress * 100)}>
                <span class="job-fill" style={{ width: `${Math.round((j.status === "done" ? 1 : j.progress) * 100)}%` }} />
              </div>
            </li>
          ))}
        </ul>
      )}
      {browse && conn && uplink && <StorageBrowser conn={conn} uplink={uplink} widgetId={searchW?.id} initialTab="craftable" onClose={() => setBrowse(false)} />}
    </section>
  );
}
