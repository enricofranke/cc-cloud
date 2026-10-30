// v0.10 scenes (docs/SPEC.md "Scenes (mod side)"): one button that runs several steps in order.
// The scene document, validation that mirrors the server's SceneCodec, the permission each step needs
// (AccessGate.actionPerm) and plain-language summaries in both languages.
import { sensorWidgetPerm } from "./sensor.ts";
import type { Uplink } from "./types.ts";
import type { Lang } from "./lang.ts";
import { ICON_NAMES, type IconName } from "./layout.ts";
import { SIDES, describeAction, type Action } from "./rules.ts";

export const SCENE_COLORS = ["cyan", "green", "yellow", "red", "gray"] as const;
export type SceneColor = (typeof SCENE_COLORS)[number];
export const MAX_STEPS = 16;
export const MAX_NAME = 32;
export const DELAY_MIN = 0.05;
export const DELAY_MAX = 300;
/** Total delay of one scene (seconds). */
export const MAX_TOTAL_DELAY = 600;

/** A step: every rule action type except `scene` (a scene cannot run another scene), plus a delay. */
export type SceneStep = Exclude<Action, { type: "scene" }> | { type: "delay"; seconds: number };
export type StepType = SceneStep["type"];
export const STEP_TYPES: StepType[] = ["widget", "redstone", "craft", "alert", "dashboard", "delay"];

export interface Scene {
  id?: string;
  name: string;
  icon?: IconName;
  color?: SceneColor;
  confirm: boolean;
  pin: boolean;
  steps: SceneStep[];
}
export type StepResult = { ok: true } | { ok: false; error: string } | null;
export type RunSource = "dashboard" | "tablet" | "display" | "rule" | "api";
export interface LastRun {
  by: string;
  source: RunSource;
  started: number;
  /** null while the scene is still running */
  finished: number | null;
  /** one entry per step: null = not run yet */
  steps: StepResult[];
}
export interface SceneState extends Scene {
  id: string;
  owner: string;
  needsPin: boolean;
  /** what the steps need (server side) */
  perms: string[];
  runs: number;
  lastRun: LastRun | null;
}
export interface SceneLogEntry {
  time: number;
  scene: string;
  name: string;
  by: string;
  source: RunSource;
  result: string;
}
export interface ScenesDoc {
  scenes: SceneState[];
  log: SceneLogEntry[];
  limit: number;
  /** the published Scenes section (null while the mainframe has none, e.g. unloaded) */
  sectionId: string | null;
}

const SOURCES: RunSource[] = ["dashboard", "tablet", "display", "rule", "api"];
const str = (v: unknown, max: number) => (typeof v === "string" ? v.slice(0, max) : "");
const num = (v: unknown, d = 0) => (typeof v === "number" && Number.isFinite(v) ? v : d);
const isColor = (v: unknown): v is SceneColor => (SCENE_COLORS as readonly unknown[]).includes(v);
const isIcon = (v: unknown): v is IconName => (ICON_NAMES as readonly unknown[]).includes(v);

function normalizeRun(r: any, steps: number): LastRun | null {
  if (!r || typeof r !== "object" || typeof r.started !== "number") return null;
  const list: StepResult[] = (Array.isArray(r.steps) ? r.steps : []).slice(0, steps).map((x: any) => (x && typeof x === "object" ? (x.ok === true ? { ok: true } : { ok: false, error: str(x.error, 40) || "failed" }) : null));
  while (list.length < steps) list.push(null);
  return {
    by: str(r.by, 32) || "?",
    source: SOURCES.includes(r.source) ? r.source : "dashboard",
    started: r.started,
    finished: typeof r.finished === "number" ? r.finished : null,
    steps: list,
  };
}

/** Validates the untrusted scenes document (snapshot field `scenes`, `{"t":"scenes"}` deltas). */
export function normalizeScenes(raw: any): ScenesDoc | null {
  if (!raw || typeof raw !== "object" || !Array.isArray(raw.scenes)) return null;
  const scenes: SceneState[] = raw.scenes
    .filter((s: any) => s && typeof s.id === "string" && /^[0-9a-f]{8}$/.test(s.id) && Array.isArray(s.steps))
    .slice(0, 64)
    .map((s: any) => {
      const steps = s.steps.filter((x: any) => x && typeof x.type === "string" && STEP_TYPES.includes(x.type)).slice(0, MAX_STEPS) as SceneStep[];
      return {
        id: s.id,
        name: str(s.name, MAX_NAME) || s.id,
        ...(isIcon(s.icon) ? { icon: s.icon } : {}),
        ...(isColor(s.color) ? { color: s.color } : {}),
        confirm: s.confirm === true,
        pin: s.pin === true,
        steps,
        owner: str(s.owner, 32) || "?",
        needsPin: s.needsPin === true,
        perms: Array.isArray(s.perms) ? s.perms.filter((p: unknown): p is string => typeof p === "string").slice(0, 16) : [],
        runs: Math.max(0, num(s.runs)),
        lastRun: normalizeRun(s.lastRun, steps.length),
      };
    });
  const log = (Array.isArray(raw.log) ? raw.log : [])
    .filter((e: any) => e && typeof e.time === "number")
    .slice(-50)
    .map((e: any) => ({
      time: e.time,
      scene: str(e.scene, 8),
      name: str(e.name, MAX_NAME),
      by: str(e.by, 32) || "?",
      source: SOURCES.includes(e.source) ? e.source : "dashboard",
      result: str(e.result, 40) || "?",
    }));
  return { scenes, log, limit: num(raw.limit, 32), sectionId: typeof raw.sectionId === "string" && raw.sectionId ? raw.sectionId.slice(0, 64) : null };
}

/** The button of a scene in the Scenes section. */
export const sceneButtonId = (id: string) => `s_${id}`;

/** Removes server-side fields before sending (the server ignores them anyway). */
export function sceneToWire(s: Scene): Scene {
  return {
    ...(s.id ? { id: s.id } : {}),
    name: s.name.trim(),
    ...(s.icon ? { icon: s.icon } : {}),
    ...(s.color ? { color: s.color } : {}),
    confirm: s.confirm,
    pin: s.pin,
    steps: s.steps,
  };
}

export function newScene(): Scene {
  return { name: "", icon: "star", color: "cyan", confirm: false, pin: false, steps: [{ type: "alert", text: "", level: "info" }] };
}

/** Seconds of all delay steps. */
export const totalDelay = (steps: readonly SceneStep[]) => steps.reduce((s, x) => s + (x.type === "delay" && Number.isFinite(x.seconds) ? x.seconds : 0), 0);

// ------------------------------------------------------------- permissions
const POWER_IDS = new Set(["power", "turn_on", "reboot", "shutdown"]);
/**
 * The permission a step needs when it runs (mirrors AccessGate.actionPerm): widgets their action permission
 * (act.redstone for redstone widgets and the auto Computer card's power buttons, else act.buttons),
 * redstone act.redstone, craft storage.craft, dashboard power.toggle; alerts and delays nothing.
 */
export function stepPerm(step: SceneStep, uplinks: Uplink[]): string | null {
  switch (step.type) {
    case "widget": {
      const u = uplinks.find((x) => x.uplinkId === step.uplinkId);
      const w = u?.widgets.find((x) => x.id === step.widgetId);
      // the Advanced Cloud Sensor's card: arming act.redstone, every setting network.link
      if (u?.template === "sensor" && u.mode === "direct") return sensorWidgetPerm(step.widgetId);
      if (step.widgetId === "redstone" || step.widgetId.endsWith("_redstone") || w?.type === "redstone_sides") return "act.redstone";
      if (u?.mode === "auto" && POWER_IDS.has(step.widgetId)) return "act.redstone";
      return "act.buttons";
    }
    case "redstone":
      return "act.redstone";
    case "craft":
      return "storage.craft";
    case "dashboard":
      return "power.toggle";
    default:
      return null;
  }
}
/** Steps whose permission this session lacks (1-based step numbers, like the server's `step <n>: <perm>`). */
export function missingPerms(steps: readonly SceneStep[], uplinks: Uplink[], can: (perm: string) => boolean) {
  const out: { step: number; perm: string }[] = [];
  steps.forEach((s, i) => {
    const p = stepPerm(s, uplinks);
    if (p && !can(p)) out.push({ step: i + 1, perm: p });
  });
  return out;
}
/** A step that targets a PIN-protected widget: the scene always asks for the PIN (and rules cannot run it). */
export const pinSteps = (steps: readonly SceneStep[], uplinks: Uplink[]) =>
  steps.flatMap((s, i) => (s.type === "widget" && uplinks.find((u) => u.uplinkId === s.uplinkId)?.widgets.find((w) => w.id === s.widgetId)?.opts?.pin === true ? [i + 1] : []));

// ------------------------------------------------------------ validation
const ITEM = /^[a-z0-9_.-]+:[a-z0-9_./-]+$/;
const V = {
  en: {
    name: "Give the scene a name (up to 32 characters).",
    steps: "Add 1–16 steps.",
    delay: "Step {n}: a delay of 0.05–300 seconds.",
    total: "All delays together may be at most 600 seconds.",
    card: "Step {n}: pick a card and a control.",
    text: "Step {n}: text up to 256 characters.",
    redstone: "Step {n}: pick a computer, a side and a level 0–15.",
    craft: "Step {n}: crafting needs a storage card, an item id like minecraft:iron_ingot and 1–{max}.",
    alert: "Step {n}: alert text up to 120 characters.",
  },
  de: {
    name: "Gib der Szene einen Namen (bis 32 Zeichen).",
    steps: "Füge 1–16 Schritte hinzu.",
    delay: "Schritt {n}: eine Pause von 0,05–300 Sekunden.",
    total: "Alle Pausen zusammen dürfen höchstens 600 Sekunden lang sein.",
    card: "Schritt {n}: wähle eine Karte und ein Bedienelement.",
    text: "Schritt {n}: Text bis 256 Zeichen.",
    redstone: "Schritt {n}: wähle einen Computer, eine Seite und eine Stärke 0–15.",
    craft: "Schritt {n}: Craften braucht eine Speicherkarte, eine Item-ID wie minecraft:iron_ingot und 1–{max}.",
    alert: "Schritt {n}: Meldungstext bis 120 Zeichen.",
  },
};
const fill = (s: string, v: Record<string, string | number>) => s.replace(/\{(\w+)\}/g, (_, k) => String(v[k] ?? ""));

/** The first problem, mirroring the server's checks (null = fine). */
export function validateScene(s: Scene, uplinks: Uplink[], lang: Lang = "en", maxCraft = 4096): string | null {
  const x = V[lang];
  if (!s.name.trim() || s.name.length > MAX_NAME) return x.name;
  if (!s.steps.length || s.steps.length > MAX_STEPS) return x.steps;
  const up = (id: string) => uplinks.some((u) => u.uplinkId === id);
  for (let i = 0; i < s.steps.length; i++) {
    const a = s.steps[i];
    const n = i + 1;
    if (a.type === "delay" && !(Number.isFinite(a.seconds) && a.seconds >= DELAY_MIN && a.seconds <= DELAY_MAX)) return fill(x.delay, { n });
    if (a.type === "widget" && (!up(a.uplinkId) || !a.widgetId)) return fill(x.card, { n });
    if (a.type === "widget" && typeof a.value === "string" && a.value.length > 256) return fill(x.text, { n });
    if (a.type === "redstone" && (!up(a.uplinkId) || !SIDES.includes(a.side) || !Number.isInteger(a.value) || a.value < 0 || a.value > 15)) return fill(x.redstone, { n });
    if (a.type === "craft" && (!up(a.uplinkId) || !ITEM.test(a.item) || !Number.isInteger(a.amount) || a.amount < 1 || a.amount > maxCraft)) return fill(x.craft, { n, max: maxCraft });
    if (a.type === "alert" && (!a.text.trim() || a.text.length > 120)) return fill(x.alert, { n });
  }
  if (totalDelay(s.steps) > MAX_TOTAL_DELAY) return x.total;
  return null;
}

// --------------------------------------------------------------- summaries
/** "wait 2 s" / "2 s warten", "wait 0.5 s". */
export function delayText(seconds: number, lang: Lang) {
  const v = Number.isInteger(seconds) ? String(seconds) : String(Math.round(seconds * 100) / 100);
  return lang === "de" ? `${v.replace(".", ",")} s warten` : `wait ${v} s`;
}
/** One step in plain words. */
export function describeStep(step: SceneStep, uplinks: Uplink[], lang: Lang = "en"): string {
  return step.type === "delay" ? delayText(step.seconds, lang) : describeAction(step, uplinks, lang);
}
/** "4 steps · 2 s of waiting" */
export function stepsSummary(steps: readonly SceneStep[], lang: Lang) {
  const n = steps.length;
  const wait = totalDelay(steps);
  const w = Number.isInteger(wait) ? String(wait) : (Math.round(wait * 10) / 10).toString();
  const head = lang === "de" ? `${n} Schritt${n === 1 ? "" : "e"}` : `${n} step${n === 1 ? "" : "s"}`;
  if (!wait) return head;
  return lang === "de" ? `${head} · ${w.replace(".", ",")} s Pausen` : `${head} · ${w} s of waiting`;
}
/** The state of the last run: running (unfinished), ok, failed (first error) or none. */
export function runState(r: LastRun | null): { state: "running" | "ok" | "failed" | "none"; error?: string } {
  if (!r) return { state: "none" };
  if (r.finished === null) return { state: "running" };
  const bad = r.steps.find((x): x is { ok: false; error: string } => !!x && x.ok === false);
  return bad ? { state: "failed", error: bad.error } : { state: "ok" };
}
