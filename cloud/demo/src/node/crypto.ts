// The part of node:crypto the simulation uses (dev/*.ts), for the Web Worker of the demo. createHash has to be
// synchronous, so SHA-256 is written out here; nothing in the demo is a secret, it only has to stay consistent.
import { Buffer, BufferShim } from "./buffer.ts";

const K = Uint32Array.from({ length: 64 }, (_, i) => {
  // the first 32 bits of the fractional parts of the cube roots of the first 64 primes
  const primes: number[] = [];
  for (let n = 2; primes.length < 64; n++) if (primes.every((p) => n % p)) primes.push(n);
  return Math.floor((Math.cbrt(primes[i]) % 1) * 2 ** 32);
});

function sha256(data: Uint8Array) {
  const h = Uint32Array.of(0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19);
  const padded = new Uint8Array((((data.length + 8) >> 6) + 1) * 64);
  padded.set(data);
  padded[data.length] = 0x80;
  const view = new DataView(padded.buffer);
  view.setUint32(padded.length - 8, Math.floor(data.length / 2 ** 29));
  view.setUint32(padded.length - 4, data.length << 3);
  const w = new Uint32Array(64);
  const rotr = (x: number, n: number) => (x >>> n) | (x << (32 - n));
  for (let at = 0; at < padded.length; at += 64) {
    for (let t = 0; t < 16; t++) w[t] = view.getUint32(at + t * 4);
    for (let t = 16; t < 64; t++) {
      const a = w[t - 15];
      const b = w[t - 2];
      w[t] = (w[t - 16] + (rotr(a, 7) ^ rotr(a, 18) ^ (a >>> 3)) + w[t - 7] + (rotr(b, 17) ^ rotr(b, 19) ^ (b >>> 10))) | 0;
    }
    let [a, b, c, d, e, f, g, hh] = h;
    for (let t = 0; t < 64; t++) {
      const t1 = (hh + (rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25)) + ((e & f) ^ (~e & g)) + K[t] + w[t]) | 0;
      const t2 = ((rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22)) + ((a & b) ^ (a & c) ^ (b & c))) | 0;
      hh = g;
      g = f;
      f = e;
      e = (d + t1) | 0;
      d = c;
      c = b;
      b = a;
      a = (t1 + t2) | 0;
    }
    h[0] += a;
    h[1] += b;
    h[2] += c;
    h[3] += d;
    h[4] += e;
    h[5] += f;
    h[6] += g;
    h[7] += hh;
  }
  const out = new BufferShim(32);
  const dv = new DataView(out.buffer);
  h.forEach((x, i) => dv.setUint32(i * 4, x));
  return out;
}

export function createHash(_algorithm: string) {
  const parts: Uint8Array[] = [];
  const hash = {
    update(d: string | Uint8Array) {
      parts.push(typeof d === "string" ? Buffer.from(d) : d);
      return hash;
    },
    digest(encoding?: string) {
      const out = sha256(Buffer.concat(parts));
      return encoding ? out.toString(encoding) : out;
    },
  };
  return hash;
}

export function randomBytes(n: number) {
  return crypto.getRandomValues(new BufferShim(n));
}

export const randomInt = (max: number) => Math.floor(Math.random() * max);

/** crypto.randomUUID only exists in secure contexts; a preview over plain HTTP has none. */
export function randomUUID() {
  const h = randomBytes(16).toString("hex");
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-a${h.slice(17, 20)}-${h.slice(20, 32)}`;
}
