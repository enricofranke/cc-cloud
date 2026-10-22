import { SIDES, type RedstoneRow, type Widget } from "./types.ts";

const clamp = (n: unknown) => (typeof n === "number" && Number.isFinite(n) ? Math.max(0, Math.min(15, Math.round(n))) : 0);

/** Normalises a redstone_sides value: always six rows in SIDES order, levels clamped to 0–15. */
export function redstoneRows(w: Pick<Widget, "value">): RedstoneRow[] {
  const rows = Array.isArray(w.value) ? w.value : [];
  return SIDES.map((side) => {
    const r = rows.find((x) => x.side === side);
    return { side, out: clamp(r?.out), in: clamp(r?.in) };
  });
}

// ---------------------------------------------------------------------------
// Compatibility: an auto Computer card built from plain widgets
// (`<side>_in` indicators + `<side>_out` toggles in group "redstone",
// optional `<side>_level` sliders in group "advanced") is shown as one compact
// redstone_sides widget. Actions are mapped back to the original widgets.
import type { Uplink } from "./types.ts";
import { SIDES as ALL_SIDES } from "./types.ts";

export const VIRTUAL_REDSTONE_ID = "__redstone";

/**
 * Auto Computer card: only the power buttons that make sense are active.
 * On → "Turn on" is disabled; off → "Reboot" and "Shut down" are disabled.
 * Returns undefined for any other widget / card.
 */
export function powerButtonState(u: Uplink, widgetId: string): { disabled: boolean; reason: string } | undefined {
  if (widgetId !== "turn_on" && widgetId !== "reboot" && widgetId !== "shutdown") return undefined;
  const power = u.widgets.find((w) => w.id === "power" && w.type === "indicator");
  if (!power || typeof power.value !== "boolean") return undefined;
  const on = power.value;
  if (widgetId === "turn_on") return { disabled: on, reason: "The computer is already on" };
  return { disabled: !on, reason: "The computer is off" };
}

const lvl = (v: unknown) => (v === true ? 15 : typeof v === "number" && Number.isFinite(v) ? Math.max(0, Math.min(15, Math.round(v))) : 0);

export function adaptComputerCard(u: Uplink): Uplink {
  const byId = new Map(u.widgets.map((w) => [w.id, w]));
  const isCard = ALL_SIDES.every((s) => byId.get(`${s}_out`)?.type === "toggle" && byId.get(`${s}_in`)?.type === "indicator");
  if (!isCard || u.widgets.some((w) => w.type === "redstone_sides")) return u;
  const consumed = new Set<string>();
  const rows = ALL_SIDES.map((side) => {
    const out = byId.get(`${side}_out`)!;
    const inn = byId.get(`${side}_in`)!;
    const level = byId.get(`${side}_level`);
    consumed.add(out.id);
    consumed.add(inn.id);
    if (level?.type === "slider") consumed.add(level.id);
    const analog = level?.type === "slider" ? lvl(level.value) : 0;
    return { side, out: analog > 0 ? analog : out.value === true ? 15 : 0, in: lvl(inn.value) };
  });
  const widgets = u.widgets.filter((w) => !consumed.has(w.id));
  widgets.push({ id: VIRTUAL_REDSTONE_ID, type: "redstone_sides", label: "Redstone", opts: { legacy: true } as never, value: rows });
  return {
    ...u,
    mode: u.mode ?? "auto",
    widgets,
    groups: (u.groups ?? []).filter((g) => g.id !== "redstone" && g.id !== "advanced"),
  };
}

/** For an adapted card: the real widget and value that set `side` to `level`. */
export function legacyTarget(u: Uplink, side: string, level: number): { id: string; type: "toggle" | "slider"; value: boolean | number } {
  const slider = u.widgets.find((w) => w.id === `${side}_level` && w.type === "slider");
  if (slider && level !== 0 && level !== 15) return { id: slider.id, type: "slider", value: level };
  return { id: `${side}_out`, type: "toggle", value: level > 0 };
}
