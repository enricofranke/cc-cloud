// v0.5 C: rules – When → If → Then automation per mainframe (lazy route #/m/<id>/rules).
import { useState } from "preact/hooks";
import type { ComponentChildren } from "preact";
import { getConn } from "../lib/conns.ts";
import type { MainframeConn } from "../lib/conn.ts";
import type { Uplink } from "../lib/types.ts";
import { navigate, pushToast, timeAgo } from "../lib/ui.ts";
import {
  OPS,
  RESULT_TEXT,
  SIDES,
  clockToTicks,
  describeAction,
  describeCheck,
  newRule,
  numericWidgets,
  opText,
  redstoneUplinks,
  settableWidgets,
  storageUplinks,
  ticksToClock,
  toWire,
  validateRule,
  type Action,
  type Check,
  type Condition,
  type Op,
  type Rule,
  type RuleState,
  type Trigger,
} from "../lib/rules.ts";
import { IconBack, IconPlus } from "../components/Icons.tsx";
import { Sheet } from "../components/Overlays.tsx";
import { DocsLink } from "../components/DocsLink.tsx";

const ERR: Record<string, string> = {
  invalid_rule: "The server refused the rule",
  too_many: "This mainframe has reached its rule limit – a Memory upgrade card allows more.",
  pin_widget: "Rules cannot use controls that are protected by the PIN.",
  unknown_rule: "That rule no longer exists.",
  not_allowed: "You lack the permission for this.",
  read_only: "This device is read-only.",
  offline: "The mainframe is offline.",
  rate_limited: "Too many rule runs right now – try again in a moment.",
  disconnected: "Not connected – try again in a moment.",
  timeout: "No answer from the server.",
};
const errText = (e: unknown) => {
  const x = e as Error & { detail?: string };
  return `${ERR[x.message] ?? x.message}${x.detail ? `: ${x.detail}` : ""}`;
};

export default function RulesScreen({ id }: { id: string }) {
  const conn = getConn(id);
  const [editing, setEditing] = useState<Rule | null>(null);
  if (!conn) return null;
  const doc = conn.rules.value;
  const uplinks = conn.uplinks.value;
  const canEdit = conn.can("rules.edit");
  const canToggle = conn.can("rules.toggle");
  const name = conn.info.value?.name ?? "Mainframe";
  return (
    <main class="page page-narrow-wide rules-page" id="main" tabIndex={-1}>
      <div class="page-head acc-head">
        <div>
          <button type="button" class="link-back" onClick={() => navigate(`/m/${encodeURIComponent(id)}`)}>
            <IconBack size={18} /> Back
          </button>
          <h1 class="px-heading">Rules</h1>
        </div>
        {canEdit && doc && (
          <button type="button" class="pxbtn pxbtn-gold" disabled={doc.rules.length >= doc.limit} onClick={() => setEditing(newRule(uplinks))}>
            <span>
              <IconPlus size={14} /> New rule
            </span>
          </button>
        )}
      </div>
      <p class="muted">
        Automation that runs on the server for <b>{name}</b>, even when no browser is open: <b>when</b> something happens, <b>if</b> conditions hold, <b>then</b> do something.{" "}
        <DocsLink to="/docs/automation#rules">How rules work</DocsLink>
      </p>
      {!doc ? (
        <p class="muted">This server does not support rules yet (CC: Cloud v0.5 or newer).</p>
      ) : (
        <>
          <p class="muted small">
            {doc.rules.length} / {doc.limit} rules{!canEdit && " · editing rules needs the “Edit rules” permission"}
          </p>
          {doc.rules.length === 0 ? (
            <div class="rules-empty">
              <b>No rules yet.</b>
              <span class="muted small">For example: when the reactor is hotter than 900 K, set the coolant pump's redstone to 15 and send an alert.</span>
            </div>
          ) : (
            <ul class="rule-list">
              {doc.rules.map((r) => (
                <RuleRow key={r.id} r={r} conn={conn} uplinks={uplinks} canEdit={canEdit} canToggle={canToggle} onEdit={() => setEditing(structuredClone(r))} />
              ))}
            </ul>
          )}
          {doc.log.length > 0 && (
            <section class="acc-sec" aria-labelledby="rules-log">
              <h2 class="sec-title" id="rules-log">
                Recent runs
              </h2>
              <ul class="rule-log">
                {[...doc.log].reverse().slice(0, 20).map((e, i) => (
                  <li key={i}>
                    <span class="muted small">{timeAgo(e.time)}</span>
                    <b>{e.name}</b>
                    {e.manual && <span class="tag tag-gray">run now</span>}
                    <span class={`rule-res ${e.result === "ok" ? "ok" : "bad"}`}>{RESULT_TEXT[e.result] ?? e.result}</span>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </>
      )}
      {editing && <RuleEditor conn={conn} initial={editing} uplinks={uplinks} onClose={() => setEditing(null)} />}
    </main>
  );
}

function RuleRow(props: { r: RuleState; conn: MainframeConn; uplinks: Uplink[]; canEdit: boolean; canToggle: boolean; onEdit: () => void }) {
  const { r, conn, uplinks } = props;
  const [busy, setBusy] = useState(false);
  const run = async (f: () => Promise<unknown>, ok?: string) => {
    setBusy(true);
    try {
      await f();
      if (ok) pushToast({ level: "info", title: "Rules", text: ok });
    } catch (e) {
      pushToast({ level: "warn", title: r.name, text: errText(e) });
    } finally {
      setBusy(false);
    }
  };
  return (
    <li class={`rule-row ${r.enabled ? "" : "off"}`}>
      <div class="rule-top">
        <button
          type="button"
          role="switch"
          aria-checked={r.enabled}
          aria-label={`${r.name} enabled`}
          disabled={!props.canToggle || busy}
          class={`switch ${r.enabled ? "on" : ""}`}
          onClick={() => run(() => conn.ruleOp({ t: "rule_enable", id: r.id, enabled: !r.enabled }))}
        >
          <span class="switch-text">{r.enabled ? "ON" : "OFF"}</span>
          <span class="switch-knob" />
        </button>
        <b class="rule-name">{r.name}</b>
        {r.notify && (
          <span class="tag tag-gray" title="Pushes the result to the notification targets">
            push
          </span>
        )}
        <span class="rule-tools">
          {props.canToggle && (
            <button type="button" class="btn btn-small" disabled={busy} onClick={() => run(() => conn.ruleOp({ t: "rule_run", id: r.id }), `${r.name}: ran once`)}>
              Run now
            </button>
          )}
          {props.canEdit && (
            <>
              <button type="button" class="btn btn-small" onClick={props.onEdit}>
                Edit
              </button>
              <button
                type="button"
                class="btn btn-small btn-danger-ghost"
                disabled={busy}
                onClick={() => confirm(`Delete the rule “${r.name}”?`) && run(() => conn.ruleOp({ t: "rule_delete", id: r.id }), `${r.name} deleted`)}
              >
                Delete
              </button>
            </>
          )}
        </span>
      </div>
      <p class="rule-sum">
        <span class="kw">When</span> {describeCheck(r.when, uplinks)}
        {r.if.length > 0 && (
          <>
            {" "}
            <span class="kw">if</span> {r.if.map((c) => describeCheck(c, uplinks)).join(" and ")}
          </>
        )}{" "}
        <span class="kw">then</span> {r.then.map((a) => describeAction(a, uplinks)).join(", ")}.
      </p>
      <p class="muted small rule-meta">
        cooldown {r.cooldown} s · {r.runs} run{r.runs === 1 ? "" : "s"} since restart
        {r.lastRun ? ` · last ${timeAgo(r.lastRun)}` : " · not run yet"}
        {r.lastResult && <span class={`rule-res ${r.lastResult === "ok" ? "ok" : "bad"}`}>{RESULT_TEXT[r.lastResult] ?? r.lastResult}</span>}
      </p>
    </li>
  );
}

// ------------------------------------------------------------------ editor
function Field({ label, children, hint }: { label: string; children: ComponentChildren; hint?: string }) {
  return (
    <label class="field">
      <span class="field-label">{label}</span>
      {children}
      {hint && <span class="field-hint">{hint}</span>}
    </label>
  );
}

function UplinkSelect({ value, list, onChange, label = "Card" }: { value: string; list: Uplink[]; onChange: (id: string) => void; label?: string }) {
  return (
    <select class="select" aria-label={label} value={value} onChange={(e) => onChange((e.target as HTMLSelectElement).value)}>
      {!list.some((u) => u.uplinkId === value) && <option value="">Pick a card…</option>}
      {list.map((u) => (
        <option key={u.uplinkId} value={u.uplinkId}>
          {u.title || "Untitled"}
        </option>
      ))}
    </select>
  );
}

function OpSelect({ value, onChange }: { value: Op; onChange: (o: Op) => void }) {
  return (
    <select class="select select-op" aria-label="Comparison" value={value} onChange={(e) => onChange((e.target as HTMLSelectElement).value as Op)}>
      {OPS.map((o) => (
        <option key={o} value={o}>
          {opText(o)}
        </option>
      ))}
    </select>
  );
}

const num = (s: string) => (s.trim() === "" ? NaN : Number(s));

/** The editable part of a widget / items / power check (shared by When and If). */
function CheckFields({ c, uplinks, onChange }: { c: Check; uplinks: Uplink[]; onChange: (c: Check) => void }) {
  if (c.type === "power")
    return (
      <div class="rule-fields">
        <OpSelect value={c.op} onChange={(op) => onChange({ ...c, op })} />
        <input class="input input-num" type="number" min={0} max={100} aria-label="Percent" value={c.value} onInput={(e) => onChange({ ...c, value: num((e.target as HTMLInputElement).value) })} />
        <span class="muted">%</span>
      </div>
    );
  if (c.type === "items") {
    const list = storageUplinks(uplinks);
    return (
      <div class="rule-fields">
        <UplinkSelect value={c.uplinkId} list={list} label="Storage card" onChange={(uplinkId) => onChange({ ...c, uplinkId })} />
        <input class="input" placeholder="minecraft:iron_ingot" aria-label="Item id" value={c.item} onInput={(e) => onChange({ ...c, item: (e.target as HTMLInputElement).value.trim().toLowerCase() })} />
        <OpSelect value={c.op} onChange={(op) => onChange({ ...c, op })} />
        <input class="input input-num" type="number" min={0} aria-label="Amount" value={c.value} onInput={(e) => onChange({ ...c, value: num((e.target as HTMLInputElement).value) })} />
      </div>
    );
  }
  const list = uplinks.filter((u) => numericWidgets(u).length);
  const u = uplinks.find((x) => x.uplinkId === c.uplinkId);
  const ws = u ? numericWidgets(u) : [];
  const w = ws.find((x) => x.id === c.widgetId);
  return (
    <div class="rule-fields">
      <UplinkSelect value={c.uplinkId} list={list} onChange={(uplinkId) => onChange({ ...c, uplinkId, widgetId: numericWidgets(uplinks.find((x) => x.uplinkId === uplinkId)!)[0]?.id ?? "" })} />
      <select class="select" aria-label="Widget" value={c.widgetId} onChange={(e) => onChange({ ...c, widgetId: (e.target as HTMLSelectElement).value })}>
        {ws.map((x) => (
          <option key={x.id} value={x.id}>
            {x.label || x.id}
          </option>
        ))}
      </select>
      <OpSelect value={c.op} onChange={(op) => onChange({ ...c, op })} />
      <input class="input input-num" type="number" aria-label="Value" value={c.value} onInput={(e) => onChange({ ...c, value: num((e.target as HTMLInputElement).value) })} />
      {typeof w?.opts?.unit === "string" && <span class="muted">{w.opts.unit}</span>}
      {(w?.type === "toggle" || w?.type === "indicator") && <span class="muted small">on = 1, off = 0</span>}
    </div>
  );
}

function freshCheck(type: string, uplinks: Uplink[]): Trigger {
  const nu = uplinks.find((u) => numericWidgets(u).length);
  const su = storageUplinks(uplinks)[0];
  switch (type) {
    case "items":
      return { type: "items", uplinkId: su?.uplinkId ?? "", item: "minecraft:iron_ingot", op: "<", value: 1000 };
    case "power":
      return { type: "power", op: "<", value: 20 };
    case "offline":
    case "online":
      return { type: type as "offline", uplinkId: uplinks[0]?.uplinkId ?? "" };
    case "interval":
      return { type: "interval", minutes: 10 };
    case "time":
      return { type: "time", time: 0 };
    default:
      return { type: "widget", uplinkId: nu?.uplinkId ?? "", widgetId: nu ? numericWidgets(nu)[0].id : "", op: ">", value: 0 };
  }
}

const TRIGGERS: [string, string][] = [
  ["widget", "a widget value"],
  ["items", "an item count"],
  ["power", "mainframe power"],
  ["offline", "a card goes offline"],
  ["interval", "every … minutes"],
  ["time", "a time of day"],
];
const CONDS: [string, string][] = [
  ["widget", "a widget value"],
  ["items", "an item count"],
  ["power", "mainframe power"],
  ["online", "a card is online"],
];
const ACTIONS: [Action["type"], string][] = [
  ["redstone", "Set redstone"],
  ["widget", "Use a control"],
  ["craft", "Craft"],
  ["alert", "Send an alert"],
  ["dashboard", "Dashboard on/off"],
];

function freshAction(type: Action["type"], uplinks: Uplink[]): Action {
  const ru = redstoneUplinks(uplinks)[0];
  const cu = uplinks.find((u) => settableWidgets(u).length);
  const su = storageUplinks(uplinks)[0];
  switch (type) {
    case "redstone":
      return { type, uplinkId: ru?.uplinkId ?? "", side: "back", value: 15 };
    case "widget": {
      const w = cu ? settableWidgets(cu)[0] : undefined;
      return { type, uplinkId: cu?.uplinkId ?? "", widgetId: w?.id ?? "", value: w?.type === "toggle" || w?.type === "button" ? true : 0 };
    }
    case "craft":
      return { type, uplinkId: su?.uplinkId ?? "", item: "minecraft:iron_ingot", amount: 64 };
    case "alert":
      return { type, text: "", level: "warn" };
    case "dashboard":
      return { type, enabled: false };
  }
}

function ActionFields({ a, uplinks, onChange }: { a: Action; uplinks: Uplink[]; onChange: (a: Action) => void }) {
  switch (a.type) {
    case "redstone":
      return (
        <div class="rule-fields">
          <UplinkSelect value={a.uplinkId} list={redstoneUplinks(uplinks)} label="Computer" onChange={(uplinkId) => onChange({ ...a, uplinkId })} />
          <select class="select" aria-label="Side" value={a.side} onChange={(e) => onChange({ ...a, side: (e.target as HTMLSelectElement).value as typeof a.side })}>
            {SIDES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
          <input class="input input-num" type="number" min={0} max={15} aria-label="Level 0–15" value={a.value} onInput={(e) => onChange({ ...a, value: num((e.target as HTMLInputElement).value) })} />
        </div>
      );
    case "widget": {
      const list = uplinks.filter((u) => settableWidgets(u).length);
      const u = uplinks.find((x) => x.uplinkId === a.uplinkId);
      const ws = u ? settableWidgets(u) : [];
      const w = ws.find((x) => x.id === a.widgetId);
      const valueFor = (id: string) => {
        const t = ws.find((x) => x.id === id)?.type;
        return t === "toggle" || t === "button" ? true : t === "text" || t === "select" ? "" : 0;
      };
      return (
        <div class="rule-fields">
          <UplinkSelect
            value={a.uplinkId}
            list={list}
            onChange={(uplinkId) => {
              const first = settableWidgets(uplinks.find((x) => x.uplinkId === uplinkId)!)[0];
              onChange({ ...a, uplinkId, widgetId: first?.id ?? "", value: first?.type === "toggle" || first?.type === "button" ? true : 0 });
            }}
          />
          <select class="select" aria-label="Control" value={a.widgetId} onChange={(e) => onChange({ ...a, widgetId: (e.target as HTMLSelectElement).value, value: valueFor((e.target as HTMLSelectElement).value) })}>
            {ws.map((x) => (
              <option key={x.id} value={x.id}>
                {x.label || x.id} ({x.type})
              </option>
            ))}
          </select>
          {w?.type === "toggle" && (
            <select class="select" aria-label="Turn" value={a.value ? "on" : "off"} onChange={(e) => onChange({ ...a, value: (e.target as HTMLSelectElement).value === "on" })}>
              <option value="on">on</option>
              <option value="off">off</option>
            </select>
          )}
          {w?.type === "button" && <span class="muted small">press it</span>}
          {(w?.type === "slider" || w?.type === "number") && (
            <input class="input input-num" type="number" aria-label="Value" min={w.opts?.min} max={w.opts?.max} value={String(a.value)} onInput={(e) => onChange({ ...a, value: num((e.target as HTMLInputElement).value) })} />
          )}
          {w?.type === "select" && (
            <select class="select" aria-label="Option" value={String(a.value)} onChange={(e) => onChange({ ...a, value: (e.target as HTMLSelectElement).value })}>
              {((w.opts?.options ?? []) as unknown[]).filter((x): x is string => typeof x === "string").map((o) => (
                <option key={o} value={o}>
                  {o}
                </option>
              ))}
            </select>
          )}
          {w?.type === "text" && <input class="input" maxLength={256} aria-label="Text" value={String(a.value)} onInput={(e) => onChange({ ...a, value: (e.target as HTMLInputElement).value })} />}
        </div>
      );
    }
    case "craft":
      return (
        <div class="rule-fields">
          <UplinkSelect value={a.uplinkId} list={storageUplinks(uplinks)} label="Storage card" onChange={(uplinkId) => onChange({ ...a, uplinkId })} />
          <input class="input" placeholder="minecraft:iron_ingot" aria-label="Item id" value={a.item} onInput={(e) => onChange({ ...a, item: (e.target as HTMLInputElement).value.trim().toLowerCase() })} />
          <input class="input input-num" type="number" min={1} aria-label="Amount" value={a.amount} onInput={(e) => onChange({ ...a, amount: num((e.target as HTMLInputElement).value) })} />
        </div>
      );
    case "alert":
      return (
        <div class="rule-fields">
          <input class="input rule-grow" maxLength={120} placeholder="Reactor is hot!" aria-label="Alert text" value={a.text} onInput={(e) => onChange({ ...a, text: (e.target as HTMLInputElement).value })} />
          <select class="select" aria-label="Level" value={a.level} onChange={(e) => onChange({ ...a, level: (e.target as HTMLSelectElement).value as typeof a.level })}>
            <option value="info">info</option>
            <option value="warn">warning</option>
            <option value="error">error</option>
          </select>
        </div>
      );
    case "dashboard":
      return (
        <div class="rule-fields">
          <select class="select" aria-label="Dashboard" value={a.enabled ? "on" : "off"} onChange={(e) => onChange({ ...a, enabled: (e.target as HTMLSelectElement).value === "on" })}>
            <option value="off">turn the dashboard off</option>
            <option value="on">turn the dashboard on</option>
          </select>
        </div>
      );
  }
}

function RuleEditor({ conn, initial, uplinks, onClose }: { conn: MainframeConn; initial: Rule; uplinks: Uplink[]; onClose: () => void }) {
  const [r, setR] = useState<Rule>(initial);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [tried, setTried] = useState(false);
  const problem = validateRule(r, uplinks);
  const set = (patch: Partial<Rule>) => setR({ ...r, ...patch });
  const save = async () => {
    setTried(true);
    if (problem) return;
    setBusy(true);
    setError(null);
    try {
      await conn.ruleOp({ t: "rule_set", rule: toWire(r) });
      pushToast({ level: "info", title: "Rules", text: `${r.name.trim()} saved` });
      onClose();
    } catch (e) {
      setError(errText(e));
    } finally {
      setBusy(false);
    }
  };
  const when = r.when;
  return (
    <Sheet title={r.id ? "Edit rule" : "New rule"} onClose={onClose} wide>
      <form
        class="rule-editor"
        onSubmit={(e) => {
          e.preventDefault();
          save();
        }}
      >
        <div class="rule-head-fields">
          <Field label="Name">
            <input class="input" maxLength={48} value={r.name} placeholder="Cool the reactor" onInput={(e) => set({ name: (e.target as HTMLInputElement).value })} />
          </Field>
          <Field label="Cooldown (seconds)" hint="Minimum time between two runs">
            <input class="input input-num" type="number" min={5} max={86400} value={r.cooldown} onInput={(e) => set({ cooldown: num((e.target as HTMLInputElement).value) })} />
          </Field>
        </div>

        <fieldset class="rule-block">
          <legend>
            <span class="kw">When</span>
          </legend>
          <div class="rule-fields">
            <select class="select" aria-label="Trigger" value={when.type} onChange={(e) => set({ when: freshCheck((e.target as HTMLSelectElement).value, uplinks) })}>
              {TRIGGERS.map(([v, l]) => (
                <option key={v} value={v}>
                  {l}
                </option>
              ))}
            </select>
          </div>
          {(when.type === "widget" || when.type === "items" || when.type === "power") && <CheckFields c={when} uplinks={uplinks} onChange={(c) => set({ when: c })} />}
          {when.type === "offline" && (
            <div class="rule-fields">
              <UplinkSelect value={when.uplinkId} list={uplinks} onChange={(uplinkId) => set({ when: { ...when, uplinkId } })} />
            </div>
          )}
          {when.type === "interval" && (
            <div class="rule-fields">
              <span>every</span>
              <input class="input input-num" type="number" min={1} max={1440} aria-label="Minutes" value={when.minutes} onInput={(e) => set({ when: { ...when, minutes: num((e.target as HTMLInputElement).value) } })} />
              <span>minutes</span>
            </div>
          )}
          {when.type === "time" && (
            <div class="rule-fields">
              <span>at</span>
              <input class="input input-num" type="time" aria-label="In-game time" value={ticksToClock(when.time)} onInput={(e) => set({ when: { ...when, time: clockToTicks((e.target as HTMLInputElement).value) ?? when.time } })} />
              <span class="muted small">in-game time ({when.time} ticks)</span>
            </div>
          )}
          <p class="muted small">Value triggers fire once when the check becomes true, not again while it stays true.</p>
        </fieldset>

        <fieldset class="rule-block">
          <legend>
            <span class="kw">If</span> <span class="muted small">(optional, all must hold)</span>
          </legend>
          {r.if.map((c, i) => (
            <div key={i} class="rule-item">
              <select class="select" aria-label="Condition" value={c.type} onChange={(e) => set({ if: r.if.map((x, j) => (j === i ? (freshCheck((e.target as HTMLSelectElement).value, uplinks) as Condition) : x)) })}>
                {CONDS.map(([v, l]) => (
                  <option key={v} value={v}>
                    {l}
                  </option>
                ))}
              </select>
              {c.type === "online" ? (
                <div class="rule-fields">
                  <UplinkSelect value={c.uplinkId} list={uplinks} onChange={(uplinkId) => set({ if: r.if.map((x, j) => (j === i ? { ...c, uplinkId } : x)) })} />
                </div>
              ) : (
                <CheckFields c={c} uplinks={uplinks} onChange={(nc) => set({ if: r.if.map((x, j) => (j === i ? nc : x)) })} />
              )}
              <button type="button" class="icon-btn sm" aria-label="Remove condition" onClick={() => set({ if: r.if.filter((_, j) => j !== i) })}>
                ×
              </button>
            </div>
          ))}
          {r.if.length < 8 && (
            <button type="button" class="btn btn-small" onClick={() => set({ if: [...r.if, freshCheck("power", uplinks) as Condition] })}>
              <IconPlus size={14} /> Condition
            </button>
          )}
        </fieldset>

        <fieldset class="rule-block">
          <legend>
            <span class="kw">Then</span> <span class="muted small">(in this order)</span>
          </legend>
          {r.then.map((a, i) => (
            <div key={i} class="rule-item">
              <select class="select" aria-label="Action" value={a.type} onChange={(e) => set({ then: r.then.map((x, j) => (j === i ? freshAction((e.target as HTMLSelectElement).value as Action["type"], uplinks) : x)) })}>
                {ACTIONS.map(([v, l]) => (
                  <option key={v} value={v}>
                    {l}
                  </option>
                ))}
              </select>
              <ActionFields a={a} uplinks={uplinks} onChange={(na) => set({ then: r.then.map((x, j) => (j === i ? na : x)) })} />
              <button type="button" class="icon-btn sm" aria-label="Remove action" disabled={r.then.length === 1} onClick={() => set({ then: r.then.filter((_, j) => j !== i) })}>
                ×
              </button>
            </div>
          ))}
          {r.then.length < 10 && (
            <button type="button" class="btn btn-small" onClick={() => set({ then: [...r.then, freshAction("alert", uplinks)] })}>
              <IconPlus size={14} /> Action
            </button>
          )}
        </fieldset>

        <label class="check-row">
          <input type="checkbox" checked={r.notify} onChange={(e) => set({ notify: (e.target as HTMLInputElement).checked })} />
          Push every run to the notification targets
        </label>
        <label class="check-row">
          <input type="checkbox" checked={r.enabled} onChange={(e) => set({ enabled: (e.target as HTMLInputElement).checked })} />
          Enabled
        </label>
        {!problem && (
          <p class="rule-preview">
            <span class="kw">When</span> {describeCheck(r.when, uplinks)}
            {r.if.length > 0 && (
              <>
                {" "}
                <span class="kw">if</span> {r.if.map((c) => describeCheck(c, uplinks)).join(" and ")}
              </>
            )}{" "}
            <span class="kw">then</span> {r.then.map((a) => describeAction(a, uplinks)).join(", ")}.
          </p>
        )}
        {tried && problem && (
          <p class="form-error" role="alert">
            {problem}
          </p>
        )}
        {error && (
          <p class="form-error" role="alert">
            {error}
          </p>
        )}
        <div class="perm-actions">
          <span class="perm-spacer" />
          <button type="button" class="btn" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" class="pxbtn pxbtn-gold" disabled={busy}>
            <span>{busy ? "Saving…" : "Save rule"}</span>
          </button>
        </div>
      </form>
    </Sheet>
  );
}
