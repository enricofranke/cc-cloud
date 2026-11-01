// The browser around the dashboard: nothing it stores may survive a reload, and its absolute URLs ("/#/…", "/docs/…")
// have to land under the demo's base path. The real storage stays available to the demo's own code through `visitorStorage`.
import { BASE_ID, MAINFRAMES, PLAYER, TOKEN } from "./session.ts";
import type { Lang } from "./strings.ts";

const BASE = import.meta.env.BASE_URL;
const DEVICE = "Demo browser";

class MemoryStorage implements Storage {
  private map = new Map<string, string>();
  get length() {
    return this.map.size;
  }
  clear() {
    this.map.clear();
  }
  getItem(k: string) {
    return this.map.get(k) ?? null;
  }
  key(i: number) {
    return [...this.map.keys()][i] ?? null;
  }
  removeItem(k: string) {
    this.map.delete(k);
  }
  setItem(k: string, v: string) {
    this.map.set(k, String(v));
  }
}

function replaceGlobal(name: string, value: unknown) {
  try {
    Object.defineProperty(window, name, { value, configurable: true, writable: true });
  } catch {}
}

const readStorage = () => {
  try {
    return window.localStorage;
  } catch {
    return null;
  }
};
/** The visitor's real localStorage: only for the tour's "seen" note. Reads and writes can throw. */
export const visitorStorage: Storage | null = readStorage();

export function docsUrl(path: string, lang: Lang) {
  const m = /^\/docs\/?([^#?]*)(.*)$/.exec(path);
  return `/${lang}/docs/${m?.[1] ? `${m[1]}/` : ""}${m?.[2] ?? ""}`;
}

export function installShims(lang: Lang) {
  const memory = new MemoryStorage();
  const now = Date.now();
  let theme: string | null = null;
  try {
    theme = visitorStorage?.getItem("cccloud.theme") ?? null;
  } catch {}
  if (theme) memory.setItem("cccloud.theme", theme);
  memory.setItem("cccloud.lang", lang);
  // already "linked": the dashboard opens with the showcase's mainframes instead of the pairing screen
  memory.setItem("cccloud.account.v1", JSON.stringify({ token: TOKEN, player: PLAYER, deviceName: DEVICE, linkedAt: now }));
  memory.setItem("cccloud.mainframes.v1", JSON.stringify(MAINFRAMES.map((m) => ({ mainframeId: m.id, token: TOKEN, name: m.name, deviceName: DEVICE, pairedAt: now, viaAccount: true, role: "owner" }))));
  // the showcase is already arranged: presets stay one step away in the layout editor
  for (const m of MAINFRAMES) memory.setItem(`cccloud.presets.dismissed.${m.id}`, "1");
  replaceGlobal("localStorage", memory);
  replaceGlobal("sessionStorage", new MemoryStorage());
  // the 3D view's cache: without IndexedDB it simply asks the worker again
  replaceGlobal("indexedDB", undefined);
  // no system notifications from a demo
  try {
    delete (window as { Notification?: unknown }).Notification;
  } catch {}

  const rewrite = (url: string | URL | null | undefined) => {
    if (typeof url !== "string") return url;
    if (url.startsWith("/#")) return `${BASE}${location.search}${url.slice(1)}`;
    return url;
  };
  for (const name of ["pushState", "replaceState"] as const) {
    const real = history[name].bind(history);
    history[name] = (data, unused, url) => {
      if (typeof url === "string" && url.startsWith("/docs")) return void window.open(docsUrl(url, lang), "_blank", "noopener");
      real(data, unused, rewrite(url));
    };
  }
  if (!location.hash.replace(/^#\/?/, "")) history.replaceState(null, "", `${BASE}${location.search}#/m/${BASE_ID}`);
}
