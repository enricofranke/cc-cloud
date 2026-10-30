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
  /** storage v2 auto-stock */
  target?: number;
  autoCraft?: boolean;
  state?: "idle" | "crafting" | "waiting_cpu" | "missing" | "paused";
}
export interface JobRow {
  jobId: string;
  name: string;
  displayName?: string;
  count: number;
  status: "queued" | "running" | "waiting" | "done" | "failed" | "cancelled";
  progress: number;
  source?: "dashboard" | "lua" | "autostock" | "rule";
  notify?: boolean;
  error?: string;
}
export type Color = "cyan" | "green" | "yellow" | "red" | "gray";
export type AlertLevel = "info" | "warn" | "crit";

export interface WidgetOpts {
  color?: Color;
  /** v0.5 F: the server records long-term history for this range */
  history?: string;
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
  /** editor display style (layout v2), e.g. "bar" for a gauge */
  display?: string;
  /** editor icon (layout v2) */
  wicon?: string;
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
  /** false = the chunk is unloaded: the mod sends the cached last snapshot (values may be old) */
  loaded?: boolean;
  /** ms since epoch, with loaded:false */
  lastSeen?: number;
  /** Lua v2: cloud.page() / cloud.group() declarations */
  pages?: { id: string; title?: string; icon?: string }[];
  groups?: { id: string; title?: string; page?: string; cols?: number }[];
  /** map overlays of mainframe cards (FTB Chunks claims, Waystones) */
  claims?: ClaimsOverlay;
  waystones?: WaystonesOverlay;
}

export interface ClaimsOverlay {
  team: string;
  /** 0xRRGGBB (the mod sends "#rrggbb") */
  color: number;
  chunks: { dim: string; x: number; z: number; forced: boolean }[];
  truncated: boolean;
}
export interface Waystone {
  name: string;
  dim: string;
  x: number;
  y: number;
  z: number;
  kind: string;
  global?: boolean;
}
export interface WaystonesOverlay {
  items: Waystone[];
  truncated: boolean;
}

/** v0.7 wireless redstone channel of a mainframe network. */
export interface RedstoneChannel {
  name: string;
  /** what receivers output (an override wins) */
  level: number;
  /** the transmitters' max */
  signal: number;
  transmitters: number;
  receivers: number;
  override?: { level: number; until: number | null };
}
export interface RedstoneInfo {
  channels: RedstoneChannel[];
  limit: number;
}

export interface Alert {
  uplinkId?: string;
  uplinkTitle?: string;
  text: string;
  level: AlertLevel;
  ts: number;
}

export interface PowerInfo {
  /** FE in the buffer */
  stored: number;
  capacity: number;
  /** FE/t currently consumed */
  usage: number;
  /** false when the mainframe is out of power */
  ok: boolean;
  /** v0.4: what costs how much (sums to usage) */
  breakdown?: PowerItem[];
}

export interface PowerItem {
  /** base | uplinks | advanced_uplinks | probes | bridges | clients | hops | anchor | efficiency (negative) | other */
  key: string;
  count?: number;
  fe: number;
  /** v0.7: how many of `count` are attached by fiber (they pay less) */
  fiber?: number;
}

// ---- v0.4 network map -------------------------------------------------------
/** "device" = a linked device whose chunk is not loaded (type unknown). me_bridge/rs_bridge: older drafts. */
export type NodeType = "mainframe" | "relay" | "advanced_relay" | "ender_relay" | "uplink" | "advanced_uplink" | "bridge" | "device" | "me_bridge" | "rs_bridge";
/** legacy_out_of_range is derived from flags (the device itself is connected). */
export type NodeStatus = "online" | "offline" | "connecting" | "no_power" | "no_route" | "unloaded" | "redstone" | "cloud_disabled" | "legacy_out_of_range" | "over_capacity";

/** v0.7: the mainframe node's device capacity (upgrade cards); limit null = unlimited (Quantum Core). */
export interface NetCapacity {
  devices: number;
  /** weighted (fiber members count less) */
  used: number;
  limit?: number | null;
  over: number;
  wired: number;
  fiber: number;
}

export interface NetNode {
  id: string;
  type: NodeType;
  name: string;
  /** omitted for read-only devices */
  pos?: { x: number; y: number; z: number };
  dim: string;
  status: NodeStatus;
  power?: PowerInfo;
  /** link range to other nodes (blocks) */
  range: number;
  /** range for devices attaching to this node */
  deviceRange?: number;
  /** uplink id of the dashboard card (devices: their own id) */
  uplinkId?: string;
  /** ms epoch */
  lastSeen?: number;
  /** previous hop towards the mainframe */
  parent?: string;
  hops?: number;
  flags?: string[];
  /** v0.7: device routed by a cable ("cable" = CC networking cable) */
  via?: "cable" | "fiber";
  /** v0.7: mainframe node only */
  capacity?: NetCapacity;
}

export interface NetLink {
  a: string;
  b: string;
  /** 0..1 */
  signal: number;
  dist: number;
  /** v0.7: "wired" = CC networking cable (100 %, no range limit) */
  kind: "radio" | "ender" | "wired";
  /** v0.7: wired links: "cc" = CC networking cable, "fiber" = CC: Cloud fiber cable */
  medium?: "cc" | "fiber";
  /** v0.3 device kept connected although out of range */
  legacy?: boolean;
  /** signal below 15 % (sent by the mod) */
  weak?: boolean;
}

export interface NetworkInfo {
  nodes: NetNode[];
  links: NetLink[];
  /** node id -> node ids from the mainframe to that node (both ends included) */
  routes: Record<string, string[]>;
}

export interface MainframeInfo {
  id: string;
  name: string;
  online: boolean;
  enabled: boolean;
  /** why it is offline, e.g. "no_power" (v0.3) */
  reason?: string;
  /** v0.3: FE buffer; absent when the server runs with requirePower=false */
  power?: PowerInfo;
  /** The block was broken (reason "removed"): ms epoch when its data is deleted unless it is placed again */
  removedUntil?: number;
}
