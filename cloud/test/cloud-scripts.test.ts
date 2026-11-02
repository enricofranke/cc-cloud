// Cloud Scripts on the dashboard side: the descriptor turned into completions, hover docs and signatures, error
// markers, the log buffer, line diffs, and the `code` protocol of MainframeConn against the mock (fengari runs Lua).
import { afterEach, describe, expect, it } from "vitest";
import API_JSON from "../../src/main/resources/cccloud_code/api.json" with { type: "json" };
import { completionsAt, hoverAt, luaStateAt, maskLua, membersOf, removedUses, signatureAt, signature, type ApiDescriptor } from "../web/src/code/api.ts";
import { appendLog, errorLine, parseDiagnostics, parseLimits, parseLogLines, parseSummary, DEFAULT_LIMITS, type LogLine } from "../web/src/lib/code.ts";
import { byteSize, cleanName, diffLines, diffStats, foldDiff, markersFor, uniqueName, visibleLog } from "../web/src/code/ide.ts";
import { createMockServer, type MockOptions, type MockServer } from "../dev/mock-server.ts";
import { MainframeConn } from "../web/src/lib/conn.ts";
import { PERMISSIONS, PERM_INFO, presetHas } from "../web/src/lib/permissions.ts";
import { sleep } from "./helpers.ts";

const API = API_JSON as unknown as ApiDescriptor;
/** text with a | marking the cursor */
const at = (s: string) => ({ text: s.replace("|", ""), pos: s.indexOf("|") });

describe("descriptor → editor help", () => {
  it("completes cloud., cloud.store., libraries and the top level", () => {
    const c = at("local v = cloud.ge|");
    const r = completionsAt(API, "en", c.text, c.pos)!;
    expect(r.from).toBe(c.pos - 2);
    const labels = r.options.map((o) => o.label);
    expect(labels).toEqual(expect.arrayContaining(["getValue", "getCards", "press", "on", "store", "script"]));
    expect(labels).not.toContain("store.get");
    const store = at("cloud.store.|");
    expect(completionsAt(API, "en", store.text, store.pos)!.options.map((o) => o.label).sort()).toEqual(["delete", "get", "keys", "set"]);
    const str = at("string.fo|");
    expect(completionsAt(API, "en", str.text, str.pos)!.options.map((o) => o.label)).toContain("format");
    const method = at('("%d"):|');
    expect(completionsAt(API, "en", method.text, method.pos)!.options.map((o) => o.label)).toContain("rep");
    const top = membersOf(API, "", "en").map((o) => o.label);
    expect(top).toEqual(expect.arrayContaining(["cloud", "print", "pairs", "math", "local", "function"]));
    // io, debug and require are gone from the sandbox and never offered
    expect(top).not.toContain("io");
    expect(top).not.toContain("require");
    // the fields of cloud.script
    const script = at("cloud.script.|");
    expect(completionsAt(API, "en", script.text, script.pos)!.options.map((o) => o.label)).toEqual(expect.arrayContaining(["name", "author", "version"]));
  });

  it("offers event names inside cloud.on(\"…\") and nothing in comments or other strings", () => {
    const ev = at('cloud.on("ab|');
    const r = completionsAt(API, "en", ev.text, ev.pos)!;
    expect(r.options.map((o) => o.label)).toEqual(["change", "above", "below", "online", "offline", "redstone"]);
    expect(r.from).toBe(ev.pos - 2);
    const comment = at("-- cloud.ge|");
    expect(completionsAt(API, "en", comment.text, comment.pos)).toBeNull();
    const str = at('print("cloud.ge|');
    expect(completionsAt(API, "en", str.text, str.pos)).toBeNull();
    expect(luaStateAt("x = [[long\nstring", 16)).toBe("string");
    expect(luaStateAt("--[[ a\nb ]] x", 13)).toBe("code");
    expect(maskLua('a("(,") -- (,')).toBe('a("  ")      ');
  });

  it("hover docs and signatures come from api.json in English and German", () => {
    const text = 'local ok, err = cloud.press("Doors", "open")';
    const h = hoverAt(API, "en", text, text.indexOf("press") + 2)!;
    expect(h.signature).toBe("cloud.press(card, widget, options?) → boolean, string?");
    expect(h.perm).toBe("act.buttons");
    expect(h.kind).toBe("await");
    expect(h.doc).toMatch(/Presses a button/);
    expect(h.params[2]).toMatchObject({ name: "options", type: "table?" });
    expect(hoverAt(API, "de", text, text.indexOf("press") + 2)!.doc).toMatch(/Drückt einen Knopf/);
    // cloud itself, a store function, a library function, a removed name
    expect(hoverAt(API, "en", text, text.indexOf("cloud") + 1)!.doc).toMatch(/Cloud Scripts API/);
    expect(hoverAt(API, "en", "cloud.store.get('x')", 13)!.name).toBe("cloud.store.get");
    expect(hoverAt(API, "en", "string.format('%d', 1)", 9)!.doc).toBe("Lua standard library");
    expect(hoverAt(API, "de", "io.write(1)", 1)!.doc).toMatch(/Sandbox/);
    expect(hoverAt(API, "en", '-- cloud.press', 10)).toBeNull();
    // aliases keep the target's permission
    expect(signature(API, API.functions.find((f) => f.name === "alert")!)).toBe("cloud.alert(text, level?) → boolean");
  });

  it("finds the cloud.* call and argument under the cursor", () => {
    const a = at('cloud.set("Reactor", "power", |');
    expect(signatureAt(API, a.text, a.pos)).toMatchObject({ fn: { name: "set" }, param: 2, open: 9 });
    // commas inside strings and tables do not count
    const b = at('cloud.on("above", {card = "a,b", widget = "x"}, |');
    expect(signatureAt(API, b.text, b.pos)!.param).toBe(2);
    const inTable = at('cloud.on("above", {card = "x", |');
    expect(signatureAt(API, inTable.text, inTable.pos)!.param).toBe(1);
    const inner = at('cloud.log("x", cloud.getValue("R", |))');
    expect(signatureAt(API, inner.text, inner.pos)!.fn.name).toBe("getValue");
    // varargs stay on the last parameter
    const va = at('cloud.log(1, 2, 3, |');
    expect(signatureAt(API, va.text, va.pos)!.param).toBe(0);
    // a handler body is not the call's argument list any more
    const body = at('cloud.on("change", function(e)\n  local x = |');
    expect(signatureAt(API, body.text, body.pos)).toBeNull();
    expect(signatureAt(API, ...(Object.values(at('print(|')) as [string, number]))).toBeNull();
  });

  it("marks names the sandbox removed, outside strings and comments", () => {
    const src = 'local f = io.open("x")\nlocal io = 1\nprint("require")\nrequire("x")\nos.execute("ls")\nos.time()\n-- debug.traceback()';
    expect(removedUses(API, src).map((u) => u.name)).toEqual(["io", "require", "os.execute"]);
  });
});

describe("IDE logic", () => {
  it("normalises summaries, limits, diagnostics and log lines defensively", () => {
    const s = parseSummary({ id: "a1", name: "Guard", status: "weird", cpu: { ms: 3, avg: 2.5 }, lastError: { message: "Guard:12: attempt to call a nil value", time: 5 }, logLines: 4.7 })!;
    expect(s).toMatchObject({ status: "stopped", cpu: { ms: 3, avg: 2.5, limit: 50 }, lastError: { line: 12 }, logLines: 4, autostart: false });
    expect(parseSummary({ name: "x" })).toBeNull();
    expect(parseLimits({ maxScripts: 4, cpuMsPerSecond: "x" })).toEqual({ ...DEFAULT_LIMITS, maxScripts: 4 });
    expect(parseDiagnostics([{ message: "x:3: 'end' expected near <eof>" }, { line: 2, column: 5, message: "w", severity: "warning" }, null])).toEqual([
      { line: 3, column: null, message: "x:3: 'end' expected near <eof>", severity: "error" },
      { line: 2, column: 5, message: "w", severity: "warning" },
    ]);
    expect(parseLogLines([{ ts: 1, level: "bogus", text: "a" }, { text: 5 }])).toEqual([{ ts: 1, level: "info", text: "a" }]);
    expect(errorLine("night_mode:12: attempt to index a nil value")).toBe(12);
    expect(errorLine("no line here")).toBeNull();
  });

  it("maps diagnostics and the runtime error to editor ranges", () => {
    const text = "local a = 1\n  print(a.b.c)\n\nend";
    const m = markersFor(text, [{ line: 4, column: null, message: "x:4: '<eof>' expected near 'end'", severity: "error" }, { line: 2, column: 9, message: "w", severity: "warning" }], { message: "x:2: attempt to index a number value", line: 2, time: 1 }, true);
    expect(m[0]).toMatchObject({ from: text.indexOf("end"), to: text.length, message: "'<eof>' expected near 'end'", source: "check" });
    // a column points at the word there
    expect(text.slice(m[1].from, m[1].to)).toBe("a.b.c");
    // the runtime error marks its line without the indentation
    expect(text.slice(m[2].from, m[2].to)).toBe("print(a.b.c)");
    // out of range lines are clamped; after an edit the runtime error is left out
    expect(markersFor("x", [{ line: 9, column: null, message: "m", severity: "error" }], { message: "x:1: e", line: 1, time: 0 }, false)).toEqual([{ from: 0, to: 1, severity: "error", message: "m", source: "check" }]);
    // an empty line gets a zero-width marker
    expect(markersFor("a\n\nb", [{ line: 2, column: null, message: "m", severity: "error" }], null, true)[0]).toMatchObject({ from: 2, to: 2 });
  });

  it("keeps the newest 200 log lines and clears only the view", () => {
    const lines = (n: number, from = 0): LogLine[] => Array.from({ length: n }, (_, i) => ({ ts: from + i, level: "info", text: `l${from + i}` }));
    const a = appendLog(lines(150), lines(80, 150));
    expect(a).toHaveLength(200);
    expect(a[0].text).toBe("l30");
    expect(a[199].text).toBe("l229");
    expect(appendLog(a, [])).toBe(a);
    expect(visibleLog(a, 220).map((l) => l.text)).toEqual(["l221", "l222", "l223", "l224", "l225", "l226", "l227", "l228", "l229"]);
  });

  it("diffs lines and folds long unchanged runs", () => {
    const a = ["a", "b", "c", "d", "e", "f", "g", "h", "i", "j"].join("\n");
    const b = ["a", "b", "C", "d", "e", "f", "g", "h", "i", "j", "k"].join("\n");
    const d = diffLines(a, b);
    expect(diffStats(d)).toEqual({ add: 2, del: 1 });
    expect(d.filter((l) => l.kind !== "same").map((l) => `${l.kind}:${l.text}:${l.a ?? "-"}:${l.b ?? "-"}`)).toEqual(["add:C:-:3", "del:c:3:-", "add:k:-:11"]);
    const rows = foldDiff(d, 2);
    expect(rows.map((r) => (r.kind === "skip" ? `skip ${r.count}` : r.kind))).toEqual(["same", "same", "add", "del", "same", "same", "skip 3", "same", "same", "add"]);
    expect(diffLines("same\n", "same")).toEqual([{ kind: "same", text: "same", a: 1, b: 1 }]);
    expect(diffStats(diffLines("", "x\ny"))).toEqual({ add: 2, del: 0 });
  });

  it("counts UTF-8 bytes and checks names", () => {
    expect(byteSize("aä€😀")).toBe(1 + 2 + 3 + 4);
    expect(cleanName("  Reactor guard ")).toBe("Reactor guard");
    expect(cleanName("")).toBeNull();
    expect(cleanName("x".repeat(33))).toBeNull();
    expect(cleanName("a\u0007")).toBeNull();
    expect(uniqueName("Night mode", ["night MODE", "Night mode 2"])).toBe("Night mode 3");
  });

  it("knows code.edit as a risky admin permission", () => {
    expect(PERMISSIONS).toContain("code.edit");
    expect(PERM_INFO["code.edit"]).toMatchObject({ label: "Cloud Scripts", group: "scripts", risky: true });
    expect(presetHas("admin", "code.edit")).toBe(true);
    expect(presetHas("owner", "code.edit")).toBe(true);
    expect(presetHas("operator", "code.edit")).toBe(false);
    expect(presetHas("viewer", "code.edit")).toBe(false);
  });
});

// ------------------------------------------------------------------ protocol against the mock

let mock: MockServer | undefined;
const conns: MainframeConn[] = [];
afterEach(async () => {
  conns.splice(0).forEach((c) => c.stop());
  await mock?.close();
  mock = undefined;
});
const base = () => `http://127.0.0.1:${mock!.port}`;
async function start(opts: MockOptions = {}) {
  mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, actionDelayMs: 5, log: () => {}, ...opts });
}
async function open(player: string) {
  const token = (await (await fetch(`${base()}/dev/token`, { method: "POST", body: JSON.stringify({ account: player }) })).json()).token as string;
  const conn = new MainframeConn({ baseUrl: base(), token, mainframeId: mock!.base.id } as never);
  conns.push(conn);
  conn.start();
  for (let i = 0; i < 300 && conn.status.value !== "open"; i++) await sleep(10);
  return conn;
}
async function waitFor(pred: () => boolean, ms = 4000) {
  const end = Date.now() + ms;
  while (!pred()) {
    if (Date.now() > end) throw new Error("timeout");
    await sleep(20);
  }
}
const err = (p: Promise<unknown>) => p.then(() => "ok", (e: Error & { detail?: string }) => (e.detail ? `${e.message}: ${e.detail}` : e.message));

describe("code protocol (mock)", () => {
  it("lists the seeded scripts, starts autostart ones and pushes status", async () => {
    await start();
    const conn = await open("Enrico");
    expect(conn.can("code.edit")).toBe(true);
    await conn.codeOp("list");
    const doc = conn.code.value!;
    expect(doc.enabled).toBe(true);
    expect(doc.limits).toEqual(DEFAULT_LIMITS);
    expect(doc.scripts.map((s) => s.name)).toEqual(["Reactor guard", "Storage report", "Door timer"]);
    expect(doc.scripts[2]).toMatchObject({ status: "error", lastError: { line: 6 } });
    // the autostart script runs once something talked to the simulation (the mod: when the mainframe loads)
    await waitFor(() => conn.code.value!.scripts[0].status === "running");
    const guard = conn.code.value!.scripts[0];
    expect(guard).toMatchObject({ autostart: true, version: 3, author: "Enrico", startedBy: "Enrico", handlers: 2, timers: 1 });
    const g = await conn.codeOp("get", { id: guard.id });
    expect(g.script.source).toMatch(/Reactor guard/);
  });

  it("saves with conflict detection, checks, versions, reverts and renames", async () => {
    await start();
    const conn = await open("Enrico");
    await conn.codeOp("list");
    const created = await conn.codeOp<{ id: string; version: number; diagnostics: unknown[] }>("save", { name: "Test", source: "print('hi')" });
    expect(created).toMatchObject({ version: 1, diagnostics: [] });
    const bad = await conn.codeOp("check", { source: "if x then\n  print(1)\n" });
    expect(parseDiagnostics(bad.diagnostics)).toEqual([{ line: 3, column: null, message: expect.stringMatching(/^check:3: 'end' expected .*near <eof>$/), severity: "error" }]);
    // saving code with errors is allowed, starting it is not
    const v2 = await conn.codeOp("save", { id: created.id, name: "Test", source: "if x then", baseVersion: 1 });
    expect(v2.version).toBe(2);
    expect(parseDiagnostics(v2.diagnostics)).toHaveLength(1);
    expect(await err(conn.codeOp("start", { id: created.id }))).toMatch(/^has_errors: Test:1:/);
    // a stale base version
    expect(await err(conn.codeOp("save", { id: created.id, name: "Test", source: "x = 1", baseVersion: 1 }))).toBe("changed: 2");
    expect(await err(conn.codeOp("save", { name: "reactor GUARD", source: "x = 1" }))).toBe("name_taken");
    expect(await err(conn.codeOp("save", { name: "Big", source: "x".repeat(33_000) }))).toBe("too_large");
    const versions = await conn.codeOp("versions", { id: created.id });
    expect(versions.versions.map((v: { version: number }) => v.version)).toEqual([2, 1]);
    expect((await conn.codeOp("version", { id: created.id, version: 1 })).source).toBe("print('hi')");
    const rev = await conn.codeOp("revert", { id: created.id, version: 1 });
    expect(rev.version).toBe(3);
    expect((await conn.codeOp("versions", { id: created.id })).versions[0]).toMatchObject({ version: 3, note: "revert of v1", author: "Enrico" });
    expect(await err(conn.codeOp("version", { id: created.id, version: 99 }))).toBe("unknown_version");
    await conn.codeOp("rename", { id: created.id, name: "Renamed" });
    await waitFor(() => conn.code.value!.scripts.some((s) => s.name === "Renamed" && s.version === 3));
    await conn.codeOp("delete", { id: created.id });
    expect(await err(conn.codeOp("get", { id: created.id }))).toBe("unknown_script");
  });

  it("runs Lua against the mock's cards, streams the log and stops on errors", async () => {
    await start();
    const conn = await open("Enrico");
    await conn.codeOp("list");
    const src = [
      'print("temp", cloud.getValue("Fission Reactor", "temp"))',
      'local ok, err = cloud.set("Fission Reactor", "power", false)',
      'print("set", ok, err)',
      'print("pin", cloud.press("Fission Reactor", "scram"))',
      "cloud.store.set('n', 41)",
      "print('store', cloud.store.get('n') + 1)",
      'cloud.on("change", {card = "Fission Reactor", widget = "coolant"}, function(e) print("coolant", e.old, "->", e.value) end)',
    ].join("\n");
    const { id } = await conn.codeOp<{ id: string }>("save", { name: "Probe", source: src });
    await conn.codeOp("logs", { id });
    await conn.codeOp("start", { id });
    await waitFor(() => (conn.codeLogs.value[id] ?? []).some((l) => l.text.startsWith("store")));
    const texts = () => conn.codeLogs.value[id].map((l) => `${l.level}:${l.text}`);
    expect(texts()).toEqual(expect.arrayContaining(["info:temp 640", "info:set true nil", "info:pin false pin_required", "info:store 42"]));
    expect(mock!.base.uplinks.get("8a1f2c4e-reactor")!.widgets.find((w) => w.id === "power")!.value).toBe(false);
    // the handler sees value changes
    mock!.setValue(mock!.base, "8a1f2c4e-reactor", "coolant", 70);
    await waitFor(() => texts().includes("info:coolant 82 -> 70"));
    expect(await err(conn.codeOp("start", { id }))).toBe("already_running");
    await conn.codeOp("stop", { id });
    await waitFor(() => conn.code.value!.scripts.find((s) => s.id === id)!.status === "stopped");
    expect(conn.code.value!.scripts.find((s) => s.id === id)!.reason).toBe("stopped");
    // a runtime error stops it with the line
    await conn.codeOp("save", { id, name: "Probe", source: "cloud.sleep(0.1)\nlocal t = nil\nprint(t.x)" });
    await conn.codeOp("start", { id });
    await waitFor(() => conn.code.value!.scripts.find((s) => s.id === id)!.status === "error");
    expect(conn.code.value!.scripts.find((s) => s.id === id)!.lastError).toMatchObject({ line: 3, message: "Probe:3: attempt to index a nil value (local 't')" });
    // an endless loop is stopped, a pcall around it does not help
    await conn.codeOp("save", { id, name: "Probe", source: "pcall(function() while true do end end)\nprint('escaped')" });
    await conn.codeOp("start", { id });
    await waitFor(() => /too long/.test(conn.code.value!.scripts.find((s) => s.id === id)!.lastError?.message ?? ""));
    expect(conn.code.value!.scripts.find((s) => s.id === id)!.lastError!.message).toMatch(/ran too long without pausing/);
    expect(texts()).not.toContain("info:escaped");
    // unwatch ends the log pushes
    await conn.codeOp("unwatch", { id });
    expect(conn.codeLogs.value[id]).toBeUndefined();
  });

  it("needs code.edit, refuses share sessions and stops scripts when the mainframe goes offline", async () => {
    await start();
    const lisa = await open("Lisa");
    expect(await err(lisa.codeOp("list"))).toBe(lisa.can("code.edit") ? "ok" : "not_allowed");
    const conn = await open("Enrico");
    await conn.codeOp("list");
    await waitFor(() => conn.code.value!.scripts[0].status === "running");
    const control = (b: object) => fetch(`${base()}/dev/control`, { method: "POST", body: JSON.stringify(b) });
    // offline stops every script; the dashboard switch does not
    await control({ enabled: false });
    await sleep(100);
    expect(conn.code.value!.scripts[0].status).toBe("running");
    await control({ online: false });
    await waitFor(() => conn.code.value!.scripts[0].status === "stopped");
    expect(conn.code.value!.scripts[0].reason).toBe("offline");
    expect(await err(conn.codeOp("start", { id: conn.code.value!.scripts[1].id }))).toBe("offline");
    // back online: autostart scripts start again
    await control({ online: true, enabled: true });
    await waitFor(() => conn.code.value!.scripts[0].status === "running");
  });

  it("switched off on the server: no permission, no feature", async () => {
    await start({ scripts: false });
    const conn = await open("Enrico");
    expect(conn.can("code.edit")).toBe(false);
    expect(await err(conn.codeOp("list"))).toBe("not_allowed");
    const cfg = await (await fetch(`${base()}/api/config`)).json();
    expect(cfg.features.scripts).toBe(false);
  });
});
