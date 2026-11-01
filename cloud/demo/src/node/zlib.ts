// The part of node:zlib the simulation uses (dev/*.ts), for the Web Worker of the demo. PNGs only travel from the
// worker to the page here, so "stored" deflate blocks do: nothing is compressed.
import { BufferShim } from "./buffer.ts";

const TABLE = Uint32Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c;
});

export function crc32(data: Uint8Array) {
  let c = 0xffffffff;
  for (const b of data) c = TABLE[(c ^ b) & 255] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

export function deflateSync(data: Uint8Array) {
  const blocks = Math.max(1, Math.ceil(data.length / 0xffff));
  const out = new BufferShim(2 + blocks * 5 + data.length + 4);
  out.set([0x78, 0x01]);
  let at = 2;
  let a = 1;
  let b = 0;
  for (let i = 0; i < blocks; i++) {
    const part = data.subarray(i * 0xffff, (i + 1) * 0xffff);
    out.set([i === blocks - 1 ? 1 : 0, part.length & 255, part.length >> 8, ~part.length & 255, (~part.length >> 8) & 255], at);
    out.set(part, at + 5);
    at += 5 + part.length;
    for (const x of part) {
      a = (a + x) % 65521;
      b = (b + a) % 65521;
    }
  }
  new DataView(out.buffer).setUint32(at, ((b << 16) | a) >>> 0);
  return out;
}

/** Only for requests that accept gzip, and the demo's never do (a real fetch would be the one to unpack it). */
export function gzipSync(_data: Uint8Array): Uint8Array {
  throw new Error("the demo does not gzip");
}
