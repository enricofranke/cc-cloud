// Paired mainframes live in localStorage: one browser can hold several.
import { signal } from "@preact/signals";

export interface SavedMainframe {
  mainframeId: string;
  token: string;
  deviceId?: string;
  name: string;
  deviceName: string;
  pairedAt: number;
  /** From the pairing answer; the snapshot may override it. */
  canControl?: boolean;
}

const KEY = "cccloud.mainframes.v1";

function load(): SavedMainframe[] {
  try {
    const v = JSON.parse(localStorage.getItem(KEY) ?? "[]");
    return Array.isArray(v) ? v.filter((m) => m && typeof m.token === "string" && typeof m.mainframeId === "string") : [];
  } catch {
    return [];
  }
}

export const saved = signal<SavedMainframe[]>(load());

function persist(list: SavedMainframe[]) {
  saved.value = list;
  try {
    localStorage.setItem(KEY, JSON.stringify(list));
  } catch {}
}

export function addSaved(m: SavedMainframe) {
  persist([...saved.value.filter((x) => x.mainframeId !== m.mainframeId), m]);
}

export function removeSaved(mainframeId: string) {
  persist(saved.value.filter((x) => x.mainframeId !== mainframeId));
}

export function renameSaved(mainframeId: string, name: string) {
  const list = saved.value;
  const i = list.findIndex((x) => x.mainframeId === mainframeId);
  if (i >= 0 && list[i].name !== name) persist(list.map((x, j) => (j === i ? { ...x, name } : x)));
}

export function getPref(key: string, fallback: string) {
  try {
    return localStorage.getItem(`cccloud.${key}`) ?? fallback;
  } catch {
    return fallback;
  }
}

export function setPref(key: string, value: string) {
  try {
    localStorage.setItem(`cccloud.${key}`, value);
  } catch {}
}
