// Store presets in the dashboard. Everything goes through the mod's server, which checks the signed index and each
// preset's hash (docs/SPEC.md "Dashboard presets"): the browser never fetches or trusts the store itself.
import type { Accent, IconName } from "../layout.ts";
import { ACCENTS, ICON_NAMES } from "../layout.ts";
import { saved } from "../store.ts";
import { STORE_ID, parseEntryText, parsePreset, parseSlots, type Preset, type PresetSlot, type Text } from "./format.ts";

export interface StorePresetEntry {
  id: string;
  version: string;
  name: Text;
  description?: Text;
  author?: string;
  tags: string[];
  icon?: IconName;
  color?: Accent;
  pages: number;
  cards: number;
  slots: PresetSlot[];
}

export type StoreErrorCode = "store_disabled" | "store_not_configured" | "store_unreachable" | "store_bad_signature" | "store_mismatch" | "unknown_preset" | "not_allowed" | "unreachable" | "invalid_preset" | "remote_locked";

export class StoreError extends Error {
  readonly code: StoreErrorCode;
  constructor(code: StoreErrorCode) {
    super(code);
    this.code = code;
  }
}

const KNOWN = new Set<string>(["store_disabled", "store_not_configured", "store_unreachable", "store_bad_signature", "store_mismatch", "unknown_preset", "not_allowed", "remote_locked"]);

async function call(mainframe: string, path: string): Promise<any> {
  const token = saved.value.find((m) => m.mainframeId === mainframe)?.token ?? "";
  let res: Response;
  try {
    res = await fetch(`${location.origin}/api/mainframes/${encodeURIComponent(mainframe)}/presets${path}`, { headers: { authorization: `Bearer ${token}` }, cache: "no-store" });
  } catch {
    throw new StoreError("unreachable");
  }
  let body: any = null;
  try {
    body = await res.json();
  } catch {}
  if (res.ok) return body;
  const error = String(body?.error ?? "");
  throw new StoreError(KNOWN.has(error) ? (error as StoreErrorCode) : res.status === 401 || res.status === 403 ? "not_allowed" : "unreachable");
}

/** One entry of the index; an entry this dashboard cannot read is left out, not an error for the whole list. */
function entry(raw: any): StorePresetEntry | null {
  try {
    if (!raw || typeof raw.id !== "string" || !STORE_ID.test(raw.id) || typeof raw.version !== "string") return null;
    return {
      id: raw.id,
      version: raw.version.slice(0, 40),
      name: parseEntryText(raw.name, "NAME"),
      description: raw.description === undefined ? undefined : parseEntryText(raw.description, "DESCRIPTION"),
      author: typeof raw.author === "string" ? raw.author.slice(0, 60) : typeof raw.author?.name === "string" ? raw.author.name.slice(0, 60) : undefined,
      tags: Array.isArray(raw.tags) ? raw.tags.filter((t: unknown) => typeof t === "string").slice(0, 6) : [],
      icon: (ICON_NAMES as readonly string[]).includes(raw.icon) ? raw.icon : undefined,
      color: (ACCENTS as readonly string[]).includes(raw.color) ? raw.color : undefined,
      pages: Number(raw.pages) || 1,
      cards: Number(raw.cards) || 0,
      slots: parseSlots(raw.slots),
    };
  } catch {
    return null;
  }
}

export async function listStorePresets(mainframe: string): Promise<StorePresetEntry[]> {
  const body = await call(mainframe, "");
  return Array.isArray(body?.presets) ? body.presets.map(entry).filter((e: StorePresetEntry | null): e is StorePresetEntry => !!e) : [];
}

/** A signed preset; the same strict check as every other preset still applies. */
export async function getStorePreset(mainframe: string, id: string): Promise<Preset> {
  const body = await call(mainframe, `/${encodeURIComponent(id)}`);
  try {
    const preset = parsePreset(body?.preset);
    if (preset.id !== id) throw new StoreError("store_mismatch");
    return preset;
  } catch (e) {
    throw e instanceof StoreError ? e : new StoreError("invalid_preset");
  }
}
