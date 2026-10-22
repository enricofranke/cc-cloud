// v0.3 item icons: URL building, letter-tile colours, mock /api/icon route.
import { afterEach, describe, expect, it } from "vitest";
import { createMockServer, type MockServer } from "../dev/mock-server.ts";
import { iconUrl, namespaceHue } from "../web/src/lib/icons.ts";

let mock: MockServer | undefined;
afterEach(async () => {
  await mock?.close();
  mock = undefined;
});

describe("iconUrl", () => {
  it("maps item ids to /api/icon/<ns>/<path>.png", () => {
    expect(iconUrl("minecraft:iron_ingot")).toBe("/api/icon/minecraft/iron_ingot.png");
    expect(iconUrl("mod:tools/wrench")).toBe("/api/icon/mod/tools/wrench.png");
  });
  it("refuses ids that could not be valid (no traversal, no odd characters)", () => {
    for (const bad of ["iron", ":x", "Minecraft:Stone", "a:../b", "a:b/../c", "a:b?c", "a:b c", "a:", "a:b//c"]) expect(iconUrl(bad)).toBeNull();
  });
});

describe("namespaceHue", () => {
  it("is stable per namespace and differs between mods", () => {
    expect(namespaceHue("ae2:a")).toBe(namespaceHue("ae2:b"));
    expect(namespaceHue("ae2:a")).not.toBe(namespaceHue("mekanism:a"));
    expect(namespaceHue("minecraft:x")).toBeGreaterThanOrEqual(0);
    expect(namespaceHue("minecraft:x")).toBeLessThan(360);
  });
});

describe("mock /api/icon", () => {
  it("serves sample PNGs publicly and 404s the rest", async () => {
    mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, log: () => {} });
    const base = `http://127.0.0.1:${mock.port}`;
    const ok = await fetch(`${base}/api/icon/minecraft/diamond.png`);
    expect(ok.status).toBe(200);
    expect(ok.headers.get("content-type")).toBe("image/png");
    expect(ok.headers.get("cache-control")).toContain("max-age=86400");
    const buf = Buffer.from(await ok.arrayBuffer());
    expect([...buf.subarray(0, 8)]).toEqual([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
    expect(buf.readUInt32BE(16)).toBe(32); // IHDR width
    expect(buf.readUInt32BE(20)).toBe(32); // IHDR height
    expect((await fetch(`${base}/api/icon/minecraft/dirt.png`)).status).toBe(404);
    expect((await fetch(`${base}/api/icon/minecraft/%2e%2e/x.png`)).status).toBe(404);
  });
});
