// Dashboard presets (docs/SPEC.md "Dashboard presets"): a portable description of dashboard pages. A preset holds layout
// data only: no Lua, nothing that runs, no ids of one world. Slots say what kind of device a card wants; the matcher
// (match.ts) fills them from the live base and the expander (expand.ts) turns them into an ordinary v2 layout of views.
//
// Everything here is untrusted: a preset can come from a pasted code. parsePreset() is strict (unknown keys and wrong
// types reject the whole document, like LayoutValidator.java) and builds a new object from the keys it knows.
import { ACCENTS, CARD_STYLES, COLS, DISPLAYS, ICON_NAMES, MAX_CARD_H, MAX_PAGES, MAX_WIDGET_H, MIN_CARD_W, type Accent, type CardStyle, type Display, type IconName } from "../layout.ts";
import { WIDGET_TYPES, type WidgetType } from "../types.ts";
import type { Lang } from "../lang.ts";

/** The version of this format. A newer one is refused with a clear message instead of being half understood. */
export const PRESET_FORMAT = 1;

/** Limits of a preset document. Titles and labels are the limits of a layout document (Limits.java). */
export const PRESET_LIMITS = {
  NAME: 60,
  DESCRIPTION: 400,
  TITLE: 48,
  LABEL: 48,
  SLOTS: 16,
  PAGES: MAX_PAGES,
  CARDS: 64,
  /** widget references of the whole preset, and widgets it may expand to on a big base */
  WIDGETS: 256,
  EXPANDED_WIDGETS: 512,
  LIST: 32,
  KEYWORD: 32,
  KIND: 48,
  WIDGET_ID: 64,
  TAGS: 6,
  TAG: 24,
  SCRIPTS: 4,
  DEVICES: 64,
  COUNT: 32,
} as const;

export const ROLES = ["any", "energy", "generator", "reactor", "storage", "items", "fluid", "machine", "farm", "redstone", "door", "sensor", "computer", "script", "control", "alarm", "server"] as const;
export type Role = (typeof ROLES)[number];

/** A text in English and, optionally, German. */
export type Text = string | { en: string; de?: string };

export interface PresetSlot {
  id: string;
  label?: Text;
  /** device kinds (the uplink's `template`), best first */
  kinds?: string[];
  /** or any device with one of these roles */
  roles?: Role[];
  /** widget ids a device must publish (all of them): for programs, whose ids are their own */
  has?: string[];
  /** a device must publish a widget of one of these types */
  types?: WidgetType[];
  /** the device's title or a widget's label must contain one of these words */
  text?: string[];
  /** only device cards (they have a kind) or only programs (their widgets are their own) */
  source?: "card" | "program";
  min: number;
  max: number;
}

export interface PresetWidget {
  slot: string;
  /** widget ids, best first; `types`/`text` choose the rest (and are the fallback when an id is not there) */
  ids?: string[];
  types?: WidgetType[];
  text?: string[];
  /** only gauges with a warn or crit level */
  limits?: boolean;
  /** how many widgets of one device, default 1 */
  count?: number;
  /** the type to show it as, default the widget's own type */
  as?: WidgetType;
  display?: Display;
  x?: number;
  y?: number;
  w?: number;
  h?: number;
  /** read-only widgets only: action widgets always show the label of their source */
  label?: Text;
  labelFrom?: "device";
  color?: Accent;
  icon?: IconName;
}

export interface PresetCard {
  x?: number;
  y?: number;
  w: number;
  h?: number;
  /** "{device}" stands for the device's name on a card with `each`; no title: the name of the first device */
  title?: Text;
  color?: Accent;
  icon?: IconName;
  style?: CardStyle;
  /** one copy of this card per device of that slot */
  each?: string;
  widgets: PresetWidget[];
}

export interface PresetPage {
  title: Text;
  icon?: IconName;
  cards: PresetCard[];
}

export interface PresetAuthor {
  name: string;
  github?: string;
  url?: string;
}

export interface Preset {
  format: 1;
  id?: string;
  version?: string;
  name: Text;
  description?: Text;
  author?: PresetAuthor;
  license?: string;
  icon?: IconName;
  color?: Accent;
  tags: string[];
  /** store scripts that fit this preset: offered as a link into the script store, never installed by the preset */
  scripts: string[];
  slots: PresetSlot[];
  pages: PresetPage[];
}

export type PresetErrorCode = "invalid_preset" | "newer_format";

export class PresetError extends Error {
  readonly code: PresetErrorCode;
  /** where the first problem is, as a JSON path */
  readonly path?: string;
  constructor(code: PresetErrorCode, path?: string) {
    super(path ? `${code}: ${path}` : code);
    this.code = code;
    this.path = path;
  }
}

/** Ids of a preset, a slot, a tag: lower case, never starting with `_` (no `__proto__`). */
const ID = /^[a-z0-9][a-z0-9_-]{0,23}$/;
/** Same as a script id in the store. */
export const STORE_ID = /^[a-z0-9][a-z0-9-]{1,38}[a-z0-9]$/;
const VERSION = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(-[0-9A-Za-z.-]+)?$/;
const KIND = /^[a-z0-9_]{1,48}$/;
const WIDGET_ID = /^[A-Za-z0-9_.:-]{1,64}$/;
const GITHUB = /^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$/;

const has = <T extends string>(list: readonly T[], v: unknown): v is T => typeof v === "string" && (list as readonly string[]).includes(v);
type Obj = Record<string, unknown>;
const fail = (path: string): never => {
  throw new PresetError("invalid_preset", path);
};
const object = (v: unknown, path: string): Obj => (v && typeof v === "object" && !Array.isArray(v) ? (v as Obj) : fail(path));
const array = (v: unknown, path: string, max: number): unknown[] => (Array.isArray(v) && v.length <= max ? v : fail(path));
const only = (o: Obj, path: string, allowed: readonly string[]) => {
  for (const k of Object.keys(o)) if (!allowed.includes(k)) fail(`${path}.${k}`);
};
/** A whole number in range, or undefined when it is not there. */
const int = (o: Obj, key: string, path: string, min: number, max: number): number | undefined => {
  const v = o[key];
  if (v === undefined) return undefined;
  return typeof v === "number" && Number.isInteger(v) && v >= min && v <= max ? v : fail(`${path}.${key}`);
};
const enumOf = <T extends string>(o: Obj, key: string, path: string, list: readonly T[]): T | undefined => {
  const v = o[key];
  if (v === undefined) return undefined;
  return has(list, v) ? v : fail(`${path}.${key}`);
};

/** Characters that make a text read differently from what it is: direction overrides and invisible ones go, without a trace. */
const HIDDEN_CHARS = /[\u00ad\u061c\u200b-\u200f\u2028-\u202e\u2060-\u206f\ufeff]/g;
const CONTROL_CHARS = /[\u0000-\u001f\u007f-\u009f]/g;
export const cleanText = (s: string) => s.replace(HIDDEN_CHARS, "").replace(CONTROL_CHARS, " ").replace(/\s+/g, " ").trim();

function string(v: unknown, path: string, max: number): string {
  if (typeof v !== "string") fail(path);
  const s = cleanText(v as string);
  return s && s.length <= max ? s : fail(path);
}
function text(v: unknown, path: string, max: number): Text {
  if (typeof v === "string") return string(v, path, max);
  const o = object(v, path);
  only(o, path, ["en", "de"]);
  const en = string(o.en, `${path}.en`, max);
  return o.de === undefined ? { en } : { en, de: string(o.de, `${path}.de`, max) };
}
const optText = (o: Obj, key: string, path: string, max: number) => (o[key] === undefined ? undefined : text(o[key], `${path}.${key}`, max));
function strings(o: Obj, key: string, path: string, max: number, re: RegExp): string[] | undefined {
  if (o[key] === undefined) return undefined;
  const list = array(o[key], `${path}.${key}`, max);
  list.forEach((v, i) => (typeof v === "string" && re.test(v) ? 0 : fail(`${path}.${key}[${i}]`)));
  return list.length ? [...new Set(list as string[])] : undefined;
}
function enums<T extends string>(o: Obj, key: string, path: string, list: readonly T[]): T[] | undefined {
  if (o[key] === undefined) return undefined;
  const a = array(o[key], `${path}.${key}`, PRESET_LIMITS.LIST);
  a.forEach((v, i) => (has(list, v) ? 0 : fail(`${path}.${key}[${i}]`)));
  return a.length ? [...new Set(a as T[])] : undefined;
}
function words(o: Obj, key: string, path: string): string[] | undefined {
  if (o[key] === undefined) return undefined;
  const list = array(o[key], `${path}.${key}`, PRESET_LIMITS.LIST).map((v, i) => string(v, `${path}.${key}[${i}]`, PRESET_LIMITS.KEYWORD).toLowerCase());
  return list.length ? [...new Set(list)] : undefined;
}
/** Keeps the keys that are set, so a parsed preset has no `undefined` values. */
function clean<T extends object>(o: T): T {
  for (const k of Object.keys(o) as (keyof T)[]) if (o[k] === undefined) delete o[k];
  return o;
}

function parseSlot(raw: unknown, path: string, seen: Set<string>): PresetSlot {
  const o = object(raw, path);
  only(o, path, ["id", "label", "kinds", "roles", "has", "types", "text", "source", "min", "max"]);
  const id = o.id;
  if (typeof id !== "string" || !ID.test(id) || seen.has(id)) fail(`${path}.id`);
  seen.add(id as string);
  const min = int(o, "min", path, 0, PRESET_LIMITS.DEVICES) ?? 0;
  const max = int(o, "max", path, 1, PRESET_LIMITS.DEVICES) ?? 1;
  if (min > max) fail(`${path}.min`);
  if (o.source !== undefined && o.source !== "card" && o.source !== "program") fail(`${path}.source`);
  return clean<PresetSlot>({
    id: id as string,
    label: optText(o, "label", path, PRESET_LIMITS.LABEL),
    kinds: strings(o, "kinds", path, PRESET_LIMITS.LIST, KIND),
    roles: enums(o, "roles", path, ROLES),
    has: strings(o, "has", path, PRESET_LIMITS.LIST, WIDGET_ID),
    types: enums(o, "types", path, WIDGET_TYPES),
    text: words(o, "text", path),
    source: o.source as PresetSlot["source"],
    min,
    max,
  });
}

function parseWidget(raw: unknown, path: string, slots: Set<string>): PresetWidget {
  const o = object(raw, path);
  only(o, path, ["slot", "ids", "types", "text", "limits", "count", "as", "display", "x", "y", "w", "h", "label", "labelFrom", "color", "icon"]);
  if (typeof o.slot !== "string" || !slots.has(o.slot)) fail(`${path}.slot`);
  const ids = strings(o, "ids", path, PRESET_LIMITS.LIST, WIDGET_ID);
  const types = enums(o, "types", path, WIDGET_TYPES);
  const keywords = words(o, "text", path);
  const as = enumOf(o, "as", path, WIDGET_TYPES);
  // a reference that names nothing would pick the device's first widget, whatever that is
  if (!ids && !types && !keywords && !as && o.limits !== true) fail(path);
  if (o.limits !== undefined && o.limits !== true) fail(`${path}.limits`);
  const x = int(o, "x", path, 0, COLS - 1);
  const w = int(o, "w", path, 1, COLS);
  if (x !== undefined && w !== undefined && x + w > COLS) fail(`${path}.w`);
  const display = enumOf(o, "display", path, DISPLAYS);
  if (o.labelFrom !== undefined && o.labelFrom !== "device") fail(`${path}.labelFrom`);
  return clean<PresetWidget>({
    slot: o.slot as string,
    ids,
    types,
    text: keywords,
    limits: o.limits as true | undefined,
    count: int(o, "count", path, 1, PRESET_LIMITS.COUNT),
    as,
    display: display === "auto" ? undefined : display,
    x,
    y: int(o, "y", path, 0, 999),
    w,
    h: int(o, "h", path, 1, MAX_WIDGET_H),
    label: optText(o, "label", path, PRESET_LIMITS.LABEL),
    labelFrom: o.labelFrom as "device" | undefined,
    color: enumOf(o, "color", path, ACCENTS),
    icon: enumOf(o, "icon", path, ICON_NAMES),
  });
}

function parseCard(raw: unknown, path: string, slots: Set<string>, budget: { widgets: number }): PresetCard {
  const o = object(raw, path);
  only(o, path, ["x", "y", "w", "h", "title", "color", "icon", "style", "each", "widgets"]);
  const x = int(o, "x", path, 0, COLS - 1);
  const w = int(o, "w", path, MIN_CARD_W, COLS);
  if (w === undefined) fail(`${path}.w`);
  if (x !== undefined && x + (w as number) > COLS) fail(`${path}.w`);
  if (o.each !== undefined && (typeof o.each !== "string" || !slots.has(o.each))) fail(`${path}.each`);
  const list = array(o.widgets, `${path}.widgets`, PRESET_LIMITS.WIDGETS);
  budget.widgets += list.length;
  if (budget.widgets > PRESET_LIMITS.WIDGETS) fail(`${path}.widgets`);
  return clean<PresetCard>({
    x,
    y: int(o, "y", path, 0, 999),
    w: w as number,
    h: int(o, "h", path, 3, MAX_CARD_H),
    title: optText(o, "title", path, PRESET_LIMITS.TITLE),
    color: enumOf(o, "color", path, ACCENTS),
    icon: enumOf(o, "icon", path, ICON_NAMES),
    style: enumOf(o, "style", path, CARD_STYLES),
    each: o.each as string | undefined,
    widgets: list.map((w2, i) => parseWidget(w2, `${path}.widgets[${i}]`, slots)),
  });
}

function parseAuthor(raw: unknown, path: string): PresetAuthor {
  const o = object(raw, path);
  only(o, path, ["name", "github", "url"]);
  if (o.github !== undefined && (typeof o.github !== "string" || !GITHUB.test(o.github))) fail(`${path}.github`);
  if (o.url !== undefined && (typeof o.url !== "string" || o.url.length > 200 || !/^https:\/\/[^\s"'<>]+$/.test(o.url))) fail(`${path}.url`);
  return clean<PresetAuthor>({ name: string(o.name, `${path}.name`, PRESET_LIMITS.NAME), github: o.github as string | undefined, url: o.url as string | undefined });
}

/** Checks an untrusted preset document and returns a clean copy. Throws {@link PresetError}. */
export function parsePreset(raw: unknown): Preset {
  const o = object(raw, "preset");
  if (typeof o.format === "number" && Number.isInteger(o.format) && o.format > PRESET_FORMAT) throw new PresetError("newer_format");
  if (o.format !== PRESET_FORMAT) fail("preset.format");
  only(o, "preset", ["$schema", "format", "id", "version", "name", "description", "author", "license", "icon", "color", "tags", "scripts", "slots", "pages"]);
  if (o.id !== undefined && (typeof o.id !== "string" || !STORE_ID.test(o.id))) fail("preset.id");
  if (o.version !== undefined && (typeof o.version !== "string" || !VERSION.test(o.version))) fail("preset.version");
  if (o.license !== undefined && (typeof o.license !== "string" || !/^[A-Za-z0-9.+-]{1,64}$/.test(o.license))) fail("preset.license");
  const seen = new Set<string>();
  const slots = array(o.slots, "preset.slots", PRESET_LIMITS.SLOTS).map((s, i) => parseSlot(s, `preset.slots[${i}]`, seen));
  const pagesRaw = array(o.pages, "preset.pages", PRESET_LIMITS.PAGES);
  if (!pagesRaw.length) fail("preset.pages");
  const budget = { widgets: 0, cards: 0 };
  const pages = pagesRaw.map((raw2, i) => {
    const path = `preset.pages[${i}]`;
    const p = object(raw2, path);
    only(p, path, ["title", "icon", "cards"]);
    const list = array(p.cards, `${path}.cards`, PRESET_LIMITS.CARDS);
    if (!list.length) fail(`${path}.cards`);
    budget.cards += list.length;
    if (budget.cards > PRESET_LIMITS.CARDS) fail(`${path}.cards`);
    return clean<PresetPage>({
      title: text(p.title, `${path}.title`, PRESET_LIMITS.TITLE),
      icon: enumOf(p, "icon", path, ICON_NAMES),
      cards: list.map((c, j) => parseCard(c, `${path}.cards[${j}]`, seen, budget)),
    });
  });
  return clean<Preset>({
    format: PRESET_FORMAT,
    id: o.id as string | undefined,
    version: o.version as string | undefined,
    name: text(o.name, "preset.name", PRESET_LIMITS.NAME),
    description: optText(o, "description", "preset", PRESET_LIMITS.DESCRIPTION),
    author: o.author === undefined ? undefined : parseAuthor(o.author, "preset.author"),
    license: o.license as string | undefined,
    icon: enumOf(o, "icon", "preset", ICON_NAMES),
    color: enumOf(o, "color", "preset", ACCENTS),
    tags: strings(o, "tags", "preset", PRESET_LIMITS.TAGS, ID) ?? [],
    scripts: strings(o, "scripts", "preset", PRESET_LIMITS.SCRIPTS, STORE_ID) ?? [],
    slots,
    pages,
  });
}

/** A name or description of a store index entry, by the same rules as in a preset. */
export const parseEntryText = (v: unknown, kind: "NAME" | "DESCRIPTION") => text(v, "entry", PRESET_LIMITS[kind]);

/** The slots of a store index entry: the dashboard shows the match state before it loads the whole preset. */
export function parseSlots(raw: unknown): PresetSlot[] {
  const seen = new Set<string>();
  return array(raw, "slots", PRESET_LIMITS.SLOTS).map((s, i) => parseSlot(s, `slots[${i}]`, seen));
}

/** A text in a language. */
export const textOf = (t: Text | undefined, lang: Lang): string => (t === undefined ? "" : typeof t === "string" ? t : (lang === "de" ? t.de : undefined) ?? t.en);
