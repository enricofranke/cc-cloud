// v0.10 scenes: one button, several steps in order (lazy route #/m/<id>/scenes). A scene runs on the
// server; pressing it is an ordinary action on the Scenes section's button, so the test run reuses the
// action/PIN flow. The step editor shares its pickers with the Rules editor (components/ActionFields.tsx).
import { useState } from "preact/hooks";
import { getConn } from "../lib/conns.ts";
import { keyOf, rejectText, type ActionOutcome, type MainframeConn } from "../lib/conn.ts";
import type { Uplink } from "../lib/types.ts";
import { navigate, pushToast } from "../lib/ui.ts";
import { feature } from "../lib/config.ts";
import { lang, t, type Key } from "../lib/i18n.ts";
import { settableWidgets } from "../lib/rules.ts";
import { agoText, describeWhen, isTimeTrigger, nextRunText } from "../lib/schedules.ts";
import {
  MAX_NAME,
  MAX_STEPS,
  MAX_TOTAL_DELAY,
  SCENE_COLORS,
  STEP_TYPES,
  describeStep,
  missingPerms,
  newScene,
  pinSteps,
  runState,
  sceneButtonId,
  sceneToWire,
  stepPerm,
  stepsSummary,
  totalDelay,
  validateScene,
  type LastRun,
  type Scene,
  type SceneState,
  type SceneStep,
  type StepType,
} from "../lib/scenes.ts";
import { ACTION_LABEL, ActionFields, EditorLang, Field, freshAction, num } from "../components/ActionFields.tsx";
import { IconPicker } from "../components/PageTabs.tsx";
import { PixelIcon } from "../components/PixelIcon.tsx";
import { IconBack, IconLock, IconPlus } from "../components/Icons.tsx";
import { ConfirmDialog, PinDialog, Sheet, type PinState } from "../components/Overlays.tsx";
import { DocsLink } from "../components/DocsLink.tsx";

/** Error codes the server answers with (scene_result, action_result, step results). */
const ERR_KEYS: Record<string, Key> = {
  invalid_scene: "sc.err.invalid_scene",
  too_many: "sc.err.too_many",
  not_allowed: "sc.err.not_allowed",
  read_only: "sc.err.read_only",
  unknown_scene: "sc.err.unknown_scene",
  disabled: "sc.err.disabled",
  forbidden: "sc.err.read_only",
  disconnected: "sc.err.disconnected",
  timeout: "sc.err.timeout",
  busy: "sc.err.busy",
  rate_limited: "sc.err.rate_limited",
  offline: "sc.err.offline",
  unknown_widget: "sc.err.unknown_widget",
};
/** Per-step results ("missing permission", "needs the PIN", …). */
const RES_KEYS: Record<string, Key> = {
  ok: "sc.res.ok",
  not_allowed: "sc.res.not_allowed",
  pin_required: "sc.res.pin_required",
  offline: "sc.res.offline",
  unknown_widget: "sc.res.unknown_widget",
  unknown_uplink: "sc.res.unknown_uplink",
  uplink_offline: "sc.res.uplink_offline",
  unloaded: "sc.res.unloaded",
  invalid_value: "sc.res.invalid_value",
  not_craftable: "sc.res.not_craftable",
  busy: "sc.res.busy",
  rate_limited: "sc.res.rate_limited",
  too_many: "sc.res.too_many",
};
const PERM_KEYS: Record<string, Key> = {
  "act.buttons": "perm.act.buttons",
  "act.redstone": "perm.act.redstone",
  "storage.craft": "perm.storage.craft",
  "power.toggle": "perm.power.toggle",
  "rules.edit": "perm.rules.edit",
};
const permName = (p: string) => (PERM_KEYS[p] ? t(PERM_KEYS[p]) : p);
const resText = (code: string) => (RES_KEYS[code] ? t(RES_KEYS[code]) : code);
/** An error with the server's detail; `step 2: act.redstone` becomes a sentence. */
function errText(e: unknown) {
  const x = e as Error & { detail?: string };
  const m = x.detail ? /^step (\d+): ([a-z.]+)$/.exec(x.detail) : null;
  if (x.message === "not_allowed" && m) return t("sc.err.stepPerm", { n: m[1], perm: permName(m[2]) });
  const base = ERR_KEYS[x.message] ? t(ERR_KEYS[x.message]) : x.message;
  return x.detail ? `${base}: ${x.detail}` : base;
}
const sourceText = (s: string) => t(`sc.src.${s}` as Key);

export default function ScenesScreen({ id }: { id: string }) {
  const conn = getConn(id);
  const [editing, setEditing] = useState<Scene | null>(null);
  if (!conn) return null;
  const L = lang.value;
  const doc = feature("scenes") ? conn.scenes.value : null;
  const uplinks = conn.uplinks.value;
  const canEdit = conn.can("rules.edit");
  const name = conn.info.value?.name ?? "Mainframe";
  return (
    <main class="page page-narrow-wide rules-page scenes-page" id="main" tabIndex={-1} lang={L}>
      <div class="page-head acc-head">
        <div>
          <button type="button" class="link-back" onClick={() => navigate(`/m/${encodeURIComponent(id)}`)}>
            <IconBack size={18} /> {t("c.back")}
          </button>
          <h1 class="px-heading">{t("sc.title")}</h1>
        </div>
        {canEdit && doc && (
          <button type="button" class="pxbtn pxbtn-gold" disabled={doc.scenes.length >= doc.limit} onClick={() => setEditing(newScene())}>
            <span>
              <IconPlus size={14} /> {t("sc.new")}
            </span>
          </button>
        )}
      </div>
      <p class="muted">
        {t("sc.intro", { name })} <DocsLink to="/docs/automation#scenes">{t("sc.docs")}</DocsLink>
      </p>
      {!doc ? (
        <p class="muted">{t("sc.unsupported")}</p>
      ) : (
        <>
          <p class="muted small">
            {t("sc.count", { n: doc.scenes.length, limit: doc.limit })}
            {!canEdit && ` · ${t("sc.needEdit")}`}
          </p>
          {doc.scenes.length > 0 && !doc.sectionId && <p class="field-hint warn">{t("sc.noSection")}</p>}
          {doc.scenes.length === 0 ? (
            <div class="rules-empty">
              <b>{t("sc.empty")}</b>
              <span class="muted small">{t("sc.emptyHint")}</span>
            </div>
          ) : (
            <ul class="rule-list">
              {doc.scenes.map((s) => (
                <SceneRow key={s.id} s={s} conn={conn} uplinks={uplinks} canEdit={canEdit} onEdit={() => setEditing(structuredClone(sceneToWire(s)))} />
              ))}
            </ul>
          )}
          {doc.log.length > 0 && (
            <section class="acc-sec" aria-labelledby="scenes-log">
              <h2 class="sec-title" id="scenes-log">
                {t("sc.log")}
              </h2>
              <ul class="rule-log">
                {[...doc.log].reverse().slice(0, 20).map((e, i) => (
                  <li key={i}>
                    <span class="muted small">{agoText(e.time, L)}</span>
                    <b>{e.name}</b>
                    <span class="muted small">{t("sc.logBy", { by: e.by, source: sourceText(e.source) })}</span>
                    <span class={`rule-res ${e.result === "ok" ? "ok" : "bad"}`}>{resText(e.result)}</span>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </>
      )}
      {editing && (
        <EditorLang.Provider value={L}>
          <SceneEditor conn={conn} initial={editing} uplinks={uplinks} onClose={() => setEditing(null)} />
        </EditorLang.Provider>
      )}
    </main>
  );
}

/** The state of each step of the last run: done, failed, running now, waiting, or no run yet. */
function stepStates(r: LastRun | null, n: number) {
  const running = !!r && r.finished === null;
  const firstOpen = r ? r.steps.findIndex((x) => x === null) : -1;
  return Array.from({ length: n }, (_, i) => {
    const x = r?.steps[i];
    if (!r) return { state: "idle" as const };
    if (x === null || x === undefined) return { state: running && i === firstOpen ? ("run" as const) : ("wait" as const) };
    return x.ok ? { state: "ok" as const } : { state: "bad" as const, error: x.error };
  });
}

function SceneRow(props: { s: SceneState; conn: MainframeConn; uplinks: Uplink[]; canEdit: boolean; onEdit: () => void }) {
  const { s, conn, uplinks } = props;
  const L = lang.value;
  const [busy, setBusy] = useState(false);
  const run = runState(s.lastRun);
  const states = stepStates(s.lastRun, s.steps.length);
  const rules = conn.rules.value?.rules.filter((r) => r.then.some((a) => a.type === "scene" && a.sceneId === s.id)) ?? [];
  const canSchedule = feature("rules") && !!conn.rules.value && conn.can("rules.edit");
  const del = async () => {
    if (!confirm(t("sc.deleteQ", { name: s.name }))) return;
    setBusy(true);
    try {
      await conn.sceneOp({ t: "scene_delete", id: s.id });
      pushToast({ level: "info", title: t("sc.title"), text: t("sc.deleted", { name: s.name }) });
    } catch (e) {
      pushToast({ level: "warn", title: s.name, text: errText(e) });
    } finally {
      setBusy(false);
    }
  };
  return (
    <li class={`rule-row scene-row ${run.state === "running" ? "running" : ""}`}>
      <div class="rule-top">
        <span class={`scene-tile sc-${s.color ?? "gray"}`} aria-hidden="true">
          <PixelIcon name={s.icon ?? "star"} size={16} />
        </span>
        <b class="rule-name">{s.name}</b>
        {s.needsPin && (
          <span class="tag tag-gray" title={t("sc.tagPinHint")}>
            <IconLock size={10} /> PIN
          </span>
        )}
        {s.confirm && <span class="tag tag-gray">{t("sc.tagConfirm")}</span>}
        <span class="rule-tools">
          <SceneRun s={s} conn={conn} running={run.state === "running"} />
          {canSchedule && (
            <button
              type="button"
              class="btn btn-small"
              disabled={s.needsPin}
              title={s.needsPin ? t("sc.schedulePin") : t("sc.scheduleHint")}
              onClick={() => navigate(`/m/${encodeURIComponent(conn.info.value?.id ?? "")}/rules?schedule=${s.id}`)}
            >
              {t("sc.schedule")}
            </button>
          )}
          {props.canEdit && (
            <>
              <button type="button" class="btn btn-small" onClick={props.onEdit}>
                {t("c.edit")}
              </button>
              <button type="button" class="btn btn-small btn-danger-ghost" disabled={busy} onClick={del}>
                {t("c.delete")}
              </button>
            </>
          )}
        </span>
      </div>
      <ol class="scene-steps" aria-label={t("sc.steps")}>
        {s.steps.map((st, i) => {
          const x = states[i];
          return (
            <li key={i} class={`ss ss-${x.state} ${st.type === "delay" ? "ss-delay" : ""}`}>
              <span class="ss-mark" aria-label={t(`sc.state.${x.state}` as Key)}>
                {x.state === "ok" ? "✓" : x.state === "bad" ? "✗" : x.state === "run" ? "" : i + 1}
              </span>
              <span class="ss-text">{describeStep(st, uplinks, L)}</span>
              {x.state === "bad" && <span class="ss-err">{resText(x.error ?? "")}</span>}
            </li>
          );
        })}
      </ol>
      <p class="muted small rule-meta">
        {stepsSummary(s.steps, L)} · {t("sc.owner", { owner: s.owner })} · {s.runs === 1 ? t("sc.run1") : t("sc.runs", { n: s.runs })}
        {s.lastRun ? ` · ${t("sc.lastRun", { ago: agoText(s.lastRun.started, L), by: s.lastRun.by, source: sourceText(s.lastRun.source) })}` : ` · ${t("sc.notRun")}`}
        {run.state === "running" && <span class="rule-res run">{t("sc.running")}</span>}
        {run.state === "ok" && <span class="rule-res ok">{t("sc.res.ok")}</span>}
        {run.state === "failed" && <span class="rule-res bad">{resText(run.error ?? "")}</span>}
      </p>
      {rules.map((r) => (
        <p key={r.id} class="small scene-sched">
          <span class="kw">{t("sc.scheduled")}</span> {t("sc.byRule", { rule: r.name, when: isTimeTrigger(r.when) ? describeWhen(r.when, L) : t("sc.onTrigger") })}
          {!r.enabled && ` · ${t("sc.ruleOff")}`}
          {r.enabled && r.nextRun ? ` · ${nextRunText(r.nextRun, L)}` : ""}
        </p>
      ))}
    </li>
  );
}

/** "Test run": the scene's button in the Scenes section, with the confirm and PIN steps of any button. */
function SceneRun({ s, conn, running }: { s: SceneState; conn: MainframeConn; running: boolean }) {
  const [confirming, setConfirming] = useState(false);
  const [pin, setPin] = useState<PinState | null>(null);
  const sectionId = conn.scenes.value?.sectionId;
  const section = conn.uplinks.value.find((u) => u.uplinkId === sectionId);
  const w = section?.widgets.find((x) => x.id === sceneButtonId(s.id));
  const needsPin = w?.opts?.pin === true || s.needsPin;
  const sending = !!(section && w && conn.busy.value[keyOf(section.uplinkId, w.id)]);
  const canRun = !!w && !!section && conn.can("act.buttons") && conn.actionable;
  const fire = (code?: string) => {
    if (!section || !w) return;
    const reqId = conn.action(section.uplinkId, w, true, false, {
      ...(code ? { pin: code } : {}),
      onResult: (r: ActionOutcome) => {
        if (r.ok) {
          setPin(null);
          pushToast({ level: "info", title: t("sc.title"), text: t("sc.started", { name: s.name }) });
          return true;
        }
        if (code && (r.error === "pin_wrong" || r.error === "wrong_pin" || r.error === "pin_required" || r.error === "pin_not_set")) {
          setPin({ kind: "error", text: rejectText(r) });
          return true;
        }
        if (r.error === "pin_locked") {
          setPin({ kind: "locked", text: rejectText(r) });
          return true;
        }
        setPin(null);
        pushToast({ level: "warn", title: s.name, text: ERR_KEYS[r.error ?? ""] ? t(ERR_KEYS[r.error!]) : rejectText(r) });
        return true;
      },
    });
    if (!reqId) setPin(null);
  };
  const start = () => {
    if (needsPin) {
      const left = Math.ceil((conn.pinLockedUntil.value - Date.now()) / 1000);
      setPin(left > 0 ? { kind: "locked", text: rejectText({ ok: false, error: "pin_locked", retryAfter: left }) } : { kind: "idle" });
    } else fire();
  };
  return (
    <>
      <button
        type="button"
        class={`btn btn-small btn-run ${sending ? "busy" : ""}`}
        disabled={!canRun || running || sending}
        title={!w ? t("sc.noSection") : !conn.can("act.buttons") ? t("sc.err.read_only") : t("sc.testHint")}
        onClick={() => (s.confirm ? setConfirming(true) : start())}
      >
        {needsPin && <IconLock size={12} />} {running ? t("sc.running") : t("sc.testRun")}
      </button>
      {confirming && (
        <ConfirmDialog
          title={`${s.name}?`}
          text={t("sc.confirmText", { name: s.name, n: s.steps.length })}
          confirmLabel={t("sc.run")}
          color={s.color ?? "cyan"}
          onCancel={() => setConfirming(false)}
          onConfirm={() => {
            setConfirming(false);
            start();
          }}
        />
      )}
      {pin && (
        <PinDialog
          title={s.name}
          text={t("sc.pinText")}
          color={s.color ?? "gold"}
          state={pin}
          onCancel={() => setPin(null)}
          onSubmit={(code) => {
            setPin({ kind: "checking" });
            fire(code);
          }}
        />
      )}
    </>
  );
}

// ------------------------------------------------------------------ editor
function freshStep(type: StepType, uplinks: Uplink[]): SceneStep {
  return type === "delay" ? { type: "delay", seconds: 2 } : (freshAction(type, uplinks) as SceneStep);
}

function SceneEditor({ conn, initial, uplinks, onClose }: { conn: MainframeConn; initial: Scene; uplinks: Uplink[]; onClose: () => void }) {
  const L = lang.value;
  const [s, setS] = useState<Scene>(initial);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [tried, setTried] = useState(false);
  const problem = validateScene(s, uplinks, L);
  const missing = missingPerms(s.steps, uplinks, (p) => conn.can(p));
  const pins = pinSteps(s.steps, uplinks);
  const set = (patch: Partial<Scene>) => setS({ ...s, ...patch });
  const setStep = (i: number, st: SceneStep) => set({ steps: s.steps.map((x, j) => (j === i ? st : x)) });
  const move = (i: number, d: -1 | 1) => {
    const steps = [...s.steps];
    [steps[i], steps[i + d]] = [steps[i + d], steps[i]];
    set({ steps });
  };
  const save = async () => {
    setTried(true);
    if (problem) return;
    setBusy(true);
    setError(null);
    try {
      await conn.sceneOp({ t: "scene_set", scene: sceneToWire(s) });
      pushToast({ level: "info", title: t("sc.title"), text: t("sc.saved", { name: s.name.trim() }) });
      onClose();
    } catch (e) {
      setError(errText(e));
    } finally {
      setBusy(false);
    }
  };
  const wait = totalDelay(s.steps);
  const firstControl = uplinks.some((u) => settableWidgets(u).length);
  return (
    <Sheet title={s.id ? t("sc.editTitle") : t("sc.newTitle")} onClose={onClose} wide>
      <form
        class="rule-editor scene-editor"
        lang={L}
        onSubmit={(e) => {
          e.preventDefault();
          save();
        }}
      >
        <div class="rule-head-fields scene-head-fields">
          <Field label={t("sc.name")}>
            <input class="input" maxLength={MAX_NAME} value={s.name} placeholder={t("sc.namePh")} onInput={(e) => set({ name: (e.target as HTMLInputElement).value })} />
          </Field>
          <div class="field">
            <span class="field-label">{t("sc.color")}</span>
            <div class="swatches" role="radiogroup" aria-label={t("sc.color")}>
              {SCENE_COLORS.map((c) => (
                <button key={c} type="button" role="radio" aria-checked={(s.color ?? "gray") === c} aria-label={t(`sc.col.${c}` as Key)} title={t(`sc.col.${c}` as Key)} class={`swatch sc-${c} ${(s.color ?? "gray") === c ? "on" : ""}`} onClick={() => set({ color: c })} />
              ))}
            </div>
          </div>
        </div>
        <div class="field">
          <span class="field-label">{t("sc.icon")}</span>
          <IconPicker value={s.icon} onChange={(icon) => set({ icon })} />
        </div>
        <div class="scene-switches">
          <label class="check-row">
            <input type="checkbox" checked={s.confirm} onChange={(e) => set({ confirm: (e.target as HTMLInputElement).checked })} />
            <span>
              {t("sc.confirm")} <span class="muted small">{t("sc.confirmHint")}</span>
            </span>
          </label>
          <label class="check-row">
            <input type="checkbox" checked={s.pin} onChange={(e) => set({ pin: (e.target as HTMLInputElement).checked })} />
            <span>
              {t("sc.pin")} <span class="muted small">{t("sc.pinHint")}</span>
            </span>
          </label>
        </div>

        <fieldset class="rule-block">
          <legend>
            <span class="kw">{t("sc.steps")}</span> <span class="muted small">{t("sc.stepsHint")}</span>
          </legend>
          {s.steps.map((st, i) => {
            const perm = stepPerm(st, uplinks);
            const lacks = perm && !conn.can(perm);
            return (
              <div key={i} class={`rule-item scene-step ${lacks ? "lacks" : ""}`}>
                <div class="step-type">
                  <span class="step-no" aria-hidden="true">
                    {i + 1}
                  </span>
                  <select class="select" aria-label={t("sc.stepN", { n: i + 1 })} value={st.type} onChange={(e) => setStep(i, freshStep((e.target as HTMLSelectElement).value as StepType, uplinks))}>
                    {STEP_TYPES.map((v) => (
                      <option key={v} value={v}>
                        {t(ACTION_LABEL[v])}
                      </option>
                    ))}
                  </select>
                </div>
                {st.type === "delay" ? (
                  <div class="rule-fields">
                    <input class="input input-num" type="number" min={0.05} max={300} step={0.05} aria-label={t("sc.seconds")} value={st.seconds} onInput={(e) => setStep(i, { type: "delay", seconds: num((e.target as HTMLInputElement).value) })} />
                    <span>{t("sc.secondsUnit")}</span>
                  </div>
                ) : (
                  <ActionFields a={st} uplinks={uplinks} onChange={(a) => setStep(i, a as SceneStep)} />
                )}
                <span class="step-tools">
                  <button type="button" class="icon-btn sm" aria-label={t("sc.up", { n: i + 1 })} title={t("sc.up", { n: i + 1 })} disabled={i === 0} onClick={() => move(i, -1)}>
                    ↑
                  </button>
                  <button type="button" class="icon-btn sm" aria-label={t("sc.down", { n: i + 1 })} title={t("sc.down", { n: i + 1 })} disabled={i === s.steps.length - 1} onClick={() => move(i, 1)}>
                    ↓
                  </button>
                  <button type="button" class="icon-btn sm" aria-label={t("sc.remove", { n: i + 1 })} title={t("sc.remove", { n: i + 1 })} disabled={s.steps.length === 1} onClick={() => set({ steps: s.steps.filter((_, j) => j !== i) })}>
                    ×
                  </button>
                </span>
                {lacks && <p class="field-hint warn step-hint">{t("sc.lacks", { perm: permName(perm!) })}</p>}
              </div>
            );
          })}
          {s.steps.length < MAX_STEPS && (
            <div class="row-btns">
              <button type="button" class="btn btn-small" onClick={() => set({ steps: [...s.steps, freshStep(firstControl ? "widget" : "alert", uplinks)] })}>
                <IconPlus size={14} /> {t("sc.addStep")}
              </button>
              <button type="button" class="btn btn-small" onClick={() => set({ steps: [...s.steps, freshStep("delay", uplinks)] })}>
                <IconPlus size={14} /> {t("sc.addWait")}
              </button>
            </div>
          )}
          <p class={`muted small ${wait > MAX_TOTAL_DELAY ? "t-red" : ""}`}>{t("sc.totalWait", { s: Math.round(wait * 100) / 100, max: MAX_TOTAL_DELAY })}</p>
        </fieldset>

        {pins.length > 0 && <p class="field-hint warn">{t("sc.pinSteps", { list: pins.join(", ") })}</p>}
        {missing.length > 0 && <p class="field-hint warn">{t("sc.missing", { list: missing.map((m) => t("sc.missingItem", { n: m.step, perm: permName(m.perm) })).join(", ") })}</p>}
        {!problem && (
          <ol class="rule-preview scene-preview">
            {s.steps.map((st, i) => (
              <li key={i}>{describeStep(st, uplinks, L)}</li>
            ))}
          </ol>
        )}
        <p class="muted small">{t("sc.ownerNote")}</p>
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
            {t("c.cancel")}
          </button>
          <button type="submit" class="pxbtn pxbtn-gold" disabled={busy}>
            <span>{busy ? t("c.saving") : t("sc.save")}</span>
          </button>
        </div>
      </form>
    </Sheet>
  );
}
