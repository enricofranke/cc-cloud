import type { MainframeConn } from "./conn.ts";
import { LAN_ONLY_TEXT, unreachableText } from "./api.ts";

export type StatusKind = "no_power" | "lan_only" | "online" | "offline" | "removed" | "disabled" | "reconnecting" | "unreachable" | "connecting" | "revoked";

export interface StatusInfo {
  kind: StatusKind;
  label: string;
  /** Explanation for the banner; undefined when everything is fine. */
  text?: string;
}

export function statusOf(conn: MainframeConn | undefined): StatusInfo {
  if (!conn) return { kind: "connecting", label: "Connecting" };
  const s = conn.status.value;
  const i = conn.info.value;
  if (s === "unauthorized") return { kind: "revoked", label: "Revoked", text: "This device was removed from the mainframe. Pair again with a new code." };
  if (s === "unreachable" && conn.lanOnly.value) return { kind: "lan_only", label: "LAN only", text: LAN_ONLY_TEXT };
  if (s === "unreachable") return { kind: "unreachable", label: "No connection", text: unreachableText() };
  if (s === "reconnecting") return { kind: "reconnecting", label: "Reconnecting", text: "Connection lost. Reconnecting to the server…" };
  if (s === "connecting" || !i) return { kind: "connecting", label: "Connecting" };
  if (!i.online && i.reason === "removed") {
    const until = i.removedUntil ? new Date(i.removedUntil).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" }) : undefined;
    return {
      kind: "removed",
      label: "Removed",
      text:
        "The Cloud Mainframe was broken. Place its item again – or get a new one with /cccloud mainframe recover – and everything comes back: devices, layout, rules and scenes" +
        (until ? `. Its data is deleted on ${until}.` : ".") +
        " Showing the last known values; controls are locked.",
    };
  }
  if (!i.online && (i.reason === "no_power" || i.power?.ok === false))
    return {
      kind: "no_power",
      label: "No power",
      text: "Mainframe has no power. Connect an FE (Forge Energy) source to the mainframe – it reconnects by itself once the buffer refills. Showing the last known values; controls are locked.",
    };
  if (!i.online)
    return {
      kind: "offline",
      label: "Offline",
      text: "The mainframe is offline (chunk unloaded, redstone kill switch, or removed). Showing the last known values; controls are locked.",
    };
  if (!i.enabled)
    return {
      kind: "disabled",
      label: "Disabled",
      text: "The dashboard is switched off in-game. Sneak + right-click the Cloud Mainframe with an empty hand to turn it on.",
    };
  return { kind: "online", label: "Online" };
}
