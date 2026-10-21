// Keeps one MainframeConn per paired mainframe, wired to toasts and notifications.
import { effect } from "@preact/signals";
import { MainframeConn } from "./conn.ts";
import { saved, renameSaved, removeSaved, type SavedMainframe } from "./store.ts";
import { navigate, notifyCrit, pushToast, route } from "./ui.ts";

const conns = new Map<string, MainframeConn>();

function create(m: SavedMainframe) {
  const conn = new MainframeConn({
    baseUrl: location.origin,
    token: m.token,
    onAlert: (a, c) => {
      const mfName = c.info.value?.name ?? m.name;
      const viewing = route.value.name === "mainframe" && route.value.id === m.mainframeId;
      const title = [a.uplinkTitle, viewing ? undefined : mfName].filter(Boolean).join(" · ") || mfName;
      // Lua alerts: warn/crit stay until dismissed, info fades after 4 s
      pushToast({ level: a.level, title, text: a.text, sticky: a.level !== "info" });
      if (a.level === "crit" && (document.hidden || !viewing)) notifyCrit(`⚠ ${title}`, a.text, `cccloud-${m.mainframeId}`);
    },
    // rejected actions (wrong PIN, offline, rate limit, validation): auto-dismiss after 6 s
    onNotice: (n) => pushToast({ level: n.level, title: n.title, text: n.text }, 6_000),
  });
  effect(() => {
    const name = conn.info.value?.name;
    if (name) renameSaved(m.mainframeId, name);
  });
  // Token revoked or unknown: forget this mainframe and send the user back to pairing.
  effect(() => {
    if (conn.status.value !== "unauthorized") return;
    queueMicrotask(() => {
      const name = conn.info.value?.name ?? m.name;
      removeSaved(m.mainframeId);
      syncConns();
      pushToast({ level: "warn", title: "Access revoked", text: `This device is no longer paired with ${name}. Pair again with a new code.` }, 10_000);
      const viewing = route.value.name === "mainframe" && route.value.id === m.mainframeId;
      if (viewing || saved.value.length === 0) navigate("/pair", true);
    });
  });
  if (m.canControl === false) conn.canControl.value = false;
  conn.start();
  return conn;
}

export function getConn(mainframeId: string) {
  return conns.get(mainframeId);
}

/** Starts connections for new entries and stops removed ones. */
export function syncConns() {
  const ids = new Set(saved.value.map((m) => m.mainframeId));
  for (const [id, c] of conns)
    if (!ids.has(id)) {
      c.stop();
      conns.delete(id);
    }
  for (const m of saved.value) {
    const existing = conns.get(m.mainframeId);
    if (existing && existing.token !== m.token) {
      existing.stop();
      conns.delete(m.mainframeId);
    }
    if (!conns.has(m.mainframeId)) conns.set(m.mainframeId, create(m));
  }
}

export function retryAll() {
  conns.forEach((c) => c.retryNow());
}

addEventListener("online", retryAll);
document.addEventListener("visibilitychange", () => {
  if (!document.hidden) retryAll();
});
