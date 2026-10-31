// Factory Manager program: read on demand, check, write, undo; rights, PIN and share links (mock + client).
import { afterEach, describe, expect, it } from "vitest";
import { createMockServer, type MockServer } from "../dev/mock-server.ts";
import { MainframeConn } from "../web/src/lib/conn.ts";
import { lineCount, programDiff, programFileError, type ProgramDetail } from "../web/src/lib/sfm.ts";
import { sleep } from "./helpers.ts";

let mock: MockServer | undefined;
const conns: MainframeConn[] = [];
afterEach(async () => {
  conns.splice(0).forEach((c) => c.stop());
  await mock?.close();
  mock = undefined;
});
async function connect(body: object = {}) {
  const base = `http://127.0.0.1:${mock!.port}`;
  const t = (await (await fetch(`${base}/dev/token`, { method: "POST", body: JSON.stringify({ name: "Vitest", ...body }) })).json()).token;
  const conn = new MainframeConn({ baseUrl: base, token: t });
  conns.push(conn);
  conn.start();
  for (let i = 0; i < 200 && conn.status.value !== "open"; i++) await sleep(10);
  return conn;
}
const sfm = (_conn: MainframeConn) => "sfm";
const start = async (o: object = {}) => {
  mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, actionDelayMs: 5, log: () => {}, ...o });
  mock.base.uplinks.set("sfm", { uplinkId: "sfm", title: "Factory Manager", online: true, mode: "direct", template: "sfm_manager", restricted: true, widgets: [] });
};
const GOOD = "EVERY 20 TICKS DO\n  INPUT FROM a\nEND\n";

describe("sfm program (lib)", () => {
  it("counts lines and finds the changed middle", () => {
    expect(lineCount("")).toBe(0);
    expect(lineCount("a\nb\n\n")).toBe(2);
    expect(programDiff("a\nb\nc\n", "a\nx\ny\nc")).toEqual({ oldLines: 3, newLines: 4, same: 1, removed: ["b"], added: ["x", "y"] });
    expect(programDiff("", "a")).toMatchObject({ oldLines: 0, newLines: 1, removed: [], added: ["a"] });
    expect(programDiff("a\r\n", "a\n")).toMatchObject({ removed: [], added: [] });
  });
  it("only takes small program files", () => {
    expect(programFileError("base.sfml", 10)).toBeNull();
    expect(programFileError("base.TXT", 10)).toBeNull();
    expect(programFileError("base.exe", 10)).toMatch(/\.sfml/);
    expect(programFileError("base.sfm", 40000)).toMatch(/too large/);
  });
});

describe("sfm program (mock)", () => {
  it("reads, refuses a broken program, writes it on confirm, undoes once", async () => {
    await start();
    const conn = await connect();
    const id = sfm(conn);
    const doc = await conn.cardDetail<ProgramDetail>(id);
    expect(doc.program).toContain("EVERY");
    expect(doc.undo).toBe(false);
    expect(await conn.cardWrite(id, "check", { text: "EVERY" })).toEqual({ errors: expect.arrayContaining([expect.any(String)]) });
    await expect(conn.cardWrite(id, "save", { text: "EVERY" })).rejects.toThrow("compile_failed");
    expect((await conn.cardDetail<ProgramDetail>(id)).program).toBe(doc.program);
    const saved = await conn.cardWrite<ProgramDetail>(id, "save", { text: "EVERY", force: true });
    expect(saved).toMatchObject({ program: "EVERY", undo: true });
    expect(await conn.cardWrite<ProgramDetail>(id, "undo")).toMatchObject({ program: doc.program, undo: false });
    await expect(conn.cardWrite(id, "undo")).rejects.toThrow("nothing_to_undo");
  });
  it("needs both rights: viewers can neither read nor write, and writing needs device.program", async () => {
    await start();
    const owner = await connect();
    const id = sfm(owner);
    const viewer = await connect({ role: "viewer" });
    await expect(viewer.cardDetail(id)).rejects.toThrow();
    await expect(viewer.cardWrite(id, "save", { text: GOOD })).rejects.toThrow();
    const reader = await connect({ role: "operator" });
    expect((await reader.cardDetail<ProgramDetail>(id)).disk).toBe(true);
    await expect(reader.cardWrite(id, "save", { text: GOOD })).rejects.toThrow("not_allowed");
    expect((await owner.cardDetail<ProgramDetail>(id)).undo).toBe(false);
  });
  it("asks for the PIN before writing, not for a check", async () => {
    await start({ requirePin: true });
    const conn = await connect();
    const id = sfm(conn);
    expect(await conn.cardWrite(id, "check", { text: GOOD })).toEqual({ errors: [] });
    await expect(conn.cardWrite(id, "save", { text: GOOD })).rejects.toThrow("pin_required");
    await expect(conn.cardWrite(id, "save", { text: GOOD, pin: "9999" })).rejects.toMatchObject({ message: "wrong_pin", attemptsLeft: 4 });
    expect(await conn.cardWrite<ProgramDetail>(id, "save", { text: GOOD, pin: "1234" })).toMatchObject({ program: GOOD });
  });
});
