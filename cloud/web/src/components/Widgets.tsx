import { useEffect, useRef, useState } from "preact/hooks";
import type { MainframeConn } from "../lib/conn.ts";
import { formatDuration, keyOf, rejectText, type ActionOutcome } from "../lib/conn.ts";
import type { Scalar, Uplink, Widget } from "../lib/types.ts";
import { IconLock } from "./Icons.tsx";
import { labelFit, splitEntries } from "../lib/format.ts";
import { ConfirmDialog, PinDialog, type PinState } from "./Overlays.tsx";

export interface WidgetProps {
  w: Widget;
  uplink: Uplink;
  conn: MainframeConn;
  locked: boolean;
}

const num = (v: unknown, fallback: number) => (typeof v === "number" && Number.isFinite(v) ? v : fallback);

function fmtNumber(v: number) {
  const abs = Math.abs(v);
  if (abs >= 10_000) return Math.round(v).toLocaleString("en-US");
  if (Number.isInteger(v)) return String(v);
  return v.toFixed(abs < 10 ? 2 : 1);
}

/** Current value of a widget: optimistic value first, then the server value, then opts.value. */
function useValue(conn: MainframeConn, uplinkId: string, w: Widget): { value: Scalar | undefined; pending: boolean } {
  const key = keyOf(uplinkId, w.id);
  const p = conn.pending.value;
  const scalar = (v: unknown): Scalar | undefined => (v === null || ["string", "number", "boolean"].includes(typeof v) ? (v as Scalar) : undefined);
  if (key in p) return { value: scalar(p[key]), pending: true };
  return { value: scalar(w.value) ?? w.opts?.value, pending: false };
}

/**
 * Sends an action, asking for the mainframe PIN first when the widget has opts.pin.
 * Returns `send(value, onCancel?)` and the dialog element to render.
 */
function usePinGate(conn: MainframeConn, uplink: Uplink, w: Widget, optimistic: boolean, after?: () => void) {
  const [ask, setAsk] = useState<{ value: Scalar; onCancel?: () => void } | null>(null);
  const [state, setState] = useState<PinState>({ kind: "idle" });
  const needsPin = w.opts?.pin === true;
  const lockedFor = () => Math.ceil((conn.pinLockedUntil.value - Date.now()) / 1000);
  const lockText = (sec: number) => rejectText({ ok: false, error: "pin_locked", retryAfter: sec });

  const send = (value: Scalar, onCancel?: () => void) => {
    if (!needsPin) {
      if (conn.action(uplink.uplinkId, w, value, optimistic)) after?.();
      return;
    }
    const sec = lockedFor();
    setState(sec > 0 ? { kind: "locked", text: lockText(sec) } : { kind: "idle" });
    setAsk({ value, onCancel });
  };

  const submit = (pin: string) => {
    if (!ask) return;
    setState({ kind: "checking" });
    const value = ask.value;
    const reqId = conn.action(uplink.uplinkId, w, value, optimistic, {
      pin,
      onResult: (r: ActionOutcome) => {
        if (r.ok) {
          setAsk(null);
          after?.();
          return true;
        }
        if (r.error === "pin_wrong" || r.error === "wrong_pin" || r.error === "pin_required" || r.error === "pin_not_set") {
          setState({ kind: "error", text: rejectText(r) });
          return true;
        }
        if (r.error === "pin_locked") {
          setState({ kind: "locked", text: rejectText(r) });
          return true;
        }
        setAsk(null); // other errors: close and let the toast explain
        return false;
      },
    });
    if (!reqId) setAsk(null);
  };

  const dialog = ask && (
    <PinDialog
      title={`${w.label ?? w.id}`}
      text={`PIN-protected control on ${uplink.title || "this uplink"}.`}
      color={w.opts?.color ?? "gold"}
      state={state}
      onCancel={() => {
        ask.onCancel?.();
        setAsk(null);
      }}
      onSubmit={submit}
    />
  );
  return { send, dialog, needsPin };
}

export { formatDuration };

// ------------------------------------------------------------------- gauge
type Zone = "ok" | "warn" | "crit";

export function zoneOf(v: number, warn?: number, crit?: number): Zone {
  // crit < warn means "lower is worse" (e.g. coolant level)
  const lowBad = warn != null && crit != null ? crit < warn : false;
  if (crit != null && (lowBad ? v <= crit : v >= crit)) return "crit";
  if (warn != null && (lowBad ? v <= warn : v >= warn)) return "warn";
  return "ok";
}

const SEGMENTS = 30;
const SWEEP = 240;

export function Gauge({ w, uplink, conn }: WidgetProps) {
  const o = w.opts ?? {};
  const min = num(o.min, 0);
  const max = num(o.max, 100) > min ? num(o.max, 100) : min + 1;
  const { value } = useValue(conn, uplink.uplinkId, w);
  const known = typeof value === "number" && Number.isFinite(value);
  const v = known ? (value as number) : min;
  const pct = Math.min(1, Math.max(0, (v - min) / (max - min)));
  const zone = known ? zoneOf(v, o.warn, o.crit) : "ok";
  const lit = Math.round(pct * SEGMENTS);
  const cx = 60;
  const cy = 58;
  if (o.display === "bar")
    return (
      <div class={`gauge-bar zone-${zone}`} role="meter" aria-label={w.label ?? w.id} aria-valuemin={min} aria-valuemax={max} aria-valuenow={known ? v : undefined}>
        <div class="w2-head">
          <span class="w2-label">{w.label ?? w.id}</span>
          <span class="w2-value">
            {known ? fmtNumber(v) : "—"}
            {o.unit ? ` ${o.unit}` : ""}
            {zone !== "ok" && <span class={`gauge-flag flag-${zone}`}>{zone === "crit" ? "CRIT" : "WARN"}</span>}
          </span>
        </div>
        <div class="pbar seg-bar">
          <span class="pbar-fill" style={{ width: `${Math.round(pct * 100)}%` }} />
        </div>
      </div>
    );
  if (o.display === "number") return <BigNumber label={w.label ?? w.id} value={known ? fmtNumber(v) : "—"} unit={o.unit} zone={zone} sub={`${fmtNumber(min)} – ${fmtNumber(max)}`} />;

  const segs = [];
  for (let i = 0; i < SEGMENTS; i++) {
    const mid = min + ((i + 0.5) / SEGMENTS) * (max - min);
    const segZone = zoneOf(mid, o.warn, o.crit);
    const a = ((-SWEEP / 2 + (i + 0.5) * (SWEEP / SEGMENTS)) * Math.PI) / 180;
    const r1 = 40;
    const r2 = 50;
    segs.push(
      <line
        key={i}
        x1={cx + r1 * Math.sin(a)}
        y1={cy - r1 * Math.cos(a)}
        x2={cx + r2 * Math.sin(a)}
        y2={cy - r2 * Math.cos(a)}
        class={`seg seg-${segZone} ${i < lit && known ? "on" : ""}`}
      />,
    );
  }

  return (
    <div class={`gauge zone-${zone}`} role="meter" aria-label={w.label ?? w.id} aria-valuemin={min} aria-valuemax={max} aria-valuenow={known ? v : undefined}>
      <svg viewBox="0 0 120 100" class="gauge-svg">
        {segs}
      </svg>
      <div class="gauge-readout">
        <span class="gauge-value">{known ? fmtNumber(v) : "—"}</span>
        {o.unit && <span class="gauge-unit">{o.unit}</span>}
      </div>
      <div class="gauge-label">{w.label ?? w.id}</div>
      <div class="gauge-range">
        <span>{fmtNumber(min)}</span>
        {zone !== "ok" && <span class={`gauge-flag flag-${zone}`}>{zone === "crit" ? "CRIT" : "WARN"}</span>}
        <span>{fmtNumber(max)}</span>
      </div>
    </div>
  );
}

// ------------------------------------------------------------------- label
/** A big number tile (display styles "number" / "big"). */
export function BigNumber({ label, value, unit, zone = "ok", sub }: { label?: string; value: string; unit?: string; zone?: string; sub?: string }) {
  return (
    <div class={`bignum zone-${zone}`}>
      {label && <span class="bignum-label">{label}</span>}
      <span class="bignum-value">
        {value}
        {unit && <span class="bignum-unit">{unit}</span>}
      </span>
      {sub && <span class="bignum-sub">{sub}</span>}
    </div>
  );
}

export function LabelRow({ w, uplink, conn }: WidgetProps) {
  const { value } = useValue(conn, uplink.uplinkId, w);
  const text = value == null || value === "" ? undefined : typeof value === "number" ? fmtNumber(value) : String(value);
  if (w.opts?.display === "big") return <BigNumber label={w.label} value={text ?? "—"} />;
  const entries = text ? splitEntries(text) : [];
  // long or multi-entry values (e.g. tick time per dimension): caption above, then a compact list
  if (w.label && text !== undefined && (entries.length > 1 || text.length > 32))
    return (
      <div class="kv kv-stack">
        <span class="kv-key">{w.label}</span>
        {entries.length > 1 ? (
          <ul class="kv-list">
            {entries.map((e, i) => (
              <li key={i}>
                <span class="kv-list-name">{e.name}</span>
                <span class="kv-list-val">
                  {e.value}
                  {e.tps !== undefined && <span class="kv-list-sub"> · {e.tps} TPS</span>}
                </span>
              </li>
            ))}
          </ul>
        ) : (
          <span class="kv-val kv-val-long">{text}</span>
        )}
      </div>
    );
  if (w.label && text !== undefined)
    return (
      <div class="kv">
        <span class="kv-key">{w.label}</span>
        <span class="kv-val">{text}</span>
      </div>
    );
  return <div class="kv-text">{text ?? w.label ?? ""}</div>;
}

// ------------------------------------------------------------------ toggle
export function Toggle({ w, uplink, conn, locked }: WidgetProps) {
  const { value, pending } = useValue(conn, uplink.uplinkId, w);
  const on = value === true;
  const id = `t-${uplink.uplinkId}-${w.id}`;
  const gate = usePinGate(conn, uplink, w, true);
  return (
    <div class={`row ${locked ? "is-locked" : ""}`}>
      <label class="row-label" for={id}>
        {w.label ?? w.id}
        {gate.needsPin && <IconLock size={12} class="pin-mark" />}
        {pending && <span class="row-hint">sending…</span>}
      </label>
      <button
        id={id}
        type="button"
        role="switch"
        aria-checked={on}
        disabled={locked}
        class={`switch ${on ? "on" : ""} ${pending ? "pending" : ""}`}
        onClick={() => gate.send(!on)}
      >
        <span class="switch-text">{on ? "ON" : "OFF"}</span>
        <span class="switch-knob" />
      </button>
      {gate.dialog}
    </div>
  );
}

// ------------------------------------------------------------------ slider
export function Slider({ w, uplink, conn, locked }: WidgetProps) {
  const o = w.opts ?? {};
  const min = num(o.min, 0);
  const max = num(o.max, 100) > min ? num(o.max, 100) : min + 1;
  const step = num(o.step, 1) > 0 ? num(o.step, 1) : 1;
  const { value, pending } = useValue(conn, uplink.uplinkId, w);
  const server = num(value, min);
  const [drag, setDrag] = useState<number | null>(null);
  const shown = drag ?? server;
  const pct = ((shown - min) / (max - min)) * 100;
  const gate = usePinGate(conn, uplink, w, true, () => setDrag(null));
  const commit = (v: number) => {
    if (v === server) return setDrag(null);
    if (!gate.needsPin) setDrag(null);
    gate.send(v, () => setDrag(null));
  };
  return (
    <div class={`row row-slider ${locked ? "is-locked" : ""}`}>
      <div class="row-head">
        <span class="row-label">
          {w.label ?? w.id}
          {gate.needsPin && <IconLock size={12} class="pin-mark" />}
          {pending && <span class="row-hint">sending…</span>}
        </span>
        <span class={`row-value ${drag != null ? "dragging" : ""}`}>
          {fmtNumber(shown)}
          {o.unit && <small> {o.unit}</small>}
        </span>
      </div>
      <input
        type="range"
        class="range"
        min={min}
        max={max}
        step={step}
        value={shown}
        disabled={locked}
        style={{ "--pct": `${pct}%` }}
        aria-label={w.label ?? w.id}
        onInput={(e) => setDrag(Number((e.target as HTMLInputElement).value))}
        onChange={(e) => commit(Number((e.target as HTMLInputElement).value))}
      />
      {gate.dialog}
    </div>
  );
}

// ------------------------------------------------------------------ button
export function ActionButton({ w, uplink, conn, locked }: WidgetProps) {
  const [confirming, setConfirming] = useState(false);
  const [flash, setFlash] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout>>();
  useEffect(() => () => clearTimeout(timer.current), []);
  const busy = !!conn.busy.value[keyOf(uplink.uplinkId, w.id)];
  const color = w.opts?.color ?? "gray";
  const flashNow = () => {
    setFlash(true);
    clearTimeout(timer.current);
    timer.current = setTimeout(() => setFlash(false), 700);
  };
  const gate = usePinGate(conn, uplink, w, false, flashNow);
  const fire = () => gate.send(true);
  return (
    <>
      <button
        type="button"
        class={`pxbtn pxbtn-${color} ${labelFit(w.label ?? w.id)} ${busy ? "busy" : ""} ${flash ? "flash" : ""}`}
        disabled={locked}
        onClick={() => (w.opts?.confirm && !gate.needsPin ? setConfirming(true) : fire())}
      >
        {(locked || gate.needsPin) && <IconLock size={14} />}
        <span>{w.label ?? w.id}</span>
      </button>
      {confirming && (
        <ConfirmDialog
          title={`${w.label ?? w.id}?`}
          text={`This sends “${w.label ?? w.id}” to ${uplink.title || "the uplink"}. The computer acts on it immediately.`}
          confirmLabel={w.label ?? "Confirm"}
          color={color}
          onCancel={() => setConfirming(false)}
          onConfirm={() => {
            setConfirming(false);
            fire();
          }}
        />
      )}
      {gate.dialog}
    </>
  );
}
