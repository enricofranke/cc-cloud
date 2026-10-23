// Script Store client: index validation, SHA-256 (also the plain-JS fallback), install records, updates, install links.
import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { createHash } from "node:crypto";
import { fileURLToPath } from "node:url";
import { compareVersions, parseIndex, readInstalled, recordInstall, sha256Hex, sha256Js, takeInstallLink, updatesFor } from "../web/src/lib/scriptstore.ts";

const indexFile = fileURLToPath(new URL("../../store/dist/index.json", import.meta.url));

describe("script store", () => {
  it("parses the real store index and drops malformed scripts", () => {
    const idx = parseIndex(JSON.parse(readFileSync(indexFile, "utf8")));
    expect(idx.scripts.length).toBeGreaterThan(3);
    expect(idx.scripts.find((s) => s.id === "control-room")).toMatchObject({ reviewed: true });
    const bad = parseIndex({
      format: 1,
      scripts: [
        { id: "ok-script", version: "1.0.0", files: [{ path: "a.lua", size: 1, sha256: "a".repeat(64) }] },
        { id: "X", version: "1.0.0", files: [{ path: "a.lua", size: 1, sha256: "a".repeat(64) }] },
        { id: "evil-path", version: "1.0.0", files: [{ path: "rom/x.lua", size: 1, sha256: "a".repeat(64) }] },
        { id: "no-hash", version: "1.0.0", files: [{ path: "a.lua", size: 1, sha256: "zz" }] },
      ],
    });
    expect(bad.scripts.map((s) => s.id)).toEqual(["ok-script"]);
    expect(() => parseIndex({ format: 2, scripts: [] })).toThrow();
  });

  it("hashes like Node's crypto, with and without crypto.subtle", async () => {
    for (const text of ["", "abc", "x".repeat(1000), "äöü – 🚀"]) {
      const data = new TextEncoder().encode(text);
      const want = createHash("sha256").update(data).digest("hex");
      expect(sha256Js(data)).toBe(want);
      expect(await sha256Hex(data)).toBe(want);
    }
  });

  it("remembers installs and finds updates", () => {
    const mem = new Map<string, string>();
    const store = { getItem: (k: string) => mem.get(k) ?? null, setItem: (k: string, v: string) => void mem.set(k, v) };
    const idx = parseIndex(JSON.parse(readFileSync(indexFile, "utf8")));
    const sc = idx.scripts[0];
    recordInstall("mf1", "up1", { ...sc, version: "0.9.0" }, store);
    const inst = readInstalled("mf1", store);
    expect(inst.up1[sc.files[0].path]).toMatchObject({ id: sc.id, version: "0.9.0" });
    expect(updatesFor(inst, idx)).toEqual([{ uplinkId: "up1", id: sc.id, from: "0.9.0", to: sc }]);
    expect(compareVersions("1.10.0", "1.9.9")).toBeGreaterThan(0);
    expect(compareVersions("1.0.0-beta", "1.0.0")).toBeLessThan(0);
  });

  it("reads /store#install links", () => {
    expect(takeInstallLink({ pathname: "/store", hash: "#install=control-room@1.2.0" })).toEqual({ want: { id: "control-room", version: "1.2.0" }, scrub: true });
    expect(takeInstallLink({ pathname: "/store/", hash: "#install=cloud-test" })).toEqual({ want: { id: "cloud-test" }, scrub: true });
    expect(takeInstallLink({ pathname: "/store", hash: "#install=../../etc" })).toEqual({ want: null, scrub: true });
    expect(takeInstallLink({ pathname: "/", hash: "#install=cloud-test" })).toEqual({ want: null, scrub: false });
  });
});

describe("script store in the mock", () => {
  it("announces the store to admins only and serves the index", async () => {
    const { createMockServer } = await import("../dev/mock-server.ts");
    const { TestSocket } = await import("./helpers.ts");
    const mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, log: () => {} });
    try {
      const base = `http://127.0.0.1:${mock.port}`;
      const tok = async (role?: string) => (await (await fetch(`${base}/dev/token`, { method: "POST", body: JSON.stringify({ name: "V", ...(role ? { role } : {}) }) })).json()).token as string;
      const snap = async (t: string) => {
        const ws = new TestSocket(`ws://127.0.0.1:${mock.port}/ws/client`);
        await ws.opened();
        ws.send({ t: "auth", token: t });
        const m = await ws.next((x) => x.t === "snapshot");
        ws.ws.close();
        return m;
      };
      const admin = await snap(await tok());
      expect(admin.store.url).toMatch(/\/store\/$/);
      expect(admin.perms).toContain("scripts.upload");
      const viewer = await snap(await tok("viewer"));
      expect(viewer.store).toBeNull();
      expect(viewer.perms).toEqual(["view", "storage.search"]);
      const idx = parseIndex(await (await fetch(`${base}/store/index.json`)).json());
      expect(idx.scripts.length).toBeGreaterThan(0);
      const sc = idx.scripts[0];
      const file = new Uint8Array(await (await fetch(`${base}/store/files/${sc.id}/${sc.version}/${sc.files[0].path}`)).arrayBuffer());
      expect(sha256Js(file)).toBe(sc.files[0].sha256);
      expect((await fetch(`${base}/store/../package.json`)).status).toBe(404);
    } finally {
      await mock.close();
    }
  });
});
