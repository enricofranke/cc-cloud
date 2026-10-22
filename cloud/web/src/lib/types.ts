export type Scalar = number | boolean | string | null;
export type WidgetType =
  | "button" | "toggle" | "slider" | "gauge" | "label"
  | "item_search" | "watchlist" | "jobs"
  | "chart" | "progress" | "indicator" | "select" | "number" | "text" | "buttons"
  | "redstone_sides";
export const WIDGET_TYPES: readonly WidgetType[] = [
  "button", "toggle", "slider", "gauge", "label", "item_search", "watchlist", "jobs",
  "chart", "progress", "indicator", "select", "number", "text", "buttons", "redstone_sides",
];

export const SIDES = ["top", "bottom", "left", "right", "front", "back"] as const;
export type Side = (typeof SIDES)[number];
/** redstone_sides value rows: one per side, levels 0–15. */
export interface RedstoneRow {
  side: Side;
  out: number;
  in: number;
}

/** A row of structured widget values (watchlist / jobs). */
export type Row = Record<string, string | number | boolean | null>;
/** Widget value: a scalar, or a list of rows for watchlist/jobs. */
export type WidgetValue = Scalar | Row[];
/** Action value: a scalar, or an operation object for item_search/watchlist ({op:"craft",...}). */
export type ActionValue = Scalar | Record<string, string | number | boolean>;

export interface ItemRow {
  name: string;
  displayName: string;
  count: number;
  craftable: boolean;
}
export interface WatchRow {
  name: string;
  displayName: string;
  count: number;
  min: number;
}
export interface JobRow {
  jobId: string;
  name: string;
  displayName?: string;
  count: number;
  status: "queued" | "running" | "done" | "failed" | "cancelled";
  progress: number;
}
export type Color = "cyan" | "green" | "yellow" | "red" | "gray";
export type AlertLevel = "info" | "warn" | "crit";

export interface WidgetOpts {
  color?: Color;
  confirm?: boolean;
  /** Ask for the mainframe PIN before sending. */
  pin?: boolean;
  value?: Scalar;
  min?: number;
  max?: number;
  step?: number;
  unit?: string;
  warn?: number;
  crit?: number;
  /** select / buttons */
  options?: string[];
  /** text */
  maxLength?: number;
  /** indicator */
  onColor?: string;
  offColor?: string;
  /** chart */
  points?: number;
  interval?: number;
  style?: "line" | "sparkline";
  /** Lua v2 layout hints */
  page?: string;
  group?: string;
  span?: number;
  order?: number;
  icon?: string;
}

export interface Widget {
  id: string;
  type: WidgetType;
  label?: string;
  opts?: WidgetOpts;
  value?: WidgetValue;
  /** chart: recent values, oldest first (snapshot); value deltas append to it */
  history?: number[];
}

export interface Uplink {
  uplinkId: string;
  title: string;
  online: boolean;
  widgets: Widget[];
  /** v0.3: "auto" = automatic Computer card, "lua" = script widgets, "direct" = bridge template */
  mode?: "auto" | "lua" | "direct";
  /** Lua v2: cloud.page() / cloud.group() declarations */
  pages?: { id: string; title?: string; icon?: string }[];
  groups?: { id: string; title?: string; page?: string; cols?: number }[];
}

export interface Alert {
  uplinkId?: string;
  uplinkTitle?: string;
  text: string;
  level: AlertLevel;
  ts: number;
}

export interface MainframeInfo {
  id: string;
  name: string;
  online: boolean;
  enabled: boolean;
}
