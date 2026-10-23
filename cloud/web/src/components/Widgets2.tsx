// v0.2 Lua widgets: chart, progress, indicator, select, number, text, buttons.
import { useEffect, useState } from "preact/hooks";
import type { MainframeConn } from "../lib/conn.ts";
import { keyOf } from "../lib/conn.ts";
import type { Scalar, Uplink, Widget } from "../lib/types.ts";
import { BigNumber } from "./Widgets.tsx";

export interface W2Props {
  w: Widget;
  uplink: Uplink;
  conn: MainframeConn;
  locked: boolean;
}

const num = (v: unknown, d: number) => (typeof v === "number" && Number.isFinite(v) ? v : d);
const fmt = (v: number) => (Math.abs(v) >= 10_000 ? Math.round(v).toLocaleString("en-US") : Number.isInteger(v) ? String(v) : v.toFixed(Math.abs(v) < 10 ? 2 : 1));

function current(conn: MainframeConn, uplinkId: string, w: Widget): { value: Scalar | undefined; pending: boolean } {
  const key = keyOf(uplinkId, w.id);
  const p = conn.pending.value;
  const scalar = (v: unknown): Scalar | undefined => (v === null || ["string", "number", "boolean"].includes(typeof v) ? (v as Scalar) : undefined);
  if (key in p) return { value: scalar(p[key]), pending: true };
  return { value: scalar(w.value) ?? w.opts?.value, pending: false };
}

const COLOR_VAR: Record<string, string> = {
  green: "var(--green)", red: "var(--red)", yellow: "var(--yellow)", cyan: "var(--cyan)", gray: "var(--faint)", gold: "var(--gold)",
  purple: "var(--purple)", orange: "var(--orange)",
};

// -------------------------------------------------------------------- chart
export function Chart({ w }: W2Props) {
  const data = w.history ?? [];
  const o = w.opts ?? {};
  const spark = o.style === "sparkline";
  const lo = typeof o.min === "number" ? o.min : data.length ? Math.min(...data) : 0;
  const hiRaw = typeof o.max === "number" ? o.max : data.length ? Math.max(...data) : 1;
  const hi = hiRaw > lo ? hiRaw : lo + 1;
  const W = 300;
  const H = spark ? 44 : 110;
  const cap = Math.max(2, num(o.points, 60));
  const step = W / (cap - 1);
  const offset = (cap - data.length) * step; // new points enter from the right
  const pts = data.map((v, i) => [offset + i * step, H - 4 - ((Math.min(hi, Math.max(lo, v)) - lo) / (hi - lo)) * (H - 8)] as const);
  const line = pts.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(" ");
  const area = pts.length ? `${pts[0][0].toFixed(1)},${H} ${line} ${pts[pts.length - 1][0].toFixed(1)},${H}` : "";
  const last = data.at(-1);
  const warn = typeof o.warn === "number" ? o.warn : undefined;
  const crit = typeof o.crit === "number" ? o.crit : undefined;
  const zone = last === undefined ? "ok" : crit !== undefined && last >= crit ? "crit" : warn !== undefined && last >= warn ? "warn" : "ok";
  const yOf = (v: number) => H - 4 - ((v - lo) / (hi - lo)) * (H - 8);
  return (
    <div class={`w2 chart ${spark ? "is-spark" : ""} zone-${zone}`}>
      <div class="w2-head">
        <span class="w2-label">{w.label ?? w.id}</span>
        <span class="chart-now">
          {last === undefined ? "—" : fmt(last)}
          {o.unit && <small> {o.unit}</small>}
        </span>
      </div>
      <div class="chart-box screen">
        <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" class="chart-svg" role="img" aria-label={`${w.label ?? w.id}: ${data.length} points, latest ${last ?? "none"}`}>
          {!spark &&
            [0.25, 0.5, 0.75].map((f) => <line key={f} x1="0" x2={W} y1={H * f} y2={H * f} class="chart-grid" vector-effect="non-scaling-stroke" />)}
          {warn !== undefined && warn > lo && warn < hi && <line x1="0" x2={W} y1={yOf(warn)} y2={yOf(warn)} class="chart-warn" vector-effect="non-scaling-stroke" />}
          {crit !== undefined && crit > lo && crit < hi && <line x1="0" x2={W} y1={yOf(crit)} y2={yOf(crit)} class="chart-crit" vector-effect="non-scaling-stroke" />}
          {area && <polygon points={area} class="chart-area" />}
          {line && <polyline points={line} class="chart-line" vector-effect="non-scaling-stroke" />}
        </svg>
        {!spark && (
          <div class="chart-scale" aria-hidden="true">
            <span>{fmt(hi)}</span>
            <span>{fmt(lo)}</span>
          </div>
        )}
        {data.length < 2 && <div class="chart-empty">Waiting for data…</div>}
      </div>
    </div>
  );
}

// ----------------------------------------------------------------- progress
export function Progress({ w, uplink, conn }: W2Props) {
  const o = w.opts ?? {};
  const min = num(o.min, 0);
  const max = num(o.max, 100) > min ? num(o.max, 100) : min + 1;
  const { value } = current(conn, uplink.uplinkId, w);
  const v = num(value, min);
  const pct = Math.min(100, Math.max(0, ((v - min) / (max - min)) * 100));
  if (o.display === "number") return <BigNumber label={w.label ?? w.id} value={fmt(v)} unit={o.unit} sub={`of ${fmt(max)} · ${Math.round(pct)} %`} />;
  return (
    <div class="w2 progress">
      <div class="w2-head">
        <span class="w2-label">{w.label ?? w.id}</span>
        <span class="w2-value">
          {fmt(v)}
          {o.unit ? ` ${o.unit}` : ""} <span class="muted">/ {fmt(max)}</span>
        </span>
      </div>
      <div class="pbar" role="progressbar" aria-valuemin={min} aria-valuemax={max} aria-valuenow={v}>
        <span class="pbar-fill" style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- indicator
export function Indicator({ w, uplink, conn }: W2Props) {
  const { value } = current(conn, uplink.uplinkId, w);
  const on = value === true;
  const o = w.opts ?? {};
  const color = on ? COLOR_VAR[o.onColor ?? "green"] ?? "var(--green)" : COLOR_VAR[o.offColor ?? "gray"] ?? "var(--faint)";
  if (o.display === "lamp")
    return (
      <div class={`w2 lamp ${on ? "on" : "off"}`}>
        <span class="lamp-bulb" style={{ "--led": color }} aria-hidden="true" />
        <span class="w2-label">{w.label ?? w.id}</span>
        <span class="ind-state">{on ? "ON" : "OFF"}</span>
      </div>
    );
  return (
    <div class={`w2 indicator ${on ? "on" : "off"}`}>
      <span class="led-big" style={{ "--led": color }} aria-hidden="true" />
      <span class="w2-label">{w.label ?? w.id}</span>
      <span class="ind-state">{on ? "ON" : "OFF"}</span>
    </div>
  );
}

// ------------------------------------------------------------------- select
export function Select({ w, uplink, conn, locked }: W2Props) {
  const options = (w.opts?.options ?? []).filter((x) => typeof x === "string").slice(0, 32);
  const { value, pending } = current(conn, uplink.uplinkId, w);
  const id = `sel-${uplink.uplinkId}-${w.id}`;
  return (
    <div class={`w2 row-like ${locked ? "is-locked" : ""}`}>
      <label class="w2-label" for={id}>
        {w.label ?? w.id}
        {pending && <span class="row-hint">sending…</span>}
      </label>
      <select
        id={id}
        class="select"
        disabled={locked}
        value={typeof value === "string" ? value : ""}
        onChange={(e) => conn.action(uplink.uplinkId, w, (e.target as HTMLSelectElement).value, true)}
      >
        {typeof value !== "string" && <option value="">Choose…</option>}
        {options.map((x) => (
          <option key={x} value={x}>
            {x}
          </option>
        ))}
      </select>
    </div>
  );
}

// ----------------------------------------------------------- number / text
function useDraft(server: string) {
  const [draft, setDraft] = useState<string | null>(null);
  useEffect(() => setDraft(null), [server]);
  return [draft ?? server, draft !== null, setDraft] as const;
}

export function NumberInput({ w, uplink, conn, locked }: W2Props) {
  const o = w.opts ?? {};
  const { value, pending } = current(conn, uplink.uplinkId, w);
  const server = typeof value === "number" ? String(value) : "";
  const [text, dirty, setDraft] = useDraft(server);
  const n = Number(text);
  const min = typeof o.min === "number" ? o.min : -Infinity;
  const max = typeof o.max === "number" ? o.max : Infinity;
  const valid = text.trim() !== "" && Number.isFinite(n) && n >= min && n <= max;
  const send = () => {
    if (!valid || locked) return;
    const step = num(o.step, 0);
    const v = step > 0 ? Math.round((n - (Number.isFinite(min) ? min : 0)) / step) * step + (Number.isFinite(min) ? min : 0) : n;
    conn.action(uplink.uplinkId, w, Number(v.toFixed(6)), true);
    setDraft(null);
  };
  return (
    <form
      class={`w2 field-like ${locked ? "is-locked" : ""}`}
      onSubmit={(e) => {
        e.preventDefault();
        send();
      }}
    >
      <label class="w2-label">
        {w.label ?? w.id}
        {o.unit && <span class="muted"> ({o.unit})</span>}
        {pending && <span class="row-hint">sending…</span>}
      </label>
      <div class="input-row">
        <input
          class="input input-num"
          type="number"
          inputMode="decimal"
          value={text}
          min={Number.isFinite(min) ? min : undefined}
          max={Number.isFinite(max) ? max : undefined}
          step={o.step ?? "any"}
          disabled={locked}
          aria-invalid={dirty && !valid}
          onInput={(e) => setDraft((e.target as HTMLInputElement).value)}
        />
        <button type="submit" class="btn btn-set" disabled={locked || !dirty || !valid}>
          Set
        </button>
      </div>
      {dirty && !valid && text !== "" && (
        <span class="field-err">
          Allowed: {Number.isFinite(min) ? fmt(min) : "−∞"} – {Number.isFinite(max) ? fmt(max) : "∞"}
        </span>
      )}
    </form>
  );
}

export function TextInput({ w, uplink, conn, locked }: W2Props) {
  const maxLength = Math.min(256, num(w.opts?.maxLength, 64));
  const { value, pending } = current(conn, uplink.uplinkId, w);
  const server = typeof value === "string" ? value : "";
  const [text, dirty, setDraft] = useDraft(server);
  const send = () => {
    if (locked || !dirty) return;
    conn.action(uplink.uplinkId, w, text.slice(0, maxLength), true);
    setDraft(null);
  };
  return (
    <form
      class={`w2 field-like ${locked ? "is-locked" : ""}`}
      onSubmit={(e) => {
        e.preventDefault();
        send();
      }}
    >
      <label class="w2-label">
        {w.label ?? w.id}
        {pending && <span class="row-hint">sending…</span>}
      </label>
      <div class="input-row">
        <input
          class="input"
          type="text"
          value={text}
          maxLength={maxLength}
          disabled={locked}
          autoComplete="off"
          onInput={(e) => setDraft((e.target as HTMLInputElement).value)}
        />
        <button type="submit" class="btn btn-set" disabled={locked || !dirty}>
          Send
        </button>
      </div>
      {dirty && <span class="field-hint">{text.length}/{maxLength}</span>}
    </form>
  );
}

// ------------------------------------------------------------------ buttons
export function ButtonGroup({ w, uplink, conn, locked }: W2Props) {
  const options = (w.opts?.options ?? []).filter((x) => typeof x === "string").slice(0, 8);
  const { value, pending } = current(conn, uplink.uplinkId, w);
  return (
    <div class={`w2 ${locked ? "is-locked" : ""}`}>
      <div class="w2-head">
        <span class="w2-label">
          {w.label ?? w.id}
          {pending && <span class="row-hint">sending…</span>}
        </span>
      </div>
      <div class="segmented" role="radiogroup" aria-label={w.label ?? w.id}>
        {options.map((x) => (
          <button
            key={x}
            type="button"
            role="radio"
            aria-checked={value === x}
            class={`seg-btn ${value === x ? "on" : ""}`}
            disabled={locked}
            onClick={() => conn.action(uplink.uplinkId, w, x, true)}
          >
            {x}
          </button>
        ))}
      </div>
    </div>
  );
}
