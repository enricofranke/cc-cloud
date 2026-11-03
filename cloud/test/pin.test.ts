// The PIN dialog: exactly as many slots as the mainframe PIN has digits (the server's `pin` message), sent as soon as
// the last one is filled; a field that grows with the input while the length is not known; and the client keeping the
// length from the server.
import { afterEach, describe, expect, it } from "vitest";
import { canSubmit, knownLength, PIN_MAX, pinKey, pinPaste, pinSlots, pinStatus } from "../web/src/lib/pin.ts";
import { createMockServer, type MockOptions, type MockServer } from "../dev/mock-server.ts";
import { MainframeConn } from "../web/src/lib/conn.ts";
import { sleep } from "./helpers.ts";

/** Types keys one after the other and returns the PIN and whether (and with what) it was sent. */
function type(keys: string[], length: number | undefined) {
  let pin = "";
  const sent: string[] = [];
  for (const k of keys) {
    const step = pinKey(pin, k, length);
    pin = step.pin;
    if (step.submit) sent.push(pin);
  }
  return { pin, sent };
}

describe("PIN slots", () => {
  it("shows exactly the PIN's digits, with no phantom slot after a full 4-digit PIN", () => {
    expect(pinSlots(4, 0)).toEqual(["active", "empty", "empty", "empty"]);
    expect(pinSlots(4, 2)).toEqual(["filled", "filled", "active", "empty"]);
    expect(pinSlots(4, 4)).toEqual(["filled", "filled", "filled", "filled"]);
    expect(pinSlots(6, 3)).toEqual(["filled", "filled", "filled", "active", "empty", "empty"]);
    expect(pinSlots(8, 8)).toHaveLength(8);
  });

  it("grows with the input while the length is not known", () => {
    expect(pinSlots(undefined, 0)).toEqual([]);
    expect(pinSlots(undefined, 4)).toEqual(["filled", "filled", "filled", "filled"]);
    expect(pinSlots(undefined, 5)).toHaveLength(5);
    expect(pinSlots(undefined, 4).includes("empty")).toBe(false);
  });

  it("only takes lengths of 4 to 8", () => {
    expect([4, 5, 6, 7, 8].map(knownLength)).toEqual([4, 5, 6, 7, 8]);
    for (const n of [0, 3, 9, 4.5, -4, NaN, "6", null, undefined]) expect(knownLength(n), String(n)).toBeUndefined();
  });

  it("says how far the input is", () => {
    expect(pinStatus(6, 2)).toBe("2 of 6 digits entered");
    expect(pinStatus(undefined, 1)).toBe("1 digit entered");
    expect(pinStatus(undefined, 5)).toBe("5 digits entered");
  });
});

describe("PIN input", () => {
  it("sends by itself when the last slot is filled", () => {
    expect(type(["1", "2", "3"], 4)).toEqual({ pin: "123", sent: [] });
    expect(type(["1", "2", "3", "4"], 4)).toEqual({ pin: "1234", sent: ["1234"] });
    expect(type(["9", "8", "7", "6", "5", "4"], 6).sent).toEqual(["987654"]);
  });

  it("takes no digit beyond the length and does not send twice", () => {
    const r = type(["1", "2", "3", "4", "5", "6"], 4);
    expect(r).toEqual({ pin: "1234", sent: ["1234"] });
  });

  it("deletes with Backspace and sends again after a correction", () => {
    expect(type(["1", "2", "Backspace", "Backspace", "Backspace"], 4)).toEqual({ pin: "", sent: [] });
    expect(type(["1", "2", "3", "Backspace", "9", "0"], 4)).toEqual({ pin: "1290", sent: ["1290"] });
  });

  it("needs OK (Enter) and 4–8 digits while the length is not known", () => {
    expect(type(["1", "2", "3", "4"], undefined)).toEqual({ pin: "1234", sent: [] });
    expect(type(["1", "2", "3", "Enter"], undefined).sent).toEqual([]);
    expect(type(["1", "2", "3", "4", "Enter"], undefined).sent).toEqual(["1234"]);
    expect(type(["1", "2", "3", "4", "5", "6", "7", "8", "9"], undefined).pin).toHaveLength(PIN_MAX);
    expect(canSubmit("12345678", undefined)).toBe(true);
    expect(canSubmit("123", undefined)).toBe(false);
  });

  it("Enter with a known length only sends a complete PIN", () => {
    expect(type(["1", "2", "Enter"], 4).sent).toEqual([]);
    expect(canSubmit("1234", 4)).toBe(true);
    expect(canSubmit("1234", 6)).toBe(false);
    expect(canSubmit("12a4", 4)).toBe(false);
  });

  it("ignores other keys", () => {
    for (const k of ["a", " ", "Tab", "ArrowLeft", "Escape", "-", "١"]) expect(pinKey("12", k, 4)).toEqual({ pin: "12", submit: false });
  });

  it("types pasted digits and sends a full PIN", () => {
    expect(pinPaste("", "1234", 4)).toEqual({ pin: "1234", submit: true });
    expect(pinPaste("", " 12-34 56 ", 6)).toEqual({ pin: "123456", submit: true });
    expect(pinPaste("1", "23", 4)).toEqual({ pin: "123", submit: false });
    expect(pinPaste("", "123456789", 4)).toEqual({ pin: "1234", submit: true });
    expect(pinPaste("", "12345", undefined)).toEqual({ pin: "12345", submit: false });
    expect(pinPaste("", "no digits", 4)).toEqual({ pin: "", submit: false });
  });
});

describe("the PIN length from the server", () => {
  let mock: MockServer | undefined;
  const conns: MainframeConn[] = [];
  afterEach(async () => {
    conns.splice(0).forEach((c) => c.stop());
    await mock?.close();
    mock = undefined;
  });
  const start = async (opts: MockOptions = {}) => {
    mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, ae2: false, controlRoom: false, computer: false, log: () => {}, ...opts });
  };
  const token = async (body: object = {}) => {
    const res = await fetch(`http://127.0.0.1:${mock!.port}/dev/token`, { method: "POST", body: JSON.stringify({ name: "Vitest", ...body }) });
    return (await res.json()).token as string;
  };
  const connect = (tok: string) => {
    const conn = new MainframeConn({ baseUrl: `http://127.0.0.1:${mock!.port}`, token: tok, minBackoffMs: 30, maxBackoffMs: 120 });
    conns.push(conn);
    conn.start();
    return conn;
  };
  /** A server message, as if it came over the socket. */
  const receive = (conn: MainframeConn, msg: object) => (conn as unknown as { handle(m: unknown): void }).handle(msg);
  const until = async (pred: () => boolean) => {
    for (let i = 0; i < 200 && !pred(); i++) await sleep(10);
    expect(pred()).toBe(true);
  };

  it("keeps the length the server sends", async () => {
    await start({ pin: "246810" });
    const conn = connect(await token());
    await until(() => conn.pinLength.value === 6);
    receive(conn, { t: "pin", set: true, length: 4 });
    expect(conn.pinLength.value).toBe(4);
  });

  it("treats anything odd as unknown", async () => {
    await start({ pinLength: 0 });
    const conn = connect(await token());
    await until(() => conn.status.value === "open");
    await sleep(30);
    expect(conn.pinLength.value).toBe(0);
    for (const msg of [{ set: true, length: 3 }, { set: true, length: 9 }, { set: true, length: "6" }, { set: false, length: 6 }, { set: true, length: 5.5 }, { set: true }]) {
      receive(conn, { t: "pin", set: true, length: 6 });
      receive(conn, { t: "pin", ...msg });
      expect(conn.pinLength.value, JSON.stringify(msg)).toBe(0);
    }
  });
});
