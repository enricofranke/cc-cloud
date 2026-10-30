// v0.5 C: rules – When → If → Then automation per mainframe (lazy route #/m/<id>/rules).
import { useEffect, useState } from "preact/hooks";
import { getConn } from "../lib/conns.ts";
import type { MainframeConn } from "../lib/conn.ts";
import type { Uplink } from "../lib/types.ts";
import { navigate, pushToast, timeAgo } from "../lib/ui.ts";
import {
  OPS,
  RESULT_TEXT,
  addsSchedule,
  clockToTicks,
  describeAction,
  describeCheck,
  newRule,
  numericWidgets,
  opText,
  scheduleRule,
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
  type SceneRef,
  type Trigger,
} from "../lib/rules.ts";
import { DAYS, TIME_PRESETS, dayShort, daysText, isTimeTrigger, nextRunText, nextScheduleRun, presetName, presetOf, type Day, type SchedulesInfo } from "../lib/schedules.ts";
import { feature } from "../lib/config.ts";
import { ACTION_LABEL, ActionFields, Field, UplinkSelect, freshAction, num } from "../components/ActionFields.tsx";
import { tr } from "../lib/i18n.ts";
import { IconBack, IconPlus } from "../components/Icons.tsx";
import { Sheet } from "../components/Overlays.tsx";
import { DocsLink } from "../components/DocsLink.tsx";

const ERR: Record<string, string> = {
  invalid_rule: "The server refused the rule",
  too_many: "This mainframe has reached its rule limit – a Memory upgrade card allows more.",
  too_many_schedules: "This mainframe has reached its limit of time-based rules (maxSchedulesPerMainframe in the server config).",
  unknown_scene: "That scene no longer exists.",
  busy: "The scene is still running.",
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

/** The scenes a `scene` action may pick (v0.10; empty on older servers). */
export function sceneRefs(conn: MainframeConn): SceneRef[] {
  return feature("scenes") ? (conn.scenes.value?.scenes ?? []).map((s) => ({ id: s.id, name: s.name, needsPin: s.needsPin })) : [];
}

export default function RulesScreen({ id }: { id: string }) {
  const conn = getConn(id);
  const [editing, setEditing] = useState<{ rule: Rule; before: Rule | null } | null>(null);
  const doc = conn?.rules.value;
  // the Scenes screen's "Schedule…" shortcut: #/m/<id>/rules?schedule=<sceneId>
  useEffect(() => {
    if (!conn || !doc) return;
    const sid = new URLSearchParams(location.hash.split("?")[1] ?? "").get("schedule");
    if (!sid) return;
    history.replaceState(null, "", `/#/m/${encodeURIComponent(id)}/rules`);
    const sc = sceneRefs(conn).find((x) => x.id === sid);
    if (sc && conn.can("rules.edit")) setEditing({ rule: scheduleRule(sc), before: null });
  }, [!!doc]);
  if (!conn) return null;
  const uplinks = conn.uplinks.value;
  const scenes = sceneRefs(conn);
  const canEdit = conn.can("rules.edit");
  const canToggle = conn.can("rules.toggle");
  const name = conn.info.value?.name ?? "Mainframe";
  const sched = doc?.schedules ?? null;
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
          <button type="button" class="pxbtn pxbtn-gold" disabled={doc.rules.length >= doc.limit} onClick={() => setEditing({ rule: newRule(uplinks), before: null })}>
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
            {doc.rules.length} / {doc.limit} rules
            {sched && (
              <>
                {" "}
                · {sched.count} / {sched.limit} time-based{sched.timezone && ` · server time zone ${sched.timezone}`}
              </>
            )}
            {!canEdit && " · editing rules needs the “Edit rules” permission"}
          </p>
          {doc.rules.length === 0 ? (
            <div class="rules-empty">
              <b>No rules yet.</b>
              <span class="muted small">For example: when the reactor is hotter than 900 K, set the coolant pump's redstone to 15 and send an alert.</span>
            </div>
          ) : (
            <ul class="rule-list">
              {doc.rules.map((r) => (
                <RuleRow key={r.id} r={r} conn={conn} uplinks={uplinks} scenes={scenes} canEdit={canEdit} canToggle={canToggle} onEdit={() => setEditing({ rule: structuredClone(r), before: r })} />
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
                  <li key={i} class={e.skipped ? "skipped" : ""}>
                    <span class="muted small">{timeAgo(e.time)}</span>
                    <b>{e.name}</b>
                    {e.manual && <span class="tag tag-gray">run now</span>}
                    <span class={`rule-res ${e.result === "ok" ? "ok" : e.skipped ? "skip" : "bad"}`}>{RESULT_TEXT[e.result] ?? (e.skipped ? `skipped – ${e.result}` : e.result)}</span>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </>
      )}
      {editing && <RuleEditor conn={conn} initial={editing.rule} before={editing.before} uplinks={uplinks} scenes={scenes} schedules={sched} onClose={() => setEditing(null)} />}
    </main>
  );
}

function RuleRow(props: { r: RuleState; conn: MainframeConn; uplinks: Uplink[]; scenes: SceneRef[]; canEdit: boolean; canToggle: boolean; onEdit: () => void }) {
  const { r, conn, uplinks, scenes } = props;
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
        <span class="kw">then</span> {r.then.map((a) => describeAction(a, uplinks, "en", scenes)).join(", ")}.
      </p>
      <p class="muted small rule-meta">
        {r.enabled && r.nextRun && <span class="rule-next">{nextRunText(r.nextRun, "en")}</span>}
        cooldown {r.cooldown} s · {r.runs} run{r.runs === 1 ? "" : "s"} since restart
        {r.lastRun ? ` · last ${timeAgo(r.lastRun)}` : " · not run yet"}
        {r.lastResult && <span class={`rule-res ${r.lastResult === "ok" ? "ok" : "bad"}`}>{RESULT_TEXT[r.lastResult] ?? r.lastResult}</span>}
      </p>
    </li>
  );
}

// ------------------------------------------------------------------ editor
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
      return { type: "time", time: 12000 };
    case "schedule":
      return { type: "schedule", days: ["mon", "tue", "wed", "thu", "fri"], at: "07:00" };
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
  ["schedule", "on days at a time"],
  ["time", "an in-game time of day"],
];
const CONDS: [string, string][] = [
  ["widget", "a widget value"],
  ["items", "an item count"],
  ["power", "mainframe power"],
  ["online", "a card is online"],
];
function RuleEditor(props: { conn: MainframeConn; initial: Rule; before: Rule | null; uplinks: Uplink[]; scenes: SceneRef[]; schedules: SchedulesInfo | null; onClose: () => void }) {
  const { conn, initial, uplinks, scenes, schedules, onClose } = props;
  const [r, setR] = useState<Rule>(initial);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [tried, setTried] = useState(false);
  const problem = validateRule(r, uplinks, 4096, { minInterval: schedules?.minInterval, scenes });
  // one more time-based rule than the server allows: say so before saving
  const scheduleFull = !!schedules && addsSchedule(r, props.before) && schedules.count >= schedules.limit;
  const actions: Action["type"][] = ["redstone", "widget", "craft", "alert", "dashboard", ...(feature("scenes") && conn.scenes.value ? (["scene"] as const) : [])];
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
              <input class="input input-num" type="number" min={schedules?.minInterval ?? 1} max={1440} aria-label="Minutes" value={when.minutes} onInput={(e) => set({ when: { ...when, minutes: num((e.target as HTMLInputElement).value) } })} />
              <span>minutes</span>
              {schedules && schedules.minInterval > 1 && <span class="muted small">at least {schedules.minInterval} on this server</span>}
            </div>
          )}
          {when.type === "schedule" && <ScheduleFields when={when} timezone={schedules?.timezone ?? ""} onChange={(w) => set({ when: w })} />}
          {when.type === "time" && (
            <div class="rule-fields">
              <select
                class="select"
                aria-label="In-game time preset"
                value={presetOf(when.time) ?? "custom"}
                onChange={(e) => {
                  const p = TIME_PRESETS.find((x) => x.id === (e.target as HTMLSelectElement).value);
                  if (p) set({ when: { ...when, time: p.time } });
                }}
              >
                {TIME_PRESETS.map((p) => (
                  <option key={p.id} value={p.id}>
                    {presetName(p.id, "en")} ({ticksToClock(p.time)})
                  </option>
                ))}
                <option value="custom">any in-game time…</option>
              </select>
              <span>at</span>
              <input class="input input-num" type="time" aria-label="In-game time" value={ticksToClock(when.time)} onInput={(e) => set({ when: { ...when, time: clockToTicks((e.target as HTMLInputElement).value) ?? when.time } })} />
              <span class="muted small">in-game ({when.time} ticks)</span>
            </div>
          )}
          {isTimeTrigger(when) ? (
            <p class="muted small">
              {when.type === "time"
                ? "In-game time only passes while the server runs – there is no catch-up after a restart."
                : "After a restart one missed run is made up (within 24 h by default). While the mainframe is unloaded or offline, runs are skipped and logged."}{" "}
              <DocsLink to="/docs/automation#schedules">Schedules</DocsLink>
            </p>
          ) : (
            <p class="muted small">Value triggers fire once when the check becomes true, not again while it stays true.</p>
          )}
          {scheduleFull && (
            <p class="field-hint warn" role="status">
              This mainframe already has {schedules!.count} of {schedules!.limit} time-based rules – the server will refuse another one.
            </p>
          )}
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
              <select class="select" aria-label="Action" value={a.type} onChange={(e) => set({ then: r.then.map((x, j) => (j === i ? freshAction((e.target as HTMLSelectElement).value as Action["type"], uplinks, scenes) : x)) })}>
                {(actions.includes(a.type) ? actions : [...actions, a.type]).map((v) => (
                  <option key={v} value={v}>
                    {tr("en", ACTION_LABEL[v])}
                  </option>
                ))}
              </select>
              <ActionFields a={a} uplinks={uplinks} scenes={scenes} onChange={(na) => set({ then: r.then.map((x, j) => (j === i ? na : x)) })} />
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
            <span class="kw">then</span> {r.then.map((a) => describeAction(a, uplinks, "en", scenes)).join(", ")}.
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

/** The `schedule` trigger: weekday chips, the time (24 h, server time zone) and the next occurrence. */
function ScheduleFields({ when, timezone, onChange }: { when: Extract<Trigger, { type: "schedule" }>; timezone: string; onChange: (w: Extract<Trigger, { type: "schedule" }>) => void }) {
  const toggle = (d: Day) => onChange({ ...when, days: when.days.includes(d) ? when.days.filter((x) => x !== d) : DAYS.filter((x) => x === d || when.days.includes(x)) });
  const next = nextScheduleRun(when.days, when.at, timezone);
  const quick: [string, Day[]][] = [
    ["Every day", [...DAYS]],
    ["Mon–Fri", ["mon", "tue", "wed", "thu", "fri"]],
    ["Weekend", ["sat", "sun"]],
  ];
  return (
    <div class="sched">
      <div class="day-chips" role="group" aria-label="Days">
        {DAYS.map((d) => (
          <button key={d} type="button" class={`day-chip ${when.days.includes(d) ? "on" : ""}`} aria-pressed={when.days.includes(d)} onClick={() => toggle(d)}>
            {dayShort(d, "en")}
          </button>
        ))}
        <span class="day-quick">
          {quick.map(([label, days]) => (
            <button key={label} type="button" class="link-btn small" onClick={() => onChange({ ...when, days })}>
              {label}
            </button>
          ))}
        </span>
      </div>
      <div class="rule-fields">
        <span>at</span>
        <input class="input input-num" type="time" aria-label="Time (24 h)" value={when.at} onInput={(e) => onChange({ ...when, at: (e.target as HTMLInputElement).value })} />
        <span class="muted small">server time{timezone ? ` (${timezone})` : ""}</span>
      </div>
      {next && (
        <p class="muted small">
          {daysText(when.days, "en")} at {when.at} – next: {new Date(next).toLocaleString([], { weekday: "short", day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" })} your time
        </p>
      )}
    </div>
  );
}
