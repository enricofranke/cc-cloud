// v0.3 power: FE buffer on the mainframe, no_power state, header formatting.
import { afterEach, describe, expect, it } from "vitest";
import { createMockServer, REACTOR, type MockServer } from "../dev/mock-server.ts";
import { MainframeConn, normalizePower } from "../web/src/lib/conn.ts";
import { statusOf } from "../web/src/lib/status.ts";
import { fmtFE } from "../web/src/lib/format.ts";
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
async function start() {
  mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, actionDelayMs: 5, ae2: false, controlRoom: false, computer: false, network: false, log: () => {} });
}
async function token() {
  return (await (await fetch(`${base()}/dev/token`, { method: "POST", body: JSON.stringify({ name: "Vitest" }) })).json()).token as string;
}
const control = (body: object) => fetch(`${base()}/dev/control`, { method: "POST", body: JSON.stringify(body) });
async function waitFor(pred: () => boolean, ms = 2000) {
  const end = Date.now() + ms;
  while (!pred()) {
    if (Date.now() > end) throw new Error("timeout");
    await sleep(10);
  }
}

describe("power protocol", () => {
  it("puts power {stored, capacity, usage, ok} on the snapshot and in state deltas", async () => {
    await start();
    const c = new TestSocket(`ws://127.0.0.1:${mock!.port}/ws/client`);
    sockets.push(c);
    await c.opened();
    c.send({ t: "auth", token: await token() });
    const snap = await c.next("snapshot");
    expect(snap.mainframe.power).toEqual({ stored: 152_000, capacity: 200_000, usage: 0, ok: true });
    mock!.tickPower();
    const st = await c.next("state");
    // 40 base + 5 per online uplink (2) + 2 per client (1) = 52, Efficiency card ×0.8 saves 10
    expect(st.power).toMatchObject({ usage: 42, ok: true, capacity: 200_000 });
    expect(st.power.stored).toBe(152_000 + (120 - 42) * 20);
  });

  it("goes offline with reason no_power after a 5 s brownout and comes back when refilled", async () => {
    await start();
    const c = new TestSocket(`ws://127.0.0.1:${mock!.port}/ws/client`);
    sockets.push(c);
    await c.opened();
    c.send({ t: "auth", token: await token() });
    await c.next("snapshot");
    await control({ power: { stored: 0, input: 0 } });
    for (let i = 0; i < 4; i++) mock!.tickPower();
    expect(mock!.base.online).toBe(true); // still riding the brownout
    mock!.tickPower();
    const off = await c.next((m) => m.t === "state" && m.online === false);
    expect(off).toMatchObject({ reason: "no_power", power: { ok: false, stored: 0 } });
    c.send({ t: "action", reqId: "a", uplinkId: REACTOR, id: "power", value: false });
    expect(await c.next("action_result")).toMatchObject({ ok: false, error: "no_power" });
    await control({ power: { input: 2000 } });
    mock!.tickPower();
    const on = await c.next((m) => m.t === "state" && m.online === true);
    expect(on.reason).toBeUndefined();
    expect(on.power.ok).toBe(true);
  });

  it("MainframeConn tracks power and statusOf reports no_power", async () => {
    await start();
    const conn = new MainframeConn({ baseUrl: base(), token: await token() });
    conns.push(conn);
    conn.start();
    await waitFor(() => conn.status.value === "open");
    expect(statusOf(conn).kind).toBe("online");
    expect(conn.info.value?.power?.capacity).toBe(200_000);
    await control({ power: { stored: 0, input: 0, tick: 5 } });
    await waitFor(() => conn.info.value?.online === false);
    expect(conn.info.value?.reason).toBe("no_power");
    const st = statusOf(conn);
    expect(st.kind).toBe("no_power");
    expect(st.text).toMatch(/Mainframe has no power/);
    expect(conn.actionable).toBe(false);
    await control({ power: { input: 2000, tick: 1 } });
    await waitFor(() => conn.info.value?.online === true);
    expect(conn.info.value?.reason).toBeUndefined();
    expect(statusOf(conn).kind).toBe("online");
  });
});

describe("power formatting", () => {
  it("formats FE with k/M", () => {
    expect(fmtFE(40, true)).toBe("40 FE/t");
    expect(fmtFE(999)).toBe("999 FE");
    expect(fmtFE(1_500)).toBe("1.5k FE");
    expect(fmtFE(182_400)).toBe("182k FE");
    expect(fmtFE(2_000_000)).toBe("2M FE");
    expect(fmtFE(12_340_000, true)).toBe("12.3M FE/t");
  });
  it("sanitises power objects", () => {
    expect(normalizePower(null)).toBeUndefined();
    expect(normalizePower({ stored: 500, capacity: 100, usage: -3, ok: "yes" })).toEqual({ stored: 100, capacity: 100, usage: 0, ok: true });
  });
});
