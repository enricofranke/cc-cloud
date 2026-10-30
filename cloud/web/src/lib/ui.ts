// App-wide UI state: route, theme, toasts, notifications.
import { signal, computed } from "@preact/signals";
import { getPref, setPref } from "./store.ts";
import type { AlertLevel } from "./types.ts";
import { deviceNameFor, takePairLink, type PairLink } from "./pairlink.ts";
import { takeInstallLink } from "./scriptstore.ts";

// ---- routing (hash based, works from any static host) ----------------------
export type Route =
  | { name: "list" }
  | { name: "pair"; code?: string; link?: PairLink }
  | { name: "mainframe"; id: string; tab?: "production"; card?: string }
  | { name: "devices" }
  | { name: "access"; id: string }
  | { name: "shares"; id: string }
  | { name: "rules"; id: string }
  | { name: "push"; id: string }
  | { name: "scenes"; id: string }
  | { name: "apikeys"; id: string };

/** Script Store link (/store#install=<id>@<version>): opened on the next mainframe dashboard. */
export const pendingInstall = signal<{ id: string; version?: string } | null>(null);
{
  const r = takeInstallLink(location);
  if (r.scrub) history.replaceState(null, "", "/#/");
  pendingInstall.value = r.want;
}
// "Pair in browser" links (/pair#code=…): take the code and clear the fragment before anything else runs
let pairLink: PairLink | null = null;
{
  const r = takePairLink(location);
  if (r.scrub) history.replaceState(null, "", "/#/pair");
  pairLink = r.link;
}

function parseRoute(): Route {
  if (pairLink) {
    const link = pairLink;
    pairLink = null;
    return { name: "pair", code: link.code, link };
  }
  const h = location.hash.replace(/^#\/?/, "");
  const [path, query] = h.split("?");
  const parts = path.split("/").filter(Boolean);
  if (parts[0] === "pair") return { name: "pair", code: new URLSearchParams(query).get("code") ?? undefined };
  if (parts[0] === "devices") return { name: "devices" };
  if (parts[0] === "m" && parts[1] && parts[2] === "access") return { name: "access", id: decodeURIComponent(parts[1]) };
  if (parts[0] === "m" && parts[1] && parts[2] === "shares") return { name: "shares", id: decodeURIComponent(parts[1]) };
  if (parts[0] === "m" && parts[1] && parts[2] === "rules") return { name: "rules", id: decodeURIComponent(parts[1]) };
  if (parts[0] === "m" && parts[1] && parts[2] === "push") return { name: "push", id: decodeURIComponent(parts[1]) };
  // v0.10: scenes and API keys are screens; production is a page of the dashboard (like Network)
  if (parts[0] === "m" && parts[1] && parts[2] === "scenes") return { name: "scenes", id: decodeURIComponent(parts[1]) };
  if (parts[0] === "m" && parts[1] && parts[2] === "apikeys") return { name: "apikeys", id: decodeURIComponent(parts[1]) };
  if (parts[0] === "m" && parts[1] && parts[2] === "production") return { name: "mainframe", id: decodeURIComponent(parts[1]), tab: "production" };
  if (parts[0] === "m" && parts[1]) {
    // ?card=<uplinkId>: the in-game "Show in dashboard" link scrolls to that card
    const card = new URLSearchParams(query).get("card") ?? "";
    return { name: "mainframe", id: decodeURIComponent(parts[1]), ...(/^[0-9a-f-]{36}$/.test(card) ? { card } : {}) };
  }
  return { name: "list" };
}

export const route = signal<Route>(parseRoute());
/** true while the /docs section is shown (path based; the app itself uses hash routes on "/"). */
export const inDocs = signal(location.pathname.startsWith("/docs"));
/** v0.6: /s/<id> – a read-only share-link view */
export const inShare = signal(/^\/s\/[A-Za-z0-9]{1,64}\/?$/.test(location.pathname));
const sync = () => {
  route.value = parseRoute();
  inDocs.value = location.pathname.startsWith("/docs");
  inShare.value = /^\/s\/[A-Za-z0-9]{1,64}\/?$/.test(location.pathname);
};
addEventListener("hashchange", sync);
addEventListener("popstate", sync);

export function navigate(to: string, replace = false) {
  const url = `/#${to}`;
  if (replace) history.replaceState(null, "", url);
  else history.pushState(null, "", url);
  sync();
  window.scrollTo(0, 0);
}

/** Opens the docs, e.g. openDocs("/docs/faq#no-such-program"). */
export function openDocs(path = "/docs") {
  history.pushState(null, "", path);
  sync();
  dispatchEvent(new PopStateEvent("popstate"));
  if (!path.includes("#")) window.scrollTo(0, 0);
}

// ---- theme -----------------------------------------------------------------
export type ThemePref = "dark" | "light" | "system";
export const themePref = signal<ThemePref>(getPref("theme", "dark") as ThemePref);
const systemLight = matchMedia("(prefers-color-scheme: light)");
const systemIsLight = signal(systemLight.matches);
systemLight.addEventListener("change", (e) => (systemIsLight.value = e.matches));
export const theme = computed(() => (themePref.value === "system" ? (systemIsLight.value ? "light" : "dark") : themePref.value));

export function applyTheme() {
  document.documentElement.dataset.theme = theme.value;
  document.querySelector('meta[name="theme-color"]')?.setAttribute("content", theme.value === "light" ? "#eef1f4" : "#0d1014");
}

export function setTheme(t: ThemePref) {
  themePref.value = t;
  setPref("theme", t);
}

export function cycleTheme() {
  const next: ThemePref = themePref.value === "dark" ? "light" : themePref.value === "light" ? "system" : "dark";
  themePref.value = next;
  setPref("theme", next);
}

// ---- toasts ----------------------------------------------------------------
export interface Toast {
  id: number;
  level: AlertLevel;
  title: string;
  text: string;
  sticky?: boolean;
}

export const toasts = signal<Toast[]>([]);
let toastSeq = 0;

/**
 * Toasts auto-dismiss (info 4 s, warn/crit 6 s) unless `sticky` is set.
 * Only Lua alerts with level warn/crit are sticky (see conns.ts); they are also in the alert log.
 */
export function pushToast(t: Omit<Toast, "id">, ttl = t.level === "info" ? 4_000 : 6_000) {
  const id = ++toastSeq;
  const sticky = t.sticky === true;
  toasts.value = [...toasts.value.slice(-3), { ...t, sticky, id }];
  if (!sticky) setTimeout(() => dismissToast(id), ttl);
}

export function dismissToast(id: number) {
  toasts.value = toasts.value.filter((t) => t.id !== id);
}

// ---- web notifications -----------------------------------------------------
export const notifyPermission = signal<NotificationPermission | "unsupported">(
  "Notification" in window ? Notification.permission : "unsupported",
);

export async function requestNotifications() {
  if (!("Notification" in window)) return;
  notifyPermission.value = await Notification.requestPermission();
}

export async function notifyCrit(title: string, body: string, tag: string) {
  if (notifyPermission.value !== "granted") return;
  const opts: NotificationOptions = { body, tag, icon: "/icon-192.png", badge: "/icon-192.png", requireInteraction: true } as NotificationOptions;
  try {
    const reg = await navigator.serviceWorker?.getRegistration();
    if (reg) return await reg.showNotification(title, opts);
    new Notification(title, opts);
  } catch {}
}

/** Best-effort guess for the default device name on the pairing screen. */
export function guessDeviceName(owner?: string) {
  return deviceNameFor(navigator.userAgent, owner);
}

export function timeAgo(ts: number) {
  const s = Math.round((Date.now() - ts) / 1000);
  if (s < 10) return "just now";
  if (s < 60) return `${s}s ago`;
  if (s < 3600) return `${Math.floor(s / 60)}m ago`;
  if (s < 86400) return `${Math.floor(s / 3600)}h ago`;
  return new Date(ts).toLocaleDateString();
}
