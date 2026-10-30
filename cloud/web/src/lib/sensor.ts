// Advanced Cloud Sensor (docs/SPEC.md "Advanced Cloud Sensor"): its activity widget (`sensor_log`) as the mod sends it,
// validated, plus the plain-language pieces the card, the rules editor and the scenes need. The server already
// replaces player names with "a player" for everyone who may not see them; this file never guesses names.
import type { Uplink } from "./types.ts";

export type SensorKind = "player" | "hostile" | "passive" | "item" | "other";
export const SENSOR_KINDS: readonly SensorKind[] = ["player", "hostile", "passive", "item", "other"];

export interface InsideRow {
  k: SensorKind;
  /** a player's name (operators only), or the mob/item name; absent = "a player" */
  n?: string;
  /** entity type id (mobs, items, other) */
  id?: string;
  /** how many */
  c: number;
}
export interface SensorEvent {
  t: number;
  in: boolean;
  k: SensorKind;
  n?: string;
  id: string;
}
export interface SensorActivity {
  armed: boolean;
  today: number;
  zone: string;
  problem?: string;
  summary: string;
  inside: InsideRow[];
  /** entities beyond the listed rows */
  more: number;
  /** newest first */
  events: SensorEvent[];
}

const MAX_ROWS = 16;
const MAX_EVENTS = 20;
const str = (v: unknown, max: number) => (typeof v === "string" ? v.slice(0, max) : undefined);
const kind = (v: unknown): SensorKind | null => (SENSOR_KINDS as readonly unknown[]).includes(v) ? (v as SensorKind) : null;
const count = (v: unknown) => (typeof v === "number" && Number.isFinite(v) && v >= 0 ? Math.min(1_000_000, Math.floor(v)) : 0);

/** Checks an untrusted `sensor_log` value; anything malformed is dropped (null when it is not an activity at all). */
export function parseActivity(raw: unknown): SensorActivity | null {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) return null;
  const r = raw as Record<string, unknown>;
  const inside: InsideRow[] = [];
  for (const row of Array.isArray(r.inside) ? r.inside.slice(0, MAX_ROWS) : []) {
    if (!row || typeof row !== "object") continue;
    const x = row as Record<string, unknown>;
    const k = kind(x.k);
    if (!k) continue;
    inside.push({ k, ...(str(x.n, 64) ? { n: str(x.n, 64) } : {}), ...(str(x.id, 128) ? { id: str(x.id, 128) } : {}), c: Math.max(1, count(x.c)) });
  }
  const events: SensorEvent[] = [];
  for (const e of Array.isArray(r.events) ? r.events.slice(0, MAX_EVENTS) : []) {
    if (!e || typeof e !== "object") continue;
    const x = e as Record<string, unknown>;
    const k = kind(x.k);
    if (!k || typeof x.t !== "number" || !Number.isFinite(x.t)) continue;
    events.push({ t: x.t, in: x.in === true, k, ...(str(x.n, 64) ? { n: str(x.n, 64) } : {}), id: str(x.id, 128) ?? "" });
  }
  return {
    armed: r.armed !== false,
    today: count(r.today),
    zone: str(r.zone, 48) ?? "",
    ...(str(r.problem, 32) ? { problem: str(r.problem, 32) } : {}),
    summary: str(r.summary, 200) ?? "",
    inside,
    more: count(r.more),
    events,
  };
}

/** "Steve", "A player", "2 players", "Zombie", "3 × Zombie". */
export function rowText(r: InsideRow) {
  if (r.k === "player" && !r.n) return r.c === 1 ? "A player" : `${r.c} players`;
  const name = r.n ?? r.id ?? "?";
  return r.c === 1 ? name : `${r.c} × ${name}`;
}

/** "Steve came in", "A player left", "Zombie came in". */
export function eventText(e: SensorEvent) {
  const who = e.k === "player" && !e.n ? "A player" : (e.n ?? e.id);
  return `${who} ${e.in ? "came in" : "left"}`;
}

export const KIND_LABEL: Record<SensorKind, string> = {
  player: "Player",
  hostile: "Hostile mob",
  passive: "Passive mob",
  item: "Item",
  other: "Other",
};

/** "just now", "45s ago", "2m ago", "3h ago", then the date – for an event (the card refreshes it every few seconds). */
export function eventAge(e: SensorEvent, now = Date.now()) {
  const s = Math.max(0, Math.round((now - e.t) / 1000));
  if (s < 10) return "just now";
  if (s < 60) return `${s}s ago`;
  if (s < 3600) return `${Math.floor(s / 60)}m ago`;
  if (s < 86400) return `${Math.floor(s / 3600)}h ago`;
  return new Date(e.t).toLocaleDateString();
}

export const PROBLEM_TEXT: Record<string, string> = {
  zone_claimed: "The zone reaches into another team's claim – the sensor is not watching.",
};

/** Cards of Advanced Cloud Sensors (rules: "Advanced Sensor detects …"). */
export const sensorUplinks = (ups: Uplink[]) => ups.filter((u) => u.template === "sensor" && u.widgets.some((w) => w.type === "sensor_log"));

/** The rule trigger's kinds, as the server spells them. */
export const TRIGGER_KINDS = ["any", "player", "mob", "hostile", "passive", "item", "other"] as const;
export type TriggerKind = (typeof TRIGGER_KINDS)[number];
export const TRIGGER_KIND_TEXT: Record<TriggerKind, string> = {
  any: "anyone or anything",
  player: "a player",
  mob: "a mob",
  hostile: "a hostile mob",
  passive: "a passive mob",
  item: "an item",
  other: "something else",
};

/** Mirrors AccessGate.actionPerm for the sensor's card: arming is act.redstone, every setting network.link. */
export const sensorWidgetPerm = (widgetId: string) => (widgetId === "armed" ? "act.redstone" : "network.link");
