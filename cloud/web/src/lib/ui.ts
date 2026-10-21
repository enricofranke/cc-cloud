// App-wide UI state: route, theme, toasts, notifications.
import { signal, computed } from "@preact/signals";
import { getPref, setPref } from "./store.ts";
import type { AlertLevel } from "./types.ts";

// ---- routing (hash based, works from any static host) ----------------------
export type Route = { name: "list" } | { name: "pair"; code?: string } | { name: "mainframe"; id: string };

function parseRoute(): Route {
  const h = location.hash.replace(/^#\/?/, "");
  const [path, query] = h.split("?");
  const parts = path.split("/").filter(Boolean);
  if (parts[0] === "pair") return { name: "pair", code: new URLSearchParams(query).get("code") ?? undefined };
  if (parts[0] === "m" && parts[1]) return { name: "mainframe", id: decodeURIComponent(parts[1]) };
  return { name: "list" };
}

export const route = signal<Route>(parseRoute());
addEventListener("hashchange", () => (route.value = parseRoute()));

export function navigate(to: string, replace = false) {
  if (replace) history.replaceState(null, "", `#${to}`);
  else history.pushState(null, "", `#${to}`);
  route.value = parseRoute();
  window.scrollTo(0, 0);
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

export function pushToast(t: Omit<Toast, "id">, ttl = t.level === "crit" ? 12_000 : 5_000) {
  const id = ++toastSeq;
  toasts.value = [...toasts.value.slice(-3), { ...t, id }];
  if (!t.sticky) setTimeout(() => dismissToast(id), ttl);
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
export function guessDeviceName() {
  const ua = navigator.userAgent;
  const device = /iPhone/.test(ua) ? "iPhone" : /iPad/.test(ua) ? "iPad" : /Android/.test(ua) ? "Android" : /Mac/.test(ua) ? "Mac" : /Windows/.test(ua) ? "Windows PC" : /Linux/.test(ua) ? "Linux PC" : "Browser";
  const browser = /Edg\//.test(ua) ? "Edge" : /Firefox\//.test(ua) ? "Firefox" : /Chrome\//.test(ua) ? "Chrome" : /Safari\//.test(ua) ? "Safari" : "";
  return browser ? `${device} · ${browser}` : device;
}

export function timeAgo(ts: number) {
  const s = Math.round((Date.now() - ts) / 1000);
  if (s < 10) return "just now";
  if (s < 60) return `${s}s ago`;
  if (s < 3600) return `${Math.floor(s / 60)}m ago`;
  if (s < 86400) return `${Math.floor(s / 3600)}h ago`;
  return new Date(ts).toLocaleDateString();
}
