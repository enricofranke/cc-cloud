// Tiny NBT reader/writer (gzip, big-endian Java edition) for editing SavedData files of a STOPPED server.
// Values are tagged: {type: "compound"|"list"|"string"|"int"|"long"|"byte"|"short"|"float"|"double"|"int_array"|..., value}
import { gunzipSync, gzipSync } from "node:zlib";
import { readFileSync, writeFileSync } from "node:fs";

const T = ["end", "byte", "short", "int", "long", "float", "double", "byte_array", "string", "list", "compound", "int_array", "long_array"];
const ID = Object.fromEntries(T.map((t, i) => [t, i]));

export function readNbtFile(path) {
  const raw = readFileSync(path);
  const buf = raw[0] === 0x1f && raw[1] === 0x8b ? gunzipSync(raw) : raw;
  let o = 0;
  const str = () => { const n = buf.readUInt16BE(o); o += 2; const s = buf.subarray(o, o + n).toString("utf8"); o += n; return s; };
  const payload = (t) => {
    switch (T[t]) {
      case "byte": return buf.readInt8(o++);
      case "short": { const v = buf.readInt16BE(o); o += 2; return v; }
      case "int": { const v = buf.readInt32BE(o); o += 4; return v; }
      case "long": { const v = buf.readBigInt64BE(o); o += 8; return v; }
      case "float": { const v = buf.readFloatBE(o); o += 4; return v; }
      case "double": { const v = buf.readDoubleBE(o); o += 8; return v; }
      case "byte_array": { const n = buf.readInt32BE(o); o += 4; const v = [...buf.subarray(o, o + n)]; o += n; return v; }
      case "string": return str();
      case "list": { const et = buf.readInt8(o++); const n = buf.readInt32BE(o); o += 4; const items = []; for (let i = 0; i < n; i++) items.push(payload(et)); return { elementType: T[et], items }; }
      case "compound": { const m = {}; for (;;) { const ct = buf.readInt8(o++); if (ct === 0) return m; const k = str(); m[k] = { type: T[ct], value: payload(ct) }; } }
      case "int_array": { const n = buf.readInt32BE(o); o += 4; const v = []; for (let i = 0; i < n; i++) { v.push(buf.readInt32BE(o)); o += 4; } return v; }
      case "long_array": { const n = buf.readInt32BE(o); o += 4; const v = []; for (let i = 0; i < n; i++) { v.push(buf.readBigInt64BE(o)); o += 8; } return v; }
    }
    throw new Error(`bad tag ${t}`);
  };
  const t = buf.readInt8(o++);
  const name = str();
  return { name, root: { type: T[t], value: payload(t) } };
}

export function writeNbtFile(path, { name, root }) {
  const parts = [];
  const u8 = (v) => parts.push(Buffer.from([v & 0xff]));
  const str = (s) => { const b = Buffer.from(s, "utf8"); const h = Buffer.alloc(2); h.writeUInt16BE(b.length); parts.push(h, b); };
  const payload = (type, v) => {
    const b = Buffer.alloc(8);
    switch (type) {
      case "byte": u8(v); return;
      case "short": b.writeInt16BE(v); parts.push(b.subarray(0, 2)); return;
      case "int": b.writeInt32BE(v); parts.push(b.subarray(0, 4)); return;
      case "long": b.writeBigInt64BE(BigInt(v)); parts.push(b); return;
      case "float": b.writeFloatBE(v); parts.push(b.subarray(0, 4)); return;
      case "double": b.writeDoubleBE(v); parts.push(b); return;
      case "byte_array": b.writeInt32BE(v.length); parts.push(b.subarray(0, 4), Buffer.from(v)); return;
      case "string": str(v); return;
      case "list": u8(v.items.length ? ID[v.elementType] : 0); b.writeInt32BE(v.items.length); parts.push(Buffer.from(b.subarray(0, 4))); for (const it of v.items) payload(v.elementType, it); return;
      case "compound": for (const [k, c] of Object.entries(v)) { u8(ID[c.type]); str(k); payload(c.type, c.value); } u8(0); return;
      case "int_array": b.writeInt32BE(v.length); parts.push(Buffer.from(b.subarray(0, 4))); for (const x of v) { const e = Buffer.alloc(4); e.writeInt32BE(x); parts.push(e); } return;
      case "long_array": b.writeInt32BE(v.length); parts.push(Buffer.from(b.subarray(0, 4))); for (const x of v) { const e = Buffer.alloc(8); e.writeBigInt64BE(BigInt(x)); parts.push(e); } return;
    }
    throw new Error(`bad type ${type}`);
  };
  u8(ID[root.type]);
  str(name);
  payload(root.type, root.value);
  writeFileSync(path, gzipSync(Buffer.concat(parts)));
}

/** Plain JS view of a tagged value (for printing). */
export function plain(tag) {
  if (tag.type === "compound") return Object.fromEntries(Object.entries(tag.value).map(([k, v]) => [k, plain(v)]));
  if (tag.type === "list") return tag.value.items.map((it) => plain({ type: tag.value.elementType, value: it }));
  if (typeof tag.value === "bigint") return tag.value.toString();
  return tag.value;
}

export const S = (value) => ({ type: "string", value });
export const L = (value) => ({ type: "long", value: BigInt(value) });
export const B = (value) => ({ type: "byte", value: value ? 1 : 0 });
export const C = (value) => ({ type: "compound", value });
