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
  /** v0.6: entry comes from the linked player account (token = account token) */
  viaAccount?: boolean;
  role?: string;
}

/** v0.6: the player account this browser is linked to. */
export interface Account {
  token: string;
  player: string;
  uuid?: string;
  deviceName: string;
  linkedAt: number;
}
const ACCOUNT_KEY = "cccloud.account.v1";
function loadAccount(): Account | null {
  try {
    const a = JSON.parse(localStorage.getItem(ACCOUNT_KEY) ?? "null");
    return a && typeof a.token === "string" && typeof a.player === "string" ? a : null;
  } catch {
    return null;
  }
}
export const account = signal<Account | null>(loadAccount());
export function setAccount(a: Account | null) {
  account.value = a;
  try {
    if (a) localStorage.setItem(ACCOUNT_KEY, JSON.stringify(a));
    else localStorage.removeItem(ACCOUNT_KEY);
  } catch {}
}

/** Replaces the account-derived entries with the server's current list. */
export function syncAccountMainframes(list: { id: string; name: string; role?: string }[], a: Account) {
  const manual = saved.value.filter((m) => !m.viaAccount && !list.some((x) => x.id === m.mainframeId));
  const fromAccount = list.map((x) => ({
    mainframeId: x.id,
    token: a.token,
    name: x.name,
    deviceName: a.deviceName,
    pairedAt: a.linkedAt,
    viaAccount: true,
    role: x.role,
  }));
  persist([...manual, ...fromAccount]);
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
