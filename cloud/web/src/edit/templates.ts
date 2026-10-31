// Ready-made cards of the toolbox: built from what this base really publishes, so a card works the moment it lands.
import type { Accent, IconName, ViewPick } from "../lib/layout.ts";
import type { Uplink, Widget, WidgetType } from "../lib/types.ts";

export interface CardTemplate {
  id: string;
  title: string;
  hint: string;
  icon: IconName;
  color: Accent;
  w: number;
  picks: ViewPick[];
}

const ENERGY = /\b[kMGT]?(FE|RF)\b|energy|power|battery/i;
const has = (w: Widget, ...types: WidgetType[]) => types.includes(w.type);
const devices = (picks: ViewPick[]) => [...new Set(picks.map((p) => p.uplink.title || "Uplink"))];
const from = (picks: ViewPick[]) => {
  const d = devices(picks);
  return `${picks.length} from ${d.slice(0, 2).join(", ")}${d.length > 2 ? ` +${d.length - 2}` : ""}`;
};

export function cardTemplates(uplinks: Uplink[]): CardTemplate[] {
  const live = uplinks.filter((u) => !u.blocked);
  const all = live.flatMap((u) => u.widgets.map((widget) => ({ uplink: u, widget })));
  const as = (list: typeof all, type: WidgetType, max: number, display?: ViewPick["display"]): ViewPick[] => list.slice(0, max).map((x) => ({ ...x, as: type, ...(display ? { display } : {}) }));
  const firstValues = live.flatMap((u) => {
    const widget = u.widgets.find((w) => has(w, "gauge", "progress"));
    return widget ? [{ uplink: u, widget }] : [];
  });
  const store = live.find((u) => u.template === "storage");
  const made: Omit<CardTemplate, "hint">[] = [
    { id: "overview", title: "Overview", icon: "home", color: "gold", w: 6, picks: as(firstValues, "gauge", 8, "number") },
    { id: "energy", title: "Energy", icon: "bolt", color: "yellow", w: 6, picks: as(all.filter((x) => has(x.widget, "gauge", "progress") && ENERGY.test(`${x.widget.label ?? ""} ${x.widget.opts?.unit ?? ""}`)), "gauge", 6, "bar") },
    { id: "trends", title: "Trends", icon: "chart", color: "cyan", w: 6, picks: as(all.filter((x) => has(x.widget, "chart")), "chart", 4) },
    { id: "alarms", title: "Status lights", icon: "warning", color: "red", w: 4, picks: as(all.filter((x) => has(x.widget, "indicator")), "indicator", 8) },
    {
      id: "controls",
      title: "Controls",
      icon: "lever",
      color: "green",
      w: 4,
      picks: all.filter((x) => has(x.widget, "toggle", "button")).slice(0, 8).map((x) => ({ ...x, as: x.widget.type })),
    },
    { id: "storage", title: "Storage", icon: "box", color: "cyan", w: 6, picks: store ? store.widgets.filter((w) => has(w, "item_search", "watchlist", "jobs")).map((widget) => ({ uplink: store, widget, as: widget.type })) : [] },
  ];
  return made.filter((t) => t.picks.length > 0).map((t) => ({ ...t, hint: from(t.picks) }));
}
