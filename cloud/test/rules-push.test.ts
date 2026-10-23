// v0.5 C rules and D push targets: client helpers and the mock protocol.
import { afterEach, describe, expect, it } from "vitest";
import { createMockServer, REACTOR, type MockServer } from "../dev/mock-server.ts";
import { MainframeConn } from "../web/src/lib/conn.ts";
import { clockToTicks, describeAction, describeCheck, newRule, normalizeRules, ticksToClock, toWire, validateRule, type Rule } from "../web/src/lib/rules.ts";
import { getPush, parsePush, postPush, urlProblem } from "../web/src/lib/push.ts";
import { sleep } from "./helpers.ts";

let mock: MockServer | undefined;
const conns: MainframeConn[] = [];
afterEach(async () => {
  conns.splice(0).forEach((c) => c.stop());
  await mock?.close();
  mock = undefined;
});
const base = () => `http://127.0.0.1:${mock!.port}`;
const accountToken = async (player: string) =>
  (await (await fetch(`${base()}/dev/token`, { method: "POST", body: JSON.stringify({ account: player }) })).json()).token as string;
async function open(player: string) {
  const conn = new MainframeConn({ baseUrl: base(), token: await accountToken(player), mainframeId: mock!.base.id } as never);
  conns.push(conn);
  conn.start();
  while (conn.status.value !== "open") await sleep(10);
  return conn;
}

const UPS = [
  { uplinkId: "r", title: "Reactor", online: true, widgets: [{ id: "temp", type: "gauge" as const, label: "Core", opts: { unit: "K" } }, { id: "power", type: "toggle" as const, label: "Power" }] },
];

describe("rule helpers", () => {
  it("converts in-game time both ways", () => {
    expect(ticksToClock(0)).toBe("06:00");
    expect(ticksToClock(18000)).toBe("00:00");
    expect(clockToTicks("06:00")).toBe(0);
    expect(clockToTicks("19:30")).toBe(13500);
    expect(clockToTicks("25:00")).toBeNull();
  });

  it("validates like the server and describes rules in plain words", () => {
    const r: Rule = { ...newRule(UPS), name: "Hot", when: { type: "widget", uplinkId: "r", widgetId: "temp", op: ">", value: 900 }, then: [{ type: "widget", uplinkId: "r", widgetId: "power", value: false }] };
    expect(validateRule(r, UPS)).toBeNull();
    expect(validateRule({ ...r, name: "" }, UPS)).toMatch(/name/);
    expect(validateRule({ ...r, cooldown: 2 }, UPS)).toMatch(/Cooldown/);
    expect(validateRule({ ...r, then: [{ type: "widget", uplinkId: "r", widgetId: "temp", value: 1 }] }, UPS)).toMatch(/Loop/);
    expect(validateRule({ ...r, then: [{ type: "craft", uplinkId: "r", item: "iron", amount: 1 }] }, UPS)).toMatch(/item id/);
    expect(describeCheck(r.when, UPS)).toBe("Core (Reactor) above 900");
    expect(describeAction(r.then[0], UPS)).toBe("turn Power off (Reactor)");
    expect(describeCheck({ type: "interval", minutes: 120 }, UPS)).toBe("every 2 h");
    expect(Object.keys(toWire({ ...r, id: "0a1b2c3d" }))).toEqual(["id", "name", "enabled", "cooldown", "notify", "when", "if", "then"]);
    expect(normalizeRules({ rules: [{ id: "XYZ", when: {}, then: [] }], log: [], limit: 8 })!.rules).toEqual([]);
  });

  it("checks push URLs on the client", () => {
    expect(urlProblem("discord", "https://discord.com/api/webhooks/1/x")).toBeNull();
    expect(urlProblem("discord", "https://evil.example/api/webhooks/1")).toMatch(/Discord/);
    expect(urlProblem("webhook", "http://example.com/x")).toMatch(/https/);
    expect(urlProblem("ntfy", "http://ntfy.lan/topic")).toBeNull();
    expect(urlProblem("webhook", "https://u:p@example.com/")).toMatch(/password/);
    expect(parsePush({ targets: [{ id: "a", type: "sms" }] }).targets).toEqual([]);
  });
});

describe("rules protocol (mock)", () => {
  it("saves, toggles, runs and deletes with permissions", async () => {
    mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, log: () => {} });
    const owner = await open("Enrico");
    expect(owner.rules.value?.rules.map((r) => r.name)).toEqual(["Cool the reactor", "Night lights"]);
    expect(owner.rules.value?.limit).toBe(64);
    const { id } = await owner.ruleOp({
      t: "rule_set",
      rule: toWire({ name: "Ping", enabled: true, cooldown: 10, notify: false, when: { type: "interval", minutes: 5 }, if: [], then: [{ type: "alert", text: "Ping", level: "info" }] }),
    });
    expect(id).toMatch(/^[0-9a-f]{8}$/);
    await sleep(30);
    expect(owner.rules.value?.rules.some((r) => r.id === id)).toBe(true);
    await expect(owner.ruleOp({ t: "rule_set", rule: { name: "x", when: { type: "nope" }, then: [] } as never })).rejects.toMatchObject({ message: "invalid_rule" });
    await owner.ruleOp({ t: "rule_run", id: id! });
    await sleep(30);
    expect(owner.rules.value?.rules.find((r) => r.id === id)).toMatchObject({ runs: 1, lastResult: "ok" });
    expect(owner.rules.value?.log.at(-1)).toMatchObject({ name: "Ping", manual: true });
    // an operator may toggle and run, not edit
    const tom = await open("Tom");
    await tom.ruleOp({ t: "rule_enable", id: id!, enabled: false });
    await expect(tom.ruleOp({ t: "rule_delete", id: id! })).rejects.toThrow("not_allowed");
    // a viewer may do neither
    const sam = await open("Sam");
    await expect(sam.ruleOp({ t: "rule_enable", id: id!, enabled: true })).rejects.toThrow("read_only");
    await owner.ruleOp({ t: "rule_delete", id: id! });
    await sleep(30);
    expect(owner.rules.value?.rules.some((r) => r.id === id)).toBe(false);
    void REACTOR;
  });
});

describe("push API (mock)", () => {
  it("adds, masks, tests and refuses unsafe targets", async () => {
    mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, log: () => {} });
    const mf = mock.base.id;
    const tok = await accountToken("Enrico");
    const d = await getPush(base(), tok, mf);
    expect(d.targets[0].url).toMatch(/^https:\/\/discord\.com\/…/);
    const added = await postPush(base(), tok, mf, { op: "add", target: { type: "ntfy", url: "https://ntfy.sh/secret-topic-1234", token: "tk_abc" } });
    const t = added.targets.find((x) => x.type === "ntfy")!;
    expect(t).toMatchObject({ hasToken: true, minLevel: "warn", events: ["alerts", "rules", "power"] });
    expect(JSON.stringify(added)).not.toContain("tk_abc");
    expect(JSON.stringify(added)).not.toContain("secret-topic");
    await expect(postPush(base(), tok, mf, { op: "add", target: { type: "webhook", url: "http://example.com/x" } })).rejects.toThrow("https_only");
    await expect(postPush(base(), tok, mf, { op: "add", target: { type: "webhook", url: "https://192.168.1.2/x" } })).rejects.toThrow("host_not_allowed");
    await expect(postPush(base(), tok, mf, { op: "add", target: { type: "discord", url: "https://example.com/api/webhooks/1" } })).rejects.toThrow("invalid_url");
    await postPush(base(), tok, mf, { op: "test", id: t.id });
    await sleep(400);
    expect((await getPush(base(), tok, mf)).targets.find((x) => x.id === t.id)?.lastResult).toBe("ok");
    const upd = await postPush(base(), tok, mf, { op: "update", id: t.id, target: { token: "" } });
    expect(upd.targets.find((x) => x.id === t.id)?.hasToken).toBe(false);
    await expect(getPush(base(), await accountToken("Tom"), mf)).rejects.toThrow("not_allowed");
  });
});
