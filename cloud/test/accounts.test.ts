// v0.6: account linking, roles, access API, devices, share links (incl. the
// guarantee that a share session never sends actions).
import { afterEach, describe, expect, it } from "vitest";
import { createMockServer, REACTOR, STORAGE, ME, CONTROL, type MockServer } from "../dev/mock-server.ts";
import { MainframeConn } from "../web/src/lib/conn.ts";
import { forgetStoredShareSecrets, takeShareSecret } from "../web/src/lib/share.ts";
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
  mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, actionDelayMs: 5, approveDelayMs: 10, extras: true, log: () => {} });
}
const accountToken = async (player: string) =>
  (await (await fetch(`${base()}/dev/token`, { method: "POST", body: JSON.stringify({ account: player }) })).json()).token as string;
async function ws(first: object) {
  const c = new TestSocket(`ws://127.0.0.1:${mock!.port}/ws/client`);
  sockets.push(c);
  await c.opened();
  c.send(first);
  return c;
}
const api = (path: string, token: string, init: RequestInit = {}) =>
  fetch(`${base()}${path}`, { ...init, headers: { authorization: `Bearer ${token}`, ...(init.headers ?? {}) } });
async function waitFor(pred: () => boolean, ms = 2000) {
  const end = Date.now() + ms;
  while (!pred()) {
    if (Date.now() > end) throw new Error("timeout");
    await sleep(10);
  }
}

describe("account linking", () => {
  it("links a browser to the player account with a /cccloud link code", async () => {
    await start();
    const codes = await (await fetch(`${base()}/dev/codes`)).json();
    const code = codes.find((c: any) => c.account === "Enrico").code;
    const started = await (await fetch(`${base()}/api/pair`, { method: "POST", body: JSON.stringify({ code, name: "Desktop PC" }), headers: { "user-agent": "Mozilla/5.0 (Windows NT 10.0)" } })).json();
    expect(started.status).toBe("pending");
    await sleep(40);
    const done = await (await fetch(`${base()}/api/pair/${started.pairingId}`)).json();
    expect(done).toMatchObject({ status: "approved", account: { player: "Enrico" } });
    expect(done.mainframes.find((m: any) => m.id === mock!.base.id)).toMatchObject({ role: "owner" });
    // the account token lists every mainframe with a role
    const list = await (await api("/api/mainframes", done.token)).json();
    expect(list.account.player).toBe("Enrico");
    expect(list.mainframes.length).toBeGreaterThanOrEqual(3);
  });

  it("requires the mainframe in the auth message for account tokens", async () => {
    await start();
    const token = await accountToken("Enrico");
    const missing = await ws({ t: "auth", token });
    expect((await missing.next("error")).error).toBe("mainframe_required");
    expect(await missing.closed()).toBe(4401);
    const ok = await ws({ t: "auth", token, mainframeId: mock!.base.id });
    const snap = await ok.next("snapshot");
    expect(snap).toMatchObject({ role: "owner", canControl: true, account: { player: "Enrico" } });
    // Mia has no role on the base
    const mia = await ws({ t: "auth", token: await accountToken("Mia"), mainframeId: mock!.base.id });
    expect((await mia.next("error")).error).toBe("no_access");
    expect(await mia.closed()).toBe(4403);
  });
});

describe("roles", () => {
  it("resolves roles from owner, direct grants and team ranks and enforces them", async () => {
    await start();
    const mf = mock!.base.id;
    const tom = await ws({ t: "auth", token: await accountToken("Tom"), mainframeId: mf }); // FTB member -> operator
    expect((await tom.next("snapshot")).role).toBe("operator");
    tom.send({ t: "action", reqId: "a", uplinkId: REACTOR, id: "power", value: true });
    expect(await tom.next("action_result")).toMatchObject({ ok: true });
    tom.send({ t: "layout_set", reqId: "l", layout: { v: 1, pages: [], cards: {}, widgets: {} } });
    expect(await tom.next("layout_result")).toMatchObject({ ok: false, error: "not_allowed" });

    const sam = await ws({ t: "auth", token: await accountToken("Sam"), mainframeId: mf }); // ally -> viewer
    expect((await sam.next("snapshot")).role).toBe("viewer");
    sam.send({ t: "action", reqId: "b", uplinkId: REACTOR, id: "power", value: false });
    expect(await sam.next("action_result")).toMatchObject({ ok: false, error: "read_only" });

    const lisa = await ws({ t: "auth", token: await accountToken("Lisa"), mainframeId: mf }); // direct admin
    expect((await lisa.next("snapshot")).role).toBe("admin");
    lisa.send({ t: "layout_set", reqId: "l2", layout: { v: 1, pages: [], cards: {}, widgets: {} } });
    expect(await lisa.next("layout_result")).toMatchObject({ ok: true });
  });

  it("access API: lists sources, changes roles live, admin only", async () => {
    await start();
    const mf = mock!.base.id;
    const owner = await accountToken("Enrico");
    const doc = await (await api(`/api/mainframes/${mf}/access`, owner)).json();
    expect(doc.you).toMatchObject({ player: "Enrico", role: "owner" });
    expect(doc.you.perms).toContain("access.manage");
    const byName = Object.fromEntries(doc.players.map((p: any) => [p.player, p]));
    expect(byName.Enrico).toMatchObject({ role: "owner", source: "owner" });
    expect(byName.Lisa).toMatchObject({ role: "admin", source: "direct" });
    expect(byName.Tom).toMatchObject({ role: "operator", source: "team", teamRank: "member" });
    expect(byName.Sam).toMatchObject({ role: "viewer", source: "team", teamRank: "ally" });
    expect(doc.team).toMatchObject({ provider: "ftbteams", name: "Builders" });

    // an operator may not manage access
    const tomToken = await accountToken("Tom");
    expect((await api(`/api/mainframes/${mf}/access`, tomToken)).status).toBe(403);

    // promote Tom while he is connected -> live role push
    const tom = await ws({ t: "auth", token: tomToken, mainframeId: mf });
    await tom.next("snapshot");
    const r = await api(`/api/mainframes/${mf}/access`, owner, { method: "POST", body: JSON.stringify({ op: "set_role", player: "Tom", role: "admin" }) });
    expect(r.status).toBe(200);
    expect(await tom.next("role")).toMatchObject({ role: "admin", canControl: true });

    // team rank mapping: allies become operators
    await api(`/api/mainframes/${mf}/access`, owner, { method: "POST", body: JSON.stringify({ op: "set_mapping", rank: "ally", role: "operator" }) });
    const sam = await ws({ t: "auth", token: await accountToken("Sam"), mainframeId: mf });
    expect((await sam.next("snapshot")).role).toBe("operator");
    // unassigning the team removes Sam's access immediately
    await api(`/api/mainframes/${mf}/access`, owner, { method: "POST", body: JSON.stringify({ op: "set_team", teamId: null }) });
    expect(await sam.closed()).toBe(4403);
    expect((await api(`/api/mainframes/${mf}/access`, owner, { method: "POST", body: JSON.stringify({ op: "set_role", player: "Nobody", role: "viewer" }) })).status).toBe(400);
  });
});

describe("fine-grained permissions", () => {
  it("custom allow/deny change the effective set live and are enforced", async () => {
    await start();
    const mf = mock!.base.id;
    const owner = await accountToken("Enrico");
    const post = (b: object, t = owner) => api(`/api/mainframes/${mf}/access`, t, { method: "POST", body: JSON.stringify(b) });
    const doc = await (await api(`/api/mainframes/${mf}/access`, owner)).json();
    expect(doc.catalog.find((c: any) => c.id === "act.buttons")).toEqual({ id: "act.buttons", group: "act", presets: ["operator", "admin", "owner"] });
    expect(doc.players.find((p: any) => p.player === "Tom")).toMatchObject({ preset: "operator", allow: [], deny: [] });

    // Mia gets viewer + layout.edit; she can save a layout but not press buttons
    expect((await post({ op: "set_role", player: "Mia", role: "viewer", allow: ["layout.edit"] })).status).toBe(200);
    const mia = await ws({ t: "auth", token: await accountToken("Mia"), mainframeId: mf });
    const snap = await mia.next("snapshot");
    expect(snap.perms).toEqual(["view", "storage.search", "layout.edit"]);
    expect(snap.canControl).toBe(false);
    mia.send({ t: "layout_set", reqId: "l", layout: { v: 1, pages: [], cards: {}, widgets: {} } });
    expect(await mia.next("layout_result")).toMatchObject({ ok: true });
    mia.send({ t: "action", reqId: "a", uplinkId: REACTOR, id: "power", value: false });
    expect(await mia.next("action_result")).toMatchObject({ ok: false, error: "read_only" });
    mia.send({ t: "net_unlink", reqId: "n", nodeId: "x" });
    expect(await mia.next("net_result")).toMatchObject({ ok: false, error: "read_only" });

    // operator preset with act.redstone denied: buttons ok, redstone not
    expect((await post({ op: "set_perms", player: "Mia", allow: ["act.buttons"], deny: ["storage.search"] })).status).toBe(200);
    const push = await mia.next("role");
    expect(push.perms).toEqual(["view", "act.buttons"]);
    expect(push.canControl).toBe(true);

    // unknown ids and escalation
    expect((await post({ op: "set_perms", player: "Mia", allow: ["root"] })).status).toBe(400);
    expect((await post({ op: "set_perms", player: "Nobody", allow: [] })).status).toBe(400);
    const lisa = await accountToken("Lisa");
    expect((await post({ op: "set_role", player: "Lisa", role: "admin", deny: ["scripts.upload"] })).status).toBe(200);
    // Lisa (no scripts.upload) cannot give it to Tom, but can manage other things
    expect((await post({ op: "set_role", player: "Sam", role: "viewer", allow: ["scripts.upload"] }, lisa)).status).toBe(403);
    expect((await post({ op: "set_role", player: "Sam", role: "viewer", allow: ["act.buttons"] }, lisa)).status).toBe(200);
    // and the script API refuses her
    expect((await api(`/api/mainframes/${mf}/computers/x/files`, lisa)).status).toBe(403);

    // team rank overrides
    expect((await post({ op: "set_mapping", rank: "member", role: "operator", deny: ["act.redstone"] })).status).toBe(200);
    const after = await (await api(`/api/mainframes/${mf}/access`, owner)).json();
    expect(after.team.overrides.member).toEqual({ allow: [], deny: ["act.redstone"] });
    expect(after.players.find((p: any) => p.player === "Tom").perms).not.toContain("act.redstone");

    // dropping view removes access
    expect((await post({ op: "set_perms", player: "Mia", deny: ["view"] })).status).toBe(200);
    expect(await mia.closed()).toBe(4403);
  });
});

describe("devices", () => {
  it("lists every browser of the player and revokes them", async () => {
    await start();
    const a = await accountToken("Enrico");
    const b = await accountToken("Enrico");
    const other = await accountToken("Lisa");
    const list = (await (await api("/api/devices", a)).json()).devices;
    expect(list.length).toBeGreaterThanOrEqual(2);
    expect(list.filter((d: any) => d.current)).toHaveLength(1);
    expect(list.every((d: any) => ["desktop", "phone", "tablet", "other"].includes(d.type))).toBe(true);
    // Lisa's browsers are not listed
    const lisaDev = (await (await api("/api/devices", other)).json()).devices[0];
    expect(list.some((d: any) => d.id === lisaDev.id)).toBe(false);
    expect((await api(`/api/devices/${lisaDev.id}`, a, { method: "DELETE" })).status).toBe(404);
    // revoke the second browser while it is connected
    const s = await ws({ t: "auth", token: b, mainframeId: mock!.base.id });
    await s.next("snapshot");
    const target = list.find((d: any) => !d.current);
    expect((await api(`/api/devices/${target.id}`, a, { method: "DELETE" })).status).toBe(200);
    expect(await s.closed()).toBe(4401);
  });
});

describe("share links", () => {
  async function newShare(body: object) {
    const owner = await accountToken("Enrico");
    const r = await api(`/api/mainframes/${mock!.base.id}/shares`, owner, { method: "POST", body: JSON.stringify(body) });
    return { owner, res: r, json: await r.json() };
  }

  it("creates a link whose secret is returned once and never listed", async () => {
    await start();
    const { owner, json } = await newShare({ label: "Reactor only", pages: [], cards: [REACTOR], expiresIn: 3600, maxViewers: 2, hideNumbers: false });
    expect(json.url).toBe(`/s/${json.id}#${json.secret}`);
    expect(json.secret.length).toBeGreaterThanOrEqual(22); // 128 bit
    const list = await (await api(`/api/mainframes/${mock!.base.id}/shares`, owner)).json();
    expect(list.shares[0]).toMatchObject({ id: json.id, label: "Reactor only", createdBy: "Enrico", maxViewers: 2, cards: [REACTOR] });
    expect(JSON.stringify(list)).not.toContain(json.secret);
    // operators cannot create links
    const tom = await accountToken("Tom");
    expect((await api(`/api/mainframes/${mock!.base.id}/shares`, tom, { method: "POST", body: "{}" })).status).toBe(403);
    const bad = await newShare({ label: "x", pages: [], cards: [], expiresIn: 3600, maxViewers: 2 });
    expect(bad.res.status).toBe(400);
  });

  it("shows only the shared cards, read-only, and denies every message type", async () => {
    await start();
    const { json } = await newShare({ label: "Status", pages: [], cards: [REACTOR, ME], expiresIn: 86400, maxViewers: 3, hideNumbers: true });
    const s = await ws({ t: "auth_share", id: json.id, secret: json.secret });
    const snap = await s.next("snapshot");
    expect(snap).toMatchObject({ role: "share", canControl: false, share: { label: "Status", sharedBy: "Enrico", hideNumbers: true } });
    expect(snap.network).toBeUndefined();
    expect(snap.uplinks.map((u: any) => u.uplinkId).sort()).toEqual([REACTOR, ME].sort());
    const types = snap.uplinks.flatMap((u: any) => u.widgets.map((w: any) => w.type));
    expect(types).not.toContain("item_search");
    expect(types).not.toContain("label"); // hideNumbers
    expect(snap.uplinks.some((u: any) => u.uplinkId === STORAGE || u.uplinkId === CONTROL)).toBe(false);

    // every message type is refused
    const reactorPowerBefore = mock!.base.uplinks.get(REACTOR)!.widgets.find((w) => w.id === "power")!.value;
    const tries: [object, string][] = [
      [{ t: "action", reqId: "1", uplinkId: REACTOR, id: "power", value: !reactorPowerBefore }, "action_result"],
      [{ t: "action", reqId: "2", uplinkId: REACTOR, id: "scram", value: true, pin: "1234" }, "action_result"],
      [{ t: "query", reqId: "3", uplinkId: ME, id: "search", q: "iron" }, "query_result"],
      [{ t: "layout_set", reqId: "4", layout: { v: 1, pages: [], cards: {}, widgets: {} } }, "layout_result"],
      [{ t: "net_unlink", reqId: "5", nodeId: "relay-west" }, "net_result"],
    ];
    for (const [msg, type] of tries) {
      s.send(msg);
      expect(await s.next((m) => m.t === type && m.reqId === (msg as any).reqId)).toMatchObject({ ok: false, error: "forbidden" });
    }
    s.send({ t: "auth", token: "x" });
    expect((await s.next("error")).error).toBe("forbidden");
    await sleep(50);
    expect(mock!.base.uplinks.get(REACTOR)!.widgets.find((w) => w.id === "power")!.value).toBe(reactorPowerBefore);
    expect(mock!.base.net!.nodes.some((n) => n.id === "relay-west")).toBe(true);

    // value deltas: shared widgets only
    await fetch(`${base()}/dev/control`, { method: "POST", body: JSON.stringify({ value: { uplinkId: STORAGE, id: "output", value: 55 } }) });
    await fetch(`${base()}/dev/control`, { method: "POST", body: JSON.stringify({ value: { uplinkId: REACTOR, id: "temp", value: 777 } }) });
    expect(await s.next((m) => m.t === "value")).toMatchObject({ uplinkId: REACTOR, id: "temp", value: 777 });
    expect(s.messages.some((m) => m.t === "value" && m.uplinkId === STORAGE)).toBe(false);
  });

  it("enforces max viewers, expiry, wrong secrets and revocation", async () => {
    await start();
    const { owner, json } = await newShare({ label: "One", pages: ["main"], cards: [], expiresIn: 3600, maxViewers: 1, hideNumbers: false });
    const a = await ws({ t: "auth_share", id: json.id, secret: json.secret });
    await a.next("snapshot");
    const b = await ws({ t: "auth_share", id: json.id, secret: json.secret });
    expect((await b.next("error")).error).toBe("share_full");
    expect(await b.closed()).toBe(4429);
    const wrong = await ws({ t: "auth_share", id: json.id, secret: "A".repeat(22) });
    expect(await wrong.closed()).toBe(4401);
    expect((await api(`/api/mainframes/${mock!.base.id}/shares/${json.id}`, owner, { method: "DELETE" })).status).toBe(200);
    expect((await a.next("error")).error).toBe("share_revoked");
    expect(await a.closed()).toBe(4401);

    const second = await newShare({ label: "Old", pages: ["main"], cards: [], expiresIn: 3600, maxViewers: 5, hideNumbers: false });
    mock!.shares.get(second.json.id)!.expires = Date.now() - 1;
    const c = await ws({ t: "auth_share", id: second.json.id, secret: second.json.secret });
    expect((await c.next("error")).error).toBe("share_expired");
  });

  it("a share-mode MainframeConn never sends anything but auth_share and pong", async () => {
    mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, actionDelayMs: 5, pingIntervalMs: 50, log: () => {} });
    const { json } = await newShare({ label: "All", pages: ["main"], cards: [], expiresIn: null, maxViewers: 5, hideNumbers: false });
    const sent: string[] = [];
    class Recording extends WebSocket {
      send(data: string) {
        sent.push(JSON.parse(data).t);
        super.send(data);
      }
    }
    const conn = new MainframeConn({ baseUrl: base(), token: "", share: { id: json.id, secret: json.secret }, WebSocketImpl: Recording as typeof WebSocket });
    conns.push(conn);
    conn.start();
    await waitFor(() => conn.status.value === "open");
    expect(conn.isShare).toBe(true);
    expect(conn.role.value).toBe("share");
    expect(conn.actionable).toBe(false);
    const power = conn.uplinks.value.find((u) => u.uplinkId === REACTOR)!.widgets.find((w) => w.id === "power")!;
    expect(conn.action(REACTOR, power, false, true)).toBeUndefined();
    await expect(conn.query(ME, "search", "iron")).rejects.toThrow();
    await expect(conn.saveLayout({ v: 1, pages: [], cards: {}, widgets: {} })).rejects.toThrow();
    await expect(conn.unlinkNode("relay-west")).rejects.toThrow();
    await sleep(200); // a few pings
    expect(new Set(sent)).toEqual(new Set(["auth_share", "pong"]));
  });

  it("takes the secret from the URL fragment and never stores it", () => {
    expect(takeShareSecret({ pathname: "/s/abc123", hash: "#S3cr3t-Secret_123456" })).toEqual({ id: "abc123", secret: "S3cr3t-Secret_123456", scrub: true });
    // after scrubbing the hash (reload): gone – the link has to be opened again
    expect(takeShareSecret({ pathname: "/s/abc123", hash: "" })).toEqual({ id: "abc123", secret: undefined, scrub: false });
    expect(takeShareSecret({ pathname: "/s/abc123", hash: "#<script>" }).secret).toBeUndefined();
    expect(takeShareSecret({ pathname: "/docs", hash: "#x" }).id).toBeUndefined();
    // leftovers of older builds are removed
    const data = new Map([["cccloud.share.abc123", "S3cr3t"], ["cccloud.other", "x"]]);
    const store = { get length() { return data.size; }, key: (i: number) => [...data.keys()][i] ?? null, removeItem: (k: string) => void data.delete(k) };
    forgetStoredShareSecrets(store);
    expect([...data.keys()]).toEqual(["cccloud.other"]);
  });

  it("serves /s/ pages with noindex and no-referrer", async () => {
    mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, staticDir: new URL("../../src/main/resources/cccloud_web", import.meta.url).pathname, log: () => {} });
    const r = await fetch(`${base()}/s/abc123`);
    expect(r.headers.get("x-robots-tag")).toBe("noindex");
    expect(r.headers.get("referrer-policy")).toBe("no-referrer");
  });
});
