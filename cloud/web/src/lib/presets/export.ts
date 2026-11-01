// "Share as preset": one page of this dashboard as a preset. Nothing of this world goes into it: no uplink ids and no
// values, and the devices' names stay out as well, a card named after its device is named after whatever fills it.
//
// Every device becomes a slot. A device card is asked for by its kind. A program has no kind, so it is asked for by its
// role and the widget ids this page uses (programs from the store keep their ids between worlds). Cloud Redstone widget
// ids (`d_<device id>`) belong to one world: those are asked for by type, in their order on the card.
import type { ResolvedCard, ResolvedPage, ResolvedWidget } from "../layout.ts";
import type { Uplink, Widget } from "../types.ts";
import { PRESET_FORMAT, PRESET_LIMITS, cleanText, parsePreset, type Preset, type PresetCard, type PresetSlot, type PresetWidget, type Role } from "./format.ts";
import { ACTION_TYPES, rolesOf } from "./roles.ts";

export interface ExportResult {
  preset: Preset;
  /** widgets left out: their device is gone or blocked, or the preset is full */
  skipped: number;
  /** widgets that came from restricted sections: whoever imports the preset needs their own */
  restricted: number;
}

/** Roles worth asking for, most telling first. */
const TELLING: Role[] = ["reactor", "generator", "storage", "energy", "farm", "door", "redstone", "fluid", "items", "machine", "sensor", "computer"];
const LOCAL_ID = /^d_[0-9a-f]{12}/;

interface Source {
  uplink: Uplink;
  widget: Widget;
}

function sourceOf(rw: ResolvedWidget, byId: Map<string, Uplink>): Source | null {
  const src = rw.view?.src ?? rw.key;
  const slash = src.indexOf("/");
  if (slash < 1) return null;
  const uplink = byId.get(src.slice(0, slash));
  const widget = uplink?.widgets.find((w) => w.id === src.slice(slash + 1));
  return uplink && widget && !uplink.blocked ? { uplink, widget } : null;
}

const isCardKind = (u: Uplink) => !!u.template && u.template !== "script";

function slotOf(id: string, u: Uplink, ids: Set<string>, count: number): PresetSlot {
  const roles = rolesOf(u);
  const telling = TELLING.filter((r) => roles.has(r));
  const stable = [...ids].filter((w) => !LOCAL_ID.test(w)).slice(0, PRESET_LIMITS.LIST);
  const slot: PresetSlot = { id, label: isCardKind(u) ? u.template!.replace(/_/g, " ").slice(0, PRESET_LIMITS.LABEL) : (telling[0] ?? "device"), min: count, max: 1 };
  if (isCardKind(u)) slot.kinds = [u.template!];
  else {
    slot.roles = telling.length ? telling.slice(0, 1) : ["any"];
    slot.source = "program";
    if (stable.length) slot.has = stable;
  }
  return slot;
}

function refOf(rw: ResolvedWidget, s: Source, slot: string, single: boolean): PresetWidget {
  const as = rw.view?.as ?? s.widget.type;
  const action = ACTION_TYPES.has(as);
  const ownLabel = rw.label !== (s.widget.label ?? s.widget.id);
  const ref: PresetWidget = {
    slot,
    ...(LOCAL_ID.test(s.widget.id) ? {} : { ids: [s.widget.id] }),
    types: [s.widget.type],
    ...(as !== s.widget.type ? { as } : {}),
    ...(rw.display !== "auto" ? { display: rw.display } : {}),
    ...(single ? { x: rw.box.x, y: rw.box.y } : {}),
    w: rw.box.w,
    h: rw.box.h,
    // the label of an action widget is never exported: an import would ignore it anyway
    ...(!action && ownLabel && cleanText(rw.label) ? { label: cleanText(rw.label).slice(0, PRESET_LIMITS.LABEL) } : {}),
    ...(!action && rw.color ? { color: rw.color } : {}),
    ...(!action && rw.icon ? { icon: rw.icon } : {}),
  };
  return ref;
}

function cardOf(card: ResolvedCard, refs: PresetWidget[], devices: Uplink[]): PresetCard {
  // a card that carries a device's name gets the name of the device that fills it
  const named = devices.some((u) => u.title === card.title);
  const title = named ? "" : cleanText(card.title).slice(0, PRESET_LIMITS.TITLE);
  return {
    x: card.box.x,
    y: card.box.y,
    w: card.box.w,
    h: card.box.h,
    ...(title ? { title } : {}),
    ...(card.color !== "gold" ? { color: card.color } : {}),
    ...(card.icon ? { icon: card.icon } : {}),
    ...(card.style === "compact" ? { style: "compact" as const } : {}),
    widgets: refs,
  };
}

/** One page as a preset. `name` and `description` are the sharer's own words. */
export function pageToPreset(page: ResolvedPage, uplinks: Uplink[], meta: { name: string; description?: string }): ExportResult {
  const byId = new Map(uplinks.map((u) => [u.uplinkId, u]));
  const found: { card: ResolvedCard; widgets: { rw: ResolvedWidget; s: Source }[] }[] = [];
  const used = new Map<string, { uplink: Uplink; ids: Set<string>; count: number }>();
  let skipped = 0;
  let restricted = 0;
  let total = 0;
  for (const card of page.cards) {
    if (card.hidden || found.length >= PRESET_LIMITS.CARDS) {
      if (!card.hidden) skipped += card.widgets.filter((w) => !w.hidden).length;
      continue;
    }
    const widgets: { rw: ResolvedWidget; s: Source }[] = [];
    for (const rw of card.widgets) {
      if (rw.hidden) continue;
      const s = sourceOf(rw, byId);
      if (!s || total >= PRESET_LIMITS.WIDGETS || (!used.has(s.uplink.uplinkId) && used.size >= PRESET_LIMITS.SLOTS)) {
        skipped++;
        continue;
      }
      total++;
      if (s.uplink.restricted) restricted++;
      const entry = used.get(s.uplink.uplinkId) ?? { uplink: s.uplink, ids: new Set<string>(), count: 0 };
      entry.ids.add(s.widget.id);
      entry.count++;
      used.set(s.uplink.uplinkId, entry);
      widgets.push({ rw, s });
    }
    if (widgets.length) found.push({ card, widgets });
  }
  // the device the page is mostly about is what the preset needs; the others are welcome
  const main = [...used.values()].reduce<{ count: number; uplink?: Uplink }>((best, e) => (e.count > best.count ? e : best), { count: 0 }).uplink;
  const slotIds = new Map([...used.values()].map((e, i) => [e.uplink.uplinkId, `d${i + 1}`]));
  const slots = [...used.values()].map((e) => slotOf(slotIds.get(e.uplink.uplinkId)!, e.uplink, e.ids, e.uplink === main ? 1 : 0));
  const cards = found.map(({ card, widgets }) => {
    const refs = widgets.map(({ rw, s }) => refOf(rw, s, slotIds.get(s.uplink.uplinkId)!, true));
    return cardOf(card, refs, [...new Set(widgets.map((w) => w.s.uplink))]);
  });
  const name = cleanText(meta.name).slice(0, PRESET_LIMITS.NAME) || "Shared page";
  const description = cleanText(meta.description ?? "").slice(0, PRESET_LIMITS.DESCRIPTION);
  const preset = parsePreset({
    format: PRESET_FORMAT,
    name,
    ...(description ? { description } : {}),
    ...(page.icon ? { icon: page.icon } : {}),
    slots,
    pages: [{ title: cleanText(page.title).slice(0, PRESET_LIMITS.TITLE) || name.slice(0, PRESET_LIMITS.TITLE), ...(page.icon ? { icon: page.icon } : {}), cards: cards.length ? cards : [] }],
  });
  return { preset, skipped, restricted };
}
