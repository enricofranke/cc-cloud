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

describe("signed store (v0.9)", () => {
  it("verifies Ed25519 signatures in the browser code", async () => {
    const { generateKeyPairSync, sign } = await import("node:crypto");
    const { verifyEd25519 } = await import("../web/src/lib/scriptstore.ts");
    const { publicKey, privateKey } = generateKeyPairSync("ed25519");
    const spki = publicKey.export({ type: "spki", format: "der" });
    const key = Buffer.from(spki.subarray(spki.length - 32)).toString("base64");
    const data = new TextEncoder().encode('{"format":1,"scripts":[]}');
    const sig = sign(null, data, privateKey).toString("base64");
    expect(await verifyEd25519(data, sig, key)).toBe(true);
    expect(await verifyEd25519(new TextEncoder().encode('{"format":1,"scripts":[ ]}'), sig, key)).toBe(false);
    expect(await verifyEd25519(data, "AAAA", key)).toBe(false);
  });

  it("signature states, store installs checked by the server, records with changed/missing, public config", async () => {
    const { createMockServer, CONTROL } = await import("../dev/mock-server.ts");
    const { fetchIndex } = await import("../web/src/lib/scriptstore.ts");
    const { scriptApi } = await import("../web/src/lib/scripts.ts");
    const { parseConfig } = await import("../web/src/lib/config.ts");
    for (const [mode, want] of [["valid", "signed"], ["bad", "bad"], ["none", "unsigned"]] as const) {
      const mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, storeSignature: mode, log: () => {} });
      try {
        const base = `http://127.0.0.1:${mock.port}`;
        const cfg = parseConfig(await (await fetch(`${base}/api/config`)).json());
        expect(cfg.features.terminal).toBe(true);
        const url = `${base}/store/`;
        const idx = await fetchIndex(url, cfg.store.key);
        expect(idx.signature).toBe(want);
        expect((await fetchIndex(url, null)).signature).toBe("no_key");
        if (mode !== "valid") continue;
        const tok = (await (await fetch(`${base}/dev/token`, { method: "POST", body: JSON.stringify({ name: "V" }) })).json()).token as string;
        const api = scriptApi(base, tok, mock.base.id, CONTROL);
        const sc = idx.scripts[0];
        const f = sc.files[0];
        const src = await (await fetch(`${url}files/${sc.id}/${sc.version}/${f.path}`)).text();
        await api.write(f.path, src, { overwrite: true, pin: "1234", store: `${sc.id}@${sc.version}` });
        let list = await api.list();
        expect(list.store?.[f.path]).toMatchObject({ id: sc.id, version: sc.version, changed: false, missing: false });
        await expect(api.write(f.path, src + "\n-- evil", { overwrite: true, pin: "1234", store: `${sc.id}@${sc.version}` })).rejects.toMatchObject({ code: "store_mismatch", status: 409 });
        await expect(api.write(f.path, src, { overwrite: true, pin: "1234", store: "Bad Ref" })).rejects.toMatchObject({ code: "invalid_store_ref" });
        await expect(api.write(f.path, src, { overwrite: true, pin: "1234", store: `${sc.id}@99.0.0` })).rejects.toMatchObject({ code: "store_unknown_script" });
        // a plain overwrite keeps the record: the file shows as changed
        await api.write(f.path, "print('mine')", { overwrite: true, pin: "1234" });
        list = await api.list();
        expect(list.store?.[f.path].changed).toBe(true);
      } finally {
        await mock.close();
      }
    }
  });
});
