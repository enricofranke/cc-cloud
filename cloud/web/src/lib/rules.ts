// v0.5 C rules (docs/SPEC.md "C. Rules – web protocol and implementation notes"):
// the rule document, validation that mirrors the server, plain-English summaries.
import type { Uplink, Widget } from "./types.ts";

export const OPS = ["<", "<=", ">", ">=", "==", "!="] as const;
export type Op = (typeof OPS)[number];
export const SIDES = ["top", "bottom", "left", "right", "front", "back"] as const;
export type Side = (typeof SIDES)[number];

export type Check =
  | { type: "widget"; uplinkId: string; widgetId: string; op: Op; value: number }
  | { type: "items"; uplinkId: string; item: string; op: Op; value: number }
  | { type: "power"; op: Op; value: number };
export type Trigger = Check | { type: "offline"; uplinkId: string } | { type: "interval"; minutes: number } | { type: "time"; time: number };
export type Condition = Check | { type: "online"; uplinkId: string };
export type Action =
  | { type: "redstone"; uplinkId: string; side: Side; value: number }
  | { type: "widget"; uplinkId: string; widgetId: string; value: number | string | boolean }
  | { type: "craft"; uplinkId: string; item: string; amount: number }
  | { type: "alert"; text: string; level: "info" | "warn" | "error" }
  | { type: "dashboard"; enabled: boolean };

export interface Rule {
  id?: string;
  name: string;
  enabled: boolean;
  cooldown: number;
  notify: boolean;
  when: Trigger;
  if: Condition[];
  then: Action[];
}
export interface RuleState extends Rule {
  id: string;
  lastRun: number | null;
  lastResult: string | null;
  runs: number;
}
export interface RulesDoc {
  rules: RuleState[];
  log: { time: number; rule: string; name: string; result: string; manual: boolean }[];
  limit: number;
}

const str = (v: unknown, max: number) => (typeof v === "string" ? v.slice(0, max) : "");
const n = (v: unknown, d = 0) => (typeof v === "number" && Number.isFinite(v) ? v : d);

export function normalizeRules(raw: any): RulesDoc | null {
  if (!raw || typeof raw !== "object" || !Array.isArray(raw.rules)) return null;
  const rules: RuleState[] = raw.rules
    .filter((r: any) => r && typeof r.id === "string" && /^[0-9a-f]{8}$/.test(r.id) && r.when && Array.isArray(r.then))
    .slice(0, 128)
    .map((r: any) => ({
      id: r.id,
      name: str(r.name, 48) || r.id,
      enabled: r.enabled !== false,
      cooldown: n(r.cooldown, 30),
      notify: r.notify === true,
      when: r.when,
      if: Array.isArray(r.if) ? r.if.slice(0, 8) : [],
      then: r.then.slice(0, 10),
      lastRun: typeof r.lastRun === "number" ? r.lastRun : null,
      lastResult: typeof r.lastResult === "string" ? r.lastResult.slice(0, 40) : null,
      runs: n(r.runs),
    }));
  const log = (Array.isArray(raw.log) ? raw.log : [])
    .filter((e: any) => e && typeof e.time === "number")
    .slice(-50)
    .map((e: any) => ({ time: e.time, rule: str(e.rule, 8), name: str(e.name, 48), result: str(e.result, 40) || "?", manual: e.manual === true }));
  return { rules, log, limit: n(raw.limit, 8) };
}

// ------------------------------------------------------------ time of day
/** In-game ticks (0 = 06:00) ↔ "HH:MM". */
export function ticksToClock(t: number) {
  const mins = Math.round(((((t + 6000) % 24000) + 24000) % 24000) * 0.06);
  return `${String(Math.floor(mins / 60) % 24).padStart(2, "0")}:${String(mins % 60).padStart(2, "0")}`;
}
export function clockToTicks(hhmm: string) {
  const m = /^(\d{1,2}):(\d{2})$/.exec(hhmm.trim());
  if (!m || Number(m[1]) > 23 || Number(m[2]) > 59) return null;
  const mins = Number(m[1]) * 60 + Number(m[2]);
  return Math.round((((mins / 0.06 - 6000) % 24000) + 24000) % 24000);
}

// ------------------------------------------------------------- widgets
const NUMERIC = new Set(["gauge", "progress", "chart", "slider", "number", "indicator", "toggle"]);
const SETTABLE = new Set(["button", "toggle", "slider", "select", "number", "text"]);
export const numericWidgets = (u: Uplink) => u.widgets.filter((w) => NUMERIC.has(w.type));
export const settableWidgets = (u: Uplink) => u.widgets.filter((w) => SETTABLE.has(w.type) && !w.opts?.pin);
export const storageUplinks = (ups: Uplink[]) => ups.filter((u) => u.widgets.some((w) => w.type === "item_search"));
/** Computers with an automatic card (their redstone sides can be set). */
export const redstoneUplinks = (ups: Uplink[]) => ups.filter((u) => u.mode === "auto" || u.widgets.some((w) => w.type === "redstone_sides"));

// ------------------------------------------------------------ validation
const ITEM = /^[a-z0-9_.-]+:[a-z0-9_./-]+$/;
/** The first problem, mirroring the server's checks (null = fine). */
export function validateRule(r: Rule, uplinks: Uplink[], maxCraft = 4096): string | null {
  if (!r.name.trim() || r.name.length > 48) return "Give the rule a name (up to 48 characters).";
  if (!Number.isInteger(r.cooldown) || r.cooldown < 5 || r.cooldown > 86400) return "Cooldown: 5 seconds to 24 hours.";
  const up = (id: string) => uplinks.find((u) => u.uplinkId === id);
  const check = (c: Check | Condition | Trigger, where: string): string | null => {
    switch (c.type) {
      case "widget":
        if (!up(c.uplinkId) || !c.widgetId) return `${where}: pick a card and a widget.`;
        if (!Number.isFinite(c.value)) return `${where}: enter a number.`;
        return null;
      case "items":
        if (!up(c.uplinkId)) return `${where}: pick a storage card.`;
        if (!ITEM.test(c.item)) return `${where}: item ids look like minecraft:iron_ingot.`;
        if (!Number.isFinite(c.value) || c.value < 0) return `${where}: the amount must be 0 or more.`;
        return null;
      case "power":
        return c.value >= 0 && c.value <= 100 ? null : `${where}: power is 0–100 %.`;
      case "offline":
      case "online":
        return up(c.uplinkId) ? null : `${where}: pick a card.`;
      case "interval":
        return Number.isInteger(c.minutes) && c.minutes >= 1 && c.minutes <= 1440 ? null : `${where}: every 1–1440 minutes.`;
      case "time":
        return Number.isInteger(c.time) && c.time >= 0 && c.time <= 23999 ? null : `${where}: pick a time of day.`;
    }
  };
  const w = check(r.when, "When");
  if (w) return w;
  if (r.if.length > 8) return "At most 8 conditions.";
  for (const c of r.if) {
    const e = check(c, "If");
    if (e) return e;
  }
  if (!r.then.length) return "Add at least one action.";
  if (r.then.length > 10) return "At most 10 actions.";
  for (const a of r.then) {
    if (a.type === "redstone" && (!up(a.uplinkId) || !SIDES.includes(a.side) || a.value < 0 || a.value > 15)) return "Then: pick a computer, a side and a level 0–15.";
    if (a.type === "widget" && (!up(a.uplinkId) || !a.widgetId)) return "Then: pick a card and a control.";
    if (a.type === "widget" && typeof a.value === "string" && a.value.length > 256) return "Then: text up to 256 characters.";
    if (a.type === "craft" && (!up(a.uplinkId) || !ITEM.test(a.item) || !Number.isInteger(a.amount) || a.amount < 1 || a.amount > maxCraft)) return `Then: crafting needs a storage card, an item id and 1–${maxCraft}.`;
    if (a.type === "alert" && (!a.text.trim() || a.text.length > 120)) return "Then: alert text up to 120 characters.";
  }
  // loop protection: an action may not write what the trigger watches
  if (r.when.type === "widget") {
    const t = r.when;
    if (r.then.some((a) => a.type === "widget" && a.uplinkId === t.uplinkId && a.widgetId === t.widgetId)) return "Loop: an action changes the widget that triggers the rule.";
    if (r.then.some((a) => a.type === "redstone" && a.uplinkId === t.uplinkId && /(^|_)redstone$/.test(t.widgetId))) return "Loop: the redstone action changes what the trigger watches.";
  }
  return null;
}

/** Removes client-only state before sending (the server ignores the run fields anyway). */
export function toWire(r: Rule): Rule {
  const { id, name, enabled, cooldown, notify, when, then } = r;
  return { ...(id ? { id } : {}), name: name.trim(), enabled, cooldown, notify, when, if: r.if, then };
}

// --------------------------------------------------------------- summaries
const OP_TEXT: Record<Op, string> = { "<": "below", "<=": "at most", ">": "above", ">=": "at least", "==": "equals", "!=": "is not" };
export const opText = (o: Op) => OP_TEXT[o];
function names(uplinks: Uplink[]) {
  const card = (id: string) => uplinks.find((u) => u.uplinkId === id)?.title || "a missing card";
  const widget = (id: string, wid: string) => {
    const w: Widget | undefined = uplinks.find((u) => u.uplinkId === id)?.widgets.find((x) => x.id === wid);
    return w?.label || wid;
  };
  return { card, widget };
}
const itemName = (item: string) => item.replace(/^[^:]+:/, "").replace(/_/g, " ");

export function describeCheck(c: Trigger | Condition, uplinks: Uplink[]): string {
  const { card, widget } = names(uplinks);
  switch (c.type) {
    case "widget":
      return `${widget(c.uplinkId, c.widgetId)} (${card(c.uplinkId)}) ${opText(c.op)} ${c.value}`;
    case "items":
      return `${itemName(c.item)} in ${card(c.uplinkId)} ${opText(c.op)} ${c.value.toLocaleString("en-US")}`;
    case "power":
      return `mainframe power ${opText(c.op)} ${c.value} %`;
    case "offline":
      return `${card(c.uplinkId)} goes offline`;
    case "online":
      return `${card(c.uplinkId)} is online`;
    case "interval":
      return c.minutes === 1 ? "every minute" : c.minutes % 60 === 0 ? `every ${c.minutes / 60} h` : `every ${c.minutes} minutes`;
    case "time":
      return `at ${ticksToClock(c.time)} in-game`;
  }
}
export function describeAction(a: Action, uplinks: Uplink[]): string {
  const { card, widget } = names(uplinks);
  switch (a.type) {
    case "redstone":
      return `set redstone ${a.side} of ${card(a.uplinkId)} to ${a.value}`;
    case "widget":
      return typeof a.value === "boolean" ? `turn ${widget(a.uplinkId, a.widgetId)} ${a.value ? "on" : "off"} (${card(a.uplinkId)})` : `set ${widget(a.uplinkId, a.widgetId)} to ${a.value} (${card(a.uplinkId)})`;
    case "craft":
      return `craft ${a.amount.toLocaleString("en-US")} × ${itemName(a.item)} (${card(a.uplinkId)})`;
    case "alert":
      return `alert “${a.text}” (${a.level})`;
    case "dashboard":
      return `${a.enabled ? "enable" : "disable"} the dashboard`;
  }
}

export const RESULT_TEXT: Record<string, string> = {
  ok: "ran fine",
  pin_required: "a control needs the PIN",
  offline: "mainframe offline",
  rate_limited: "too many runs",
  unknown_widget: "a widget is gone",
  unknown_uplink: "a card is gone",
  uplink_offline: "a card was offline",
  not_craftable: "not craftable",
};

/** A fresh rule with sensible defaults for the editor. */
export function newRule(uplinks: Uplink[]): Rule {
  const u = uplinks.find((x) => numericWidgets(x).length);
  const w = u ? numericWidgets(u)[0] : undefined;
  return {
    name: "",
    enabled: true,
    cooldown: 30,
    notify: false,
    when: u && w ? { type: "widget", uplinkId: u.uplinkId, widgetId: w.id, op: ">", value: 0 } : { type: "interval", minutes: 10 },
    if: [],
    then: [{ type: "alert", text: "", level: "warn" }],
  };
}
