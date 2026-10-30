// v0.10 in the mock: scenes (save rules, runs with delays and per-step results, PIN, permissions),
// schedules on rules, production statistics with pins, API key management and the public config.
import { afterEach, describe, expect, it } from "vitest";
import { createMockServer, COMPUTER, CONTROL, ME, REACTOR, type MockOptions, type MockServer } from "../dev/mock-server.ts";
import { MainframeConn } from "../web/src/lib/conn.ts";
import { sceneButtonId, sceneToWire, type SceneState } from "../web/src/lib/scenes.ts";
import { normalizeItemStats, normalizeOverview } from "../web/src/lib/production.ts";
import { createApiKey, getApiKeys, revokeApiKey, KEY_PATTERN } from "../web/src/lib/apikeys.ts";
import { parseConfig } from "../web/src/lib/config.ts";
import { toWire } from "../web/src/lib/rules.ts";
import { TestSocket, sleep } from "./helpers.ts";

let mock: MockServer | undefined;
const conns: MainframeConn[] = [];
const sockets: TestSocket[] = [];
afterEach(async () => {
  conns.splice(0).forEach((c) => c.stop());
  sockets.splice(0).forEach((s) => s.close());
  await mock?.close();
  mock = undefined;
});
const base = () => `http://127.0.0.1:${mock!.port}`;
async function start(opts: MockOptions = {}) {
  mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, actionDelayMs: 5, log: () => {}, ...opts });
}
const accountToken = async (player: string) =>
  (await (await fetch(`${base()}/dev/token`, { method: "POST", body: JSON.stringify({ account: player }) })).json()).token as string;
async function open(player: string) {
  const token = await accountToken(player);
  const conn = new MainframeConn({ baseUrl: base(), token, mainframeId: mock!.base.id } as never);
  conns.push(conn);
  conn.start();
  for (let i = 0; i < 300 && conn.status.value !== "open"; i++) await sleep(10);
  return { conn, token };
}
async function waitFor(pred: () => boolean, ms = 3000) {
  const end = Date.now() + ms;
  while (!pred()) {
    if (Date.now() > end) throw new Error("timeout");
    await sleep(10);
  }
}
/** Presses a scene's button like the dashboard does. */
function press(conn: MainframeConn, id: string, pin?: string) {
  const sectionId = conn.scenes.value!.sectionId!;
  const w = conn.uplinks.value.find((u) => u.uplinkId === sectionId)!.widgets.find((x) => x.id === sceneButtonId(id))!;
  return new Promise<{ ok: boolean; error?: string }>((resolve) => conn.action(sectionId, w, true, false, { ...(pin ? { pin } : {}), onResult: (r) => (resolve(r), true) }));
}
const sceneOf = (conn: MainframeConn, name: string) => conn.scenes.value!.scenes.find((s) => s.name === name) as SceneState;

describe("scenes (mock)", () => {
  it("publishes the scenes document and the Scenes section, never to share sessions", async () => {
    await start();
    const { conn, token } = await open("Enrico");
    const doc = conn.scenes.value!;
    expect(doc.scenes.map((s) => s.name)).toEqual(["Night mode", "Reactor safe", "Good morning", "Emergency"]);
    expect(doc.limit).toBe(32);
    expect(sceneOf(conn, "Emergency")).toMatchObject({ needsPin: true, owner: "Enrico" });
    expect(sceneOf(conn, "Night mode").perms.sort()).toEqual(["act.buttons", "act.redstone"]);
    const section = conn.uplinks.value.find((u) => u.uplinkId === doc.sectionId)!;
    expect(section.title).toBe("Scenes");
    expect(section.widgets.map((w) => [w.id, w.type, w.opts?.pin === true])).toEqual([
      ["s_4e1a9c20", "button", false],
      ["s_7b3f0d51", "button", false],
      ["s_c2d84e17", "button", false],
      ["s_e90a6b3c", "button", true],
    ]);
    // a share link that even contains the Scenes card gets no scenes document and cannot save
    const share = await (
      await fetch(`${base()}/api/mainframes/${mock!.base.id}/shares`, {
        method: "POST",
        headers: { authorization: `Bearer ${token}` },
        body: JSON.stringify({ label: "x", pages: [], cards: [REACTOR, doc.sectionId], expiresIn: 3600, maxViewers: 2, hideNumbers: false }),
      })
    ).json();
    const s = new TestSocket(`ws://127.0.0.1:${mock!.port}/ws/client`);
    sockets.push(s);
    await s.opened();
    s.send({ t: "auth_share", id: share.id, secret: share.secret });
    const snap = await s.next("snapshot");
    expect(snap.scenes).toBeUndefined();
    expect(snap.rules).toBeUndefined();
    s.send({ t: "scene_set", reqId: "r1", scene: { name: "x", steps: [{ type: "delay", seconds: 1 }] } });
    expect(await s.next("scene_result")).toMatchObject({ ok: false, error: "forbidden" });
    s.send({ t: "production", reqId: "r2", op: "overview" });
    expect(await s.next("production_result")).toMatchObject({ ok: false, error: "forbidden" });
    await conn.sceneOp({ t: "scene_set", scene: { name: "Ping", confirm: false, pin: false, steps: [{ type: "alert", text: "ping", level: "info" }] } });
    await sleep(60);
    expect(s.messages.some((m) => m.t === "scenes")).toBe(false);
  });

  it("validates, refuses steps the saver may not do, and deletes", async () => {
    await start();
    const { conn: owner } = await open("Enrico");
    const { id } = await owner.sceneOp({ t: "scene_set", scene: { name: "Lights", icon: "star", color: "green", confirm: false, pin: false, steps: [{ type: "redstone", uplinkId: COMPUTER, side: "top", value: 15 }] } });
    expect(id).toMatch(/^[0-9a-f]{8}$/);
    await waitFor(() => !!owner.scenes.value?.scenes.some((s) => s.id === id));
    await expect(owner.sceneOp({ t: "scene_set", scene: { name: "", confirm: false, pin: false, steps: [] } })).rejects.toMatchObject({ message: "invalid_scene", detail: "name" });
    await expect(owner.sceneOp({ t: "scene_set", scene: { name: "x", confirm: false, pin: false, steps: [{ type: "delay", seconds: 300 }, { type: "delay", seconds: 300 }, { type: "delay", seconds: 1 }] } })).rejects.toMatchObject({ detail: /600/ });
    await expect(owner.sceneOp({ t: "scene_set", scene: { name: "x", confirm: false, pin: false, steps: [{ type: "scene" }] } as never })).rejects.toMatchObject({ message: "invalid_scene" });
    // Lisa (admin) without act.redstone: saving a redstone step names the step and the permission
    const tok = await accountToken("Enrico");
    await fetch(`${base()}/api/mainframes/${mock!.base.id}/access`, { method: "POST", headers: { authorization: `Bearer ${tok}` }, body: JSON.stringify({ op: "set_perms", player: "Lisa", allow: [], deny: ["act.redstone"] }) });
    const { conn: lisa } = await open("Lisa");
    await expect(lisa.sceneOp({ t: "scene_set", scene: { name: "Y", confirm: false, pin: false, steps: [{ type: "alert", text: "a", level: "info" }, { type: "redstone", uplinkId: COMPUTER, side: "top", value: 1 }] } })).rejects.toMatchObject({ message: "not_allowed", detail: "step 2: act.redstone" });
    // an operator may run but not edit; a viewer neither
    const { conn: tom } = await open("Tom");
    await expect(tom.sceneOp({ t: "scene_delete", id: id! })).rejects.toThrow("not_allowed");
    const { conn: sam } = await open("Sam");
    await expect(sam.sceneOp({ t: "scene_delete", id: id! })).rejects.toThrow("read_only");
    await owner.sceneOp({ t: "scene_delete", id: id! });
    await expect(owner.sceneOp({ t: "scene_delete", id: id! })).rejects.toThrow("unknown_scene");
  });

  it("runs the steps in order with delays and reports each step live", async () => {
    await start();
    const { conn } = await open("Enrico");
    const { id } = await conn.sceneOp({
      t: "scene_set",
      scene: {
        name: "Quick",
        confirm: false,
        pin: false,
        steps: [
          { type: "widget", uplinkId: CONTROL, widgetId: "mode", value: "boost" },
          { type: "delay", seconds: 0.2 },
          { type: "redstone", uplinkId: COMPUTER, side: "top", value: 9 },
          { type: "widget", uplinkId: CONTROL, widgetId: "gone", value: true },
          { type: "alert", text: "done", level: "info" },
        ],
      },
    });
    await waitFor(() => !!conn.scenes.value?.scenes.some((s) => s.id === id) && !!conn.uplinks.value.find((u) => u.uplinkId === conn.scenes.value!.sectionId)?.widgets.some((w) => w.id === `s_${id}`));
    expect(await press(conn, id!)).toMatchObject({ ok: true });
    // waiting in the delay: step 1 done, the rest still open
    await waitFor(() => sceneOf(conn, "Quick").lastRun?.steps[0]?.ok === true);
    const mid = sceneOf(conn, "Quick").lastRun!;
    expect(mid.finished).toBeNull();
    expect(mid.steps.slice(1)).toEqual([null, null, null, null]);
    expect(await press(conn, id!)).toMatchObject({ ok: false, error: "busy" });
    await waitFor(() => sceneOf(conn, "Quick").lastRun?.finished != null);
    const run = sceneOf(conn, "Quick").lastRun!;
    expect(run.steps).toEqual([{ ok: true }, { ok: true }, { ok: true }, { ok: false, error: "unknown_widget" }, { ok: true }]);
    expect(run).toMatchObject({ by: "Enrico", source: "dashboard" });
    expect(sceneOf(conn, "Quick").runs).toBe(1);
    expect(conn.scenes.value!.log.at(-1)).toMatchObject({ name: "Quick", result: "unknown_widget", by: "Enrico" });
    expect(conn.uplinks.value.find((u) => u.uplinkId === CONTROL)!.widgets.find((w) => w.id === "mode")!.value).toBe("boost");
    expect(conn.alerts.value.some((a) => a.text === "done")).toBe(true);
  });

  it("asks for the PIN, checks each step with the presser's rights, and keeps PIN scenes out of rules", async () => {
    await start();
    const { conn } = await open("Enrico");
    expect(await press(conn, "e90a6b3c")).toMatchObject({ ok: false, error: "pin_required" });
    expect(await press(conn, "e90a6b3c", "9999")).toMatchObject({ ok: false, error: "wrong_pin" });
    expect(await press(conn, "e90a6b3c", "1234")).toMatchObject({ ok: true });
    await waitFor(() => sceneOf(conn, "Emergency").lastRun?.finished != null);
    expect(sceneOf(conn, "Emergency").lastRun!.steps.every((x) => x?.ok)).toBe(true);
    // Tom (operator) may press "Night mode"; its steps need act.buttons/act.redstone, which operators have
    const { conn: tom } = await open("Tom");
    expect(await press(tom, "4e1a9c20")).toMatchObject({ ok: true });
    await waitFor(() => sceneOf(tom, "Night mode").lastRun?.by === "Tom" && sceneOf(tom, "Night mode").lastRun?.finished != null, 5000);
    expect(sceneOf(tom, "Night mode").lastRun!.steps.every((x) => x?.ok)).toBe(true);
    // a viewer cannot press at all
    const { conn: sam } = await open("Sam");
    const sec = sam.scenes.value!.sectionId!;
    const w = sam.uplinks.value.find((u) => u.uplinkId === sec)!.widgets[0];
    const r = await new Promise<{ ok: boolean; error?: string }>((res) => {
      if (!sam.action(sec, w, true, false, { onResult: (x) => (res(x), true) })) res({ ok: false, error: "read_only" });
    });
    expect(r).toMatchObject({ ok: false, error: "read_only" });
    // a rule may not run a scene that needs the PIN
    await expect(conn.ruleOp({ t: "rule_set", rule: toWire({ name: "Bad", enabled: true, cooldown: 30, notify: false, when: { type: "interval", minutes: 5 }, if: [], then: [{ type: "scene", sceneId: "e90a6b3c" }] }) })).rejects.toThrow("pin_widget");
    // a rule runs a normal scene with its owner's rights
    const { id } = await conn.ruleOp({ t: "rule_set", rule: toWire({ name: "Run morning", enabled: true, cooldown: 30, notify: false, when: { type: "interval", minutes: 5 }, if: [], then: [{ type: "scene", sceneId: "c2d84e17" }] }) });
    await conn.ruleOp({ t: "rule_run", id: id! });
    await waitFor(() => sceneOf(conn, "Good morning").lastRun?.source === "rule" && sceneOf(conn, "Good morning").lastRun?.finished != null && sceneOf(conn, "Good morning").runs === 23);
  });
});

describe("schedules (mock)", () => {
  it("accepts schedule triggers, reports nextRun and the limits", async () => {
    await start({ maxSchedules: 3, minIntervalMinutes: 5, timezone: "Europe/Berlin" });
    const { conn } = await open("Enrico");
    const doc = conn.rules.value!;
    expect(doc.schedules).toEqual({ count: 3, limit: 3, minInterval: 5, timezone: "Europe/Berlin" });
    const morning = doc.rules.find((r) => r.name === "Morning routine")!;
    expect(morning.nextRun).toBeGreaterThan(Date.now());
    expect(new Date(morning.nextRun!).toLocaleString("en-GB", { timeZone: "Europe/Berlin", hour: "2-digit", minute: "2-digit" })).toBe("07:00");
    expect(doc.rules.find((r) => r.name === "Coolant check")!.nextRun).toBeGreaterThan(Date.now());
    expect(doc.rules.find((r) => r.name === "Night lights")!.nextRun).toBeNull(); // in-game time
    expect(doc.log.some((e) => e.skipped && e.result === "skipped_unloaded")).toBe(true);
    // the fourth time-based rule is refused, a value rule is fine, too short intervals are invalid
    const rule = (when: object) => toWire({ name: "x", enabled: true, cooldown: 30, notify: false, when: when as never, if: [], then: [{ type: "alert", text: "x", level: "info" }] });
    await expect(conn.ruleOp({ t: "rule_set", rule: rule({ type: "schedule", days: ["sat"], at: "09:00" }) })).rejects.toThrow("too_many_schedules");
    await expect(conn.ruleOp({ t: "rule_set", rule: rule({ type: "interval", minutes: 2 }) })).rejects.toMatchObject({ message: "invalid_rule", detail: /interval/ });
    await expect(conn.ruleOp({ t: "rule_set", rule: rule({ type: "schedule", days: ["mon", "mon"], at: "09:00" }) })).rejects.toMatchObject({ message: "invalid_rule", detail: /schedule/ });
    // changing an existing schedule keeps its slot
    const r = doc.rules.find((x) => x.name === "Morning routine")!;
    await conn.ruleOp({ t: "rule_set", rule: toWire({ ...r, when: { type: "schedule", days: ["sat", "sun"], at: "09:30" } }) });
    await waitFor(() => conn.rules.value!.rules.find((x) => x.id === r.id)!.when.type === "schedule" && (conn.rules.value!.rules.find((x) => x.id === r.id)!.when as { at: string }).at === "09:30");
  });
});

describe("production (mock)", () => {
  it("answers the overview with storages, energy and pins, and samples live", async () => {
    await start();
    const { conn } = await open("Enrico");
    const ov = normalizeOverview(await conn.production("overview"));
    expect(ov).toMatchObject({ interval: 20, window: 60, pinLimit: 16 });
    const me = ov.storages.find((s) => s.uplinkId === ME)!;
    expect(me.samples).toBe(54);
    expect(me.series.gained).toHaveLength(54);
    expect(me.consumers.map((c) => c.name)).toContain("minecraft:iron_ingot");
    expect(me.gained).toBeGreaterThan(0);
    expect(ov.energy!.cards.map((c) => c.title)).toEqual(["Induction Matrix", "Fission Reactor", "Storage cell"]);
    expect(ov.energy!.cards[2].measured).toBe(true);
    expect(ov.pins.map((p) => p.label)).toEqual(["Iron Ingot /min", "Energy in/out FE/t"]);
    // the Production section: one chart per pin
    const section = conn.uplinks.value.find((u) => u.title === "Production")!;
    expect(section.widgets.map((w) => [w.type, w.label])).toEqual([
      ["chart", "Iron Ingot /min"],
      ["chart", "Energy in/out FE/t"],
    ]);
    expect(section.widgets[0].history!.length).toBe(54);
    // two samples later the window has moved
    mock!.sampleProduction();
    mock!.sampleProduction();
    const ov2 = normalizeOverview(await conn.production("overview"));
    expect(ov2.storages[0].samples).toBe(55);
    await waitFor(() => conn.uplinks.value.find((u) => u.title === "Production")!.widgets[0].history!.length === 55);
  });

  it("drills into one item, pins and unpins with permissions", async () => {
    await start();
    const { conn } = await open("Enrico");
    const it = normalizeItemStats(await conn.production("item", { uplinkId: ME, name: "minecraft:iron_ingot" }), "minecraft:iron_ingot");
    expect(it).toMatchObject({ name: "minecraft:iron_ingot", displayName: "Iron Ingot" });
    expect(it.consumed).toBeGreaterThan(it.gained);
    expect(it.series.net).toHaveLength(54);
    expect(it.history).toHaveLength(288); // watched item
    expect(it.tasks[0]).toMatchObject({ name: "minecraft:iron_ingot", count: 2048 });
    const still = normalizeItemStats(await conn.production("item", { uplinkId: ME, name: "minecraft:diamond" }), "minecraft:diamond");
    expect(still).toMatchObject({ gained: 0, consumed: 0, net: 0 });
    await expect(conn.production("item", { uplinkId: ME, name: "minecraft:nope" })).rejects.toThrow("unknown_item");
    await expect(conn.production("item", { uplinkId: REACTOR, name: "minecraft:iron_ingot" })).rejects.toThrow("unknown_storage");
    // 5 requests per second per socket
    await conn.production("overview");
    await expect(conn.production("overview")).rejects.toThrow("rate_limited");
    await sleep(1050);
    const { id } = await conn.production<{ id: string }>("pin", { kind: "storage", uplinkId: ME });
    await waitFor(() => !!conn.uplinks.value.find((u) => u.title === "Production")?.widgets.some((w) => w.id === `p_${id}` && w.label === "ME Network net /min"));
    // the same pin again answers the same id
    expect((await conn.production<{ id: string }>("pin", { kind: "storage", uplinkId: ME })).id).toBe(id);
    await conn.production("unpin", { id });
    await expect(conn.production("unpin", { id })).rejects.toThrow("unknown_pin");
    // a viewer sees no storages and cannot pin; an operator cannot pin either (layout.edit)
    const { conn: sam } = await open("Sam");
    const ov = normalizeOverview(await sam.production("overview"));
    expect(ov.storages.length).toBe(1); // viewers have storage.search
    await expect(sam.production("pin", { kind: "energy" })).rejects.toThrow("read_only");
    const { conn: tom } = await open("Tom");
    await expect(tom.production("pin", { kind: "energy" })).rejects.toThrow("not_allowed");
  });

  it("is switched off with productionEnabled=false", async () => {
    await start({ production: false });
    const { conn } = await open("Enrico");
    await expect(conn.production("overview")).rejects.toThrow("disabled");
    expect(conn.uplinks.value.some((u) => u.title === "Production")).toBe(false);
    const cfg = parseConfig(await (await fetch(`${base()}/api/config`)).json());
    expect(cfg.features).toMatchObject({ production: false, scenes: true, api: true });
  });
});

describe("API keys (mock)", () => {
  it("creates a key once, lists, revokes and moderates", async () => {
    await start();
    const mf = mock!.base.id;
    const enrico = await accountToken("Enrico");
    const doc = await getApiKeys(base(), enrico, mf);
    expect(doc).toMatchObject({ enabled: true, limit: 5 });
    expect(doc.keys.map((k) => [k.name, k.own])).toEqual([
      ["Discord bot", true],
      ["Home Assistant", true],
      ["Stock sheet", false],
    ]);
    const made = await createApiKey(base(), enrico, mf, { name: "  Bot 2 ", scopes: ["read:sections", "run:scenes"], expiresIn: 86400, ips: ["192.168.1.0/24"] });
    expect(made.key).toMatch(KEY_PATTERN);
    const after = await getApiKeys(base(), enrico, mf);
    const row = after.keys.find((k) => k.id === made.id)!;
    expect(row).toMatchObject({ name: "Bot 2", player: "Enrico", scopes: ["read:sections", "run:scenes"], ips: ["192.168.1.0/24"], lastUsed: null, own: true });
    expect(row.expires! - row.created).toBe(86_400_000);
    // the key itself is never listed and only its hash is kept
    expect(JSON.stringify(after)).not.toContain(made.key);
    expect(JSON.stringify([...mock!.apiKeys.values()])).not.toContain(made.key);
    await expect(createApiKey(base(), enrico, mf, { name: "x", scopes: [], expiresIn: null, ips: [] })).rejects.toThrow("invalid");
    await expect(createApiKey(base(), enrico, mf, { name: "x", scopes: ["admin" as never], expiresIn: null, ips: [] })).rejects.toThrow("invalid");
    await expect(createApiKey(base(), enrico, mf, { name: "x", scopes: ["read:stats"], expiresIn: 5 as never, ips: [] })).rejects.toThrow("invalid");
    await expect(createApiKey(base(), enrico, mf, { name: "x", scopes: ["read:stats"], expiresIn: null, ips: ["nope"] })).rejects.toThrow("invalid");
    expect(after.keys.find((k) => k.name === "Stock sheet")!.expired).toBe(true);
    // Enrico moderates Lisa's key (access.manage); Lisa may not revoke Enrico's after losing access.manage
    await revokeApiKey(base(), enrico, mf, after.keys.find((k) => k.name === "Stock sheet")!.id);
    await fetch(`${base()}/api/mainframes/${mf}/access`, { method: "POST", headers: { authorization: `Bearer ${enrico}` }, body: JSON.stringify({ op: "set_perms", player: "Lisa", allow: [], deny: ["access.manage"] }) });
    const lisa = await accountToken("Lisa");
    expect((await getApiKeys(base(), lisa, mf)).keys).toEqual([]);
    await expect(revokeApiKey(base(), lisa, mf, made.id)).rejects.toThrow("not_allowed");
    // five keys per player
    for (let i = 0; i < 2; i++) await createApiKey(base(), enrico, mf, { name: `k${i}`, scopes: ["read:stats"], expiresIn: null, ips: [] });
    await expect(createApiKey(base(), enrico, mf, { name: "six", scopes: ["read:stats"], expiresIn: null, ips: [] })).rejects.toThrow("too_many");
    // operators lack api.keys; a key never gets a scope its creator could not use
    await expect(getApiKeys(base(), await accountToken("Tom"), mf)).rejects.toThrow("not_allowed");
    await fetch(`${base()}/api/mainframes/${mf}/access`, { method: "POST", headers: { authorization: `Bearer ${enrico}` }, body: JSON.stringify({ op: "set_perms", player: "Lisa", allow: [], deny: ["act.buttons"] }) });
    await expect(createApiKey(base(), lisa, mf, { name: "x", scopes: ["run:scenes"], expiresIn: null, ips: [] })).rejects.toThrow("not_allowed");
    expect((await createApiKey(base(), lisa, mf, { name: "x", scopes: ["read:storage"], expiresIn: null, ips: [] })).key).toMatch(KEY_PATTERN);
  });

  it("explains a switched-off API", async () => {
    await start({ apiEnabled: false });
    const enrico = await accountToken("Enrico");
    expect((await getApiKeys(base(), enrico, mock!.base.id)).enabled).toBe(false);
    await expect(createApiKey(base(), enrico, mock!.base.id, { name: "x", scopes: ["read:stats"], expiresIn: null, ips: [] })).rejects.toThrow("disabled");
  });
});

void sceneToWire;
