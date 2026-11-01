// The part of Node's Buffer the simulation uses (dev/*.ts), for the Web Worker of the demo.
const encoder = new TextEncoder();
const decoder = new TextDecoder();

function toBase64(b: Uint8Array) {
  let s = "";
  for (let i = 0; i < b.length; i += 0x8000) s += String.fromCharCode(...b.subarray(i, i + 0x8000));
  return btoa(s);
}
function fromBase64(s: string) {
  const bin = atob(s.replace(/-/g, "+").replace(/_/g, "/"));
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}
const toHex = (b: Uint8Array) => Array.from(b, (x) => x.toString(16).padStart(2, "0")).join("");

export class BufferShim extends Uint8Array {
  toString(encoding: string = "utf8"): string {
    if (encoding === "base64") return toBase64(this);
    if (encoding === "base64url") return toBase64(this).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
    if (encoding === "hex") return toHex(this);
    return decoder.decode(this);
  }
  writeUInt32BE(v: number, at = 0) {
    new DataView(this.buffer, this.byteOffset, this.byteLength).setUint32(at, v, false);
    return at + 4;
  }
  writeInt16LE(v: number, at = 0) {
    new DataView(this.buffer, this.byteOffset, this.byteLength).setInt16(at, v, true);
    return at + 2;
  }
}

const wrap = (b: Uint8Array) => new BufferShim(b.buffer as ArrayBuffer, b.byteOffset, b.byteLength);

export const Buffer = {
  alloc: (n: number) => new BufferShim(n),
  from(v: string | ArrayBuffer | ArrayLike<number>, encoding?: string) {
    if (typeof v !== "string") return v instanceof ArrayBuffer ? new BufferShim(v) : new BufferShim(Array.from(v as ArrayLike<number>));
    if (encoding === "base64" || encoding === "base64url") return wrap(fromBase64(v));
    if (encoding === "ascii") return new BufferShim(Array.from(v, (c) => c.charCodeAt(0)));
    return wrap(encoder.encode(v));
  },
  concat(list: Uint8Array[]) {
    const out = new BufferShim(list.reduce((n, b) => n + b.length, 0));
    let at = 0;
    for (const b of list) {
      out.set(b, at);
      at += b.length;
    }
    return out;
  },
  isBuffer: (v: unknown) => v instanceof BufferShim,
};
