export type Scalar = number | boolean | string | null;
export type WidgetType = "button" | "toggle" | "slider" | "gauge" | "label";
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
}

export interface Widget {
  id: string;
  type: WidgetType;
  label?: string;
  opts?: WidgetOpts;
  value?: Scalar;
}

export interface Uplink {
  uplinkId: string;
  title: string;
  online: boolean;
  widgets: Widget[];
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
