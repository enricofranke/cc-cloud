// Client protocol handling (web/src/lib/conn.ts) against the dev mock server,
// using Node's built-in WebSocket – the same API the browser uses.
import { afterEach, describe, expect, it } from "vitest";
import { createMockServer, REACTOR, STORAGE, type MockOptions, type MockServer } from "../dev/mock-server.ts";
import { MainframeConn, keyOf, sameValue, type ConnOptions, type Notice } from "../web/src/lib/conn.ts";
import { isLanHost, insecureContext } from "../web/src/lib/security.ts";
import { sleep } from "./helpers.ts";

let mock: MockServer | undefined;
const conns: MainframeConn[] = [];

afterEach(async () => {
  conns.splice(0).forEach((c) => c.stop());
  await mock?.close();
  mock = undefined;
});

const base = () => `http://127.0.0.1:${mock!.port}`;

async function start(opts: MockOptions = {}) {
  mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, actionDelayMs: 5, approveDelayMs: 10, ae2: false, controlRoom: false, computer: false, log: () => {}, ...opts });
}

async function token(body: object = {}) {
  const res = await fetch(`${base()}/dev/token`, { method: "POST", body: JSON.stringify({ name: "Vitest", ...body }) });
  return (await res.json()).token as string;
}

const control = (body: object) => fetch(`${base()}/dev/control`, { method: "POST", body: JSON.stringify(body) });

async function waitFor(pred: () => boolean, timeoutMs = 2000) {
  const end = Date.now() + timeoutMs;
  while (!pred()) {
    if (Date.now() > end) throw new Error("condition not met in time");
    await sleep(10);
  }
}

function connect(tok: string, opts: Partial<ConnOptions> = {}) {
  const notices: Notice[] = [];
  const conn = new MainframeConn({ baseUrl: base(), token: tok, minBackoffMs: 30, maxBackoffMs: 120, onNotice: (n) => notices.push(n), ...opts });
  conns.push(conn);
  conn.start();
  return { conn, notices };
}

const widget = (conn: MainframeConn, uplinkId: string, id: string) => conn.uplinks.value.find((u) => u.uplinkId === uplinkId)!.widgets.find((w) => w.id === id)!;

describe("sameValue", () => {
  it("compares scalars and structured values", () => {
    expect(sameValue("a", "a")).toBe(true);
    expect(sameValue(1, "1")).toBe(false);
    expect(sameValue({ a: [1, 2] }, { a: [1, 2] })).toBe(true);
    expect(sameValue({ a: 1 }, null)).toBe(false);
  });
});

describe("MainframeConn", () => {
  it("authenticates via the first message and loads the snapshot", async () => {
    await start();
    const { conn } = connect(await token());
    await waitFor(() => conn.status.value === "open");
    expect(conn.info.value).toMatchObject({ name: "Mainframe @ 12 64 -30", online: true, enabled: true });
    expect(conn.uplinks.value.map((u) => u.title)).toEqual(["Fission Reactor", "Storage"]);
    expect(conn.actionable).toBe(true);
    expect(conn.canControl.value).toBe(true);
  });

  it("shows a toggle optimistically and settles on the server value", async () => {
    await start({ actionDelayMs: 60 });
    const { conn } = connect(await token());
    await waitFor(() => conn.status.value === "open");
    const power = widget(conn, REACTOR, "power");
    expect(power.value).toBe(true);
    conn.action(REACTOR, power, false, true);
    expect(conn.pending.value[keyOf(REACTOR, "power")]).toBe(false); // instant
    await waitFor(() => widget(conn, REACTOR, "power").value === false);
    await waitFor(() => !(keyOf(REACTOR, "power") in conn.pending.value));
  });

  it("rolls back when the server rejects the action", async () => {
    await start();
    const { conn, notices } = connect(await token());
    await waitFor(() => conn.status.value === "open");
    conn.action(REACTOR, { id: "ghost", type: "toggle", label: "Ghost" }, true, true);
    expect(conn.pending.value[keyOf(REACTOR, "ghost")]).toBe(true);
    await waitFor(() => !(keyOf(REACTOR, "ghost") in conn.pending.value));
    expect(notices.at(-1)).toMatchObject({ level: "warn", title: "Ghost: not applied" });
  });

  it("rolls back when the computer never confirms the value", async () => {
    await start();
    const { conn, notices } = connect(await token(), { confirmTimeoutMs: 150 });
    await waitFor(() => conn.status.value === "open");
    await control({ ignoreActions: true });
    conn.action(STORAGE, widget(conn, STORAGE, "export"), true, true);
    expect(conn.pending.value[keyOf(STORAGE, "export")]).toBe(true);
    await waitFor(() => !(keyOf(STORAGE, "export") in conn.pending.value));
    expect(widget(conn, STORAGE, "export").value).toBe(false);
    expect(notices.at(-1)).toMatchObject({ level: "info", title: "Export to AE2: no answer", text: "No answer from the computer yet – is the program running?" });
  });

  it("does nothing when the picked value is already set (e.g. the current select option)", async () => {
    await start();
    const { conn, notices } = connect(await token(), { confirmTimeoutMs: 150 });
    await waitFor(() => conn.status.value === "open");
    await control({ ignoreActions: true });
    const exp = widget(conn, STORAGE, "export");
    expect(conn.action(STORAGE, exp, exp.value as boolean, true)).toBeUndefined();
    expect(conn.pending.value).toEqual({});
    await sleep(250);
    expect(notices).toEqual([]);
  });

  it("treats an action_result carrying the new value as confirmation", async () => {
    await start();
    const { conn, notices } = connect(await token(), { confirmTimeoutMs: 150 });
    await waitFor(() => conn.status.value === "open");
    await control({ ignoreActions: true, echoResultValue: true });
    conn.action(STORAGE, widget(conn, STORAGE, "export"), true, true);
    await waitFor(() => !(keyOf(STORAGE, "export") in conn.pending.value));
    await sleep(250);
    expect(notices).toEqual([]);
  });

  it("keeps cached sections of unloaded chunks and explains rejected actions", async () => {
    await start({ extras: true });
    const { conn, notices } = connect(await token());
    await waitFor(() => conn.status.value === "open");
    const turbine = conn.uplinks.value.find((u) => u.title === "Turbine Hall")!;
    expect(turbine).toMatchObject({ loaded: false, online: false });
    expect(Date.now() - turbine.lastSeen!).toBeGreaterThan(10 * 60_000);
    expect(turbine.widgets.find((w) => w.id === "rpm")!.value).toBe(1240); // last values kept
    // a stale client that still sends gets the server's "unloaded" answer
    conn.action(turbine.uplinkId, turbine.widgets.find((w) => w.id === "coils")!, false);
    await waitFor(() => notices.length > 0);
    expect(notices.at(-1)!.text).toMatch(/chunk is not loaded/);
  });

  it("waits 8 s by default before giving up on the computer", () => {
    const conn = new MainframeConn({ baseUrl: "http://x", token: "t" });
    expect((conn as unknown as { o: { confirmTimeoutMs: number } }).o.confirmTimeoutMs).toBe(8000);
  });

  it("tracks offline and disabled states and blocks actions locally", async () => {
    await start();
    const { conn, notices } = connect(await token());
    await waitFor(() => conn.status.value === "open");
    await control({ enabled: false });
    await waitFor(() => conn.info.value?.enabled === false);
    expect(conn.actionable).toBe(false);
    expect(conn.action(REACTOR, widget(conn, REACTOR, "power"), false, true)).toBeUndefined();
    expect(conn.pending.value).toEqual({});
    expect(notices.at(-1)?.text).toMatch(/switched off/);
    await control({ enabled: true, online: false });
    await waitFor(() => conn.info.value?.online === false);
    expect(conn.actionable).toBe(false);
    // uplink offline is reflected per card
    await control({ online: true });
    await control({ uplinkOnline: { uplinkId: REACTOR, online: false } });
    await waitFor(() => conn.uplinks.value[0].online === false);
  });

  it("disables controls for read-only devices", async () => {
    await start();
    const { conn } = connect(await token({ canControl: false }));
    await waitFor(() => conn.status.value === "open");
    expect(conn.canControl.value).toBe(false);
    expect(conn.actionable).toBe(false);
  });

  it("reconnects after the connection drops and drops pending values", async () => {
    await start();
    const { conn } = connect(await token());
    await waitFor(() => conn.status.value === "open");
    await control({ ignoreActions: true });
    conn.action(STORAGE, widget(conn, STORAGE, "export"), true, true);
    await control({ dropClients: true });
    await waitFor(() => conn.status.value === "reconnecting");
    expect(conn.pending.value).toEqual({});
    expect(conn.nextRetryAt.value).toBeGreaterThan(0);
    await waitFor(() => conn.status.value === "open");
  });

  it("reports an unreachable server and recovers when it comes up", async () => {
    await start();
    const port = mock!.port;
    const tok = await token();
    await mock!.close();
    mock = undefined;
    const conn = new MainframeConn({ baseUrl: `http://127.0.0.1:${port}`, token: tok, minBackoffMs: 30, maxBackoffMs: 60 });
    conns.push(conn);
    conn.start();
    await waitFor(() => conn.status.value === "unreachable");
    conn.stop();
  });

  it("answers server pings so idle detection keeps it connected", async () => {
    await start({ pingIntervalMs: 30, idleTimeoutMs: 150 });
    const { conn } = connect(await token());
    await waitFor(() => conn.status.value === "open");
    await sleep(500);
    expect(conn.status.value).toBe("open");
  });

  it("goes to unauthorized on revocation and stops retrying", async () => {
    await start();
    const { conn } = connect(await token());
    await waitFor(() => conn.status.value === "open");
    await control({ revokeAll: true });
    await waitFor(() => conn.status.value === "unauthorized");
    await sleep(200);
    expect(conn.status.value).toBe("unauthorized");
  });

  it("treats a player losing access like a revocation", async () => {
    await start();
    const { conn } = connect(await token());
    await waitFor(() => conn.status.value === "open");
    await control({ revokePlayer: true });
    await waitFor(() => conn.status.value === "unauthorized");
  });

  it("sends the PIN and reports wrong PINs and lockouts to the caller", async () => {
    await start({ pin: "1234" });
    const { conn, notices } = connect(await token());
    await waitFor(() => conn.status.value === "open");
    const scram = widget(conn, REACTOR, "scram");
    expect(scram.opts?.pin).toBe(true);
    const results: any[] = [];
    const send = (pin: string) => conn.action(REACTOR, scram, true, false, { pin, onResult: (r) => (results.push(r), /pin/.test(r.error ?? "")) });
    send("9999");
    await waitFor(() => results.length === 1);
    expect(results[0]).toMatchObject({ ok: false, error: "wrong_pin", attemptsLeft: 4 });
    expect(notices).toHaveLength(0); // handled by the dialog, no toast
    send("1234");
    await waitFor(() => results.length === 2);
    expect(results[1]).toMatchObject({ ok: true });
    for (let i = 0; i < 5; i++) {
      send("0000");
      await waitFor(() => results.length === 3 + i);
    }
    expect(results.at(-1)).toMatchObject({ error: "pin_locked", retryAfter: 300 });
    expect(conn.pinLockedUntil.value).toBeGreaterThan(Date.now() + 290_000);
  });

  it("detects a LAN-only server (403 internet_access_disabled) while unreachable", async () => {
    // Node's WebSocket/fetch cannot fake the Host header, so stub the WebSocket to fail and fetch to answer 403.
    const realFetch = globalThis.fetch;
    // the mod answers with a plain-text 403
    globalThis.fetch = (async () =>
      new Response("This CC: Cloud dashboard only accepts connections from the local network. Server admins can allow internet access with webAllowInternet=true", {
        status: 403,
      })) as typeof fetch;
    class FailingWS {
      readyState = 3;
      onclose: ((e: any) => void) | null = null;
      onopen = null;
      onmessage = null;
      onerror = null;
      constructor() {
        setTimeout(() => this.onclose?.({ code: 1006 }), 5);
      }
      send() {}
      close() {}
    }
    try {
      const conn = new MainframeConn({ baseUrl: "http://mc.example.net:8787", token: "t", WebSocketImpl: FailingWS as any, minBackoffMs: 1000 });
      conns.push(conn);
      conn.start();
      await waitFor(() => conn.lanOnly.value);
      expect(conn.status.value).toBe("unreachable");
    } finally {
      globalThis.fetch = realFetch;
    }
  });

  it("collects alerts and counts unread ones", async () => {
    await start();
    const alerts: string[] = [];
    const { conn } = connect(await token(), { onAlert: (a) => alerts.push(a.text) });
    await waitFor(() => conn.status.value === "open");
    await control({ alert: { uplinkId: REACTOR, text: "Core temperature critical", level: "crit" } });
    await waitFor(() => conn.alerts.value.length === 1);
    expect(conn.alerts.value[0]).toMatchObject({ level: "crit", uplinkTitle: "Fission Reactor" });
    expect(conn.unread.value).toBe(1);
    expect(alerts).toEqual(["Core temperature critical"]);
  });
});

describe("pairing API client", () => {
  it("runs the two-step flow: pending, then approved with a token", async () => {
    await start({ approveDelayMs: 300 });
    const { pair, pollPairing } = await import("../web/src/lib/api.ts");
    const started = await pair(mock!.code(), "Vitest phone", base());
    expect(started.status).toBe("pending");
    if (started.status !== "pending") return;
    expect((await pollPairing(started.pairingId, base())).status).toBe("pending");
    await sleep(450);
    const done = await pollPairing(started.pairingId, base());
    expect(done).toMatchObject({ status: "approved", mainframeId: mock!.base.id });
  });

  it("maps denied, expired, wrong codes and rate limits", async () => {
    await start({ approveDelayMs: 5 });
    const { pair, pollPairing, ApiError } = await import("../web/src/lib/api.ts");
    const s = await pair(mock!.code(), "deny this one", base());
    await sleep(30);
    if (s.status === "pending") expect((await pollPairing(s.pairingId, base())).status).toBe("denied");
    expect((await pollPairing("nope", base())).status).toBe("expired");
    const err = await pair("ZZZZ-ZZZZ", "x", base()).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.kind).toBe("invalid_code");
    for (let i = 0; i < 5; i++) await pair("ZZZZ-ZZZZ", "x", base()).catch(() => {});
    expect((await pair("ZZZZ-ZZZZ", "x", base()).catch((e) => e)).kind).toBe("too_many_attempts");
  });
});

describe("security helpers", () => {
  it("recognizes LAN hosts", () => {
    for (const h of ["localhost", "127.0.0.1", "192.168.1.20", "10.0.0.5", "172.20.1.1", "mc.local", "[::1]", "100.101.5.5"]) expect(isLanHost(h)).toBe(true);
    for (const h of ["mc.example.com", "8.8.8.8", "172.32.0.1", "fdroid.org"]) expect(isLanHost(h)).toBe(false);
  });
  it("warns only for plain HTTP on public hosts", () => {
    expect(insecureContext({ protocol: "http:", hostname: "mc.example.com" })).toBe(true);
    expect(insecureContext({ protocol: "https:", hostname: "mc.example.com" })).toBe(false);
    expect(insecureContext({ protocol: "http:", hostname: "192.168.0.10" })).toBe(false);
  });
});
