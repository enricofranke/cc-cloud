// v0.5 E browser terminal: frames, key codes, and the mock protocol through MainframeConn.
import { afterEach, describe, expect, it } from "vitest";
import { createMockServer, CONTROL, type MockServer } from "../dev/mock-server.ts";
import { MainframeConn } from "../web/src/lib/conn.ts";
import { applyTermFrame, colourRuns, glfwKey, isPrintable, pasteText } from "../web/src/lib/terminal.ts";
import { sleep } from "./helpers.ts";

let mock: MockServer | undefined;
const conns: MainframeConn[] = [];
afterEach(async () => {
  conns.splice(0).forEach((c) => c.stop());
  await mock?.close();
  mock = undefined;
});

describe("terminal helpers", () => {
  it("applies full and partial frames", () => {
    const a = applyTermFrame(null, { t: "term", on: true, full: true, w: 5, h: 2, cursor: { x: 2, y: 1, blink: true }, rows: [{ y: 1, text: "ab\u0007", fg: "01", bg: "ff" }] })!;
    expect(a.text).toEqual(["ab   ", "     "]);
    expect(a.fg[0]).toBe("01000");
    const b = applyTermFrame(a, { t: "term", on: true, full: false, rows: [{ y: 2, text: "xyz", fg: "zz!", bg: "fff" }] })!;
    expect(b.text[1]).toBe("xyz  ");
    expect(b.fg[1]).toBe("00000"); // invalid blit kept the old colours
    expect(b.text[0]).toBe("ab   ");
    expect(applyTermFrame(b, { on: false })!.on).toBe(false);
    expect(colourRuns("abcd", "0011", "ffff")).toEqual([{ text: "ab", fg: "0", bg: "f" }, { text: "cd", fg: "1", bg: "f" }]);
  });
  it("maps keys like CC's keys API", () => {
    expect(glfwKey("Enter")).toBe(257);
    expect(glfwKey("KeyA")).toBe(65);
    expect(glfwKey("Digit0")).toBe(48);
    expect(glfwKey("F1")).toBe(290);
    expect(glfwKey("ArrowUp")).toBe(265);
    expect(glfwKey("Unidentified")).toBeNull();
    expect(isPrintable("a")).toBe(true);
    expect(isPrintable("Enter")).toBe(false);
    expect(isPrintable("€")).toBe(false);
    expect(pasteText("ls\nrm -rf")).toBe("ls");
  });
});

describe("terminal protocol (mock)", () => {
  it("opens, types, runs a command and refuses viewers", async () => {
    mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, log: () => {} });
    const base = `http://127.0.0.1:${mock.port}`;
    const tok = async (p: string) => (await (await fetch(`${base}/dev/token`, { method: "POST", body: JSON.stringify({ account: p }) })).json()).token as string;
    const conn = new MainframeConn({ baseUrl: base, token: await tok("Enrico"), mainframeId: mock.base.id } as never);
    conns.push(conn);
    conn.start();
    while (conn.status.value !== "open") await sleep(10);
    await expect(conn.termOpen(CONTROL)).rejects.toThrow("pin_required");
    await conn.termOpen(CONTROL, "1234");
    await sleep(30);
    const t0 = conn.terminals.value[CONTROL];
    expect(t0.on).toBe(true);
    expect(t0.text[0]).toMatch(/^CraftOS/);
    conn.termInput(CONTROL, [..."help"].map((c) => ({ type: "char" as const, c })).concat([{ type: "key", key: 257, held: false } as never]));
    await sleep(50);
    expect(conn.terminals.value[CONTROL].text.join("\n")).toMatch(/Try: ls/);
    conn.termInput(CONTROL, [{ type: "shutdown" }]);
    await sleep(30);
    expect(conn.terminals.value[CONTROL].on).toBe(false);
    conn.termClose(CONTROL);
    expect(conn.terminals.value[CONTROL]).toBeUndefined();
    const viewer = new MainframeConn({ baseUrl: base, token: await tok("Sam"), mainframeId: mock.base.id } as never);
    conns.push(viewer);
    viewer.start();
    while (viewer.status.value !== "open") await sleep(10);
    await expect(viewer.termOpen(CONTROL)).rejects.toThrow("read_only");
  });
});
