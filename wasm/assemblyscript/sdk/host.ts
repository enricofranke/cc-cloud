// The bridge to CC: Cloud: one host call that takes a function name ("cloud.getCard") and the arguments as JSON, and
// the handler table the host calls back into (cloud.on, cloud.every, ...).

import { Value } from "./value";

@external("cccloud", "call")
declare function hostCall(name: usize, nameLen: i32, args: usize, argsLen: i32): i32;

@external("cccloud", "take")
declare function hostTake(ptr: usize): void;

/** The answer of an action: ok, or not ok with the reason (unknown_card, pin_required, rate_limited, ...). */
export class Result {
  constructor(public ok: bool, public error: string | null) {}
}

function takeBytes(length: i32): ArrayBuffer {
  const buffer = new ArrayBuffer(length);
  if (length > 0) hostTake(changetype<usize>(buffer));
  else hostTake(0);
  return buffer;
}

function take(length: i32): string {
  return String.UTF8.decode(takeBytes(length));
}

/** One byte per character (Cloud Computers: the terminal's 8-bit characters); characters above 255 become "?". */
function latin1(s: string): ArrayBuffer {
  const out = new ArrayBuffer(s.length);
  const view = Uint8Array.wrap(out);
  for (let i = 0; i < s.length; i++) {
    const c = s.charCodeAt(i);
    view[i] = c < 256 ? <u8>c : 0x3f;
  }
  return out;
}

function fromLatin1(buffer: ArrayBuffer): string {
  const view = Uint8Array.wrap(buffer);
  const parts = new Array<string>();
  for (let i = 0; i < view.length; i++) parts.push(String.fromCharCode(view[i]));
  return parts.join("");
}

/**
 * Calls a host function with JSON arguments. Returns the results as an array (a function can return several values,
 * like Lua). A wrong call (bad argument, unknown event, a full quota) throws: the script stops with that error.
 */
export function invoke(name: string, args: Array<Value>): Value {
  const n = String.UTF8.encode(name);
  const a = String.UTF8.encode(Value.array(args).toString());
  const r = hostCall(changetype<usize>(n), n.byteLength, changetype<usize>(a), a.byteLength);
  if (r < 0) throw new Error(take(-r - 1));
  return Value.parse(take(r));
}

/** {@link invoke} for Cloud Computer programs: strings as one byte per character. */
export function invokeProgram(name: string, args: Array<Value>): Value {
  const n = String.UTF8.encode(name);
  const a = latin1(Value.array(args).toString());
  const r = hostCall(changetype<usize>(n), n.byteLength, changetype<usize>(a), a.byteLength);
  if (r < 0) throw new Error(take(-r - 1));
  return Value.parse(fromLatin1(takeBytes(r)));
}

export function first(results: Value): Value {
  return results.at(0);
}

export function result(results: Value): Result {
  const error = results.at(1);
  return new Result(results.at(0).asBool(), error.isString ? error.asString() : null);
}

// ---- handlers --------------------------------------------------------------------------------------------------------

export type EventHandler = (event: Value) => void;
export type TimerHandler = () => void;

class Handler {
  constructor(public onEvent: EventHandler | null, public onTimer: TimerHandler | null) {}
}

const handlers = new Array<Handler>();

export function eventHandler(f: EventHandler): Value {
  handlers.push(new Handler(f, null));
  return Value.handler(handlers.length - 1);
}

export function timerHandler(f: TimerHandler): Value {
  handlers.push(new Handler(null, f));
  return Value.handler(handlers.length - 1);
}

/** Called by the host to run a handler: the argument (an event) waits in the host's buffer, length -1 = none. */
export function cccloud_handle(id: i32, length: i32): void {
  const arg = length < 0 ? Value.null() : Value.parse(take(length));
  if (id < 0 || id >= handlers.length) throw new Error("unknown handler " + id.toString());
  const h = handlers[id];
  const onEvent = h.onEvent;
  if (onEvent != null) {
    onEvent(arg);
    return;
  }
  const onTimer = h.onTimer;
  if (onTimer != null) onTimer();
}
