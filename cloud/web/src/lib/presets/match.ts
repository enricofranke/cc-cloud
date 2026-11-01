// Fills a preset's slots from the live base and says what is there and what is missing. Pure.
//
// A device fills one slot of a preset, so two slots asking for "a battery" get two batteries. Slots with a minimum go
// first. Candidates are ranked: the kinds a slot names come in the order it names them, working devices before offline
// ones. A slot with more candidates than it holds is what the picker asks about; everywhere else the best ones are used.
import { VIEW_SOURCES } from "../layout.ts";
import type { Uplink, Widget, WidgetType } from "../types.ts";
import type { Preset, PresetSlot, PresetWidget, Role } from "./format.ts";
import { rolesOf } from "./roles.ts";

export interface SlotMatch {
  slot: PresetSlot;
  /** every device that fits, best first (devices another slot took are not in it) */
  candidates: Uplink[];
  /** the devices that fill the slot */
  chosen: Uplink[];
  /** how many devices a required slot still needs */
  missing: number;
  /** more candidates than the slot holds: the user may want to choose */
  choice: boolean;
}

/** ready: every slot has devices. partial: all required ones do, some optional ones stay empty. missing: it cannot be applied. */
export type MatchState = "ready" | "partial" | "missing";

export interface PresetMatch {
  preset: Preset;
  state: MatchState;
  slots: SlotMatch[];
  /** widgets the preset would show on this base */
  widgets: number;
  /** devices of the base the preset uses */
  devices: number;
}

/** The user's choice per slot id: uplink ids. */
export type SlotPicks = Record<string, string[]>;

const isCard = (u: Uplink) => !!u.template && u.template !== "script";
const warns = (w: Widget) => w.type === "gauge" && (typeof w.opts?.warn === "number" || typeof w.opts?.crit === "number");
const haystack = (u: Uplink) => `${u.title ?? ""} ${u.widgets.map((w) => w.label ?? "").join(" ")}`.toLowerCase();

/** Does the device fit the slot? A blocked device (claim guard) publishes nothing, so it never does. */
export function fits(u: Uplink, slot: PresetSlot): boolean {
  if (u.blocked) return false;
  // a restricted section (claims, waystones, the server's numbers) is only used by a slot that asks for it by name
  if (u.restricted && !slot.kinds?.includes(u.template ?? "") && !slot.roles?.includes("server")) return false;
  const roles = rolesOf(u);
  if (slot.kinds || slot.roles) {
    const byKind = !!u.template && !!slot.kinds?.includes(u.template);
    const byRole = !!slot.roles?.some((r: Role) => roles.has(r));
    if (!byKind && !byRole) return false;
  } else if (!roles.has("any")) return false;
  if (slot.source && (slot.source === "card") !== isCard(u)) return false;
  if (slot.has && !slot.has.every((id) => u.widgets.some((w) => w.id === id))) return false;
  if (slot.types && !u.widgets.some((w) => slot.types!.includes(w.type))) return false;
  if (slot.text) {
    const text = haystack(u);
    if (!slot.text.some((word) => text.includes(word))) return false;
  }
  return true;
}

/** Lower is better: the position in the slot's kinds, then whether it works right now. */
function rank(u: Uplink, slot: PresetSlot) {
  const kind = slot.kinds && u.template ? slot.kinds.indexOf(u.template) : -1;
  return (kind >= 0 ? kind : (slot.kinds?.length ?? 0)) * 4 + (u.online ? 0 : 2) + (u.loaded === false ? 1 : 0);
}

/** Fills the slots of a preset; only its `slots` matter here. */
export function matchSlots(preset: Pick<Preset, "slots">, uplinks: Uplink[], picks: SlotPicks = {}): SlotMatch[] {
  const taken = new Set<string>();
  const out = new Map<string, SlotMatch>();
  // what the user chose is taken first: the automatic choice of another slot must not get in the way
  const order = [...preset.slots].sort((a, b) => Number(!!picks[b.id]) - Number(!!picks[a.id]) || Number(b.min > 0) - Number(a.min > 0));
  for (const slot of order) {
    const fit = uplinks.filter((u) => !taken.has(u.uplinkId) && fits(u, slot));
    const candidates = fit.map((u, i) => ({ u, i })).sort((a, b) => rank(a.u, slot) - rank(b.u, slot) || a.i - b.i).map((x) => x.u);
    const wanted = picks[slot.id];
    const chosen = wanted ? candidates.filter((u) => wanted.includes(u.uplinkId)).slice(0, slot.max) : candidates.slice(0, slot.max);
    for (const u of chosen) taken.add(u.uplinkId);
    out.set(slot.id, { slot, candidates, chosen, missing: Math.max(0, slot.min - chosen.length), choice: candidates.length > slot.max });
  }
  return preset.slots.map((s) => out.get(s.id)!);
}

/** The widget types a widget can be shown as, for this reference. */
const showable = (ref: PresetWidget, w: Widget) => !ref.as || VIEW_SOURCES[ref.as].includes(w.type);

/**
 * The widgets a reference means on one device. Ids first, in the order they are named; `types` and `text` fill up to
 * `count`, and are also the fallback when an id is not there (optional widgets, other versions of a card). `used` keeps
 * a widget from landing twice in one card in the same shape.
 */
export function pickWidgets(u: Uplink, ref: PresetWidget, used: Set<string>): Widget[] {
  const count = ref.count ?? 1;
  const as = (w: Widget): WidgetType => ref.as ?? w.type;
  const key = (w: Widget) => `${u.uplinkId}/${w.id}|${as(w)}`;
  const take = (w: Widget, out: Widget[]) => {
    if (out.length >= count || !showable(ref, w) || used.has(key(w))) return;
    used.add(key(w));
    out.push(w);
  };
  const out: Widget[] = [];
  for (const id of ref.ids ?? []) {
    const w = u.widgets.find((x) => x.id === id);
    if (w) take(w, out);
  }
  if (out.length >= count || (ref.ids && !ref.types && !ref.text && !ref.limits)) return out;
  for (const w of u.widgets) {
    if (ref.types && !ref.types.includes(w.type)) continue;
    if (ref.limits && !warns(w)) continue;
    if (ref.text) {
      const text = `${w.label ?? ""} ${w.opts?.unit ?? ""}`.toLowerCase();
      if (!ref.text.some((word) => text.includes(word))) continue;
    }
    take(w, out);
  }
  return out;
}

