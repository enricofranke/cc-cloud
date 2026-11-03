// The PIN dialog's logic (components/Overlays.tsx, PinDialog): pure functions, so Vitest runs them without a DOM.
//
// The server says how many digits the mainframe PIN has (the `pin` message, conn.pinLength). With that the dialog shows
// exactly that many slots and sends the PIN as soon as the last one is filled. Without it (a PIN saved before the
// length was kept, learnt on its next correct entry) the field grows with every digit and has no empty slot that
// would suggest a digit is missing; OK sends it.

export const PIN_MIN = 4;
export const PIN_MAX = 8;

/** A PIN length the dialog can use (4–8 digits), else undefined. */
export function knownLength(n: unknown): number | undefined {
  return typeof n === "number" && Number.isInteger(n) && n >= PIN_MIN && n <= PIN_MAX ? n : undefined;
}

export type SlotState = "filled" | "active" | "empty";

/**
 * The slots to draw: with a known length exactly that many (the digits so far, then the one being typed, then the
 * rest); without one only the digits typed so far.
 */
export function pinSlots(length: number | undefined, entered: number): SlotState[] {
  if (length === undefined) return Array.from({ length: Math.min(entered, PIN_MAX) }, () => "filled");
  return Array.from({ length }, (_, i) => (i < entered ? "filled" : i === entered ? "active" : "empty"));
}

/** Whether the PIN can be sent: all slots filled, or 4–8 digits when the length is not known. */
export function canSubmit(pin: string, length: number | undefined): boolean {
  if (!/^\d*$/.test(pin)) return false;
  return length === undefined ? pin.length >= PIN_MIN && pin.length <= PIN_MAX : pin.length === length;
}

export interface PinStep {
  pin: string;
  /** send it now: the last slot was just filled, or Enter on a complete PIN */
  submit: boolean;
}

/** One key, from the keypad or the keyboard: a digit, Backspace or Enter. Anything else changes nothing. */
export function pinKey(pin: string, key: string, length: number | undefined): PinStep {
  const max = length ?? PIN_MAX;
  if (/^\d$/.test(key)) {
    if (pin.length >= max) return { pin, submit: false };
    const next = pin + key;
    return { pin: next, submit: length !== undefined && next.length === length };
  }
  if (key === "Backspace") return { pin: pin.slice(0, -1), submit: false };
  if (key === "Enter") return { pin, submit: canSubmit(pin, length) };
  return { pin, submit: false };
}

/** Pasted text: its digits are typed one by one (other characters are skipped), and a full PIN is sent. */
export function pinPaste(pin: string, text: string, length: number | undefined): PinStep {
  let step: PinStep = { pin, submit: false };
  for (const c of text.slice(0, 64)) {
    if (!/^\d$/.test(c)) continue;
    step = pinKey(step.pin, c, length);
    if (step.submit) break;
  }
  return step;
}

/** What a screen reader hears about the field. */
export function pinStatus(length: number | undefined, entered: number): string {
  if (length === undefined) return `${entered} ${entered === 1 ? "digit" : "digits"} entered`;
  return `${entered} of ${length} digits entered`;
}
