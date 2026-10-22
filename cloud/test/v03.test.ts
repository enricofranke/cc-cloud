// v0.3: automatic Computer card (redstone_sides) and the clipboard fallback.
import { afterEach, describe, expect, it } from "vitest";
import { createMockServer, COMPUTER, type MockServer } from "../dev/mock-server.ts";
import { MainframeConn } from "../web/src/lib/conn.ts";
import { adaptComputerCard, legacyTarget, redstoneRows, VIRTUAL_REDSTONE_ID } from "../web/src/lib/redstone.ts";
import { resolveLayout } from "../web/src/lib/layout.ts";
import { copyText } from "../web/src/lib/clipboard.ts";
import { TestSocket, sleep } from "./helpers.ts";

let mock: MockServer | undefined;
const sockets: TestSocket[] = [];
const conns: MainframeConn[] = [];
afterEach(async () => {
  sockets.splice(0).forEach((s) => s.close());
  conns.splice(0).forEach((c) => c.stop());
  await mock?.close();
  mock = undefined;
});
const base = () => `http://127.0.0.1:${mock!.port}`;
async function start(extra: object = {}) {
  mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, actionDelayMs: 5, ae2: false, controlRoom: false, log: () => {}, ...extra });
}
async function token(body: object = {}) {
  return (await (await fetch(`${base()}/dev/token`, { method: "POST", body: JSON.stringify({ name: "Vitest", ...body }) })).json()).token as string;
}
async function client(body: object = {}) {
  const c = new TestSocket(`ws://127.0.0.1:${mock!.port}/ws/client`);
  sockets.push(c);
  await c.opened();
  c.send({ t: "auth", token: await token(body) });
  return { c, snap: await c.next("snapshot") };
}
async function waitFor(pred: () => boolean, ms = 2000) {
  const end = Date.now() + ms;
  while (!pred()) {
    if (Date.now() > end) throw new Error("timeout");
    await sleep(10);
  }
}

describe("auto Computer card", () => {
  it("is published in auto mode with power, buttons and redstone_sides", async () => {
    await start();
    const { snap } = await client();
    const u = snap.uplinks.find((x: any) => x.uplinkId === COMPUTER);
    expect(u).toMatchObject({ title: "Computer #7", mode: "auto" });
    expect(u.widgets.map((w: any) => [w.id, w.type])).toEqual([
      ["power", "indicator"],
      ["turn_on", "button"],
      ["reboot", "button"],
      ["shutdown", "button"],
      ["redstone", "redstone_sides"],
    ]);
    expect(u.widgets.find((w: any) => w.id === "reboot").opts.confirm).toBe(true);
    const rs = u.widgets.find((w: any) => w.id === "redstone").value;
    expect(rs).toHaveLength(6);
    expect(rs[0]).toEqual({ side: "top", out: 0, in: 0 });
  });

  it("sets a redstone output and validates side and level", async () => {
    await start();
    const { c } = await client();
    const set = (reqId: string, value: unknown) => c.send({ t: "action", reqId, uplinkId: COMPUTER, id: "redstone", value });
    set("a", { op: "set_output", side: "front", value: 15 });
    expect(await c.next((m) => m.reqId === "a")).toMatchObject({ ok: true });
    const v = await c.next((m) => m.t === "value" && m.id === "redstone");
    expect(v.value.find((r: any) => r.side === "front")).toEqual({ side: "front", out: 15, in: 0 });
    expect(v.value.find((r: any) => r.side === "right").in).toBe(15); // wired back in
    set("b", { op: "set_output", side: "north", value: 15 });
    expect(await c.next((m) => m.reqId === "b")).toMatchObject({ ok: false, error: "invalid_value" });
    set("c", { op: "set_output", side: "top", value: 16 });
    expect(await c.next((m) => m.reqId === "c")).toMatchObject({ ok: false, error: "invalid_value" });
    set("d", { op: "craft", name: "minecraft:stone", count: 1 });
    expect(await c.next((m) => m.reqId === "d")).toMatchObject({ ok: false, error: "invalid_value" });
    set("e", true);
    expect(await c.next((m) => m.reqId === "e")).toMatchObject({ ok: false, error: "invalid_value" });
  });

  it("rejects redstone and power actions from read-only devices", async () => {
    await start();
    const { c } = await client({ canControl: false });
    c.send({ t: "action", reqId: "x", uplinkId: COMPUTER, id: "redstone", value: { op: "set_output", side: "top", value: 15 } });
    expect(await c.next("action_result")).toMatchObject({ ok: false, error: "read_only" });
  });

  it("drives the power indicator from the buttons (reboot = off, then on)", async () => {
    await start();
    const conn = new MainframeConn({ baseUrl: base(), token: await token() });
    conns.push(conn);
    conn.start();
    await waitFor(() => conn.status.value === "open");
    const u = () => conn.uplinks.value.find((x) => x.uplinkId === COMPUTER)!;
    const power = () => u().widgets.find((w) => w.id === "power")!.value;
    expect(u().mode).toBe("auto");
    conn.action(COMPUTER, u().widgets.find((w) => w.id === "shutdown")!, true);
    await waitFor(() => power() === false);
    conn.action(COMPUTER, u().widgets.find((w) => w.id === "reboot")!, true);
    await waitFor(() => power() === true, 3000);
    // redstone rows arrive as arrays and normalise to six sides
    conn.action(COMPUTER, u().widgets.find((w) => w.id === "redstone")!, { op: "set_output", side: "bottom", value: 7 });
    await waitFor(() => redstoneRows(u().widgets.find((w) => w.id === "redstone")!).find((r) => r.side === "bottom")!.out === 7);
    // one card on the Overview page
    expect(resolveLayout(conn.uplinks.value, null)[0].cards.find((c) => c.key === COMPUTER)?.title).toBe("Computer #7");
  });
});

describe("per-side widget format (adapter)", () => {
  it("shows per-side indicators/toggles/sliders as one compact redstone grid and maps actions back", async () => {
    await start({ computerFormat: "widgets" });
    const conn = new MainframeConn({ baseUrl: base(), token: await token() });
    conns.push(conn);
    conn.start();
    await waitFor(() => conn.status.value === "open");
    const raw = () => conn.uplinks.value.find((x) => x.uplinkId === COMPUTER)!;
    expect(raw().widgets).toHaveLength(4 + 18);
    const adapted = adaptComputerCard(raw());
    expect(adapted.widgets.map((w) => w.id)).toEqual(["power", "turn_on", "reboot", "shutdown", VIRTUAL_REDSTONE_ID]);
    expect(adapted.groups).toEqual([]);
    const rows = () => redstoneRows(adaptComputerCard(raw()).widgets.find((w) => w.id === VIRTUAL_REDSTONE_ID)!);
    expect(rows().find((r) => r.side === "back")).toEqual({ side: "back", out: 15, in: 0 });
    expect(rows().find((r) => r.side === "left")).toEqual({ side: "left", out: 0, in: 15 });
    // one card only (the groups are folded into the main card)
    expect(resolveLayout([adapted], null)[0].cards).toHaveLength(1);

    // tap "front" -> toggle front_out; analog 7 on "bottom" -> slider bottom_level
    const t1 = legacyTarget(raw(), "front", 15);
    expect(t1).toEqual({ id: "front_out", type: "toggle", value: true });
    conn.action(COMPUTER, { id: t1.id, type: t1.type }, t1.value);
    await waitFor(() => rows().find((r) => r.side === "front")!.out === 15 && rows().find((r) => r.side === "right")!.in === 15);
    const t2 = legacyTarget(raw(), "bottom", 7);
    expect(t2).toEqual({ id: "bottom_level", type: "slider", value: 7 });
    conn.action(COMPUTER, { id: t2.id, type: t2.type }, t2.value);
    await waitFor(() => rows().find((r) => r.side === "bottom")!.out === 7);
  });

  it("leaves other uplinks alone", () => {
    const u = { uplinkId: "x", title: "X", online: true, widgets: [{ id: "top_out", type: "toggle" as const }] };
    expect(adaptComputerCard(u)).toBe(u);
  });
});

describe("redstoneRows", () => {
  it("always yields six sides in order with clamped levels", () => {
    const rows = redstoneRows({ value: [{ side: "back", out: 99, in: -3 }, { side: "nope", out: 1, in: 1 }, { side: "top", out: 7.4, in: "x" }] as any });
    expect(rows.map((r) => r.side)).toEqual(["top", "bottom", "left", "right", "front", "back"]);
    expect(rows[0]).toEqual({ side: "top", out: 7, in: 0 });
    expect(rows[5]).toEqual({ side: "back", out: 15, in: 0 });
    expect(redstoneRows({ value: null }).every((r) => r.out === 0 && r.in === 0)).toBe(true);
  });
});

describe("copyText", () => {
  const g = globalThis as any;
  afterEach(() => {
    delete g.window;
    delete g.document;
    delete g.navigator;
  });
  function fakeDom(execResult: boolean) {
    const log: string[] = [];
    g.document = {
      body: { appendChild: () => log.push("append") },
      createElement: () => ({ value: "", setAttribute() {}, select: () => log.push("select"), setSelectionRange() {}, remove: () => log.push("remove"), className: "" }),
      getSelection: () => null,
      execCommand: (cmd: string) => (log.push(`exec:${cmd}`), execResult),
    };
    return log;
  }

  it("uses navigator.clipboard in a secure context", async () => {
    let written = "";
    g.window = { isSecureContext: true };
    Object.defineProperty(globalThis, "navigator", { value: { clipboard: { writeText: async (t: string) => void (written = t) } }, configurable: true });
    const log = fakeDom(true);
    expect(await copyText("hello")).toBe(true);
    expect(written).toBe("hello");
    expect(log).toEqual([]);
  });

  it("falls back to a hidden textarea + execCommand on plain HTTP", async () => {
    g.window = { isSecureContext: false };
    Object.defineProperty(globalThis, "navigator", { value: {}, configurable: true });
    const log = fakeDom(true);
    expect(await copyText("local cloud = ...")).toBe(true);
    expect(log).toEqual(["append", "select", "exec:copy", "remove"]);
    fakeDom(false);
    expect(await copyText("x")).toBe(false);
  });
});
