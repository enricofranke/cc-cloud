// Script import: the mock mirrors docs/SPEC.md "Script import – HTTP API"; client helpers.
import { afterEach, describe, expect, it } from "vitest";
import { createMockServer, COMPUTER, CONTROL, ME, type MockServer } from "../dev/mock-server.ts";
import { checkPath } from "../dev/computer-fs.ts";
import { describeScript, pathError, scriptApi, suggestName, titleOf, ScriptError } from "../web/src/lib/scripts.ts";

let mock: MockServer | undefined;
afterEach(async () => {
  await mock?.close();
  mock = undefined;
});
const base = () => `http://127.0.0.1:${mock!.port}`;
async function token(body: object = {}) {
  return (await (await fetch(`${base()}/dev/token`, { method: "POST", body: JSON.stringify({ name: "Vitest", ...body }) })).json()).token as string;
}
async function start() {
  mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, log: () => {} });
  return mock.base.id;
}

describe("paths", () => {
  it("client and mock agree on the path rules", () => {
    const cases: [string, boolean][] = [
      ["control-room.lua", true],
      ["lib/util.lua", true],
      ["a/b/c.lua", false],
      ["/abs.lua", false],
      ["..", false],
      ["lib/..", false],
      ["rom/x.lua", false],
      ["disk2/x.lua", false],
      [".cloud/run_next", false],
      ["bad name.lua", false],
      ["back\\slash.lua", false],
      ["x".repeat(65), false],
      ["", false],
    ];
    for (const [p, ok] of cases) {
      expect(pathError(p) === null, `client ${p}`).toBe(ok);
      expect(checkPath(p) !== null, `mock ${p}`).toBe(ok);
    }
  });

  it("suggests file names", () => {
    expect(suggestName("Control Room.LUA")).toBe("Control-Room.lua");
    expect(suggestName("C:\\\\Users\\\\me\\\\farm.txt")).toBe("farm.lua");
    expect(suggestName("???")).toBe("program.lua");
  });

  it("describes example scripts from their header comment", () => {
    expect(describeScript("-- CC: Cloud example: a small control room.\n-- Run: control-room\nlocal x")).toEqual({ text: "A small control room.", run: "control-room" });
    expect(titleOf("control-room.lua")).toBe("Control room");
  });
});

describe("script import API (mock)", () => {
  it("is admin only and knows which cards have a computer", async () => {
    const mf = await start();
    const url = `${base()}/api/mainframes/${mf}/computers/${CONTROL}/files`;
    expect((await fetch(url)).status).toBe(401);
    const op = await token({ role: "operator" });
    expect(await (await fetch(url, { headers: { authorization: `Bearer ${op}` } })).json()).toEqual({ error: "not_allowed" });
    const t = await token();
    const list = await (await fetch(url, { headers: { authorization: `Bearer ${t}` } })).json();
    expect(list.computer).toMatchObject({ id: 0, advanced: true });
    expect(list.files.map((f: { path: string }) => f.path)).toEqual(["lib", "control-room.lua", "lib/util.lua", "startup.lua"]);
    const me = await fetch(`${base()}/api/mainframes/${mf}/computers/${ME}/files`, { headers: { authorization: `Bearer ${t}` } });
    expect(me.status).toBe(404);
    expect((await me.json()).error).toBe("unknown_computer");
  });

  it("writes with the PIN, refuses overwrites, writes startup.lua and runs", async () => {
    const mf = await start();
    const api = scriptApi(base(), await token(), mf, CONTROL);
    await expect(api.write("farm.lua", "print(1)", {})).rejects.toMatchObject({ code: "pin_required", status: 403 });
    await expect(api.write("farm.lua", "print(1)", { pin: "0000" })).rejects.toMatchObject({ code: "wrong_pin", attemptsLeft: 4 });
    expect(await api.write("farm.lua", "print(1)", { pin: "1234", startup: true })).toEqual({ ok: true, path: "farm.lua", size: 8 });
    expect(await api.read("startup.lua")).toBe('shell.run("farm.lua")\n');
    await expect(api.write("farm.lua", "print(2)", { pin: "1234" })).rejects.toMatchObject({ code: "exists", status: 409 });
    await api.write("farm.lua", "print(2)", { pin: "1234", overwrite: true });
    expect(await api.read("farm.lua")).toBe("print(2)");
    await expect(api.write("rom/x.lua", "x", { pin: "1234" })).rejects.toMatchObject({ code: "invalid_path" });
    expect(await api.run("farm.lua", "auto", "1234")).toBe("event");
    await api.remove("farm.lua", "1234");
    await expect(api.read("farm.lua")).rejects.toMatchObject({ code: "not_found" });
  });

  it("basic computers need a reboot to run", async () => {
    const mf = await start();
    const api = scriptApi(base(), await token(), mf, COMPUTER);
    await api.write("door.lua", "rs.setOutput('left', true)", { pin: "1234" });
    await expect(api.run("door.lua", "auto", "1234")).rejects.toMatchObject({ code: "needs_reboot", status: 409 });
    expect(await api.run("door.lua", "reboot", "1234")).toBe("reboot");
  });

  it("limits size and rate", async () => {
    const mf = await start();
    const api = scriptApi(base(), await token(), mf, CONTROL);
    await expect(api.write("big.lua", "x".repeat(256 * 1024 + 1), { pin: "1234" })).rejects.toMatchObject({ code: "too_large" });
    for (let i = 0; i < 10; i++) await api.write(`f${i}.lua`, "x", { pin: "1234" });
    const e = await api.write("f10.lua", "x", { pin: "1234" }).catch((x) => x);
    expect(e).toBeInstanceOf(ScriptError);
    expect(e.code).toBe("rate_limited");
  });
});
