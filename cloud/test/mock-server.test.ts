// Protocol tests against the dev mock server, which enforces the same rules
// as the mod's embedded server (docs/SPEC.md "Browser protocol" + "Security").
import { afterEach, describe, expect, it } from "vitest";
import { createMockServer, REACTOR, STORAGE, type MockOptions, type MockServer } from "../dev/mock-server.ts";
import { CODE_ALPHABET } from "../dev/protocol.ts";
import { TestSocket, sleep } from "./helpers.ts";

let mock: MockServer;
const sockets: TestSocket[] = [];
const base = () => `http://127.0.0.1:${mock.port}`;

async function start(opts: MockOptions = {}) {
  mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, actionDelayMs: 5, approveDelayMs: 10, ae2: false, controlRoom: false, log: () => {}, ...opts });
  return mock;
}

afterEach(async () => {
  sockets.splice(0).forEach((s) => s.close());
  await mock?.close();
});

async function pair(code: string, name = "Test Phone") {
  const res = await fetch(`${base()}/api/pair`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ code, name }) });
  return { status: res.status, body: (await res.json()) as any, headers: res.headers };
}

const poll = async (id: string) => {
  const res = await fetch(`${base()}/api/pair/${id}`);
  return { status: res.status, body: (await res.json()) as any };
};

/** Full two-step pairing: code -> pending -> (simulated in-game approval) -> token. */
async function pairApproved(name = "Test Phone") {
  const { body } = await pair(mock.code(), name);
  expect(body).toMatchObject({ status: "pending" });
  for (let i = 0; i < 50; i++) {
    const r = await poll(body.pairingId);
    if (r.body.status !== "pending") return r.body;
    await sleep(10);
  }
  throw new Error("approval timed out");
}

async function control(body: object) {
  await fetch(`${base()}/dev/control`, { method: "POST", body: JSON.stringify(body) });
}

function socket(headers?: Record<string, string>) {
  const s = new TestSocket(`ws://127.0.0.1:${mock.port}/ws/client`, headers);
  sockets.push(s);
  return s;
}

/** Pairs, connects and authenticates; resolves with the socket and its snapshot. */
async function client(name = "Enrico") {
  const body = await pairApproved(name);
  const c = socket();
  await c.opened();
  c.send({ t: "auth", token: body.token });
  const snap = await c.next("snapshot");
  return { c, snap, token: body.token as string };
}

describe("pairing", () => {
  it("puts a correct code into pending until the player approves in-game", async () => {
    await start({ approveDelayMs: -1 });
    const code = mock.code();
    expect(code).toMatch(/^[A-Z2-9]{4}-[A-Z2-9]{4}$/);
    for (const ch of code.replace("-", "")) expect(CODE_ALPHABET).toContain(ch);
    const { status, body } = await pair(code.toLowerCase().replace("-", " "));
    expect(status).toBe(202);
    expect(body).toEqual({ status: "pending", pairingId: expect.any(String), expiresIn: 120 });
    expect((await poll(body.pairingId)).body).toEqual({ status: "pending" });
    await control({ approve: body.pairingId });
    const done = (await poll(body.pairingId)).body;
    expect(done).toMatchObject({ status: "approved", mainframeId: mock.base.id, name: "Mainframe @ 12 64 -30" });
    expect(done.token).toMatch(/^[A-Za-z0-9_-]{40,}$/);
    // the token is handed out exactly once
    expect((await poll(body.pairingId)).status).toBe(404);
  });

  it("reports denied and expired pairings", async () => {
    await start({ pairTtlMs: 60 });
    const denied = (await pair(mock.code(), "please deny me")).body;
    await sleep(30);
    expect(await poll(denied.pairingId)).toEqual({ status: 403, body: { status: "denied" } });
    await mock.close();
    await start({ approveDelayMs: -1, pairTtlMs: 60 });
    const slow = (await pair(mock.code())).body;
    await sleep(90);
    expect(await poll(slow.pairingId)).toEqual({ status: 404, body: { status: "expired" } });
    expect((await poll("does-not-exist")).status).toBe(404);
  });

  it("answers 403 internet_access_disabled for non-LAN clients when LAN-only", async () => {
    await start({ lanOnly: true });
    // fetch() cannot override Host, so use node:http
    const http = await import("node:http");
    const status = await new Promise<{ code: number; body: string }>((resolve) => {
      const req = http.request({ host: "127.0.0.1", port: mock.port, path: "/api/pair", method: "POST", headers: { host: "mc.example.net" } }, (r) => {
        let b = "";
        r.on("data", (c) => (b += c));
        r.on("end", () => resolve({ code: r.statusCode!, body: b }));
      });
      req.end("{}");
    });
    expect(status.code).toBe(403);
    expect(status.body).toMatch(/webAllowInternet=true/);
    const lan = socket({ host: "mc.example.net" });
    await expect(lan.opened()).rejects.toThrow(/403/);
  });

  it("protects every non-public route (deny by default)", async () => {
    await start();
    for (const [method, path] of [
      ["GET", "/api/mainframes"],
      ["GET", "/api/devices"],
      ["DELETE", "/api/devices/x"],
      ["POST", "/api/mainframes"],
      ["GET", "/api/anything"],
    ]) {
      const res = await fetch(`${base()}${path}`, { method });
      expect([401, 403, 404], `${method} ${path}`).toContain(res.status);
      expect(res.status === 200).toBe(false);
    }
    // the public ones answer without a token
    expect((await fetch(`${base()}/api/pair/x`)).status).toBe(404);
  });

  it("makes codes single-use and rotates them", async () => {
    await start();
    const code = mock.code();
    expect((await pair(code)).body.status).toBe("pending");
    expect(mock.code()).not.toBe(code);
    expect((await pair(code)).status).toBe(403);
  });

  it("expires codes after the TTL", async () => {
    await start({ codeTtlMs: 50 });
    const code = mock.code();
    await sleep(80);
    expect((await pair(code)).status).toBe(403);
  });

  it("answers 429 after 5 wrong attempts per minute", async () => {
    await start();
    const statuses = [];
    for (let i = 0; i < 7; i++) statuses.push((await pair("ZZZZ-ZZZZ")).status);
    expect(statuses).toEqual([403, 403, 403, 403, 403, 429, 429]);
    // even the right code is blocked now
    expect((await pair(mock.code())).status).toBe(429);
  });

  it("rejects malformed and oversized bodies", async () => {
    await start();
    expect((await fetch(`${base()}/api/pair`, { method: "POST", body: "{nope" })).status).toBe(400);
    expect((await pair("x", "")).status).toBe(400);
    const big = await fetch(`${base()}/api/pair`, { method: "POST", body: JSON.stringify({ code: "x", name: "a".repeat(5000) }) });
    expect(big.status).toBe(400);
  });

  it("sends the hardening headers", async () => {
    await start();
    const { headers } = await pair("ZZZZ-ZZZZ");
    expect(headers.get("content-security-policy")).toContain("script-src 'self'");
    expect(headers.get("x-frame-options")).toBe("DENY");
    expect(headers.get("x-content-type-options")).toBe("nosniff");
    expect(headers.get("referrer-policy")).toBe("no-referrer");
    expect(headers.get("cache-control")).toBe("no-store");
    expect(headers.get("access-control-allow-origin")).toBeNull();
  });

  it("serves /api/mainframes only with a valid bearer token", async () => {
    await start();
    const body = await pairApproved();
    expect((await fetch(`${base()}/api/mainframes`)).status).toBe(401);
    const res = await fetch(`${base()}/api/mainframes`, { headers: { authorization: `Bearer ${body.token}` } });
    expect((await res.json()).mainframes[0]).toMatchObject({ id: mock.base.id, online: true, enabled: true, canControl: true });
  });
});

describe("websocket auth", () => {
  it("authenticates with the first message and sends a snapshot", async () => {
    await start();
    const { snap } = await client();
    expect(snap.mainframe).toMatchObject({ id: mock.base.id, online: true, enabled: true });
    expect(snap.canControl).toBe(true);
    expect(snap.uplinks.map((u: any) => u.title)).toEqual(["Fission Reactor", "Storage"]);
  });

  it("closes with 4001 on a wrong token", async () => {
    await start();
    const c = socket();
    await c.opened();
    c.send({ t: "auth", token: "forged" });
    expect((await c.next("error")).error).toBe("unauthorized");
    expect(await c.closed()).toBe(4001);
  });

  it("closes with 4001 when the first message is not auth", async () => {
    await start();
    const c = socket();
    await c.opened();
    c.send({ t: "action", reqId: "1", uplinkId: REACTOR, id: "power", value: true });
    expect(await c.closed()).toBe(4001);
  });

  it("closes unauthenticated sockets after the auth timeout", async () => {
    await start({ authTimeoutMs: 80 });
    const c = socket();
    await c.opened();
    expect(await c.closed()).toBe(4001);
  });

  it("refuses cross-site origins", async () => {
    await start();
    const c = socket({ origin: "https://evil.example" });
    await expect(c.opened()).rejects.toThrow(/403/);
  });

  it("limits connections per IP", async () => {
    await start({ maxConnsPerIp: 2 });
    const a = socket();
    const b = socket();
    await Promise.all([a.opened(), b.opened()]);
    await expect(socket().opened()).rejects.toThrow(/429/);
  });

  it("drops idle sockets that never answer pings", async () => {
    await start({ pingIntervalMs: 30, idleTimeoutMs: 120 });
    const { c } = await client();
    await c.next("ping");
    await c.closed(1000);
  });

  it("kills devices of a player who lost access", async () => {
    await start();
    const { c } = await client();
    await control({ revokePlayer: true });
    expect((await c.next("error")).error).toBe("access_revoked");
    expect(await c.closed()).toBe(4003);
  });

  it("disconnects revoked devices with an auth error", async () => {
    await start();
    const { c, token } = await client();
    await control({ revokeAll: true });
    expect((await c.next("error")).error).toBe("revoked");
    expect(await c.closed()).toBe(4001);
    const again = socket();
    await again.opened();
    again.send({ t: "auth", token });
    expect(await again.closed()).toBe(4001);
  });
});

describe("actions", () => {
  it("accepts an action, answers action_result and pushes the new value", async () => {
    await start();
    const { c } = await client();
    c.send({ t: "action", reqId: "r1", uplinkId: REACTOR, id: "power", value: false });
    expect(await c.next("action_result")).toEqual({ t: "action_result", reqId: "r1", ok: true });
    expect(await c.next((m) => m.t === "value" && m.id === "power")).toMatchObject({ uplinkId: REACTOR, value: false });
  });

  it("normalizes slider values to min/max/step", async () => {
    await start();
    const { c } = await client();
    c.send({ t: "action", reqId: "s", uplinkId: STORAGE, id: "output", value: 73 });
    expect(await c.next((m) => m.t === "value" && m.id === "output")).toMatchObject({ value: 75 });
  });

  it("rejects actions while disabled or offline", async () => {
    await start();
    const { c } = await client();
    await control({ enabled: false });
    expect(await c.next("state")).toMatchObject({ online: true, enabled: false });
    c.send({ t: "action", reqId: "d", uplinkId: REACTOR, id: "power", value: true });
    expect(await c.next("action_result")).toMatchObject({ reqId: "d", ok: false, error: "disabled" });
    await control({ online: false, enabled: true });
    c.send({ t: "action", reqId: "o", uplinkId: REACTOR, id: "power", value: true });
    expect(await c.next((m) => m.t === "action_result" && m.reqId === "o")).toMatchObject({ ok: false, error: "offline" });
  });

  it("rejects actions on offline uplinks, read-only widgets and bad values", async () => {
    await start();
    const { c } = await client();
    c.send({ t: "action", reqId: "g", uplinkId: REACTOR, id: "temp", value: 1 });
    expect(await c.next("action_result")).toMatchObject({ ok: false, error: "not_interactive" });
    c.send({ t: "action", reqId: "v", uplinkId: REACTOR, id: "power", value: "yes" });
    expect(await c.next("action_result")).toMatchObject({ ok: false, error: "invalid_value" });
    c.send({ t: "action", reqId: "x", uplinkId: "nope", id: "power", value: true });
    expect(await c.next("action_result")).toMatchObject({ ok: false, error: "unknown_uplink" });
    c.send({ t: "action", reqId: "bad", uplinkId: "has spaces", id: "power" });
    expect(await c.next("action_result")).toMatchObject({ reqId: "bad", ok: false, error: "invalid_message" });
    await control({ uplinkOnline: { uplinkId: REACTOR, online: false } });
    c.send({ t: "action", reqId: "u", uplinkId: REACTOR, id: "power", value: true });
    expect(await c.next((m) => m.t === "action_result" && m.reqId === "u")).toMatchObject({ ok: false, error: "uplink_offline" });
  });

  it("rejects actions from read-only devices", async () => {
    await start();
    const { token } = await (await fetch(`${base()}/dev/token`, { method: "POST", body: JSON.stringify({ name: "TV", canControl: false }) })).json();
    const c = socket();
    await c.opened();
    c.send({ t: "auth", token });
    expect((await c.next("snapshot")).canControl).toBe(false);
    c.send({ t: "action", reqId: "ro", uplinkId: REACTOR, id: "power", value: false });
    expect(await c.next("action_result")).toMatchObject({ ok: false, error: "read_only" });
  });

  it("limits each client to 10 actions per second", async () => {
    await start();
    const { c } = await client();
    for (let i = 0; i < 15; i++) c.send({ t: "action", reqId: `r${i}`, uplinkId: STORAGE, id: "sort" });
    await sleep(150);
    const results = c.messages.filter((m) => m.t === "action_result");
    expect(results.filter((r) => r.ok)).toHaveLength(10);
    expect(results.filter((r) => r.error === "rate_limited")).toHaveLength(5);
    await sleep(1000);
    c.send({ t: "action", reqId: "later", uplinkId: STORAGE, id: "sort" });
    expect(await c.next((m) => m.t === "action_result" && m.reqId === "later")).toMatchObject({ ok: true });
  });

  it("survives garbage frames and rejects oversized ones", async () => {
    await start();
    const { c } = await client();
    c.send("not json");
    c.send(JSON.stringify([1, 2, 3]));
    c.send({ t: "nope" });
    c.ws.ping();
    await sleep(50);
    expect(c.ws.readyState).toBe(1);
    c.send({ t: "action", reqId: "big", uplinkId: REACTOR, id: "power", value: "x".repeat(9000) });
    expect(await c.closed()).toBe(1009);
    expect((await fetch(`${base()}/api/mainframes`)).status).toBe(401); // server still up
  });

  it("requires the PIN for PIN widgets and locks after 5 wrong tries", async () => {
    await start({ pin: "4711" });
    const { c } = await client();
    const scram = (reqId: string, pin?: string) => c.send({ t: "action", reqId, uplinkId: REACTOR, id: "scram", value: true, ...(pin ? { pin } : {}) });
    scram("none");
    expect(await c.next("action_result")).toMatchObject({ reqId: "none", ok: false, error: "pin_required" });
    scram("bad1", "0000");
    expect(await c.next("action_result")).toMatchObject({ ok: false, error: "wrong_pin", attemptsLeft: 4 });
    scram("good", "4711");
    expect(await c.next("action_result")).toMatchObject({ reqId: "good", ok: true });
    for (let i = 0; i < 4; i++) {
      scram(`w${i}`, "0000");
      expect(await c.next("action_result")).toMatchObject({ error: "wrong_pin", attemptsLeft: 4 - i });
    }
    scram("w5", "0000");
    expect(await c.next("action_result")).toMatchObject({ error: "pin_locked", retryAfter: 300 });
    scram("after", "4711"); // even the right PIN is refused while locked
    expect(await c.next("action_result")).toMatchObject({ reqId: "after", error: "pin_locked" });
  });

  it("broadcasts alerts with a timestamp", async () => {
    await start();
    const { c } = await client();
    await control({ alert: { uplinkId: REACTOR, text: "Core hot", level: "crit" } });
    const a = await c.next("alert");
    expect(a).toMatchObject({ uplinkId: REACTOR, text: "Core hot", level: "crit" });
    expect(typeof a.ts).toBe("number");
  });
});
