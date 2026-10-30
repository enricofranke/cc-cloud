// Editor parts shared by the Rules editor ("Then" actions) and the Scenes editor (steps): the card,
// control, side, craft and alert pickers and fresh defaults per action type. Labels come from the
// i18n table; the Rules screen is English (no provider), the Scenes screen passes its language.
import { createContext, type ComponentChildren } from "preact";
import { useContext } from "preact/hooks";
import type { Uplink } from "../lib/types.ts";
import { SIDES, redstoneUplinks, settableWidgets, storageUplinks, type Action, type SceneRef } from "../lib/rules.ts";
import { tr, type Key, type Lang } from "../lib/i18n.ts";

/** The language of the shared editor parts (default English, like the Rules screen). */
export const EditorLang = createContext<Lang>("en");
export function useTr() {
  const l = useContext(EditorLang);
  return (key: Key, vars?: Record<string, string | number>) => tr(l, key, vars);
}

export function Field({ label, children, hint }: { label: string; children: ComponentChildren; hint?: string }) {
  return (
    <label class="field">
      <span class="field-label">{label}</span>
      {children}
      {hint && <span class="field-hint">{hint}</span>}
    </label>
  );
}

export function UplinkSelect({ value, list, onChange, label }: { value: string; list: Uplink[]; onChange: (id: string) => void; label?: string }) {
  const T = useTr();
  return (
    <select class="select" aria-label={label ?? T("ed.card")} value={value} onChange={(e) => onChange((e.target as HTMLSelectElement).value)}>
      {!list.some((u) => u.uplinkId === value) && <option value="">{T("ed.pickCard")}</option>}
      {list.map((u) => (
        <option key={u.uplinkId} value={u.uplinkId}>
          {u.title || T("ed.untitled")}
        </option>
      ))}
    </select>
  );
}

/** Number inputs: an empty field is NaN (validation explains it). */
export const num = (s: string) => (s.trim() === "" ? NaN : Number(s));

/** Action types with their label keys (the Rules editor adds `scene` when the server has scenes). */
export const ACTION_LABEL: Record<Action["type"] | "delay", Key> = {
  redstone: "ed.act.redstone",
  widget: "ed.act.widget",
  craft: "ed.act.craft",
  alert: "ed.act.alert",
  dashboard: "ed.act.dashboard",
  scene: "ed.act.scene",
  delay: "ed.act.delay",
};

/** A new action of a type with sensible defaults (the first matching card, control, scene). */
export function freshAction(type: Action["type"], uplinks: Uplink[], scenes: SceneRef[] = []): Action {
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
    case "scene":
      return { type, sceneId: scenes.find((s) => !s.needsPin)?.id ?? "" };
  }
}

/** The editable fields of one action. `scenes` feeds the scene picker of `scene` actions. */
export function ActionFields({ a, uplinks, onChange, scenes = [] }: { a: Action; uplinks: Uplink[]; onChange: (a: Action) => void; scenes?: SceneRef[] }) {
  const T = useTr();
  switch (a.type) {
    case "redstone":
      return (
        <div class="rule-fields">
          <UplinkSelect value={a.uplinkId} list={redstoneUplinks(uplinks)} label={T("ed.computer")} onChange={(uplinkId) => onChange({ ...a, uplinkId })} />
          <select class="select" aria-label={T("ed.side")} value={a.side} onChange={(e) => onChange({ ...a, side: (e.target as HTMLSelectElement).value as typeof a.side })}>
            {SIDES.map((s) => (
              <option key={s} value={s}>
                {T(`ed.side.${s}`)}
              </option>
            ))}
          </select>
          <input class="input input-num" type="number" min={0} max={15} aria-label={T("ed.level")} value={a.value} onInput={(e) => onChange({ ...a, value: num((e.target as HTMLInputElement).value) })} />
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
          <select class="select" aria-label={T("ed.control")} value={a.widgetId} onChange={(e) => onChange({ ...a, widgetId: (e.target as HTMLSelectElement).value, value: valueFor((e.target as HTMLSelectElement).value) })}>
            {ws.map((x) => (
              <option key={x.id} value={x.id}>
                {x.label || x.id} ({x.type})
              </option>
            ))}
          </select>
          {w?.type === "toggle" && (
            <select class="select" aria-label={T("ed.turn")} value={a.value ? "on" : "off"} onChange={(e) => onChange({ ...a, value: (e.target as HTMLSelectElement).value === "on" })}>
              <option value="on">{T("ed.on")}</option>
              <option value="off">{T("ed.off")}</option>
            </select>
          )}
          {w?.type === "button" && <span class="muted small">{T("ed.pressIt")}</span>}
          {(w?.type === "slider" || w?.type === "number") && (
            <input class="input input-num" type="number" aria-label={T("ed.value")} min={w.opts?.min} max={w.opts?.max} value={String(a.value)} onInput={(e) => onChange({ ...a, value: num((e.target as HTMLInputElement).value) })} />
          )}
          {w?.type === "select" && (
            <select class="select" aria-label={T("ed.option")} value={String(a.value)} onChange={(e) => onChange({ ...a, value: (e.target as HTMLSelectElement).value })}>
              {((w.opts?.options ?? []) as unknown[]).filter((x): x is string => typeof x === "string").map((o) => (
                <option key={o} value={o}>
                  {o}
                </option>
              ))}
            </select>
          )}
          {w?.type === "text" && <input class="input" maxLength={256} aria-label={T("ed.text")} value={String(a.value)} onInput={(e) => onChange({ ...a, value: (e.target as HTMLInputElement).value })} />}
        </div>
      );
    }
    case "craft":
      return (
        <div class="rule-fields">
          <UplinkSelect value={a.uplinkId} list={storageUplinks(uplinks)} label={T("ed.storageCard")} onChange={(uplinkId) => onChange({ ...a, uplinkId })} />
          <input class="input" placeholder="minecraft:iron_ingot" aria-label={T("ed.itemId")} value={a.item} onInput={(e) => onChange({ ...a, item: (e.target as HTMLInputElement).value.trim().toLowerCase() })} />
          <input class="input input-num" type="number" min={1} aria-label={T("ed.amount")} value={a.amount} onInput={(e) => onChange({ ...a, amount: num((e.target as HTMLInputElement).value) })} />
        </div>
      );
    case "alert":
      return (
        <div class="rule-fields">
          <input class="input rule-grow" maxLength={120} placeholder={T("ed.alertPlaceholder")} aria-label={T("ed.alertText")} value={a.text} onInput={(e) => onChange({ ...a, text: (e.target as HTMLInputElement).value })} />
          <select class="select" aria-label={T("ed.levelSel")} value={a.level} onChange={(e) => onChange({ ...a, level: (e.target as HTMLSelectElement).value as typeof a.level })}>
            <option value="info">{T("ed.info")}</option>
            <option value="warn">{T("ed.warning")}</option>
            <option value="error">{T("ed.error")}</option>
          </select>
        </div>
      );
    case "dashboard":
      return (
        <div class="rule-fields">
          <select class="select" aria-label={T("ed.dashboard")} value={a.enabled ? "on" : "off"} onChange={(e) => onChange({ ...a, enabled: (e.target as HTMLSelectElement).value === "on" })}>
            <option value="off">{T("ed.dashOff")}</option>
            <option value="on">{T("ed.dashOn")}</option>
          </select>
        </div>
      );
    case "scene":
      return (
        <div class="rule-fields">
          {scenes.length === 0 ? (
            <span class="muted small">{T("ed.noScenes")}</span>
          ) : (
            <select class="select" aria-label={T("ed.scene")} value={a.sceneId} onChange={(e) => onChange({ ...a, sceneId: (e.target as HTMLSelectElement).value })}>
              {!scenes.some((s) => s.id === a.sceneId) && <option value="">{T("ed.pickScene")}</option>}
              {scenes.map((s) => (
                <option key={s.id} value={s.id} disabled={s.needsPin}>
                  {s.name}
                  {s.needsPin ? ` – ${T("ed.scenePin")}` : ""}
                </option>
              ))}
            </select>
          )}
          {scenes.some((s) => s.needsPin) && <span class="muted small">{T("ed.scenePinHint")}</span>}
        </div>
      );
  }
}
