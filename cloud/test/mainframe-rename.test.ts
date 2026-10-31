// Renaming a mainframe: the mainframe_rename message (mock: rights, length, live state delta, network node), and the
// client's handling of the new name and of the snapshot's alert history.
import { afterEach, describe, expect, it } from "vitest";
import { createMockServer, type MockServer } from "../dev/mock-server.ts";
import { TestSocket } from "./helpers.ts";

let mock: MockServer | undefined;
const sockets: TestSocket[] = [];

afterEach(async () => {
  for (const s of sockets.splice(0)) s.close();
  await mock?.close();
  mock = undefined;
});

async function start() {
  mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, log: () => {} });
  const base = `http://127.0.0.1:${mock.port}`;
  const token = async (body: object) => (await (await fetch(`${base}/dev/token`, { method: "POST", body: JSON.stringify({ name: "Vitest", ...body }) })).json()).token;
  return async (body: object) => {
    const c = new TestSocket(`ws://127.0.0.1:${mock!.port}/ws/client`);
    sockets.push(c);
    await c.opened();
    c.send({ t: "auth", token: await token(body) });
    const snapshot = await c.next("snapshot");
    return { c, snapshot };
  };
}

describe("mainframe_rename", () => {
  it("renames live for every client and goes back to the default with an empty name", async () => {
    const open = await start();
    const { c: admin, snapshot } = await open({});
    const { c: other } = await open({});
    const original = snapshot.mainframe.name;
    admin.send({ t: "mainframe_rename", reqId: "a", name: "  North Base " });
    expect(await admin.next("mainframe_result")).toMatchObject({ reqId: "a", ok: true });
    expect((await other.next("state")).name).toBe("North Base");
    const { snapshot: later } = await open({});
    expect(later.mainframe.name).toBe("North Base");
    admin.send({ t: "mainframe_rename", reqId: "b", name: "" });
    await admin.next("mainframe_result");
    expect(mock!.base.name).toBe(original);
  });

  it("needs layout.edit", async () => {
    const open = await start();
    const { c: viewer } = await open({ canControl: false });
    viewer.send({ t: "mainframe_rename", reqId: "v", name: "Mine now" });
    const denied = await viewer.next("mainframe_result");
    expect(denied.ok).toBe(false);
    expect(["read_only", "not_allowed"]).toContain(denied.error);
  });

  it("refuses names over 32 characters (strict schema)", async () => {
    const open = await start();
    const { c: admin } = await open({});
    admin.send({ t: "mainframe_rename", reqId: "l", name: "x".repeat(33) });
    const result = await admin.next((m) => m.reqId === "l");
    expect(result).toMatchObject({ ok: false, error: "invalid_message" });
    expect(mock!.base.name.length).toBeLessThanOrEqual(32);
  });
});
