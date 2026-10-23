// v0.5 E browser terminal (docs/SPEC.md "E. Browser terminal + Lua editor – web protocol"):
// the terminal picture from term frames, GLFW key codes, colour runs for rendering.

/** CC's default palette, indexed by blit digit 0–f. */
export const DEFAULT_PALETTE = [
  "#f0f0f0", "#f2b233", "#e57fd8", "#99b2f2", "#dede6c", "#7fcc19", "#f2b2cc", "#4c4c4c",
  "#999999", "#4c99b2", "#b266e5", "#3366cc", "#7f664c", "#57a64e", "#cc4c4c", "#111111",
];
const BLIT = /^[0-9a-f]*$/;

export interface TermState {
  on: boolean;
  w: number;
  h: number;
  colour: boolean;
  cursor: { x: number; y: number; blink: boolean };
  palette: string[];
  text: string[];
  fg: string[];
  bg: string[];
}

const clampInt = (v: unknown, lo: number, hi: number, d: number) => (typeof v === "number" && Number.isFinite(v) ? Math.max(lo, Math.min(hi, Math.round(v))) : d);

/** Applies a `term` message; returns the new state (or null when it is not usable). */
export function applyTermFrame(prev: TermState | null, msg: any): TermState | null {
  if (!msg || typeof msg !== "object") return prev;
  if (msg.on === false) return prev ? { ...prev, on: false } : { on: false, w: 51, h: 19, colour: true, cursor: { x: 1, y: 1, blink: false }, palette: DEFAULT_PALETTE, text: [], fg: [], bg: [] };
  const full = msg.full === true || !prev || !prev.text.length;
  const w = full ? clampInt(msg.w, 1, 256, 51) : prev!.w;
  const h = full ? clampInt(msg.h, 1, 128, 19) : prev!.h;
  const blank = (c: string) => c.repeat(w);
  const next: TermState = {
    on: true,
    w,
    h,
    colour: full ? msg.colour !== false : prev!.colour,
    cursor: msg.cursor && typeof msg.cursor === "object" ? { x: clampInt(msg.cursor.x, 1, w + 1, 1), y: clampInt(msg.cursor.y, 1, h + 1, 1), blink: msg.cursor.blink === true } : (prev?.cursor ?? { x: 1, y: 1, blink: false }),
    palette:
      Array.isArray(msg.palette) && msg.palette.length === 16 && msg.palette.every((c: unknown) => typeof c === "string" && /^#[0-9a-f]{6}$/i.test(c))
        ? msg.palette
        : (prev?.palette ?? DEFAULT_PALETTE),
    text: full ? Array.from({ length: h }, () => blank(" ")) : [...prev!.text],
    fg: full ? Array.from({ length: h }, () => blank("0")) : [...prev!.fg],
    bg: full ? Array.from({ length: h }, () => blank("f")) : [...prev!.bg],
  };
  for (const r of Array.isArray(msg.rows) ? msg.rows : []) {
    if (!r || typeof r.y !== "number") continue;
    const y = Math.round(r.y) - 1;
    if (y < 0 || y >= h) continue;
    const text = typeof r.text === "string" ? r.text : "";
    // control characters as spaces, exact width
    next.text[y] = (text.replace(/[\x00-\x1f\x7f]/g, " ") + " ".repeat(w)).slice(0, w);
    next.fg[y] = typeof r.fg === "string" && BLIT.test(r.fg) ? (r.fg + "0".repeat(w)).slice(0, w) : next.fg[y];
    next.bg[y] = typeof r.bg === "string" && BLIT.test(r.bg) ? (r.bg + "f".repeat(w)).slice(0, w) : next.bg[y];
  }
  return next;
}

/** Splits a row into runs of equal colours for cheap rendering. */
export function colourRuns(text: string, fg: string, bg: string) {
  const out: { text: string; fg: string; bg: string }[] = [];
  for (let i = 0; i < text.length; i++) {
    const last = out[out.length - 1];
    if (last && last.fg === fg[i] && last.bg === bg[i]) last.text += text[i];
    else out.push({ text: text[i], fg: fg[i] ?? "0", bg: bg[i] ?? "f" });
  }
  return out;
}

/** KeyboardEvent.code → GLFW key code (CC's keys API). */
const CODES: Record<string, number> = {
  Space: 32, Quote: 39, Comma: 44, Minus: 45, Period: 46, Slash: 47, Semicolon: 59, Equal: 61,
  BracketLeft: 91, Backslash: 92, BracketRight: 93, Backquote: 96,
  Escape: 256, Enter: 257, Tab: 258, Backspace: 259, Insert: 260, Delete: 261,
  ArrowRight: 262, ArrowLeft: 263, ArrowDown: 264, ArrowUp: 265, PageUp: 266, PageDown: 267, Home: 268, End: 269,
  CapsLock: 280, ScrollLock: 281, NumLock: 282, PrintScreen: 283, Pause: 284,
  NumpadEnter: 335, NumpadDecimal: 330, NumpadDivide: 331, NumpadMultiply: 332, NumpadSubtract: 333, NumpadAdd: 334, NumpadEqual: 336,
  ShiftLeft: 340, ControlLeft: 341, AltLeft: 342, MetaLeft: 343, ShiftRight: 344, ControlRight: 345, AltRight: 346, MetaRight: 347, ContextMenu: 348,
};
export function glfwKey(code: string): number | null {
  if (code in CODES) return CODES[code];
  let m = /^Key([A-Z])$/.exec(code);
  if (m) return m[1].charCodeAt(0); // 65–90
  m = /^Digit([0-9])$/.exec(code);
  if (m) return 48 + Number(m[1]);
  m = /^F([1-9]|1[0-9]|2[0-5])$/.exec(code);
  if (m) return 289 + Number(m[1]); // F1 = 290
  m = /^Numpad([0-9])$/.exec(code);
  if (m) return 320 + Number(m[1]);
  return null;
}

/** A printable character CC accepts as a `char` event (CC's charset is 8-bit). */
export const isPrintable = (key: string) => key.length === 1 && key.charCodeAt(0) >= 32 && key.charCodeAt(0) <= 255 && key.charCodeAt(0) !== 127;

export type TermEvent =
  | { type: "char"; c: string }
  | { type: "key"; key: number; held: boolean }
  | { type: "key_up"; key: number }
  | { type: "paste"; text: string }
  | { type: "terminate" | "turn_on" | "reboot" | "shutdown" }
  | { type: "mouse_click" | "mouse_up" | "mouse_drag"; button: number; x: number; y: number }
  | { type: "mouse_scroll"; dir: -1 | 1; x: number; y: number };

/** First line of a paste, at most 512 characters, CC charset only. */
export const pasteText = (raw: string) => (raw.split(/\r?\n/)[0] ?? "").replace(/[^\x20-\x7e\xa0-\xff]/g, "").slice(0, 512);
