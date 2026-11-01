// The live demo's stand-ins for node:crypto, node:zlib and Buffer: the mock's protocol code runs on them in the browser.
import { createHash as nodeHash } from "node:crypto";
import { inflateSync } from "node:zlib";
import { describe, expect, it } from "vitest";
import { Buffer as ShimBuffer } from "../demo/src/node/buffer.ts";
import { createHash as shimHash, randomBytes, randomUUID } from "../demo/src/node/crypto.ts";
import { crc32, deflateSync } from "../demo/src/node/zlib.ts";

const bytes = (n: number) => Uint8Array.from({ length: n }, (_, i) => (i * 31 + (i >> 3)) & 255);

describe("demo shims", () => {
  it("hashes like node's SHA-256 at every padding boundary", () => {
    for (const n of [0, 1, 55, 56, 63, 64, 65, 119, 120, 1000]) {
      const data = bytes(n);
      expect(shimHash("sha256").update(data).digest("hex")).toBe(nodeHash("sha256").update(data).digest("hex"));
    }
  });

  it("hashes text and base64url like node", () => {
    expect(shimHash("sha256").update("demo-session").digest("base64url")).toBe(nodeHash("sha256").update("demo-session").digest("base64url"));
  });

  it("makes ids and random bytes of the right shape", () => {
    expect(randomBytes(32)).toHaveLength(32);
    expect(randomUUID()).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  });

  it("writes deflate that real zlib reads", () => {
    for (const n of [0, 1, 70_000]) {
      const data = bytes(n);
      expect(new Uint8Array(inflateSync(deflateSync(data)))).toEqual(data);
    }
  });

  it("computes the CRC of PNG chunks", () => {
    expect(crc32(new TextEncoder().encode("IEND"))).toBe(0xae426082);
  });

  it("round-trips base64 like Buffer", () => {
    const data = bytes(257);
    const b64 = ShimBuffer.from(data).toString("base64");
    expect(b64).toBe(Buffer.from(data).toString("base64"));
    expect(new Uint8Array(ShimBuffer.from(b64, "base64"))).toEqual(data);
  });
});
